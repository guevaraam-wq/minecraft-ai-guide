package minecraft_ai_guide.client.guide;

import java.util.*;

/** Read-only recipe dependency guidance. No player controls, learned policy, or game API calls. */
public final class RecipeGuidance {
    public enum Action { COMPLETE, BLOCKED, COLLECT, CRAFT, OPEN_INVENTORY, CLOSE_SCREEN,
        PLACE_TABLE, OPEN_TABLE, GO_TO_TABLE, EQUIP_TOOL, MOVE_TO_HOTBAR, TAKE_RESULT }
    public enum Table { NONE, BLOCKED, NEARBY, IN_REACH, OPEN }
    public record Need(Set<String> items, int count) {
        public Need { items = Set.copyOf(items); if (items.isEmpty() || count < 1) throw new IllegalArgumentException(); }
        public Need(String item, int count) { this(Set.of(item), count); }
    }
    public record Recipe(String item, int output, List<Need> ingredients, boolean table) {
        public Recipe { ingredients = List.copyOf(ingredients); if (output < 1) throw new IllegalArgumentException(); }
    }
    public record Resource(Set<String> sources, Set<String> tools) {
        public Resource { sources = Set.copyOf(sources); tools = Set.copyOf(tools); }
    }
    public record Knowledge(Map<String, List<Recipe>> recipes, Map<String, Resource> resources) {
        public Knowledge {
            Map<String, List<Recipe>> copy = new HashMap<>();
            recipes.forEach((id, rows) -> copy.put(id, List.copyOf(rows)));
            recipes = Map.copyOf(copy); resources = Map.copyOf(resources);
        }
    }
    public record Context(Map<String, Integer> inventory, Map<String, Integer> grid, Table table,
            boolean inventoryOpen, boolean otherScreen, boolean canPlace, Set<String> hotbar,
            String heldItem, String resultItem) {
        public Context { inventory = Map.copyOf(inventory); grid = Map.copyOf(grid); hotbar = Set.copyOf(hotbar); }
    }
    public record Step(Action action, String item, int amount, Set<String> alternatives, String text) {
        public Step { alternatives = Set.copyOf(alternatives); }
    }
    private static final String TABLE = "minecraft:crafting_table";
    private final Knowledge knowledge;
    private final String goalItem;
    private final int goalAmount;
    private Context context;
    private final Set<String> relevant = new HashSet<>();

    public RecipeGuidance(Knowledge knowledge) { this(knowledge,"minecraft:stone_axe",1); }
    public RecipeGuidance(Knowledge knowledge,String goalItem,int goalAmount) {
        if(goalAmount<1)throw new IllegalArgumentException("Invalid goal quantity");
        this.knowledge = knowledge;this.goalItem=goalItem;this.goalAmount=goalAmount;
        Deque<String> queue = new ArrayDeque<>(); queue.add(goalItem);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (!relevant.add(id)) continue;
            Resource resource = knowledge.resources().get(id);
            if (resource != null) queue.addAll(resource.tools());
            else for (Recipe recipe : knowledge.recipes().getOrDefault(id, List.of())) {
                for (Need need : recipe.ingredients()) queue.addAll(need.items());
                if (recipe.table()) queue.add(TABLE);
            }
        }
    }

    public Step next(Context current) {
        context = current;
        if (current.inventory().getOrDefault(goalItem, 0) >= goalAmount)
            return step(Action.COMPLETE, goalItem, goalAmount, completedText(goalItem,goalAmount));
        if (current.otherScreen()) return step(Action.CLOSE_SCREEN, "", 0, "Close this screen to continue the guide.");
        if (!current.resultItem().isEmpty() && relevant.contains(current.resultItem()))
            return step(Action.TAKE_RESULT, current.resultItem(), 0,
                    "Take the " + name(current.resultItem()) + " from the result box.");
        Map<String, Integer> budget = new HashMap<>(current.inventory());
        current.grid().forEach((id, count) -> budget.merge(id, count, Integer::sum));
        Step next = solve(new Need(goalItem, goalAmount), budget, new HashSet<>());
        if (next == null) return step(Action.BLOCKED, "", 0, "Take your " + name(goalItem) + " out of the crafting grid.");
        if (next.action() == Action.CRAFT && next.item().equals(current.resultItem()))
            return step(Action.TAKE_RESULT, next.item(), next.amount(), "Take the " + name(next.item()) + " from the result box.");
        if (next.action() == Action.CRAFT && current.table() != Table.OPEN && !current.inventoryOpen())
            return step(Action.OPEN_INVENTORY, next.item(), next.amount(), "Open your inventory to make " + name(next.item()) + ".");
        if ((next.action() == Action.COLLECT || next.action() == Action.PLACE_TABLE || next.action() == Action.OPEN_TABLE
                || next.action() == Action.GO_TO_TABLE || next.action() == Action.EQUIP_TOOL
                || (next.action() == Action.OPEN_INVENTORY && current.table() == Table.OPEN)) && (current.inventoryOpen() || current.table() == Table.OPEN))
            return step(Action.CLOSE_SCREEN, next.item(), next.amount(), "Close the crafting screen to continue.");
        return next;
    }

    private Step solve(Need need, Map<String, Integer> budget, Set<String> path) {
        int missing = need.count();
        for (String id : new TreeSet<>(need.items())) {
            int used = Math.min(missing, budget.getOrDefault(id, 0));
            budget.merge(id, -used, Integer::sum);
            missing -= used;
        }
        if (missing == 0) return null;
        final int amount = missing;
        String chosen = need.items().stream().sorted().min(Comparator.comparingDouble(
                id -> cost(id, amount, budget, path))).orElseThrow();
        if (path.size() >= 16 || path.contains(chosen) || (!Double.isFinite(cost(chosen, missing, budget, path))
                && !knowledge.resources().containsKey(chosen) && knowledge.recipes().getOrDefault(chosen,List.of()).isEmpty()))
            return step(Action.BLOCKED, chosen, missing, "The guide cannot find a usable recipe for " + name(chosen) + ".");
        Set<String> nextPath = new HashSet<>(path); nextPath.add(chosen);
        Resource resource = knowledge.resources().get(chosen);
        if (resource != null) {
            if (!resource.tools().isEmpty()) {
                boolean hasTool = resource.tools().stream().anyMatch(id -> context.inventory().getOrDefault(id, 0) > 0);
                if (!hasTool) return solve(new Need(resource.tools(), 1), budget, nextPath);
                if (!resource.tools().contains(context.heldItem())) {
                    if (context.hotbar().stream().noneMatch(resource.tools()::contains)) {
                        return step(context.inventoryOpen() ? Action.MOVE_TO_HOTBAR : Action.OPEN_INVENTORY, chosen, missing,
                                context.inventoryOpen() ? "Move a pickaxe to your hotbar." : "Open your inventory to find your pickaxe.");
                    }
                    return step(Action.EQUIP_TOOL, chosen, missing, "Hold your pickaxe to mine stone.");
                }
            }
            Set<String> accepted = new TreeSet<>(need.items()); accepted.retainAll(knowledge.resources().keySet());
            return new Step(Action.COLLECT, chosen, missing, accepted, "Collect " + missing + " " + name(chosen) + ".");
        }
        Recipe recipe = chooseRecipe(chosen, missing, budget, nextPath);
        if (recipe == null) return step(Action.BLOCKED, chosen, missing, "A recipe for " + name(chosen) + " is not available yet.");
        // Acquire a station through its recipe, not a fixed stone-axe task sequence.
        if (recipe.table() && context.table() == Table.NONE && context.inventory().getOrDefault(TABLE, 0) == 0) {
            if (context.grid().getOrDefault(TABLE, 0) > 0)
                return step(Action.BLOCKED, TABLE, 1, "Move your crafting table out of the crafting grid.");
            Step station = solve(new Need(TABLE, 1), new HashMap<>(budget), nextPath);
            if (station != null) return station;
        }
        int crafts = (missing + recipe.output() - 1) / recipe.output();
        for (Need ingredient : recipe.ingredients()) {
            Step prerequisite = solve(new Need(ingredient.items(), ingredient.count() * crafts), budget, nextPath);
            if (prerequisite != null) return widenResourceChoice(prerequisite, need);
        }
        if (recipe.table() && context.table() != Table.OPEN) {
            if (context.table() == Table.IN_REACH) return step(Action.OPEN_TABLE, TABLE, 1, "Open the highlighted crafting table.");
            if (context.table() == Table.NEARBY) return step(Action.GO_TO_TABLE, TABLE, 1, "Go to the highlighted crafting table.");
            if (context.inventory().getOrDefault(TABLE, 0) > 0) {
                if (!context.canPlace()) return step(Action.BLOCKED, TABLE, 1, "Move to open ground. There is no room for a crafting table here.");
                if (!context.hotbar().contains(TABLE)) return step(context.inventoryOpen() ? Action.MOVE_TO_HOTBAR : Action.OPEN_INVENTORY,
                        TABLE, 1, context.inventoryOpen() ? "Move your crafting table to your hotbar." : "Open your inventory to find your crafting table.");
                if (!TABLE.equals(context.heldItem())) return step(Action.EQUIP_TOOL, TABLE, 1, "Hold your crafting table.");
                return step(Action.PLACE_TABLE, TABLE, 1, "Place your crafting table on the ground.");
            }
            return step(Action.BLOCKED, TABLE, 1, "Look for an accessible crafting table. The nearby table is blocked.");
        }
        return step(Action.CRAFT, chosen, crafts * recipe.output(), "Make " + crafts * recipe.output() + " " + countedName(chosen, crafts * recipe.output()) + ".");
    }

    private Step widenResourceChoice(Step step, Need parent) {
        if (step.action() != Action.COLLECT || parent.items().size() < 2) return step;
        Resource selected = knowledge.resources().get(step.item());
        Set<String> leaves = new TreeSet<>();
        collectResourceChoices(parent.items(), selected.tools().isEmpty(), new HashSet<>(), leaves);
        if (leaves.isEmpty()) return step;
        return new Step(step.action(), step.item(), step.amount(), leaves, step.text());
    }

    private void collectResourceChoices(Set<String> items, boolean toolFree, Set<String> seen, Set<String> leaves) {
        for (String id : items) {
            if (!seen.add(id)) continue;
            Resource resource = knowledge.resources().get(id);
            if (resource != null) {
                if (resource.tools().isEmpty() == toolFree) leaves.add(id);
            } else for (Recipe recipe : knowledge.recipes().getOrDefault(id, List.of())) {
                for (Need ingredient : recipe.ingredients()) collectResourceChoices(ingredient.items(), toolFree, seen, leaves);
            }
        }
    }

    private double cost(String id, int amount, Map<String, Integer> budget, Set<String> path) {
        if (path.contains(id) || path.size() >= 16) return Double.POSITIVE_INFINITY;
        int missing = Math.max(0, amount - budget.getOrDefault(id, 0));
        if (missing == 0) return 0;
        Set<String> next = new HashSet<>(path); next.add(id);
        Resource resource = knowledge.resources().get(id);
        if (resource != null) {
            if (resource.tools().isEmpty() || resource.tools().stream().anyMatch(tool -> context.inventory().getOrDefault(tool, 0) > 0)) return missing;
            return missing + needCost(new Need(resource.tools(), 1), budget, next);
        }
        return knowledge.recipes().getOrDefault(id, List.of()).stream()
                .mapToDouble(recipe -> recipeCost(recipe, missing, budget, next)).min().orElse(Double.POSITIVE_INFINITY);
    }

    private double needCost(Need need, Map<String, Integer> budget, Set<String> path) {
        int missing = Math.max(0, need.count() - need.items().stream().mapToInt(id -> budget.getOrDefault(id, 0)).sum());
        if (missing == 0) return 0;
        Map<String, Integer> remaining = new HashMap<>(budget);
        need.items().forEach(id -> remaining.remove(id));
        return need.items().stream().mapToDouble(id -> cost(id, missing, remaining, path)).min().orElse(Double.POSITIVE_INFINITY);
    }

    private double recipeCost(Recipe recipe, int amount, Map<String, Integer> budget, Set<String> path) {
        int crafts = (amount + recipe.output() - 1) / recipe.output();
        double value = .01;
        for (Need need : recipe.ingredients()) value += needCost(new Need(need.items(), need.count() * crafts), budget, path);
        if (recipe.table() && context.table() == Table.NONE && context.inventory().getOrDefault(TABLE, 0) == 0)
            value += cost(TABLE, 1, budget, path);
        return value;
    }

    private Recipe chooseRecipe(String id, int amount, Map<String, Integer> budget, Set<String> path) {
        return knowledge.recipes().getOrDefault(id, List.of()).stream()
                .min(Comparator.comparingDouble(recipe -> recipeCost(recipe, amount, budget, path))).orElse(null);
    }

    private static Step step(Action action, String item, int amount, String text) {
        return new Step(action, item, amount, item.isEmpty() ? Set.of() : Set.of(item), text);
    }
    private static String countedName(String id, int count) {
        String name = name(id);
        if (count == 1 || name.endsWith("planks") || name.endsWith("stone") || name.endsWith("deepslate")) return name;
        return name + "s";
    }
    public static String completedText(String item,int amount) {
        return item.equals("minecraft:stone_axe") && amount==1 ? "Your stone axe is ready."
                : "You have " + amount + " " + countedName(item,amount) + ".";
    }
    public static String name(String id) { return id.replace("minecraft:", "").replace('_', ' '); }
}
