package minecraft_ai_guide.agent;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class GameState {

    private final Map<String, Integer> inventory;

    private final double x;
    private final double y;
    private final double z;


    public GameState(
            Map<String, Integer> inventory,
            double x,
            double y,
            double z
    ) {

        this.inventory =
                inventory;

        this.x = x;
        this.y = y;
        this.z = z;
    }


    // =========================================================
    // OBSERVE GAME
    // =========================================================

    public static GameState observe(
            Minecraft minecraft
    ) {

        Map<String, Integer> inventory =
                new HashMap<>();


        if (minecraft.player == null) {

            return new GameState(
                    inventory,
                    0,
                    0,
                    0
            );
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


            if (stack.isEmpty()) {
                continue;
            }


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


        return new GameState(
                inventory,
                minecraft.player.getX(),
                minecraft.player.getY(),
                minecraft.player.getZ()
        );
    }


    // =========================================================
    // INVENTORY QUERIES
    // =========================================================

    public Map<String, Integer> getInventory() {

        return new HashMap<>(
                inventory
        );
    }


    public int getItemCount(
            String itemId
    ) {

        return inventory.getOrDefault(
                itemId,
                0
        );
    }


    public boolean hasItem(
            String itemId
    ) {

        return getItemCount(
                itemId
        ) > 0;
    }


    public int countItemsContaining(
            String text
    ) {

        int total = 0;


        for (
                Map.Entry<String, Integer> entry
                        : inventory.entrySet()
        ) {

            if (entry
                    .getKey()
                    .contains(text)) {

                total +=
                        entry.getValue();
            }
        }


        return total;
    }


    // =========================================================
    // POSITION
    // =========================================================

    public double getX() {
        return x;
    }


    public double getY() {
        return y;
    }


    public double getZ() {
        return z;
    }


    // =========================================================
    // DEBUG
    // =========================================================

    @Override
    public String toString() {

        return "GameState{" +
                "inventory=" +
                inventory +
                ", position=(" +
                x +
                ", " +
                y +
                ", " +
                z +
                ")}";
    }
}