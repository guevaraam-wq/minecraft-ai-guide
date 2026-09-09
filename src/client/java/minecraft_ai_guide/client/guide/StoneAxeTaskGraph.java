package minecraft_ai_guide.client.guide;

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

        // Define dependencies

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