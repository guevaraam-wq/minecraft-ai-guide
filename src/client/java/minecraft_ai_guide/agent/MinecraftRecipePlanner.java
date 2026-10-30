package minecraft_ai_guide.agent;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import minecraft_ai_guide.client.guide.TargetHighlighter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public class MinecraftRecipePlanner {

    /** Snapshot Minecraft's crafting recipes for the read-only accessibility guide. */
    public static minecraft_ai_guide.client.guide.RecipeGuidance.Knowledge guideKnowledge(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.getSingleplayerServer() == null) return null;
        var recipes = new LinkedHashMap<String, List<minecraft_ai_guide.client.guide.RecipeGuidance.Recipe>>();
        var manager = minecraft.getSingleplayerServer().getRecipeManager();
        for (var holder : manager.getRecipes()) {
            manager.listDisplaysForRecipe(holder.id(), entry -> {
                boolean table;
                if (entry.display() instanceof ShapedCraftingRecipeDisplay shaped) {
                    table = shaped.width() > 2 || shaped.height() > 2;
                } else if (entry.display() instanceof net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay shapeless) {
                    table = shapeless.ingredients().size() > 4;
                } else return;
                if (entry.craftingRequirements().isEmpty()) return;
                ItemStack output = RecipeEvaluator.getOutput(minecraft, entry);
                if (output.isEmpty()) return;
                var counts = new LinkedHashMap<Set<String>, Integer>();
                for (Ingredient ingredient : entry.craftingRequirements().get()) {
                    Set<String> alternatives = ingredient.items().map(Holder::value)
                            .map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
                            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
                    if (alternatives.isEmpty()) return;
                    counts.merge(Set.copyOf(alternatives), 1, Integer::sum);
                }
                var needs = counts.entrySet().stream().map(e -> new minecraft_ai_guide.client.guide.RecipeGuidance.Need(e.getKey(), e.getValue())).toList();
                String id = BuiltInRegistries.ITEM.getKey(output.getItem()).toString();
                recipes.computeIfAbsent(id, key -> new ArrayList<>()).add(
                        new minecraft_ai_guide.client.guide.RecipeGuidance.Recipe(id, output.getCount(), needs, table));
            });
        }
        var resources = new LinkedHashMap<String, minecraft_ai_guide.client.guide.RecipeGuidance.Resource>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (new ItemStack(item).is(net.minecraft.tags.ItemTags.LOGS)
                    && item instanceof net.minecraft.world.item.BlockItem block) {
                resources.put(BuiltInRegistries.ITEM.getKey(item).toString(),
                        new minecraft_ai_guide.client.guide.RecipeGuidance.Resource(
                                Set.of(BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString()), Set.of()));
            }
        }
        // Acquisition mechanics supplement recipe knowledge; these are not task sequences.
        Map<String, net.minecraft.world.level.block.Block> stoneSources = Map.of(
                "minecraft:cobblestone", Blocks.STONE, "minecraft:blackstone", Blocks.BLACKSTONE,
                "minecraft:cobbled_deepslate", Blocks.DEEPSLATE);
        stoneSources.forEach((drop, block) -> {
            Set<String> tools = BuiltInRegistries.ITEM.stream().filter(item -> new ItemStack(item)
                    .isCorrectToolForDrops(block.defaultBlockState()))
                    .map(item -> BuiltInRegistries.ITEM.getKey(item).toString()).collect(java.util.stream.Collectors.toSet());
            resources.put(drop, new minecraft_ai_guide.client.guide.RecipeGuidance.Resource(
                    new HashSet<>(List.of(BuiltInRegistries.BLOCK.getKey(block).toString(), drop)), tools));
        });
        return new minecraft_ai_guide.client.guide.RecipeGuidance.Knowledge(recipes, resources);
    }

    private static final int MAX_DEPTH = 12;


    // =========================================================
    // PLAN NODE
    // =========================================================

    public static class PlanNode {

        private final String itemId;
        private final int amount;
        private final boolean resource;
        private final List<PlanNode> dependencies;

        /*
         * Other Minecraft items that may satisfy this
         * requirement.
         *
         * Example:
         * #minecraft:planks may contain oak, spruce,
         * birch, cherry, etc.
         */
        private final Set<String> alternatives =
                new HashSet<>();

        public PlanNode(
                String itemId,
                int amount,
                boolean resource
        ) {
            this.itemId = itemId;
            this.amount = amount;
            this.resource = resource;
            this.dependencies = new ArrayList<>();
        }

        public String getItemId() {
            return itemId;
        }

        public int getAmount() {
            return amount;
        }

        public boolean isResource() {
            return resource;
        }

        public List<PlanNode> getDependencies() {
            return dependencies;
        }

        public void addDependency(PlanNode node) {
            dependencies.add(node);
        }

        public void addAlternative(
                String itemId
        ) {
            alternatives.add(itemId);
        }

        public Set<String> getAlternatives() {
            return alternatives;
        }

        public String getReadableName() {

            /*
             * If Minecraft says several items can satisfy
             * this requirement, avoid displaying one
             * arbitrary representative as mandatory.
             */
            if (alternatives.size() > 1) {

                boolean allLogs =
                        alternatives.stream()
                                .allMatch(
                                        id ->
                                                id.endsWith("_log")
                                                || id.endsWith("_stem")
                                );

                if (allLogs) {
                    return "log";
                }

                boolean allPlanks =
                        alternatives.stream()
                                .allMatch(
                                        id ->
                                                id.endsWith("_planks")
                                );

                if (allPlanks) {
                    return "wooden plank";
                }

            }

            return itemId
                    .replace("minecraft:", "")
                    .replace("_", " ");
        }
    }


    // =========================================================
    // PUBLIC PLAN ENTRY
    // =========================================================

    public static PlanNode buildPlan(
            Minecraft minecraft,
            String targetItemId
    ) {

        System.out.println(
                "[Recipe Planner] Building Minecraft-data plan for "
                        + targetItemId
        );

        PlanNode root =
                buildNode(
                        minecraft,
                        targetItemId,
                        1,
                        new HashSet<>(),
                        0
                );

        System.out.println(
                "[Recipe Planner] ===== PLAN ====="
        );

        printPlan(
                root,
                0
        );

        System.out.println(
                "[Recipe Planner] ================"
        );

        return root;
    }


    // =========================================================
    // RECURSIVE DEPENDENCY BUILDING
    // =========================================================

    private static PlanNode buildNode(
            Minecraft minecraft,
            String itemId,
            int amount,
            Set<String> path,
            int depth
    ) {

        if (depth >= MAX_DEPTH) {

            return new PlanNode(
                    itemId,
                    amount,
                    true
            );
        }

        if (path.contains(itemId)) {

            /*
             * Protect against recipe cycles.
             */
            return new PlanNode(
                    itemId,
                    amount,
                    true
            );
        }

        RecipeDisplayEntry recipe =
                findRecipeForOutput(
                        minecraft,
                        itemId
                );


        if (recipe != null) {

            ItemStack station =
                    RecipeEvaluator.getCraftingStation(
                            minecraft,
                            recipe
                    );

            String stationId =
                    station.isEmpty()
                            ? "none"
                            : BuiltInRegistries.ITEM
                                    .getKey(
                                            station.getItem()
                                    )
                                    .toString();

            System.out.println(
                    "[Recipe Planner] "
                            + itemId
                            + " crafting station="
                            + stationId
            );
        }

        /*
         * If Minecraft's recipe book has no crafting
         * recipe for this item, treat it as something
         * the player must obtain from the world.
         */
        if (recipe == null) {

            PlanNode resourceNode =
                    new PlanNode(
                            itemId,
                            amount,
                            true
                    );

            /*
             * RESOURCE ACQUISITION REQUIREMENTS
             *
             * A resource existing in the world does not
             * necessarily mean the player can obtain it
             * with their current inventory.
             *
             * Cobblestone is the first prototype case:
             * normal stone requires a pickaxe before it
             * can provide cobblestone.
             */
            if (itemId.equals("minecraft:cobblestone")
                    && !hasPickaxe(minecraft)) {

                System.out.println(
                        "[Recipe Planner] Cobblestone requires "
                                + "a pickaxe; player has none."
                );

                PlanNode toolDependency =
                        buildNode(
                                minecraft,
                                "minecraft:wooden_pickaxe",
                                1,
                                path,
                                depth + 1
                        );

                // Use Minecraft's tool rules, including tools acquired after planning.
                BuiltInRegistries.ITEM.stream()
                        .filter(item -> canMineStone(new ItemStack(item)))
                        .map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
                        .forEach(toolDependency::addAlternative);
                resourceNode.addDependency(toolDependency);
            }

            return resourceNode;
        }

        /*
         * Minecraft recipes can produce more than one
         * item per craft.
         *
         * Examples:
         *   1 log -> 4 planks
         *   2 planks -> 4 sticks
         *
         * Calculate how many times this recipe actually
         * needs to be performed.
         */
        int outputPerCraft =
                RecipeEvaluator.getOutputCount(
                        minecraft,
                        recipe
                );

        int craftsNeeded =
                Math.max(
                        1,
                        (amount + outputPerCraft - 1)
                                / outputPerCraft
                );

        PlanNode node =
                new PlanNode(
                        itemId,
                        amount,
                        false
                );

        Set<String> nextPath =
                new HashSet<>(path);

        nextPath.add(itemId);

        /*
         * Recipes larger than the player's 2x2
         * inventory grid require a crafting table.
         */
        if (requiresCraftingTable(recipe)
                && !itemId.equals(
                        "minecraft:crafting_table"
                )
                && countInventory(
                        minecraft,
                        "minecraft:crafting_table"
                ) == 0) {

            System.out.println(
                    "[Recipe Planner] "
                            + itemId
                            + " requires a crafting table."
            );

            PlanNode craftingTable =
                    buildNode(
                            minecraft,
                            "minecraft:crafting_table",
                            1,
                            nextPath,
                            depth + 1
                    );

            node.addDependency(
                    craftingTable
            );
        }

        var requirements =
                recipe.craftingRequirements();

        if (requirements.isEmpty()) {

            return new PlanNode(
                    itemId,
                    amount,
                    true
            );
        }

        /*
         * Count repeated ingredients. A stone axe,
         * for example, should become roughly:
         *
         * 3 x cobblestone
         * 2 x stick
         *
         * instead of five unrelated nodes.
         */
        Map<String, Integer> ingredientCounts =
                new LinkedHashMap<>();

        for (Ingredient ingredient
                : requirements.get()) {

            String selected =
                    chooseIngredient(
                            minecraft,
                            ingredient
                    );

            if (selected == null) {
                continue;
            }

            ingredientCounts.merge(
                    selected,
                    craftsNeeded,
                    Integer::sum
            );
        }

        for (Map.Entry<String, Integer> ingredient
                : ingredientCounts.entrySet()) {

            PlanNode dependency =
                    buildNode(
                            minecraft,
                            ingredient.getKey(),
                            ingredient.getValue(),
                            nextPath,
                            depth + 1
                    );

            /*
             * Recover every Minecraft-valid alternative
             * for ingredient slots represented by this
             * selected item.
             *
             * This allows a plan initially represented
             * by oak planks to also accept spruce,
             * birch, cherry, etc.
             */
            for (Ingredient recipeIngredient
                    : requirements.get()) {

                boolean containsSelected =
                        recipeIngredient.items()
                                .map(Holder::value)
                                .map(
                                        item ->
                                                BuiltInRegistries.ITEM
                                                        .getKey(item)
                                                        .toString()
                                )
                                .anyMatch(
                                        id ->
                                                id.equals(
                                                        ingredient.getKey()
                                                )
                                );

                if (!containsSelected) {
                    continue;
                }

                recipeIngredient.items()
                        .map(Holder::value)
                        .map(
                                item ->
                                        BuiltInRegistries.ITEM
                                                .getKey(item)
                                                .toString()
                        )
                        .forEach(
                                dependency::addAlternative
                        );
            }

            node.addDependency(
                    dependency
            );
        }

        return node;
    }


    // =========================================================
    // CRAFTING TABLE REQUIREMENT
    // =========================================================

    private static boolean requiresCraftingTable(
            RecipeDisplayEntry recipe
    ) {

        if (!(recipe.display()
                instanceof ShapedCraftingRecipeDisplay shaped)) {

            /*
             * Shapeless recipes used by the planner are
             * handled separately for now.
             */
            return false;
        }

        return shaped.width() > 2
                || shaped.height() > 2;
    }


    // =========================================================
    // FIND RECIPE BY OUTPUT
    // =========================================================

    private static RecipeDisplayEntry findRecipeForOutput(
            Minecraft minecraft,
            String targetItemId
    ) {

        /*
         * PLANNING KNOWLEDGE
         *
         * Do NOT use the player's ClientRecipeBook here.
         *
         * The player's recipe book only contains recipes
         * currently known/unlocked by that player. A guide
         * must know how to reach a goal before the player
         * has discovered the recipe.
         *
         * During the current single-player research
         * prototype, the integrated server's RecipeManager
         * gives us Minecraft's complete loaded recipe set.
         */

        if (minecraft == null
                || !minecraft.hasSingleplayerServer()
                || minecraft.getSingleplayerServer() == null) {

            System.out.println(
                    "[Recipe Planner] Full recipe catalog "
                            + "unavailable outside local server."
            );

            return null;
        }

        var server =
                minecraft.getSingleplayerServer();

        var recipeManager =
                server.getRecipeManager();

        final RecipeDisplayEntry[] match =
                new RecipeDisplayEntry[1];

        for (var recipeHolder
                : recipeManager.getRecipes()) {

            if (match[0] != null) {
                break;
            }

            recipeManager.listDisplaysForRecipe(
                    recipeHolder.id(),
                    display -> {

                        if (match[0] != null) {
                            return;
                        }

                        String output =
                                RecipeEvaluator.getOutputId(
                                        minecraft,
                                        display
                                );

                        if (targetItemId.equals(output)) {

                            match[0] =
                                    display;
                        }
                    }
            );
        }

        if (match[0] == null) {

            System.out.println(
                    "[Recipe Planner] No recipe found for "
                            + targetItemId
            );

        } else {

            System.out.println(
                    "[Recipe Planner] Found full-data recipe for "
                            + targetItemId
            );
        }

        return match[0];
    }


    // =========================================================
    // CHOOSE FROM INGREDIENT ALTERNATIVES
    // =========================================================

    private static String chooseIngredient(
            Minecraft minecraft,
            Ingredient ingredient
    ) {

        List<String> alternatives =
                ingredient.items()
                        .map(Holder::value)
                        .map(
                                item ->
                                        BuiltInRegistries.ITEM
                                                .getKey(item)
                                                .toString()
                        )
                        .toList();

        if (alternatives.isEmpty()) {
            return null;
        }

        /*
         * FIRST:
         * Prefer any valid alternative the player
         * already owns.
         *
         * Example:
         * If the recipe accepts cobblestone,
         * blackstone, or cobbled deepslate and the
         * player already has cobblestone, use it.
         */
        if (minecraft.player != null) {

            String bestOwned = null;
            int bestOwnedCount = 0;

            for (String alternative
                    : alternatives) {

                int owned =
                        countInventory(
                                minecraft,
                                alternative
                        );

                if (owned > bestOwnedCount) {

                    bestOwned =
                            alternative;

                    bestOwnedCount =
                            owned;
                }
            }

            if (bestOwned != null) {

                System.out.println(
                        "[Recipe Planner] Using owned "
                                + "ingredient alternative: "
                                + bestOwned
                );

                return bestOwned;
            }
        }

        /*
         * SECOND:
         * Prefer an alternative that does not have a
         * crafting recipe.
         *
         * This generally represents a resource that
         * should be collected from the world instead
         * of recursively crafted.
         */
        for (String alternative
                : alternatives) {

            RecipeDisplayEntry recipe =
                    findRecipeForOutput(
                            minecraft,
                            alternative
                    );

            if (recipe == null) {

                System.out.println(
                        "[Recipe Planner] Using collectable "
                                + "ingredient alternative: "
                                + alternative
                );

                return alternative;
            }
        }

        /*
         * FALLBACK:
         * Every alternative appears craftable.
         * Keep deterministic behavior by choosing
         * the first valid Minecraft alternative.
         */
        String selected =
                alternatives.get(0);

        System.out.println(
                "[Recipe Planner] Using fallback "
                        + "ingredient alternative: "
                        + selected
        );

        return selected;
    }


    // =========================================================
    // TOOL AVAILABILITY
    // =========================================================

    private static boolean hasPickaxe(
            Minecraft minecraft
    ) {

        if (minecraft.player == null) {
            return false;
        }

        var inventory =
                minecraft.player.getInventory();

        return inventory.contains(MinecraftRecipePlanner::canMineStone);
    }

    private static boolean canMineStone(ItemStack stack) {
        return !stack.isEmpty() && stack.isCorrectToolForDrops(Blocks.STONE.defaultBlockState());
    }


    // =========================================================
    // INVENTORY
    // =========================================================

    private static int countInventory(
            Minecraft minecraft,
            String itemId
    ) {

        if (minecraft.player == null) {
            return 0;
        }

        int count = 0;

        for (int i = 0;
             i < minecraft.player
                     .getInventory()
                     .getContainerSize();
             i++) {

            ItemStack stack =
                    minecraft.player
                            .getInventory()
                            .getItem(i);

            if (stack.isEmpty()) {
                continue;
            }

            String stackId =
                    BuiltInRegistries.ITEM
                            .getKey(
                                    stack.getItem()
                            )
                            .toString();

            if (itemId.equals(stackId)) {
                count += stack.getCount();
            }
        }

        return count;
    }


    // =========================================================
    // NEXT GUIDE STEP
    // =========================================================

    public static String getNextInstruction(
            Minecraft minecraft,
            PlanNode plan
    ) {

        if (plan == null) {
            return null;
        }

        /*
         * Goal itself always wins.
         *
         * If the final target is already in inventory,
         * the entire plan is complete regardless of
         * intermediate materials that were consumed.
         */
        if (hasEnough(
                minecraft,
                plan
        )) {

            return "Goal complete: "
                    + plan.getReadableName();
        }

        PlanNode next =
                findNextAction(
                        plan,
                        node -> hasEnough(minecraft, node)
                );

        if (next == null) {

            return "Goal complete: "
                    + plan.getReadableName();
        }

        int owned =
                countInventory(
                        minecraft,
                        next.getItemId()
                );

        for (String alternative
                : next.getAlternatives()) {

            if (!alternative.equals(
                    next.getItemId()
            )) {

                owned +=
                        countInventory(
                                minecraft,
                                alternative
                        );
            }
        }

        int needed =
                Math.max(
                        1,
                        next.getAmount() - owned
                );

        if (next.isResource()) {

            return "Collect "
                    + needed
                    + " "
                    + pluralize(
                            next.getReadableName(),
                            needed
                    );
        }

        return "Craft "
                + needed
                + " "
                + pluralize(
                        next.getReadableName(),
                        needed
                );
    }


    /*
     * Recursively determine the next actionable step.
     *
     * IMPORTANT:
     *
     * We descend through dependencies BEFORE returning
     * the parent crafting goal.
     *
     * Example:
     *
     * stone axe
     *   -> stick
     *       -> planks
     *           -> log
     *
     * becomes:
     *
     * Collect log
     *
     * rather than:
     *
     * Craft stone axe
     */
    // Inventory predicate keeps the traversal independently testable.
    static PlanNode findNextAction(PlanNode node, Predicate<PlanNode> satisfied) {
        if (satisfied.test(node)) {
            return null;
        }

        // Resources may have acquisition prerequisites, such as mining tools.
        for (PlanNode dependency : node.getDependencies()) {
            PlanNode next = findNextAction(dependency, satisfied);
            if (next != null) {
                return next;
            }
        }
        return node;
    }


    private static boolean hasEnough(
            Minecraft minecraft,
            PlanNode node
    ) {

        return hasEnough(node, itemId -> countInventory(minecraft, itemId));
    }

    static boolean hasEnough(PlanNode node, ToIntFunction<String> inventoryCount) {
        int total = inventoryCount.applyAsInt(node.getItemId());
        for (String alternative : node.getAlternatives()) {
            if (!alternative.equals(node.getItemId())) {
                total += inventoryCount.applyAsInt(alternative);
            }
        }
        return total >= node.getAmount();
    }


    private static String pluralize(
            String name,
            int amount
    ) {

        if (amount == 1) {
            return name;
        }

        /*
         * Keep this intentionally simple for HUD text.
         */
        if (name.endsWith("s")) {
            return name;
        }

        return name + "s";
    }


    // =========================================================
    // DEBUG TREE
    // =========================================================

    private static void printPlan(
            PlanNode node,
            int depth
    ) {

        if (node == null) {
            return;
        }

        String indent =
                "  ".repeat(depth);

        String type =
                node.isResource()
                        ? "OBTAIN"
                        : "CRAFT";

        System.out.println(
                "[Recipe Planner] "
                        + indent
                        + type
                        + " "
                        + node.getAmount()
                        + "x "
                        + node.getItemId()
        );

        for (PlanNode dependency
                : node.getDependencies()) {

            printPlan(
                    dependency,
                    depth + 1
            );
        }
    }


    private MinecraftRecipePlanner() {
    }
}
