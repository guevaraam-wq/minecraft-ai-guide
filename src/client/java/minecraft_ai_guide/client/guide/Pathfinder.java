package minecraft_ai_guide.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class Pathfinder {

    public static NavigationPlanner.Cell cell(BlockPos pos) {
        return new NavigationPlanner.Cell(pos.getX(), pos.getY(), pos.getZ());
    }
    public static BlockPos block(NavigationPlanner.Cell cell) { return new BlockPos(cell.x(), cell.y(), cell.z()); }

    /** A per-update, cached read-only world view for accessibility routes. */
    public static NavigationPlanner.World guideWorld(Minecraft minecraft) {
        return new NavigationPlanner.World() {
            private final Map<NavigationPlanner.Cell, NavigationPlanner.Voxel> cache = new HashMap<>();
            private int walkingBudget = 3000, excavationBudget = 6500;
            private long walkingStarted, excavationStarted;
            public boolean allowSearch(boolean excavation) {
                long now = System.nanoTime();
                if (excavation && excavationStarted == 0) excavationStarted = now;
                if (!excavation && walkingStarted == 0) walkingStarted = now;
                return now - (excavation ? excavationStarted : walkingStarted) < (excavation ? 65_000_000L : 35_000_000L)
                        && (excavation ? excavationBudget-- > 0 : walkingBudget-- > 0);
            }
            public double reach() { return minecraft.player.blockInteractionRange() - .5; }
            public double eyeHeight() { return minecraft.player.getEyePosition().y - minecraft.player.getY(); }
            public NavigationPlanner.Voxel voxel(NavigationPlanner.Cell cell) {
                return cache.computeIfAbsent(cell, key -> inspect(minecraft, block(key)));
            }
        };
    }
    private static NavigationPlanner.Voxel inspect(Minecraft mc, BlockPos pos) {
        if (mc.level == null || mc.level.isOutsideBuildHeight(pos) || !mc.level.hasChunkAt(pos))
            return NavigationPlanner.Voxel.UNKNOWN;
        BlockState state = mc.level.getBlockState(pos);
        var block = state.getBlock();
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        String name = id.toString();
        Set<String> hazards = HAZARDS;
        boolean hazard = !mc.level.getFluidState(pos).isEmpty() || hazards.contains(name)
                || !id.getNamespace().equals("minecraft");
        boolean falling = block instanceof net.minecraft.world.level.block.FallingBlock;
        boolean support = !falling && state.isCollisionShapeFullBlock(mc.level, pos);
        boolean passable = state.getCollisionShape(mc.level, pos).isEmpty();
        boolean removable = EXCAVATABLE.contains(name) && !hazard && !falling && !state.hasBlockEntity()
                && support && state.getDestroySpeed(mc.level, pos) >= 0
                && (!state.requiresCorrectToolForDrops() || mc.player.getMainHandItem().isCorrectToolForDrops(state));
        return new NavigationPlanner.Voxel(state.toString(), true, passable, support, hazard, removable, falling);
    }
    // Deliberately small terrain whitelist: never tunnel through structures, containers or falling terrain.
    private static final Set<String> EXCAVATABLE = Set.of("minecraft:dirt", "minecraft:grass_block",
            "minecraft:coarse_dirt", "minecraft:rooted_dirt", "minecraft:stone", "minecraft:deepslate",
            "minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:andesite", "minecraft:diorite",
            "minecraft:granite", "minecraft:tuff", "minecraft:calcite");
    private static final Set<String> HAZARDS = Set.of("minecraft:lava", "minecraft:water", "minecraft:fire",
            "minecraft:soul_fire", "minecraft:magma_block", "minecraft:campfire", "minecraft:soul_campfire",
            "minecraft:powder_snow", "minecraft:cactus", "minecraft:sweet_berry_bush", "minecraft:wither_rose",
            "minecraft:pointed_dripstone", "minecraft:nether_portal", "minecraft:end_portal", "minecraft:tnt",
            "minecraft:sculk_shrieker", "minecraft:slime_block", "minecraft:honey_block", "minecraft:ice",
            "minecraft:packed_ice", "minecraft:blue_ice");

    private static final int MAX_SEARCH_NODES = 2000;

    public static List<BlockPos> findPath(
            Minecraft minecraft,
            BlockPos start,
            BlockPos target
    ) {

        if (minecraft.level == null) {
            return Collections.emptyList();
        }

        PriorityQueue<PathNode> openSet =
                new PriorityQueue<>(
                        Comparator.comparingDouble(PathNode::getTotalCost)
                );

        Map<BlockPos, PathNode> allNodes = new HashMap<>();
        Set<BlockPos> closedSet = new HashSet<>();

        PathNode startNode = new PathNode(start);
        startNode.setDistanceFromStart(0);
        startNode.setEstimatedDistanceToGoal(
                heuristic(start, target)
        );

        openSet.add(startNode);
        allNodes.put(start, startNode);

        int searchedNodes = 0;

        while (!openSet.isEmpty()
                && searchedNodes < MAX_SEARCH_NODES) {

            PathNode current = openSet.poll();

            if (closedSet.contains(current.getPosition())) {
                continue;
            }

            searchedNodes++;

            BlockPos currentPos = current.getPosition();

            // We only need to get close to the target tree.
            if (horizontalDistance(currentPos, target) <= 1.5) {
                return reconstructPath(current);
            }

            closedSet.add(currentPos);

            for (BlockPos neighbor :
                    getNeighbors(minecraft, currentPos)) {

                if (closedSet.contains(neighbor)) {
                    continue;
                }

                double newDistance =
                        current.getDistanceFromStart()
                                + movementCost(currentPos, neighbor);

                PathNode neighborNode =
                        allNodes.get(neighbor);

                if (neighborNode == null) {

                    neighborNode = new PathNode(neighbor);

                    neighborNode.setParent(current);
                    neighborNode.setDistanceFromStart(newDistance);
                    neighborNode.setEstimatedDistanceToGoal(
                            heuristic(neighbor, target)
                    );

                    allNodes.put(neighbor, neighborNode);
                    openSet.add(neighborNode);

                } else if (newDistance
                        < neighborNode.getDistanceFromStart()) {

                    neighborNode.setParent(current);
                    neighborNode.setDistanceFromStart(newDistance);

                    // Re-add so the priority queue uses its new cost.
                    openSet.add(neighborNode);
                }
            }
        }

        // No safe path was found.
        return Collections.emptyList();
    }

    private static List<BlockPos> getNeighbors(
            Minecraft minecraft,
            BlockPos position
    ) {

        List<BlockPos> neighbors = new ArrayList<>();

        int[][] directions = {
                {1, 0},
                {-1, 0},
                {0, 1},
                {0, -1}
        };

        for (int[] direction : directions) {

            int x = position.getX() + direction[0];
            int z = position.getZ() + direction[1];

            // Same level
            BlockPos same =
                    new BlockPos(x, position.getY(), z);

            if (isWalkable(minecraft, same)) {
                neighbors.add(same);
                continue;
            }

            // One-block step upward
            BlockPos up =
                    new BlockPos(x, position.getY() + 1, z);

            if (isWalkable(minecraft, up)) {
                neighbors.add(up);
                continue;
            }

            // One-block step downward
            BlockPos down =
                    new BlockPos(x, position.getY() - 1, z);

            if (isWalkable(minecraft, down)) {
                neighbors.add(down);
            }
        }

        return neighbors;
    }

    private static boolean isWalkable(
            Minecraft minecraft,
            BlockPos feetPosition
    ) {

        if (minecraft.level == null) {
            return false;
        }

        BlockPos groundPosition = feetPosition.below();
        BlockPos headPosition = feetPosition.above();

        BlockState ground =
                minecraft.level.getBlockState(groundPosition);

        BlockState feet =
                minecraft.level.getBlockState(feetPosition);

        BlockState head =
                minecraft.level.getBlockState(headPosition);

        // Player needs something underneath them.
        if (ground.isAir()) {
            return false;
        }

        // Player needs room for both feet and head.
        if (!feet.isAir()) {
            return false;
        }

        if (!head.isAir()) {
            return false;
        }

        return true;
    }

    private static double heuristic(
            BlockPos from,
            BlockPos target
    ) {

        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();

        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double horizontalDistance(
            BlockPos first,
            BlockPos second
    ) {

        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();

        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double movementCost(
            BlockPos from,
            BlockPos to
    ) {

        // Slightly discourage unnecessary elevation changes.
        if (from.getY() != to.getY()) {
            return 1.25;
        }

        return 1.0;
    }

    private static List<BlockPos> reconstructPath(
            PathNode endNode
    ) {

        List<BlockPos> path = new ArrayList<>();

        PathNode current = endNode;

        while (current != null) {
            path.add(current.getPosition());
            current = current.getParent();
        }

        Collections.reverse(path);

        return path;
    }
}