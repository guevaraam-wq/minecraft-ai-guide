package minecraft_ai_guide.agent;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.List;

public class RecipeEvaluator {

    // =========================================================
    // GET RECIPE OUTPUT
    // =========================================================

    public static ItemStack getOutput(
            Minecraft minecraft,
            RecipeDisplayEntry recipe
    ) {

        if (minecraft.level == null) {
            return ItemStack.EMPTY;
        }

        ContextMap context =
                SlotDisplayContext.fromLevel(
                        minecraft.level
                );

        ItemStack output =
                recipe.display()
                        .result()
                        .resolveForFirstStack(
                                context
                        );

        if (output == null) {
            return ItemStack.EMPTY;
        }

        return output;
    }


    // =========================================================
    // GET OUTPUT ID
    // =========================================================

    public static String getOutputId(
            Minecraft minecraft,
            RecipeDisplayEntry recipe
    ) {

        ItemStack output =
                getOutput(
                        minecraft,
                        recipe
                );

        if (output.isEmpty()) {
            return "minecraft:air";
        }

        return BuiltInRegistries.ITEM
                .getKey(output.getItem())
                .toString();
    }


    // =========================================================
    // CHOOSE RECIPE
    // =========================================================

    public static RecipeDisplayEntry chooseRecipe(
            Minecraft minecraft,
            List<RecipeDisplayEntry> recipes,
            AgentGoal goal
    ) {

        if (recipes == null
                || recipes.isEmpty()) {

            return null;
        }


        RecipeDisplayEntry bestRecipe =
                null;

        double bestScore =
                Double.NEGATIVE_INFINITY;


        for (RecipeDisplayEntry recipe
                : recipes) {

            ItemStack output =
                    getOutput(
                            minecraft,
                            recipe
                    );

            if (output.isEmpty()) {
                continue;
            }

            String outputId =
                    getOutputId(
                            minecraft,
                            recipe
                    );


            /*
             * This starts neutral.
             *
             * We are deliberately NOT hard-coding:
             *
             * "pickaxe = 100"
             * "button = bad"
             *
             * Eventually this score will incorporate
             * learned outcomes from ExperienceMemory.
             */
            double score =
                    0.0;


            /*
             * Slightly prefer recipes whose output
             * the player does not already possess.
             *
             * This is an exploration heuristic,
             * not a Minecraft progression script.
             */
            int currentCount =
                    getInventoryCount(
                            minecraft,
                            outputId
                    );

            if (currentCount == 0) {
                score += 0.25;
            }


            /*
             * Small penalty for repeatedly creating
             * an item already present in inventory.
             */
            if (currentCount > 0) {
                score -= 0.05;
            }


            System.out.println(
                    "[AI Recipe Evaluation] "
                            + outputId
                            + " score="
                            + score
                            + " currentCount="
                            + currentCount
                            + " goal="
                            + goal
            );


            if (bestRecipe == null
                    || score > bestScore) {

                bestRecipe =
                        recipe;

                bestScore =
                        score;
            }
        }


        if (bestRecipe != null) {

            System.out.println(
                    "[AI Recipe Evaluation] Selected: "
                            + getOutputId(
                                    minecraft,
                                    bestRecipe
                            )
                            + " score="
                            + bestScore
            );
        }


        return bestRecipe;
    }


    // =========================================================
    // INVENTORY COUNT
    // =========================================================

    private static int getInventoryCount(
            Minecraft minecraft,
            String itemId
    ) {

        if (minecraft.player == null) {
            return 0;
        }

        int count =
                0;

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

            if (stack.isEmpty()) {
                continue;
            }

            String stackId =
                    BuiltInRegistries.ITEM
                            .getKey(
                                    stack.getItem()
                            )
                            .toString();

            if (stackId.equals(itemId)) {

                count +=
                        stack.getCount();
            }
        }

        return count;
    }


    private RecipeEvaluator() {

        // Utility class.
    }
}
