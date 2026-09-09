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

        boolean hasStoneAxe =
                inventory.contains(stack -> stack.is(Items.STONE_AXE));

        boolean hasCobblestone =
                inventory.countItem(Items.COBBLESTONE) >= 3;

        boolean hasSticks =
                inventory.countItem(Items.STICK) >= 2;

        boolean hasLogs =
                inventory.contains(stack -> stack.is(ItemTags.LOGS));

        if (hasStoneAxe) {
            return new GuideStep(
                    "complete",
                    "Goal complete! You have a stone axe."
            );
        }

        if (!hasCobblestone) {
            return new GuideStep(
                    "collect_cobblestone",
                    "Collect 3 cobblestone"
            );
        }

        if (!hasSticks && !hasLogs) {
            return new GuideStep(
                    "collect_log",
                    "Collect a log"
            );
        }

        if (!hasSticks) {
            return new GuideStep(
                    "craft_sticks",
                    "Craft 2 sticks"
            );
        }

        return new GuideStep(
                "craft_stone_axe",
                "Craft a stone axe"
        );
    }
}