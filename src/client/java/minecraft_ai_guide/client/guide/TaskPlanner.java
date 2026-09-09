package minecraft_ai_guide.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

public class TaskPlanner {

    public static GuideStep getNextStep(
            Minecraft minecraft,
            TaskNode goal
    ) {

        if (minecraft.player == null) {
            return new GuideStep(
                    "waiting",
                    "Waiting for player..."
            );
        }

        // If the overall goal is complete, we're done.
        if (isTaskComplete(minecraft, goal)) {
            return new GuideStep(
                    "complete",
                    "Goal complete! You have a stone axe."
            );
        }

        TaskNode nextTask =
                findFirstIncompleteTask(
                        minecraft,
                        goal
                );

        if (nextTask == null) {
            return new GuideStep(
                    "waiting",
                    "Checking next step..."
            );
        }

        return new GuideStep(
                nextTask.getId(),
                nextTask.getInstruction()
        );
    }

    private static TaskNode findFirstIncompleteTask(
            Minecraft minecraft,
            TaskNode task
    ) {

        // Check dependencies first.
        for (TaskNode dependency :
                task.getDependencies()) {

            if (!isTaskComplete(
                    minecraft,
                    dependency
            )) {

                TaskNode deeperTask =
                        findFirstIncompleteTask(
                                minecraft,
                                dependency
                        );

                if (deeperTask != null) {
                    return deeperTask;
                }
            }
        }

        // Dependencies are satisfied,
        // so this task itself is next.
        if (!isTaskComplete(minecraft, task)) {
            return task;
        }

        return null;
    }

    private static boolean isTaskComplete(
            Minecraft minecraft,
            TaskNode task
    ) {

        var inventory =
                minecraft.player.getInventory();

        return switch (task.getId()) {

            case "collect_log" ->
                    inventory.contains(
                            stack -> stack.is(ItemTags.LOGS)
                    );

            case "craft_planks" ->
                    inventory.contains(
                            stack -> stack.is(ItemTags.PLANKS)
                    );

            case "craft_sticks" ->
                    inventory.countItem(Items.STICK) >= 2;

            case "craft_crafting_table" ->
                    inventory.contains(
                            stack ->
                                    stack.is(Items.CRAFTING_TABLE)
                    );

            case "craft_wooden_pickaxe" ->
                    inventory.contains(
                            stack ->
                                    stack.is(Items.WOODEN_PICKAXE)
                                    || stack.is(Items.STONE_PICKAXE)
                                    || stack.is(Items.IRON_PICKAXE)
                                    || stack.is(Items.GOLDEN_PICKAXE)
                                    || stack.is(Items.DIAMOND_PICKAXE)
                                    || stack.is(Items.NETHERITE_PICKAXE)
                    );

            case "collect_cobblestone" ->
                    inventory.countItem(
                            Items.COBBLESTONE
                    ) >= 3;

            case "craft_stone_axe" ->
                    inventory.contains(
                            stack ->
                                    stack.is(Items.STONE_AXE)
                    );

            default -> false;
        };
    }
}