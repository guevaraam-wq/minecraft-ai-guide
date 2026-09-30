package minecraft_ai_guide.agent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RewardSystem {

    public static double calculate(
            GameState before,
            GameState after
    ) {

        /*
         * Small time/action penalty.
         *
         * An action should accomplish something
         * useful rather than being rewarded simply
         * for existing.
         */
        double reward = -0.01;


        Map<String, Integer> beforeInventory =
                before.getInventory();

        Map<String, Integer> afterInventory =
                after.getInventory();


        // -----------------------------------------------------
        // INVENTORY CHANGES
        // -----------------------------------------------------

        /*
         * Look at every item that existed either
         * before or after the action.
         */
        Set<String> itemIds =
                new HashSet<>();

        itemIds.addAll(
                beforeInventory.keySet()
        );

        itemIds.addAll(
                afterInventory.keySet()
        );


        int totalItemsGained = 0;
        int totalItemsLost = 0;

        boolean discoveredNewItem = false;


        for (String itemId : itemIds) {

            int beforeCount =
                    beforeInventory.getOrDefault(
                            itemId,
                            0
                    );

            int afterCount =
                    afterInventory.getOrDefault(
                            itemId,
                            0
                    );


            if (afterCount > beforeCount) {

                int gained =
                        afterCount
                                - beforeCount;

                totalItemsGained += gained;


                /*
                 * New item types are useful
                 * experiences because the agent has
                 * discovered a new state.
                 */
                if (beforeCount == 0) {

                    discoveredNewItem =
                            true;
                }
            }


            if (afterCount < beforeCount) {

                totalItemsLost +=
                        beforeCount
                                - afterCount;
            }
        }


        /*
         * Reward actual acquisition.
         *
         * This works for:
         *
         * - collecting blocks
         * - crafting outputs
         * - obtaining drops
         *
         * without needing to know which Minecraft
         * items are "supposed" to be valuable.
         */
        if (totalItemsGained > 0) {

            reward += 0.25;
        }


        /*
         * Give a small exploration bonus when the
         * agent obtains an item type it did not
         * previously possess.
         */
        if (discoveredNewItem) {

            reward += 0.25;
        }


        /*
         * Inventory rearrangement should NOT
         * automatically count as success.
         *
         * If items disappear but nothing new is
         * gained, this may simply be ingredients
         * being moved into a crafting grid.
         */
        if (totalItemsLost > 0
                && totalItemsGained == 0) {

            reward -= 0.10;
        }


        // -----------------------------------------------------
        // MOVEMENT
        // -----------------------------------------------------

        double movement =
                Math.abs(
                        after.getX()
                                - before.getX()
                )
                + Math.abs(
                        after.getY()
                                - before.getY()
                )
                + Math.abs(
                        after.getZ()
                                - before.getZ()
                );


        /*
         * Movement gets only a tiny reward.
         *
         * Otherwise wandering can become more
         * attractive than making real progress.
         */
        if (movement > 1.0) {

            reward += 0.02;
        }


        return reward;
    }


    private RewardSystem() {

        // Utility class.
    }
}