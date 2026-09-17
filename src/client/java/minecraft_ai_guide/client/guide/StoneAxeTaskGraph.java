package minecraft_ai_guide.client.guide;

import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
public class StoneAxeTaskGraph {

    public static TaskNode createGraph() {

        TaskNode collectLog = new TaskNode(
                "collect_log",
                "Collect a log"
        );

        TaskNode craftPlanks = new TaskNode(
                "craft_planks",
                "Turn your log into wooden planks"
        );

        TaskNode craftSticks = new TaskNode(
                "craft_sticks",
                "Craft sticks"
        );

        TaskNode craftCraftingTable = new TaskNode(
                "craft_crafting_table",
                "Craft a crafting table"
        );

        TaskNode craftWoodenPickaxe = new TaskNode(
                "craft_wooden_pickaxe",
                "Craft a wooden pickaxe"
        );

        TaskNode collectCobblestone = new TaskNode(
                "collect_cobblestone",
                "Mine 3 cobblestone"
        );

        TaskNode craftStoneAxe = new TaskNode(
                "craft_stone_axe",
                "Craft a stone axe"
        );


        // MATERIAL REQUIREMENTS

        craftSticks.addRequirement(
                new CraftingRequirement(
                        ItemTags.PLANKS,
                        2,
                        "Wooden Planks"
                )
        );

        craftCraftingTable.addRequirement(
                new CraftingRequirement(
                        ItemTags.PLANKS,
                        4,
                        "Wooden Planks"
                )
        );

        craftWoodenPickaxe.addRequirement(
                new CraftingRequirement(
                        ItemTags.PLANKS,
                        3,
                        "Wooden Planks"
                )
        );

        craftWoodenPickaxe.addRequirement(
                new CraftingRequirement(
                        Items.STICK,
                        2,
                        "Sticks"
                )
        );

        craftStoneAxe.addRequirement(
                new CraftingRequirement(
                        Items.COBBLESTONE,
                        3,
                        "Cobblestone"
                )
        );

        craftStoneAxe.addRequirement(
                new CraftingRequirement(
                        Items.STICK,
                        2,
                        "Sticks"
                )
        );


        // TASK DEPENDENCIES

        craftPlanks.addDependency(collectLog);

        craftSticks.addDependency(craftPlanks);

        craftCraftingTable.addDependency(craftPlanks);

        craftWoodenPickaxe.addDependency(craftSticks);
        craftWoodenPickaxe.addDependency(craftCraftingTable);

        collectCobblestone.addDependency(craftWoodenPickaxe);

        craftStoneAxe.addDependency(collectCobblestone);
        craftStoneAxe.addDependency(craftSticks);
        craftStoneAxe.addDependency(craftCraftingTable);

        return craftStoneAxe;
    }
}