package minecraft_ai_guide.agent;
import net.minecraft.world.entity.player.Inventory;
import minecraft_ai_guide.client.guide.Pathfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class ActionExecutor {

    private static final Random RANDOM = new Random();

    private static AgentAction activeAction = AgentAction.WAIT;

    private static long actionEndTime = 0;

    private static BlockPos resourceTarget = null;

    private static List<BlockPos> currentPath =
            Collections.emptyList();

    private static int pathIndex = 0;

    // Trees that caused navigation problems.
    private static final Set<BlockPos> rejectedTargets =
            new HashSet<>();

    // Stuck detection.
    private static double lastProgressX = 0;
    private static double lastProgressY = 0;
    private static double lastProgressZ = 0;

    private static long lastProgressCheck = 0;

    private static int stuckAttempts = 0;

    private static final long PROGRESS_CHECK_INTERVAL = 1500;
    private static final double MIN_PROGRESS = 0.35;
    private static final int MAX_STUCK_ATTEMPTS = 2;

    // Keep the first autonomous tests fairly local.
    private static final int RESOURCE_SEARCH_RADIUS = 16;
    private static final int RESOURCE_VERTICAL_RANGE = 4;


    public static void execute(
            Minecraft minecraft,
            AgentAction action
    ) {

        if (minecraft.player == null) {
            return;
        }

        stopMovement(minecraft);

        activeAction = action;

        switch (action) {

            case EXPLORE ->
                    startExploring(minecraft);

            case FIND_RESOURCE, MOVE_TO_RESOURCE ->
                    startMovingToResource(minecraft);

            case COLLECT_RESOURCE ->
                    beginCollectingResource(minecraft);
            case CRAFT -> {

    boolean started =
            CraftingExecutor.craft(minecraft);

    if (started) {
        System.out.println(
                "[AI Agent] Crafting action started"
        );
    } else {
        System.out.println(
                "[AI Agent] Crafting action failed"
        );
    }

    activeAction = AgentAction.WAIT;
}
case PLACE_BLOCK -> {

    boolean placed =
            placeInventoryBlock(
                    minecraft
            );

    if (placed) {

        System.out.println(
                "[AI Agent] Block placement attempted"
        );

    } else {

        System.out.println(
                "[AI Agent] No valid block placement available"
        );
    }

    activeAction =
            AgentAction.WAIT;
}

            case WAIT -> {
                // Do nothing.
            }

            default ->
                    System.out.println(
                            "[AI Agent] Action not implemented yet: "
                                    + action
                    );
        }
    }


    // =========================================================
    // EXPLORATION
    // =========================================================

    private static void startExploring(
            Minecraft minecraft
    ) {

        float currentYaw =
                minecraft.player.getYRot();

        float turnAmount =
                RANDOM.nextFloat(90.0f) - 45.0f;

        minecraft.player.setYRot(
                currentYaw + turnAmount
        );

        minecraft.options.keyUp.setDown(true);

        actionEndTime =
                System.currentTimeMillis() + 800;
    }


    private static void updateExploration(
        Minecraft minecraft
) {

    /*
     * Keep exploration simple and predictable for
     * the prototype.
     *
     * Previously the agent jumped every time it
     * touched the ground, which caused continuous
     * rabbit-like movement.
     */
    minecraft.options.keyUp.setDown(true);

    minecraft.options.keyJump.setDown(false);


    if (System.currentTimeMillis()
            >= actionEndTime) {

        stopMovement(minecraft);

        activeAction =
                AgentAction.WAIT;
    }
}


    // =========================================================
    // RESOURCE SEARCH
    // =========================================================

    private static void startMovingToResource(
            Minecraft minecraft
    ) {

        stopMovement(minecraft);

        PathChoice choice =
                findBestReachableTree(minecraft);

        if (choice == null) {

            System.out.println(
                    "[AI Agent] No reachable tree found nearby"
            );

            activeAction =
                    AgentAction.EXPLORE;

            startExploring(minecraft);

            return;
        }

        resourceTarget =
                choice.target;

        currentPath =
                choice.path;

        pathIndex = 0;

        stuckAttempts = 0;

        resetProgressTracking(minecraft);

        activeAction =
                AgentAction.MOVE_TO_RESOURCE;

        System.out.println(
                "[AI Agent] Selected reachable tree: "
                        + resourceTarget
        );

        System.out.println(
                "[AI Agent] Path contains "
                        + currentPath.size()
                        + " nodes"
        );
    }


    private static PathChoice findBestReachableTree(
            Minecraft minecraft
    ) {

        if (minecraft.player == null
                || minecraft.level == null) {

            return null;
        }

        BlockPos playerPos =
                minecraft.player.blockPosition();

        List<BlockPos> candidates =
                new ArrayList<>();

        for (int x = -RESOURCE_SEARCH_RADIUS;
             x <= RESOURCE_SEARCH_RADIUS;
             x++) {

            for (int y = -RESOURCE_VERTICAL_RANGE;
                 y <= RESOURCE_VERTICAL_RANGE;
                 y++) {

                for (int z = -RESOURCE_SEARCH_RADIUS;
                     z <= RESOURCE_SEARCH_RADIUS;
                     z++) {

                    BlockPos position =
                            playerPos.offset(x, y, z);

                    if (rejectedTargets.contains(position)) {
                        continue;
                    }

                    if (minecraft.level
                            .getBlockState(position)
                            .is(BlockTags.LOGS)) {

                        candidates.add(
                                position.immutable()
                        );
                    }
                }
            }
        }

        // Try closer trees first.
        candidates.sort(
                Comparator.comparingDouble(
                        playerPos::distSqr
                )
        );

        PathChoice bestChoice = null;

        // Avoid running Pathfinder against every log
        // in an entire forest.
        int candidatesToTest =
                Math.min(20, candidates.size());

        for (int i = 0;
             i < candidatesToTest;
             i++) {

            BlockPos candidate =
                    candidates.get(i);

            List<BlockPos> path =
                    Pathfinder.findPath(
                            minecraft,
                            playerPos,
                            candidate
                    );

            if (path.isEmpty()) {
                continue;
            }

            if (bestChoice == null
                    || path.size()
                    < bestChoice.path.size()) {

                bestChoice =
                        new PathChoice(
                                candidate,
                                path
                        );
            }
        }

        return bestChoice;
    }


    // =========================================================
    // UPDATE LOOP
    // =========================================================

    public static void update(
            Minecraft minecraft
    ) {

        if (minecraft.player == null) {
            return;
        }

        if (activeAction == AgentAction.EXPLORE) {

            updateExploration(minecraft);

        } else if (
                activeAction
                        == AgentAction.MOVE_TO_RESOURCE) {

            followResourcePath(minecraft);

        } else if (
                activeAction
                        == AgentAction.COLLECT_RESOURCE) {

            collectResource(minecraft);
        }
    }


    // =========================================================
    // PATH FOLLOWING
    // =========================================================

    private static void followResourcePath(
            Minecraft minecraft
    ) {

        if (resourceTarget == null
                || currentPath.isEmpty()) {

            stopMovement(minecraft);

            activeAction =
                    AgentAction.WAIT;

            return;
        }

        if (minecraft.level == null) {

            stopMovement(minecraft);
            activeAction = AgentAction.WAIT;
            return;
        }

        // Make sure the target still exists.
        if (!minecraft.level
                .getBlockState(resourceTarget)
                .is(BlockTags.LOGS)) {

            System.out.println(
                    "[AI Agent] Target disappeared"
            );

            resourceTarget = null;

            stopMovement(minecraft);

            activeAction =
                    AgentAction.WAIT;

            return;
        }

        checkForStuckMovement(minecraft);

        // Stuck detection may change the current action.
        if (activeAction
                != AgentAction.MOVE_TO_RESOURCE) {

            return;
        }

        // Skip path nodes already reached.
        while (pathIndex < currentPath.size()) {

            BlockPos node =
                    currentPath.get(pathIndex);

            double dx =
                    node.getX() + 0.5
                            - minecraft.player.getX();

            double dz =
                    node.getZ() + 0.5
                            - minecraft.player.getZ();

            double distance =
                    Math.sqrt(
                            dx * dx
                                    + dz * dz
                    );

            if (distance < 0.7) {
                pathIndex++;
            } else {
                break;
            }
        }

        // We reached the end of the path.
        if (pathIndex >= currentPath.size()) {

            stopMovement(minecraft);

            System.out.println(
                    "[AI Agent] Reached tree - beginning collection"
            );

            activeAction =
                    AgentAction.COLLECT_RESOURCE;

            beginCollectingResource(minecraft);

            return;
        }

        BlockPos nextNode =
                currentPath.get(pathIndex);

        facePosition(
                minecraft,
                nextNode
        );

        minecraft.options.keyUp.setDown(true);

        // Jump only when the planned path goes upward.
        if (minecraft.player.onGround()
                && nextNode.getY()
                > minecraft.player
                        .blockPosition()
                        .getY()) {

            minecraft.options.keyJump.setDown(true);

        } else {

            minecraft.options.keyJump.setDown(false);
        }
    }


    // =========================================================
    // STUCK DETECTION
    // =========================================================

    private static void checkForStuckMovement(
            Minecraft minecraft
    ) {

        long now =
                System.currentTimeMillis();

        if (now - lastProgressCheck
                < PROGRESS_CHECK_INTERVAL) {

            return;
        }

        double dx =
                minecraft.player.getX()
                        - lastProgressX;

        double dy =
                minecraft.player.getY()
                        - lastProgressY;

        double dz =
                minecraft.player.getZ()
                        - lastProgressZ;

        double movement =
                Math.sqrt(
                        dx * dx
                                + dy * dy
                                + dz * dz
                );

        if (movement < MIN_PROGRESS) {

            stuckAttempts++;

            System.out.println(
                    "[AI Agent] Movement stalled. Attempt "
                            + stuckAttempts
            );

            if (stuckAttempts
                    >= MAX_STUCK_ATTEMPTS) {

                rejectCurrentTarget(minecraft);

                return;
            }

            List<BlockPos> recalculated =
                    Pathfinder.findPath(
                            minecraft,
                            minecraft.player.blockPosition(),
                            resourceTarget
                    );

            if (!recalculated.isEmpty()) {

                currentPath =
                        recalculated;

                pathIndex = 0;

                System.out.println(
                        "[AI Agent] Recalculated path"
                );

            } else {

                rejectCurrentTarget(minecraft);

                return;
            }

        } else {

            stuckAttempts = 0;
        }

        resetProgressTracking(minecraft);
    }


    private static void rejectCurrentTarget(
            Minecraft minecraft
    ) {

        stopMovement(minecraft);

        if (resourceTarget != null) {

            rejectedTargets.add(
                    resourceTarget.immutable()
            );

            System.out.println(
                    "[AI Agent] Rejecting difficult tree: "
                            + resourceTarget
            );
        }

        resourceTarget = null;

        currentPath =
                Collections.emptyList();

        pathIndex = 0;

        startMovingToResource(minecraft);
    }


    private static void resetProgressTracking(
            Minecraft minecraft
    ) {

        lastProgressX =
                minecraft.player.getX();

        lastProgressY =
                minecraft.player.getY();

        lastProgressZ =
                minecraft.player.getZ();

        lastProgressCheck =
                System.currentTimeMillis();
    }


    // =========================================================
    // RESOURCE COLLECTION
    // =========================================================

    private static void beginCollectingResource(
            Minecraft minecraft
    ) {

        stopMovement(minecraft);

        if (resourceTarget == null) {

            activeAction =
                    AgentAction.WAIT;

            return;
        }

        System.out.println(
                "[AI Agent] Mining target "
                        + resourceTarget
        );
    }


    private static void collectResource(
            Minecraft minecraft
    ) {

        if (resourceTarget == null
                || minecraft.level == null) {

            stopMining(minecraft);

            activeAction =
                    AgentAction.WAIT;

            return;
        }

        // If the target is no longer a log,
        // the block was successfully broken.
        if (!minecraft.level
                .getBlockState(resourceTarget)
                .is(BlockTags.LOGS)) {

            stopMining(minecraft);

            System.out.println(
                    "[AI Agent] Successfully broke log"
            );

            resourceTarget = null;

            currentPath =
                    Collections.emptyList();

            pathIndex = 0;

            activeAction =
                    AgentAction.WAIT;

            return;
        }

        // Keep looking directly at the target log.
        faceBlock(
                minecraft,
                resourceTarget
        );

        // Hold Minecraft's normal attack control.
        minecraft.options.keyAttack.setDown(true);
    }
    // =========================================================
// BLOCK PLACEMENT
// =========================================================

private static boolean placeInventoryBlock(
        Minecraft minecraft
) {

    if (minecraft.player == null
            || minecraft.level == null
            || minecraft.gameMode == null) {

        return false;
    }


// -----------------------------------------------------
// FIND CRAFTING TABLE ANYWHERE IN INVENTORY
// -----------------------------------------------------

int craftingTableSlot = -1;

Inventory inventory =
        minecraft.player
                .getInventory();


for (int i = 0;
     i < inventory.getContainerSize();
     i++) {

    ItemStack stack =
            inventory.getItem(i);

    if (stack.isEmpty()) {
        continue;
    }


    String itemId =
            BuiltInRegistries.ITEM
                    .getKey(
                            stack.getItem()
                    )
                    .toString();


    if (itemId.equals(
            "minecraft:crafting_table"
    )) {

        craftingTableSlot = i;
        break;
    }
}


if (craftingTableSlot == -1) {

    System.out.println(
            "[AI Placement] Crafting table "
                    + "not found anywhere in inventory."
    );

    return false;
}


System.out.println(
        "[AI Placement] Crafting table found "
                + "in inventory slot "
                + craftingTableSlot
);


// -----------------------------------------------------
// MOVE / SELECT CRAFTING TABLE
// -----------------------------------------------------

if (Inventory.isHotbarSlot(
        craftingTableSlot
)) {

    /*
     * Already in hotbar.
     */
    inventory.setSelectedSlot(
            craftingTableSlot
    );

    System.out.println(
            "[AI Placement] Crafting table already "
                    + "in hotbar."
    );

} else {

    /*
     * Minecraft's own Inventory.pickSlot()
     * moves the requested inventory slot into a
     * suitable hotbar slot and selects it.
     */
    System.out.println(
            "[AI Placement] Moving crafting table "
                    + "to hotbar."
    );

    inventory.pickSlot(
            craftingTableSlot
    );
}


// -----------------------------------------------------
// VERIFY SELECTED ITEM
// -----------------------------------------------------

ItemStack selectedStack =
        inventory.getSelectedItem();


if (selectedStack.isEmpty()) {

    System.out.println(
            "[AI Placement] Selected hotbar slot "
                    + "is unexpectedly empty."
    );

    return false;
}


String selectedItemId =
        BuiltInRegistries.ITEM
                .getKey(
                        selectedStack.getItem()
                )
                .toString();


System.out.println(
        "[AI Placement] Selected item: "
                + selectedItemId
);


if (!selectedItemId.equals(
        "minecraft:crafting_table"
)) {

    System.out.println(
            "[AI Placement] Failed to select "
                    + "crafting table."
    );

    return false;
}


    // -----------------------------------------------------
    // FIND NEARBY GROUND
    // -----------------------------------------------------

    BlockPos playerPos =
            minecraft.player
                    .blockPosition();

    BlockPos ground = null;


    for (int radius = 1;
         radius <= 3 && ground == null;
         radius++) {

        for (int x = -radius;
             x <= radius && ground == null;
             x++) {

            for (int z = -radius;
                 z <= radius;
                 z++) {

                if (x == 0 && z == 0) {
                    continue;
                }


                BlockPos candidate =
                        playerPos.offset(
                                x,
                                -1,
                                z
                        );

                BlockPos placementPos =
                        candidate.above();


                /*
                 * Ground must actually support placement.
                 */
                boolean groundSolid =
                        !minecraft.level
                                .getBlockState(candidate)
                                .isAir();


                /*
                 * Destination must be empty.
                 */
                boolean placementEmpty =
                        minecraft.level
                                .getBlockState(placementPos)
                                .isAir();


                if (groundSolid
                        && placementEmpty) {

                    ground = candidate;
                    break;
                }
            }
        }
    }


    if (ground == null) {

        System.out.println(
                "[AI Placement] No valid nearby "
                        + "placement position."
        );

        return false;
    }


    BlockPos placementPos =
            ground.above();


    // -----------------------------------------------------
    // CREATE HIT RESULT
    // -----------------------------------------------------

    Vec3 hitLocation =
            new Vec3(
                    ground.getX() + 0.5,
                    ground.getY() + 1.0,
                    ground.getZ() + 0.5
            );


    BlockHitResult hit =
            new BlockHitResult(
                    hitLocation,
                    Direction.UP,
                    ground,
                    false
            );


    faceBlock(
            minecraft,
            ground
    );


    System.out.println(
            "[AI Placement] Attempting crafting table "
                    + "placement at "
                    + placementPos
    );


    // -----------------------------------------------------
    // PLACE
    // -----------------------------------------------------

    var result =
            minecraft.gameMode.useItemOn(
                    minecraft.player,
                    InteractionHand.MAIN_HAND,
                    hit
            );


    System.out.println(
            "[AI Placement] Interaction result: "
                    + result
    );


    /*
     * Don't claim that the block exists yet.
     *
     * This only tells us whether Minecraft accepted
     * the interaction attempt.
     */
    boolean accepted =
            result.consumesAction();


    if (accepted) {

        System.out.println(
                "[AI Placement] Placement interaction "
                        + "accepted by Minecraft."
        );

    } else {

        System.out.println(
                "[AI Placement] Placement interaction "
                        + "was rejected."
        );
    }


    return accepted;
}


    // =========================================================
    // LOOK DIRECTION
    // =========================================================

    private static void facePosition(
            Minecraft minecraft,
            BlockPos position
    ) {

        double dx =
                position.getX() + 0.5
                        - minecraft.player.getX();

        double dz =
                position.getZ() + 0.5
                        - minecraft.player.getZ();

        float yaw =
                (float) Math.toDegrees(
                        Math.atan2(-dx, dz)
                );

        minecraft.player.setYRot(yaw);
    }


    private static void faceBlock(
            Minecraft minecraft,
            BlockPos position
    ) {

        double targetX =
                position.getX() + 0.5;

        double targetY =
                position.getY() + 0.5;

        double targetZ =
                position.getZ() + 0.5;

        double eyeY =
                minecraft.player.getEyeY();

        double dx =
                targetX - minecraft.player.getX();

        double dy =
                targetY - eyeY;

        double dz =
                targetZ - minecraft.player.getZ();

        double horizontalDistance =
                Math.sqrt(
                        dx * dx
                                + dz * dz
                );

        float yaw =
                (float) Math.toDegrees(
                        Math.atan2(-dx, dz)
                );

        float pitch =
                (float) -Math.toDegrees(
                        Math.atan2(
                                dy,
                                horizontalDistance
                        )
                );

        minecraft.player.setYRot(yaw);
        minecraft.player.setXRot(pitch);
    }


    // =========================================================
    // INPUT CLEANUP
    // =========================================================

    private static void stopMining(
            Minecraft minecraft
    ) {

        minecraft.options.keyAttack.setDown(false);
    }


    public static void stopMovement(
            Minecraft minecraft
    ) {

        minecraft.options.keyUp.setDown(false);
        minecraft.options.keyDown.setDown(false);
        minecraft.options.keyLeft.setDown(false);
        minecraft.options.keyRight.setDown(false);
        minecraft.options.keyJump.setDown(false);

        stopMining(minecraft);
    }


    public static boolean isBusy() {

        return activeAction
                != AgentAction.WAIT;
    }


    // =========================================================
    // PATH + TARGET CONTAINER
    // =========================================================

    private static class PathChoice {

        private final BlockPos target;
        private final List<BlockPos> path;

        private PathChoice(
                BlockPos target,
                List<BlockPos> path
        ) {

            this.target = target;
            this.path = path;
        }
    }
}