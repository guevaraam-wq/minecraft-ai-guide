package minecraft_ai_guide.client.guide;

import java.util.*;

/** Conservative read-only voxel search. Every excavation is performed by the player. */
public final class NavigationPlanner {
    public record Cell(int x, int y, int z) {
        public Cell offset(int dx, int dy, int dz) { return new Cell(x + dx, y + dy, z + dz); }
        public Cell above() { return offset(0, 1, 0); }
        public Cell below() { return offset(0, -1, 0); }
    }
    public record Voxel(String identity, boolean known, boolean passable, boolean support,
            boolean hazard, boolean removable, boolean falling) {
        public static final Voxel UNKNOWN = new Voxel("unknown", false, false, false, true, false, false);
    }
    public interface World {
        Voxel voxel(Cell cell);
        default boolean allowSearch(boolean excavation) { return true; }
        default double reach() { return 4.0; }
        default double eyeHeight() { return 1.62; }
    }
    public enum Goal { HARVEST, INTERACT, WALK }
    public enum Status { REACHABLE_NOW, AFTER_EXCAVATION, INACCESSIBLE }
    public enum Action { WALK, MINE, ARRIVED, BLOCKED }
    public record Stage(Cell feet, List<Cell> removeBefore) {
        public Stage { removeBefore = List.copyOf(removeBefore); }
    }
    public record Route(Status status, Cell target, List<Stage> stages, Map<Cell, Voxel> watched, String reason) {
        public Route { stages = List.copyOf(stages); watched = Map.copyOf(watched); }
        public boolean valid(World world, Cell start) {
            return !stages.isEmpty() && stages.getFirst().feet().equals(start)
                    && watched.entrySet().stream().allMatch(e -> e.getValue().equals(world.voxel(e.getKey())));
        }
    }
    public record Instruction(Action action, Cell marker, List<Cell> walkPath, String text) {
        public Instruction { walkPath = List.copyOf(walkPath); }
    }
    private record Key(Cell feet, Set<Cell> removed) {}
    private record Node(Key key, double cost, double rank, Node parent, List<Cell> clearing) {}
    private static final int[][] SIDES = {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1}};
    private static final int[][] FACES = {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}};
    private static final int MAX_REMOVALS = 6;

    public static Route plan(World world, Cell start, Cell target, Goal goal, boolean excavation) {
        Route walking = search(world, start, target, goal, false);
        return walking.status() != Status.INACCESSIBLE || !excavation ? walking
                : search(world, start, target, goal, true);
    }

    private static Route search(World world, Cell start, Cell target, Goal goal, boolean excavation) {
        if (!standing(world, start, Set.of())) return unavailable(target, "The starting ground is not safe or fully known.");
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingDouble(Node::rank));
        Map<Key, Double> best = new HashMap<>();
        Key initial = new Key(start, Set.of());
        Node first = new Node(initial, 0, estimate(start, target, goal), null, List.of());
        if (atGoal(world, start, target, goal, Set.of())) return finish(world, first, target);
        queue.add(first);
        best.put(initial, 0.0);
        int visited = 0;
        int limit = excavation ? 6500 : 3500;
        while (!queue.isEmpty() && visited++ < limit && world.allowSearch(excavation)) {
            Node node = queue.remove();
            if (node.cost() > best.getOrDefault(node.key(), Double.POSITIVE_INFINITY)) continue;
            Cell feet = node.key().feet();
            if (atGoal(world, feet, target, goal, node.key().removed())) return finish(world, node, target);
            for (int[] side : SIDES) for (int dy : new int[]{0, -1, 1}) {
                Cell next = feet.offset(side[0], dy, side[2]);
                if (Math.abs(next.x() - start.x()) > 24 || Math.abs(next.z() - start.z()) > 24
                        || Math.abs(next.y() - start.y()) > 16) continue;
                // Head space is swept at both ends of steps; a low ceiling cannot be jumped through.
                Set<Cell> clearance = clearance(feet, next);
                List<Cell> cuts = new ArrayList<>();
                Set<Cell> removed = new HashSet<>(node.key().removed());
                boolean valid = true;
                for (Cell cell : clearance) {
                    if (open(world, cell, removed)) continue;
                    if (!excavation || removed.size() >= MAX_REMOVALS || cell.equals(target)
                            || !world.voxel(cell).removable() || !safeRemoval(world, feet, cell, removed)
                            || supportsEarlierStep(node, cell) || !visible(world, feet, cell, removed)) {
                        valid = false; break;
                    }
                    removed.add(cell); cuts.add(cell);
                }
                if (!valid || !standing(world, next, removed)) continue;
                Set<Cell> immutable = Set.copyOf(removed);
                Key key = new Key(next, immutable);
                double cost = node.cost() + 1 + Math.abs(dy) * .4 + cuts.size() * 8;
                if (cost >= best.getOrDefault(key, Double.POSITIVE_INFINITY)) continue;
                best.put(key, cost);
                queue.add(new Node(key, cost, cost + estimate(next, target, goal), node, List.copyOf(cuts)));
            }
        }
        return unavailable(target, "No checked route within the local search and six-block excavation limits.");
    }

    /** Revalidate the remaining plan without spending search budget or choosing a new target. */
    public static Route resume(World world, Route route, Cell current, Goal goal) {
        if (route.status() == Status.INACCESSIBLE || route.stages().isEmpty()) return route;
        if (!world.voxel(route.target()).equals(route.watched().get(route.target())))
            return unavailable(route.target(), "The destination block has changed.");
        int index = -1;
        for (int i = 0; i < route.stages().size(); i++) {
            Stage stage = route.stages().get(i);
            // A player cannot skip a still-solid clearance stage and call it completed.
            if (stage.removeBefore().stream().anyMatch(cell -> !open(world, cell, Set.of()))) break;
            if (stage.feet().equals(current)) index = i;
        }
        if (index < 0) return unavailable(route.target(), "You moved off the checked route; a new approach is needed.");
        if (!standing(world, current, Set.of()))
            return unavailable(route.target(), "The current footing is not safe or fully known.");
        Node previous = new Node(new Key(current, Set.of()), 0, 0, null, List.of());
        Set<Cell> removed = new HashSet<>();
        Set<Cell> protectedFloor = new HashSet<>();
        route.stages().forEach(stage -> protectedFloor.add(stage.feet().below()));
        for (int i = index + 1; i < route.stages().size(); i++) {
            Stage stage = route.stages().get(i);
            Cell from = previous.key().feet(), next = stage.feet();
            int dy = next.y() - from.y();
            if (Math.abs(next.x() - from.x()) + Math.abs(next.z() - from.z()) != 1 || Math.abs(dy) > 1)
                return unavailable(route.target(), "The route has an unsupported elevation change.");
            List<Cell> cuts = new ArrayList<>();
            for (Cell cell : clearance(from, next)) {
                if (open(world, cell, removed)) continue;
                if (!stage.removeBefore().contains(cell) || removed.size() >= MAX_REMOVALS
                        || cell.equals(route.target()) || protectedFloor.contains(cell)
                        || !world.voxel(cell).removable() || !safeRemoval(world, from, cell, removed)
                        || !visible(world, from, cell, removed)) {
                    return unavailable(route.target(), "Terrain, clearance, or tool checks no longer permit this route.");
                }
                removed.add(cell); cuts.add(cell);
            }
            if (!standing(world, next, removed))
                return unavailable(route.target(), "A route step now has unsafe footing or nearby hazards.");
            previous = new Node(new Key(next, Set.copyOf(removed)), previous.cost() + 1, 0,
                    previous, List.copyOf(cuts));
        }
        if (!atGoal(world, previous.key().feet(), route.target(), goal, removed))
            return unavailable(route.target(), "The destination no longer has a safe interaction approach.");
        return finish(world, previous, route.target());
    }

    private static Set<Cell> clearance(Cell from, Cell next) {
        Set<Cell> cells = new LinkedHashSet<>();
        if (next.y() > from.y()) cells.add(from.offset(0, 2, 0));
        if (next.y() < from.y()) cells.add(next.offset(0, 2, 0));
        cells.add(next.above()); cells.add(next);
        return cells;
    }

    private static boolean supportsEarlierStep(Node node, Cell block) {
        for (Node previous = node; previous != null; previous = previous.parent()) {
            if (block.equals(previous.key().feet().below())) return true;
        }
        return false;
    }

    private static boolean open(World world, Cell cell, Set<Cell> removed) {
        Voxel voxel = world.voxel(cell);
        return voxel.known() && !voxel.hazard() && (removed.contains(cell) || voxel.passable());
    }
    private static boolean floor(World world, Cell cell, Set<Cell> removed) {
        Voxel voxel = world.voxel(cell);
        return voxel.known() && voxel.support() && !voxel.hazard() && !voxel.falling() && !removed.contains(cell);
    }
    public static boolean standing(World world, Cell feet, Set<Cell> removed) {
        if (!open(world, feet, removed) || !open(world, feet.above(), removed) || !floor(world, feet.below(), removed)) return false;
        for (int[] side : SIDES) {
            Cell adjacent = feet.offset(side[0], 0, side[2]);
            if (!world.voxel(adjacent).known() || world.voxel(adjacent).hazard()
                    || world.voxel(adjacent.below()).hazard()) return false;
            // Avoid an unguarded side with a drop of two or more blocks.
            if (open(world, adjacent, removed) && !floor(world, adjacent.below(), removed)
                    && !floor(world, adjacent.offset(0, -2, 0), removed)) return false;
        }
        return true;
    }
    public static boolean safeRemoval(World world, Cell feet, Cell block, Set<Cell> removed) {
        Voxel voxel = world.voxel(block);
        if (!voxel.known() || voxel.hazard() || voxel.falling() || block.equals(feet.below())
                || (block.x() == feet.x() && block.z() == feet.z() && block.y() < feet.y())) return false;
        for (int[] face : FACES) {
            Voxel neighbor = world.voxel(block.offset(face[0], face[1], face[2]));
            if (!neighbor.known() || neighbor.hazard()) return false;
        }
        if (world.voxel(block.above()).falling()) return false;
        Set<Cell> after = new HashSet<>(removed);
        after.add(block);
        // A valid landing is not enough: each individual cut must leave the player safe too.
        return standing(world, feet, after);
    }
    private static boolean atGoal(World world, Cell feet, Cell target, Goal goal, Set<Cell> removed) {
        if (goal == Goal.WALK) return feet.equals(target);
        return visible(world, feet, target, removed)
                && (goal != Goal.HARVEST || safeRemoval(world, feet, target, removed));
    }
    public static boolean visible(World world, Cell feet, Cell target, Set<Cell> removed) {
        double x = feet.x() + .5, y = feet.y() + world.eyeHeight(), z = feet.z() + .5;
        double dx = target.x() + .5 - x, dy = target.y() + .5 - y, dz = target.z() + .5 - z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > world.reach()) return false;
        int samples = Math.max(1, (int) Math.ceil(distance * 12));
        for (int i = 0; i < samples; i++) {
            double t = (double) i / samples;
            Cell sample = new Cell((int) Math.floor(x + dx * t), (int) Math.floor(y + dy * t), (int) Math.floor(z + dz * t));
            if (!sample.equals(target) && !open(world, sample, removed)) return false;
        }
        return world.voxel(target).known() && !world.voxel(target).hazard();
    }
    private static double estimate(Cell feet, Cell target, Goal goal) {
        double distance = Math.sqrt(Math.pow(feet.x() - target.x(), 2) + Math.pow(feet.y() - target.y(), 2)
                + Math.pow(feet.z() - target.z(), 2));
        return Math.max(0, distance - (goal == Goal.WALK ? 0 : 5));
    }
    private static Route finish(World world, Node node, Cell target) {
        List<Stage> stages = new ArrayList<>();
        Map<Cell, Voxel> watched = new HashMap<>();
        boolean excavation = false;
        for (Node previous = node; previous != null; previous = previous.parent()) {
            stages.add(new Stage(previous.key().feet(), previous.clearing()));
            excavation |= !previous.clearing().isEmpty();
            Set<Cell> watch = new HashSet<>(previous.clearing());
            watch.add(previous.key().feet()); watch.add(previous.key().feet().below());
            watch.add(previous.key().feet().above()); watch.add(previous.key().feet().offset(0, 2, 0));
            for (int[] side : SIDES) for (int depth = 0; depth <= 2; depth++)
                watch.add(previous.key().feet().offset(side[0], -depth, side[2]));
            for (Cell cell : List.copyOf(watch)) for (int[] face : FACES) watch.add(cell.offset(face[0], face[1], face[2]));
            watch.forEach(cell -> watched.put(cell, world.voxel(cell)));
        }
        watched.put(target, world.voxel(target));
        for (int[] face : FACES) { Cell cell = target.offset(face[0], face[1], face[2]); watched.put(cell, world.voxel(cell)); }
        // Watch line-of-sight cells as well as the player's corridor.
        Cell end = node.key().feet();
        double x = end.x() + .5, y = end.y() + world.eyeHeight(), z = end.z() + .5;
        for (int i = 0; i <= 64; i++) {
            double t = i / 64.0;
            Cell cell = new Cell((int) Math.floor(x + (target.x() + .5 - x) * t),
                    (int) Math.floor(y + (target.y() + .5 - y) * t), (int) Math.floor(z + (target.z() + .5 - z) * t));
            watched.put(cell, world.voxel(cell));
        }
        Collections.reverse(stages);
        return new Route(excavation ? Status.AFTER_EXCAVATION : Status.REACHABLE_NOW, target, stages, watched, "");
    }
    private static Route unavailable(Cell target, String reason) {
        return new Route(Status.INACCESSIBLE, target, List.of(), Map.of(), reason);
    }
    public static Instruction next(World world, Route route, Cell current) {
        if (route.status() == Status.INACCESSIBLE || !route.valid(world, current))
            return new Instruction(Action.BLOCKED, null, List.of(), "Look for another resource location. This route is not safe now.");
        List<Cell> walk = new ArrayList<>();
        for (int i = 0; i < route.stages().size(); i++) {
            Stage stage = route.stages().get(i);
            if (!stage.removeBefore().isEmpty()) {
                if (i > 1) break;
                Cell block = stage.removeBefore().getFirst();
                if (!safeRemoval(world, current, block, Set.of()) || !visible(world, current, block, Set.of()))
                    return new Instruction(Action.BLOCKED, null, List.of(), "Look for another resource location. This block is not safe to remove.");
                return new Instruction(Action.MINE, block, List.of(current), "Mine the orange outlined block to open the path.");
            }
            walk.add(stage.feet());
        }
        if (walk.size() > 1) {
            Cell next = walk.get(1);
            String text = next.y() < current.y() ? "Step down one block toward the white marker."
                    : next.y() > current.y() ? "Step up one block toward the white marker."
                    : "Follow the blue path to the white marker.";
            return new Instruction(Action.WALK, next, walk, text);
        }
        return new Instruction(Action.ARRIVED, route.target(), walk, "You have reached the destination.");
    }
}
