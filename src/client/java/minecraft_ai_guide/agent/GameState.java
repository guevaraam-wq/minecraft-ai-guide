package minecraft_ai_guide.agent;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class GameState {

    private final Map<String, Integer> inventory;
    private final int logCount;

    private final double x;
    private final double y;
    private final double z;

    /*
     * World-state information.
     *
     * This lets the learner distinguish between
     * losing a crafting table and successfully
     * placing one in the world.
     */
    private final boolean craftingTableNearby;


    public GameState(
            Map<String, Integer> inventory,
            double x,
            double y,
            double z,
            boolean craftingTableNearby
    ) {

        this(inventory, x, y, z, craftingTableNearby, inventory.entrySet().stream()
                .filter(e -> e.getKey().endsWith("_log") || e.getKey().endsWith("_stem")
                        || e.getKey().endsWith("_wood") || e.getKey().endsWith("_hyphae"))
                .mapToInt(Map.Entry::getValue).sum());
    }

    public GameState(Map<String, Integer> inventory, double x, double y, double z,
            boolean craftingTableNearby, int logCount) {
        this.logCount = logCount;
        this.inventory = Map.copyOf(inventory);

        this.x = x;
        this.y = y;
        this.z = z;

        this.craftingTableNearby =
                craftingTableNearby;
    }


    // =========================================================
    // OBSERVE GAME
    // =========================================================

    public static GameState observe(
            Minecraft minecraft
    ) {

        Map<String, Integer> inventory =
                new HashMap<>();


        if (minecraft.player == null
                || minecraft.level == null) {

            return new GameState(
                    inventory,
                    0,
                    0,
                    0,
                    false
            );
        }


        // -----------------------------------------------------
        // INVENTORY
        // -----------------------------------------------------

        int logs = 0;
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


            if (stack.is(net.minecraft.tags.ItemTags.LOGS)) logs += stack.getCount();
            String itemId =
                    BuiltInRegistries.ITEM
                            .getKey(
                                    stack.getItem()
                            )
                            .toString();


            inventory.merge(
                    itemId,
                    stack.getCount(),
                    Integer::sum
            );
        }


        // -----------------------------------------------------
        // NEARBY WORLD
        // -----------------------------------------------------

        boolean craftingTableNearby =
                findNearbyCraftingTable(
                        minecraft
                );


        return new GameState(
                inventory,
                minecraft.player.getX(),
                minecraft.player.getY(),
                minecraft.player.getZ(),
                craftingTableNearby,
                logs
        );
    }


    // =========================================================
    // FIND CRAFTING TABLE
    // =========================================================

    private static boolean findNearbyCraftingTable(
            Minecraft minecraft
    ) {

        BlockPos center =
                minecraft.player
                        .blockPosition();


        /*
         * Search a small cube around the player.
         *
         * This is intentionally local because the
         * agent only needs to know whether it has
         * created a usable crafting environment
         * nearby.
         */
        for (int x = -4; x <= 4; x++) {

            for (int y = -2; y <= 2; y++) {

                for (int z = -4; z <= 4; z++) {

                    BlockPos pos =
                            center.offset(
                                    x,
                                    y,
                                    z
                            );


                    String blockId =
                            BuiltInRegistries.BLOCK
                                    .getKey(
                                            minecraft.level
                                                    .getBlockState(pos)
                                                    .getBlock()
                                    )
                                    .toString();


                    if (blockId.equals(
                            "minecraft:crafting_table"
                    )) {

                        return true;
                    }
                }
            }
        }


        return false;
    }


    // =========================================================
    // GETTERS
    // =========================================================

    public int getLogCount() { return logCount; }

    public Map<String, Integer> getInventory() {

        return new HashMap<>(
                inventory
        );
    }


    public double getX() {
        return x;
    }


    public double getY() {
        return y;
    }


    public double getZ() {
        return z;
    }


    public boolean isCraftingTableNearby() {

        return craftingTableNearby;
    }
    // =========================================================
// INVENTORY HELPERS
// =========================================================

/*
 * Check whether the player currently possesses
 * at least one of a specific Minecraft item.
 */
public boolean hasItem(
        String itemId
) {

    return inventory.getOrDefault(
            itemId,
            0
    ) > 0;
}


/*
 * Count all inventory items whose Minecraft ID
 * contains the supplied text.
 *
 * Examples:
 *
 * "_log"    -> oak_log, birch_log, etc.
 * "_planks" -> oak_planks, spruce_planks, etc.
 */
public int countItemsContaining(
        String text
) {

    int total = 0;

    for (Map.Entry<String, Integer> entry
            : inventory.entrySet()) {

        if (entry.getKey().contains(text)) {

            total += entry.getValue();
        }
    }

    return total;
}


    // =========================================================
    // STRING
    // =========================================================

    @Override
    public String toString() {

        return "GameState{" +
                "inventory=" + inventory +
                ", position=(" +
                x + ", " +
                y + ", " +
                z + ")" +
                ", craftingTableNearby=" +
                craftingTableNearby +
                "}";
    }
}