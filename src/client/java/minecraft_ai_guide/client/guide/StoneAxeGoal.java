package minecraft_ai_guide.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

public class StoneAxeGoal {

    public static GuideStep getNextStep(Minecraft minecraft) {

        if (minecraft.player == null) {
            return new GuideStep(
                    "waiting",
                    "Waiting for player..."
            );
        }

        var inventory = minecraft.player.getInventory();

        // Check what the player currently has
        boolean hasStoneAxe =
                inventory.contains(stack -> stack.is(Items.STONE_AXE));

        boolean hasEnoughCobblestone =
                inventory.countItem(Items.COBBLESTONE) >= 3;

        boolean hasEnoughSticks =
                inventory.countItem(Items.STICK) >= 2;

        boolean hasLogs =
                inventory.contains(stack -> stack.is(ItemTags.LOGS));

        boolean hasPlanks =
                inventory.contains(stack -> stack.is(ItemTags.PLANKS));

        boolean hasPickaxe =
                inventory.contains(stack ->
                        stack.is(Items.WOODEN_PICKAXE)
                        || stack.is(Items.STONE_PICKAXE)
                        || stack.is(Items.IRON_PICKAXE)
                        || stack.is(Items.GOLDEN_PICKAXE)
                        || stack.is(Items.DIAMOND_PICKAXE)
                        || stack.is(Items.NETHERITE_PICKAXE)
                );

        // Goal is already finished
        if (hasStoneAxe) {
            return new GuideStep(
                    "complete",
                    "Goal complete! You have a stone axe."
            );
        }

        // We need cobblestone, but we cannot mine it yet
        if (!hasEnoughCobblestone && !hasPickaxe) {

            if (!hasLogs && !hasPlanks) {
                return new GuideStep(
                        "collect_log",
                        "Collect a log"
                );
            }

            if (!hasPlanks) {
                return new GuideStep(
                        "craft_planks",
                        "Turn your log into wooden planks"
                );
            }

            if (!hasEnoughSticks) {
                return new GuideStep(
                        "craft_sticks",
                        "Craft sticks"
                );
            }

            return new GuideStep(
                    "craft_wooden_pickaxe",
                    "Craft a wooden pickaxe"
            );
        }

        // We now have a pickaxe, so we can collect stone
        if (!hasEnoughCobblestone) {
            return new GuideStep(
                    "collect_cobblestone",
                    "Mine 3 cobblestone"
            );
        }

        // Stone is handled, but the axe still needs sticks
        if (!hasEnoughSticks) {

            if (!hasLogs && !hasPlanks) {
                return new GuideStep(
                        "collect_log",
                        "Collect a log"
                );
            }

            if (!hasPlanks) {
                return new GuideStep(
                        "craft_planks",
                        "Turn your log into wooden planks"
                );
            }

            return new GuideStep(
                    "craft_sticks",
                    "Craft 2 sticks"
            );
        }

        // Everything required for the axe is available
        return new GuideStep(
                "craft_stone_axe",
                "Craft your stone axe"
        );
    }
}