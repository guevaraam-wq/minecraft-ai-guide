package minecraft_ai_guide.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameplayRecorder {

    private static final Map<Item, Integer> previousInventory =
            new HashMap<>();

    private static final List<GameplayAction> actions =
            new ArrayList<>();

    private static boolean recording = false;

    public static void startRecording(Minecraft minecraft) {

        actions.clear();
        previousInventory.clear();

        captureInventory(minecraft, previousInventory);

        recording = true;

        System.out.println(
                "[Minecraft AI Guide] Recording started"
        );
    }

    public static void update(Minecraft minecraft) {

        if (!recording || minecraft.player == null) {
            return;
        }

        Map<Item, Integer> currentInventory =
                new HashMap<>();

        captureInventory(
                minecraft,
                currentInventory
        );

        compareInventories(currentInventory);

        previousInventory.clear();
        previousInventory.putAll(currentInventory);
    }

    public static void stopRecording() {

        recording = false;

        System.out.println(
                "[Minecraft AI Guide] Recording stopped"
        );

        System.out.println(
                "[Minecraft AI Guide] Recorded actions:"
        );

        for (GameplayAction action : actions) {
            System.out.println(
                    "[Minecraft AI Guide] " + action
            );
        }
    }

    public static List<GameplayAction> getActions() {
        return new ArrayList<>(actions);
    }

    public static boolean isRecording() {
        return recording;
    }

    private static void captureInventory(
            Minecraft minecraft,
            Map<Item, Integer> inventory
    ) {

        if (minecraft.player == null) {
            return;
        }

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

            inventory.merge(
                    stack.getItem(),
                    stack.getCount(),
                    Integer::sum
            );
        }
    }

    private static void compareInventories(
            Map<Item, Integer> currentInventory
    ) {

        Map<Item, Boolean> checked =
                new HashMap<>();

        for (Item item : currentInventory.keySet()) {

            int oldAmount =
                    previousInventory.getOrDefault(
                            item,
                            0
                    );

            int newAmount =
                    currentInventory.getOrDefault(
                            item,
                            0
                    );

            recordDifference(
                    item,
                    oldAmount,
                    newAmount
            );

            checked.put(item, true);
        }

        for (Item item : previousInventory.keySet()) {

            if (checked.containsKey(item)) {
                continue;
            }

            int oldAmount =
                    previousInventory.getOrDefault(
                            item,
                            0
                    );

            recordDifference(
                    item,
                    oldAmount,
                    0
            );
        }
    }

    private static void recordDifference(
            Item item,
            int oldAmount,
            int newAmount
    ) {

        int difference =
                newAmount - oldAmount;

        if (difference == 0) {
            return;
        }

        String itemId =
                BuiltInRegistries.ITEM
                        .getKey(item)
                        .toString();

        if (difference > 0) {

            actions.add(
                    new GameplayAction(
                            "GAINED",
                            itemId,
                            difference
                    )
            );

        } else {

            actions.add(
                    new GameplayAction(
                            "LOST",
                            itemId,
                            Math.abs(difference)
                    )
            );
        }
    }
}