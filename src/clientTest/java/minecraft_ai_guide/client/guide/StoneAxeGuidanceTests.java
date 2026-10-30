package minecraft_ai_guide.client.guide;

import java.util.*;
import static minecraft_ai_guide.client.guide.RecipeGuidance.*;

/** Recipe fixtures exercise the production dependency solver without launching a world. */
public final class StoneAxeGuidanceTests {
    private static int checks;
    private static final String AXE = "minecraft:stone_axe", TABLE = "minecraft:crafting_table";
    private static final String WOOD = "minecraft:wooden_pickaxe", IRON = "minecraft:iron_pickaxe";
    private static final String COBBLE = "minecraft:cobblestone", BLACK = "minecraft:blackstone", STICK = "minecraft:stick";
    private static final String LOG = "minecraft:oak_log", SPRUCE_LOG = "minecraft:spruce_log";
    private static final String PLANK = "minecraft:oak_planks", SPRUCE = "minecraft:spruce_planks";

    public static void run() {
        var planner = new RecipeGuidance(knowledge());
        var materials = inventory(COBBLE, 3, STICK, 2);
        check(planner.next(context(inventory(AXE, 1), Table.NONE)).action() == Action.COMPLETE,
                "Owned axe skips the entire dependency tree");
        check(planner.next(context(materials, Table.OPEN)).item().equals(AXE), "Existing materials skip wood and mining tools");
        check(planner.next(context(materials, Table.OPEN)).action() == Action.CRAFT, "Ready open table crafts axe");
        check(planner.next(context(materials, Table.IN_REACH)).action() == Action.OPEN_TABLE, "Nearby placed table must be opened");
        check(planner.next(context(materials, Table.NEARBY)).action() == Action.GO_TO_TABLE, "Distant reachable table gets navigation");
        check(planner.next(context(materials, Table.BLOCKED)).action() == Action.BLOCKED, "Blocked table is not treated as usable");
        check(planner.next(context(materials, Table.BLOCKED)).text().contains("blocked"), "Blocked table gives an explanation");

        var readyTable = inventory(COBBLE, 3, STICK, 2, TABLE, 1);
        var heldTable = with(context(readyTable, Table.NONE), true, false, TABLE, Set.of(TABLE), "", Map.of());
        check(planner.next(heldTable).action() == Action.PLACE_TABLE, "Carried table must be placed");
        check(planner.next(with(heldTable, false, false, TABLE, Set.of(TABLE), "", Map.of())).text().contains("no room"),
                "No placement space gives a helpful action and explanation");
        check(planner.next(with(heldTable, true, false, "", Set.of(), "", Map.of())).action() == Action.OPEN_INVENTORY,
                "Table outside hotbar first opens inventory");
        check(planner.next(with(heldTable, true, true, "", Set.of(), "", Map.of())).action() == Action.MOVE_TO_HOTBAR,
                "Open inventory moves the table to hotbar");
        check(planner.next(with(heldTable, true, false, "", Set.of(TABLE), "", Map.of())).text().equals("Hold your crafting table."),
                "Holding table is its own action");
        check(planner.next(context(readyTable, Table.IN_REACH)).action() == Action.OPEN_TABLE,
                "An existing placed table is reused instead of placing another");

        var tableMaterials = inventory(COBBLE, 3, STICK, 2, PLANK, 4);
        var step = planner.next(with(context(tableMaterials, Table.NONE), true, true, "", Set.of(), "", Map.of()));
        check(step.action() == Action.CRAFT && step.item().equals(TABLE), "Missing table is obtained from its recipe");
        var fromLog = with(context(inventory(LOG, 1), Table.NONE), true, true, "", Set.of(), "", Map.of());
        step = planner.next(fromLog);
        check(step.action() == Action.CRAFT && step.item().equals(PLANK) && step.amount() == 4,
                "One owned log leads to its four-plank recipe, not more gathering");
        step = planner.next(with(context(inventory(SPRUCE_LOG, 1), Table.NONE), true, true, "", Set.of(), "", Map.of()));
        check(step.item().equals(SPRUCE) && step.action() == Action.CRAFT, "Owned wood species selects a usable recipe");
        step = planner.next(with(context(inventory(PLANK, 2, LOG, 1), Table.NONE), true, true, "", Set.of(), "", Map.of()));
        check(step.action() == Action.CRAFT && step.amount() == 4, "Partial materials use batch output and only missing ingredients");

        var toolMaterials = inventory(PLANK, 3, STICK, 4);
        step = planner.next(context(toolMaterials, Table.OPEN));
        check(step.action() == Action.CRAFT && step.item().equals(WOOD), "A mining prerequisite comes before cobblestone collection");
        var withIron = with(context(inventory(IRON, 1, STICK, 2), Table.IN_REACH), true, false, IRON, Set.of(IRON), "", Map.of());
        step = planner.next(withIron);
        check(step.action() == Action.COLLECT && step.alternatives().contains(COBBLE), "Existing suitable higher-tier tool skips wooden pickaxe");
        var notHeld = with(withIron, true, false, "", Set.of(IRON), "", Map.of());
        check(planner.next(notHeld).action() == Action.EQUIP_TOOL, "Mining requires holding the available pickaxe");
        check(planner.next(with(withIron, true, false, "", Set.of(), "", Map.of())).action() == Action.OPEN_INVENTORY,
                "Stored pickaxe gets an inventory action");
        check(planner.next(with(withIron, true, true, "", Set.of(), "", Map.of())).action() == Action.MOVE_TO_HOTBAR,
                "Stored pickaxe gets a separate hotbar action");
        check(planner.next(with(withIron, true, true, IRON, Set.of(IRON), "", Map.of())).action() == Action.CLOSE_SCREEN,
                "Close inventory before mining instead of issuing an impossible mine instruction");
        step = planner.next(with(context(inventory(COBBLE, 2, IRON, 1, STICK, 2), Table.IN_REACH), true, false, IRON, Set.of(IRON), "", Map.of()));
        check(step.action() == Action.COLLECT && step.amount() == 1, "Only the missing stone is requested");
        step = planner.next(context(inventory(COBBLE, 2, BLACK, 1, STICK, 2), Table.OPEN));
        check(step.action() == Action.CRAFT && step.item().equals(AXE), "Minecraft-valid mixed ingredient alternatives count together");

        var grid = inventory(COBBLE, 3, STICK, 2);
        step = planner.next(with(context(Map.of(), Table.OPEN), true, false, "", Set.of(), "", grid));
        check(step.action() == Action.CRAFT && step.item().equals(AXE), "Materials in the crafting grid are not treated as lost");
        step = planner.next(with(context(Map.of(), Table.OPEN), true, false, "", Set.of(), AXE, grid));
        check(step.action() == Action.TAKE_RESULT && step.item().equals(AXE), "Result box requires taking the axe, not recrafting it");
        step = planner.next(with(fromLog, true, true, "", Set.of(), STICK, inventory(PLANK, 2)));
        check(step.action() == Action.TAKE_RESULT && step.item().equals(STICK), "Useful crafting results are taken even outside planner order");
        step = planner.next(with(context(inventory(PLANK, 3), Table.OPEN), true, false, "", Set.of(), "", Map.of()));
        check(step.action() == Action.CLOSE_SCREEN && step.item().equals(LOG),
                "Missing shared materials require closing the table before gathering");
        step = planner.next(context(inventory(PLANK, 3), Table.IN_REACH));
        check(step.action() == Action.COLLECT && step.alternatives().contains(LOG),
                "After closing the table, missing shared tool materials are gathered rather than counted twice");

        step = planner.next(context(inventory(STICK, 2), Table.IN_REACH));
        check(step.action() == Action.COLLECT && step.alternatives().contains(LOG),
                "Losing the pickaxe replans its missing prerequisites rather than repeating mine stone");
        step = planner.next(context(materials, Table.NONE));
        check(step.action() == Action.COLLECT && step.alternatives().contains(LOG),
                "Removing the nearby table replans table materials instead of repeating open table");

        step = planner.next(context(Map.of(), Table.NONE));
        check(step.action() == Action.COLLECT && step.alternatives().containsAll(Set.of(LOG, SPRUCE_LOG)),
                "Empty start gathers a valid log type through recipe dependencies");
        check(planner.next(context(materials, Table.IN_REACH)).action() == Action.OPEN_TABLE,
                "Repeated inventory updates replace resource instructions with the next actual action");
        check(planner.next(context(inventory(AXE, 1), Table.IN_REACH)).action() == Action.COMPLETE,
                "Completion automatically replaces the crafting action");
        var screen = new Context(materials, Map.of(), Table.NONE, false, true, true, Set.of(), "", "");
        check(planner.next(screen).action() == Action.CLOSE_SCREEN, "Unrelated menus have one relevant close action");
        var unsupported = new RecipeGuidance(new Knowledge(Map.of(), Map.of()));
        check(unsupported.next(context(Map.of(), Table.NONE)).action() == Action.BLOCKED,
                "Missing recipe knowledge is explained instead of pretending the axe is collectible");
        var cyclic = new RecipeGuidance(new Knowledge(Map.of(AXE, List.of(new Recipe(AXE, 1,
                List.of(new Need(AXE, 1)), false))), Map.of()));
        check(cyclic.next(context(Map.of(), Table.NONE)).action() == Action.BLOCKED, "Recipe cycles terminate with an explanation");
        System.out.println("Stone-axe guidance regression checks passed: " + checks);
    }

    private static Knowledge knowledge() {
        Set<String> planks = Set.of(PLANK, SPRUCE), tools = Set.of(WOOD, IRON), stone = Set.of(COBBLE, BLACK);
        Map<String, List<Recipe>> recipes = Map.of(
                AXE, List.of(new Recipe(AXE, 1, List.of(new Need(stone, 3), new Need(STICK, 2)), true)),
                WOOD, List.of(new Recipe(WOOD, 1, List.of(new Need(planks, 3), new Need(STICK, 2)), true)),
                TABLE, List.of(new Recipe(TABLE, 1, List.of(new Need(planks, 4)), false)),
                STICK, List.of(new Recipe(STICK, 4, List.of(new Need(planks, 2)), false)),
                PLANK, List.of(new Recipe(PLANK, 4, List.of(new Need(LOG, 1)), false)),
                SPRUCE, List.of(new Recipe(SPRUCE, 4, List.of(new Need(SPRUCE_LOG, 1)), false)));
        Map<String, Resource> resources = Map.of(LOG, new Resource(Set.of(LOG), Set.of()),
                SPRUCE_LOG, new Resource(Set.of(SPRUCE_LOG), Set.of()), COBBLE, new Resource(Set.of("minecraft:stone"), tools),
                BLACK, new Resource(Set.of(BLACK), tools));
        return new Knowledge(recipes, resources);
    }
    private static Context context(Map<String, Integer> inventory, Table table) {
        return new Context(inventory, Map.of(), table, false, false, true, inventory.keySet(), "", "");
    }
    private static Context with(Context c, boolean canPlace, boolean open, String held, Set<String> hotbar,
            String result, Map<String, Integer> grid) {
        return new Context(c.inventory(), grid, c.table(), open, false, canPlace, hotbar, held, result);
    }
    private static Map<String, Integer> inventory(Object... pairs) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], (Integer) pairs[i + 1]);
        return map;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
