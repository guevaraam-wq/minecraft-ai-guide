package minecraft_ai_guide.agent;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;

import net.minecraft.world.entity.player.StackedItemContents;

import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;

import net.minecraft.world.item.ItemStack;

import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;

import java.util.ArrayList;
import java.util.List;


public class CraftingExecutor {


    // =========================================================
    // CRAFTING STATE
    // =========================================================

    /*
     * Crafting happens in two stages:
     *
     * 1. Place the recipe into the crafting grid.
     * 2. Wait for Minecraft to populate the result slot,
     *    then QUICK_MOVE the result into inventory.
     */

    private static boolean waitingForResult =
            false;

    private static boolean timedOut;

    private static String lastCraftTarget =
            null;

    private static long resultReadyTime =
            0;

    private static final long RESULT_DELAY_MS =
            250;


    /*
     * Prevent the agent from waiting forever if
     * something goes wrong with a recipe.
     */
    private static long craftingTimeoutTime =
            0;

    private static final long CRAFT_TIMEOUT_MS =
            3000;


    // =========================================================
    // KNOWN RECIPES
    // =========================================================

    public static List<RecipeDisplayEntry> getKnownRecipes(
            Minecraft minecraft
    ) {

        List<RecipeDisplayEntry> recipes =
                new ArrayList<>();

        if (minecraft.player == null) {
            return recipes;
        }


        ClientRecipeBook recipeBook =
                minecraft.player.getRecipeBook();


        for (RecipeCollection collection
                : recipeBook.getCollections()) {

            recipes.addAll(
                    collection.getRecipes()
            );
        }


        return recipes;
    }


    // =========================================================
    // INVENTORY CONTENTS
    // =========================================================

    private static StackedItemContents getInventoryContents(
            Minecraft minecraft
    ) {

        StackedItemContents contents =
                new StackedItemContents();


        if (minecraft.player == null) {
            return contents;
        }


        for (
                int i = 0;
                i < minecraft.player
                        .getInventory()
                        .getContainerSize();
                i++
        ) {

            ItemStack stack =
                    minecraft.player
                            .getInventory()
                            .getItem(i);


            if (!stack.isEmpty()) {

                contents.accountStack(
                        stack
                );
            }
        }


        return contents;
    }


    // =========================================================
    // CRAFTING GRID RECIPE CHECK
    // =========================================================

    /*
     * The recipe book contains many kinds of recipes:
     *
     * - crafting grid
     * - furnace
     * - smithing
     * - stonecutter
     *
     * We only want shaped and shapeless crafting recipes.
     *
     * The available crafting-grid size depends on the
     * player's currently open menu:
     *
     * Inventory crafting = 2x2
     * Crafting table     = 3x3
     */

    private static boolean isCraftingGridRecipe(
            Minecraft minecraft,
            RecipeDisplayEntry entry
    ) {

        RecipeDisplay display =
                entry.display();


        /*
         * If CraftingMenu is currently open,
         * Minecraft has given the player access
         * to a crafting table's 3x3 grid.
         */
        boolean usingCraftingTable =
                minecraft.player != null
                        && minecraft.player.containerMenu
                        instanceof CraftingMenu;


        int gridWidth =
                usingCraftingTable
                        ? 3
                        : 2;

        int gridHeight =
                usingCraftingTable
                        ? 3
                        : 2;

        int gridSlots =
                gridWidth * gridHeight;


        // -----------------------------------------------------
        // SHAPED RECIPES
        // -----------------------------------------------------

        if (display
                instanceof ShapedCraftingRecipeDisplay shaped) {

            boolean fitsGrid =
                    shaped.width() <= gridWidth
                            && shaped.height() <= gridHeight;


            if (!fitsGrid) {

                System.out.println(
                        "[AI Crafting] Recipe requires "
                                + shaped.width()
                                + "x"
                                + shaped.height()
                                + " grid - current grid is "
                                + gridWidth
                                + "x"
                                + gridHeight
                                + "."
                );
            }


            return fitsGrid;
        }


        // -----------------------------------------------------
        // SHAPELESS RECIPES
        // -----------------------------------------------------

        if (display
                instanceof ShapelessCraftingRecipeDisplay shapeless) {

            int ingredientCount =
                    shapeless.ingredients().size();


            boolean fitsGrid =
                    ingredientCount <= gridSlots;


            if (!fitsGrid) {

                System.out.println(
                        "[AI Crafting] Shapeless recipe requires "
                                + ingredientCount
                                + " ingredients - current grid has "
                                + gridSlots
                                + " slots."
                );
            }


            return fitsGrid;
        }


        /*
         * Furnace, smithing, stonecutting, etc.
         * are still excluded.
         */
        return false;
    }


    // =========================================================
    // REQUIRES CRAFTING TABLE
    // =========================================================

    private static boolean requiresCraftingTable(
            RecipeDisplayEntry entry
    ) {

        RecipeDisplay display =
                entry.display();


        // Shaped recipe larger than 2x2.
        if (display
                instanceof ShapedCraftingRecipeDisplay shaped) {

            return shaped.width() > 2
                    || shaped.height() > 2;
        }


        // Shapeless recipe needing more than four slots.
        if (display
                instanceof ShapelessCraftingRecipeDisplay shapeless) {

            return shapeless
                    .ingredients()
                    .size() > 4;
        }


        return false;
    }


    // =========================================================
    // CRAFTABLE RECIPES
    // =========================================================

    public static List<RecipeDisplayEntry> getCraftableRecipes(
            Minecraft minecraft
    ) {

        List<RecipeDisplayEntry> craftable =
                new ArrayList<>();


        if (minecraft.player == null) {
            return craftable;
        }


        StackedItemContents contents =
                getInventoryContents(
                        minecraft
                );


        for (RecipeDisplayEntry recipe
                : getKnownRecipes(minecraft)) {

            /*
             * Only allow recipes that fit the
             * currently available crafting grid.
             *
             * Inventory menu:
             * 2x2
             *
             * Crafting table menu:
             * 3x3
             */
            if (!isCraftingGridRecipe(
                    minecraft,
                    recipe
            )) {

                continue;
            }


            /*
             * Check whether the player currently
             * owns the required ingredients.
             */
            if (recipe.canCraft(contents)) {

                craftable.add(
                        recipe
                );
            }
        }


        return craftable;
    }


    // =========================================================
    // RECIPES BLOCKED BY CRAFTING TABLE
    // =========================================================

    public static List<RecipeDisplayEntry>
    getRecipesBlockedByCraftingTable(
            Minecraft minecraft
    ) {

        List<RecipeDisplayEntry> blocked =
                new ArrayList<>();


        if (minecraft.player == null) {
            return blocked;
        }


        /*
         * If the crafting table is already open,
         * these recipes are no longer blocked.
         */
        if (minecraft.player.containerMenu
                instanceof CraftingMenu) {

            return blocked;
        }


        StackedItemContents contents =
                getInventoryContents(
                        minecraft
                );


        for (RecipeDisplayEntry recipe
                : getKnownRecipes(minecraft)) {

            /*
             * We're specifically looking for recipes
             * whose ingredients the player possesses,
             * but which cannot fit in the inventory's
             * 2x2 crafting grid.
             */
            if (!requiresCraftingTable(
                    recipe
            )) {

                continue;
            }


            if (recipe.canCraft(contents)) {

                blocked.add(
                        recipe
                );
            }
        }


        return blocked;
    }


    public static boolean needsCraftingTable(
            Minecraft minecraft
    ) {

        return !getRecipesBlockedByCraftingTable(
                minecraft
        ).isEmpty();
    }


    // =========================================================
    // CRAFTING TABLE STATE
    // =========================================================

    public static boolean isUsingCraftingTable(
            Minecraft minecraft
    ) {

        return minecraft.player != null
                && minecraft.player.containerMenu
                instanceof CraftingMenu;
    }


    // =========================================================
    // CHOOSE RECIPE
    // =========================================================

    public static RecipeDisplayEntry chooseCraftableRecipe(
            Minecraft minecraft
    ) {

        List<RecipeDisplayEntry> craftable =
                getCraftableRecipes(
                        minecraft
                );


        if (craftable.isEmpty()) {

            System.out.println(
                    "[AI Crafting] No crafting-grid "
                            + "recipes currently available."
            );

            return null;
        }


        /*
         * Determine the agent's current goal so recipe
         * evaluation can consider what the agent is
         * currently trying to accomplish.
         */
        GameState currentState =
                GameState.observe(
                        minecraft
                );


        AgentGoalManager goalManager =
                new AgentGoalManager();


        AgentGoal currentGoal =
                goalManager.evaluate(
                        currentState
                );


        /*
         * RecipeEvaluator replaces the previous random
         * recipe selection.
         */
        RecipeDisplayEntry selected =
                RecipeEvaluator.chooseRecipe(
                        minecraft,
                        craftable,
                        currentGoal
                );


        if (selected == null) {

            System.out.println(
                    "[AI Crafting] Recipe evaluator "
                            + "could not select a recipe."
            );

            return null;
        }


        System.out.println(
                "[AI Crafting] Crafting-grid choices: "
                        + craftable.size()
        );


        System.out.println(
                "[AI Crafting] Selected recipe: "
                        + selected.id()
        );


        lastCraftTarget =
                RecipeEvaluator.getOutputId(
                        minecraft,
                        selected
                );


        System.out.println(
                "[AI Crafting] Selected output: "
                        + lastCraftTarget
        );


        System.out.println(
                "[AI Crafting] Display type: "
                        + selected.display()
                                .getClass()
                                .getSimpleName()
        );


        return selected;
    }


    // =========================================================
    // START / CONTINUE CRAFTING
    // =========================================================

    public static boolean craft(
            Minecraft minecraft
    ) {

        if (minecraft.player == null
                || minecraft.gameMode == null) {

            return false;
        }


        /*
         * If we've already placed a recipe,
         * finish that craft before choosing
         * another one.
         */
        if (waitingForResult) {

            return collectCraftingResult(
                    minecraft
            );
        }


        lastCraftTarget = null;
        timedOut = false;
        RecipeDisplayEntry recipe =
                chooseCraftableRecipe(
                        minecraft
                );


        if (recipe == null) {

            return false;
        }


        int containerId =
                minecraft.player
                        .containerMenu
                        .containerId;


        System.out.println(
                "[AI Crafting] Container: "
                        + containerId
        );


        if (isUsingCraftingTable(
                minecraft
        )) {

            System.out.println(
                    "[AI Crafting] Using 3x3 "
                            + "crafting table."
            );

        } else {

            System.out.println(
                    "[AI Crafting] Using 2x2 "
                            + "inventory crafting grid."
            );
        }


        System.out.println(
                "[AI Crafting] Attempting recipe: "
                        + recipe.id()
        );


        /*
         * Ask Minecraft to place the recipe's
         * ingredients into the currently open
         * crafting grid.
         */
        minecraft.gameMode.handlePlaceRecipe(
                containerId,
                recipe.id(),
                false
        );


        waitingForResult =
                true;


        resultReadyTime =
                System.currentTimeMillis()
                        + RESULT_DELAY_MS;


        craftingTimeoutTime =
                System.currentTimeMillis()
                        + CRAFT_TIMEOUT_MS;


        System.out.println(
                "[AI Crafting] Recipe placed - "
                        + "waiting for result."
        );


        return true;
    }


    // =========================================================
    // COLLECT CRAFTING RESULT
    // =========================================================

    private static boolean collectCraftingResult(
            Minecraft minecraft
    ) {

        if (minecraft.player == null
                || minecraft.gameMode == null) {

            reset();

            return false;
        }


        long now =
                System.currentTimeMillis();


        /*
         * Don't wait forever.
         *
         * If Minecraft never creates an output,
         * abandon this attempt and allow the
         * agent to make another decision.
         */
        if (now >= craftingTimeoutTime) {

            System.out.println(
                    "[AI Crafting] Craft timed out - "
                            + "abandoning attempt."
            );

            reset();
            timedOut = true;
            return false;
        }


        /*
         * Give the server time to update the
         * crafting result slot.
         */
        if (now < resultReadyTime) {

            return true;
        }


        int containerId =
                minecraft.player
                        .containerMenu
                        .containerId;


        /*
         * Both InventoryMenu and CraftingMenu use
         * result slot 0.
         */
        int resultSlot =
                InventoryMenu.RESULT_SLOT;


        ItemStack result =
                minecraft.player
                        .containerMenu
                        .getSlot(resultSlot)
                        .getItem();


        /*
         * Result isn't ready yet.
         */
        if (result.isEmpty()) {

            resultReadyTime =
                    now + RESULT_DELAY_MS;

            return true;
        }


        System.out.println(
                "[AI Crafting] Result available: "
                        + result
        );


        /*
         * QUICK_MOVE is equivalent to
         * shift-clicking the crafting output.
         *
         * This moves the result directly into
         * the player's inventory.
         */
        minecraft.gameMode.handleContainerInput(
                containerId,
                resultSlot,
                0,
                ContainerInput.QUICK_MOVE,
                minecraft.player
        );


        System.out.println(
                "[AI Crafting] Collected crafting result."
        );


        reset();

        return true;
    }


    // =========================================================
    // CRAFTING STATE
    // =========================================================

    public static boolean didTimeOut() { return timedOut; }

    public static boolean isCrafting() {

        return waitingForResult;
    }


    public static String getLastCraftTarget() {

        return lastCraftTarget;
    }


    public static void reset() {
        timedOut = false;
        waitingForResult =
                false;

        resultReadyTime =
                0;

        craftingTimeoutTime =
                0;
    }


    // =========================================================
    // DEBUGGING
    // =========================================================

    public static void printCraftableRecipes(
            Minecraft minecraft
    ) {

        List<RecipeDisplayEntry> known =
                getKnownRecipes(
                        minecraft
                );


        List<RecipeDisplayEntry> craftable =
                getCraftableRecipes(
                        minecraft
                );


        System.out.println(
                "[AI Crafting] Known recipes: "
                        + known.size()
        );


        System.out.println(
                "[AI Crafting] Crafting-grid recipes "
                        + "currently craftable: "
                        + craftable.size()
        );


        if (isUsingCraftingTable(
                minecraft
        )) {

            System.out.println(
                    "[AI Crafting] Active grid: 3x3"
            );

        } else {

            System.out.println(
                    "[AI Crafting] Active grid: 2x2"
            );
        }


        int shown =
                0;


        for (RecipeDisplayEntry recipe
                : craftable) {

            System.out.println(
                    "[AI Crafting] CAN CRAFT: "
                            + recipe.id()
                            + " ["
                            + recipe.display()
                                    .getClass()
                                    .getSimpleName()
                            + "]"
            );


            shown++;


            if (shown >= 20) {
                break;
            }
        }
    }


    // =========================================================
    // UTILITY CLASS
    // =========================================================

    private CraftingExecutor() {

        // Prevent construction.
    }
}