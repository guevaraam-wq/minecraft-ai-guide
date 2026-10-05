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
         * Small cost for every action.
         */
        double reward = -0.01;


        Map<String, Integer> beforeInventory =
                before.getInventory();

        Map<String, Integer> afterInventory =
                after.getInventory();


        // =====================================================
        // INVENTORY CHANGES
        // =====================================================

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

        int newItemTypes = 0;


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


            // -------------------------------------------------
            // ITEMS GAINED
            // -------------------------------------------------

            if (afterCount > beforeCount) {

                int gained =
                        afterCount - beforeCount;

                totalItemsGained += gained;


                /*
                 * Discovering a completely new item type
                 * represents exploration/progression.
                 */
                if (beforeCount == 0) {

                    newItemTypes++;
                }


                /*
                 * Repeatedly producing large quantities of
                 * something already owned should not dominate
                 * the learning signal.
                 */
                if (beforeCount > 0) {

                    reward -=
                            Math.min(
                                    gained,
                                    8
                            ) * 0.01;
                }
            }


            // -------------------------------------------------
            // ITEMS LOST
            // -------------------------------------------------

            if (afterCount < beforeCount) {

                totalItemsLost +=
                        beforeCount - afterCount;
            }
        }


        // =====================================================
        // GENERAL ACQUISITION REWARD
        // =====================================================

        /*
         * Acquiring something is useful, but this is
         * deliberately much smaller than before.
         *
         * Crafting any random item should not immediately
         * produce a huge reward.
         */
        if (totalItemsGained > 0) {

            reward += 0.08;
        }


        /*
         * New item TYPES are more interesting than simply
         * producing more copies of an existing item.
         */
        if (newItemTypes > 0) {

            reward +=
                    Math.min(
                            newItemTypes,
                            3
                    ) * 0.12;
        }


        // =====================================================
        // PROGRESSION MILESTONES
        // =====================================================

        /*
         * These are broad capability milestones rather than
         * a complete hard-coded Minecraft playthrough.
         *
         * They tell the learner that reaching a new capability
         * is more useful than endlessly crafting arbitrary
         * objects.
         */


        // -----------------------------------------------------
        // WOOD -> PLANKS
        // -----------------------------------------------------

        boolean hadPlanks =
                containsMatching(
                        beforeInventory,
                        "_planks"
                );

        boolean hasPlanks =
                containsMatching(
                        afterInventory,
                        "_planks"
                );

        if (!hadPlanks && hasPlanks) {

            reward += 0.40;

            System.out.println(
                    "[AI Reward] Progress milestone: "
                            + "obtained planks."
            );
        }


        // -----------------------------------------------------
        // CRAFTING TABLE
        // -----------------------------------------------------

        boolean hadCraftingTable =
                hasItem(
                        beforeInventory,
                        "minecraft:crafting_table"
                );

        boolean hasCraftingTable =
                hasItem(
                        afterInventory,
                        "minecraft:crafting_table"
                );

        if (!hadCraftingTable
                && hasCraftingTable) {

            reward += 0.75;

            System.out.println(
                    "[AI Reward] Progress milestone: "
                            + "obtained crafting table."
            );
        }


        // -----------------------------------------------------
        // STICKS
        // -----------------------------------------------------

        boolean hadSticks =
                hasItem(
                        beforeInventory,
                        "minecraft:stick"
                );

        boolean hasSticks =
                hasItem(
                        afterInventory,
                        "minecraft:stick"
                );

        if (!hadSticks && hasSticks) {

            reward += 0.25;

            System.out.println(
                    "[AI Reward] Progress milestone: "
                            + "obtained sticks."
            );
        }


        // -----------------------------------------------------
        // TOOLS
        // -----------------------------------------------------

        boolean hadTool =
                containsTool(
                        beforeInventory
                );

        boolean hasTool =
                containsTool(
                        afterInventory
                );

        if (!hadTool && hasTool) {

            reward += 1.0;

            System.out.println(
                    "[AI Reward] Major progress milestone: "
                            + "obtained first tool."
            );
        }


        // =====================================================
        // ITEM LOSS
        // =====================================================

        /*
         * Losing items without gaining anything may mean:
         *
         * - ingredients were moved
         * - an item was dropped
         * - a block was placed
         *
         * Therefore this penalty stays small.
         *
         * Placement will eventually get its own world-state
         * reward rather than being judged only by inventory.
         */
        if (totalItemsLost > 0
                && totalItemsGained == 0) {

            reward -= 0.02;
        }

        // =====================================================
// WORLD PROGRESSION
// =====================================================

/*
 * Reward the transition from having no nearby
 * crafting table to successfully establishing one.
 *
 * This gives PLACE_BLOCK credit for unlocking a
 * new crafting capability.
 */
if (!before.isCraftingTableNearby()
        && after.isCraftingTableNearby()) {

    reward += 1.25;

    System.out.println(
            "[AI Reward] Major progress milestone: "
                    + "established crafting environment."
    );
}
        // =====================================================
        // MOVEMENT
        // =====================================================

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
         * Movement matters because exploration and resource
         * collection require it, but wandering should not
         * overpower progression.
         */
        if (movement > 1.0) {

            reward += 0.02;
        }


        return reward;
    }


    // =========================================================
    // EXACT ITEM CHECK
    // =========================================================

    private static boolean hasItem(
            Map<String, Integer> inventory,
            String itemId
    ) {

        return inventory.getOrDefault(
                itemId,
                0
        ) > 0;
    }


    // =========================================================
    // PARTIAL ITEM-ID CHECK
    // =========================================================

    private static boolean containsMatching(
            Map<String, Integer> inventory,
            String text
    ) {

        for (Map.Entry<String, Integer> entry
                : inventory.entrySet()) {

            if (entry.getValue() > 0
                    && entry.getKey()
                    .contains(text)) {

                return true;
            }
        }

        return false;
    }


    // =========================================================
    // TOOL CHECK
    // =========================================================

    private static boolean containsTool(
            Map<String, Integer> inventory
    ) {

        for (Map.Entry<String, Integer> entry
                : inventory.entrySet()) {

            if (entry.getValue() <= 0) {
                continue;
            }

            String id =
                    entry.getKey();

            if (id.endsWith("_pickaxe")
                    || id.endsWith("_axe")
                    || id.endsWith("_shovel")
                    || id.endsWith("_hoe")) {

                return true;
            }
        }

        return false;
    }


    private RewardSystem() {
        // Utility class.
    }
}