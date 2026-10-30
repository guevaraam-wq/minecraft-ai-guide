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

    private static boolean trainingSession;
    private static net.minecraft.client.server.IntegratedServer trainingServer;
    private static net.minecraft.client.multiplayer.ClientLevel trainingLevel;

    public record LogTarget(BlockPos position, List<BlockPos> path, double distance, boolean canMine) {
        public LogTarget { position = position.immutable(); path = List.copyOf(path); }
    }

    public static boolean trainingAllowed(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) return false;
        var server = minecraft.getSingleplayerServer();
        return new TrainingEligibility(minecraft.hasSingleplayerServer() && server != null,
                server != null && server.isPublished(), minecraft.player.isAlive(),
                !minecraft.player.isCreative() && !minecraft.player.isSpectator(),
                minecraft.isWindowActive(), minecraft.isPaused(), minecraft.gui.screen() != null).allowed();
    }

    // Package-private: only the explicitly activated training controller owns this session.
    static void beginTrainingSession(Minecraft minecraft) {
        if (!trainingAllowed(minecraft)) throw new IllegalStateException("Local survival world required");
        cancel(minecraft);
        drainExperiences();
        rejectedTargets.clear();
        trainingServer = minecraft.getSingleplayerServer();
        trainingLevel = minecraft.level;
        trainingSession = true;
    }

    static void endTrainingSession(Minecraft minecraft) {
        cancel(minecraft);
        trainingSession = false;
        trainingServer = null;
        trainingLevel = null;
    }

    static boolean trainingSessionValid(Minecraft minecraft) {
        return trainingWorldMatches(minecraft) && trainingAllowed(minecraft);
    }

    static boolean trainingWorldMatches(Minecraft minecraft) {
        return trainingSession && minecraft.player != null && minecraft.level != null
                && minecraft.getSingleplayerServer() == trainingServer && minecraft.level == trainingLevel;
    }

    public static LogTarget observeTrainingTarget(Minecraft minecraft) {
        if (!trainingSessionValid(minecraft)) return null;
        PathChoice choice = findBestReachableTree(minecraft);
        if (choice == null) return null;
        return new LogTarget(choice.target, choice.path,
                minecraft.player.getEyePosition().distanceTo(Vec3.atCenterOf(choice.target)),
                canMineFrom(minecraft, choice.target, minecraft.player.getEyePosition()));
    }

    private static boolean canMineFrom(Minecraft minecraft, BlockPos target, Vec3 eye) {
        Vec3 center = Vec3.atCenterOf(target);
        if (eye.distanceTo(center) > minecraft.player.blockInteractionRange() - 0.5) return false;
        var hit = minecraft.level.clip(new net.minecraft.world.level.ClipContext(eye, center,
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, minecraft.player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
    }

    static void executeTraining(Minecraft minecraft, AgentAction action, LogTarget target) {
        if (!trainingSessionValid(minecraft)) {
            cancel(minecraft);
            throw new IllegalStateException("Training has no active local-world authorization");
        }
        if (isBusy()) throw new IllegalStateException("Previous training action is unfinished");
        cancel(minecraft);
        activeAction = action;
        if (action == AgentAction.EXPLORE) {
            startExploring(minecraft);
            return;
        }
        if (action != AgentAction.MOVE_TO_RESOURCE && action != AgentAction.COLLECT_RESOURCE)
            throw new IllegalArgumentException("Only one-log actions are permitted");
        if (target == null || !minecraft.level.getBlockState(target.position()).is(BlockTags.LOGS)
                || (action == AgentAction.COLLECT_RESOURCE
                    && !canMineFrom(minecraft, target.position(), minecraft.player.getEyePosition()))) {
            beginAttempt(minecraft, action, null);
            finishAttempt(minecraft, ActionOutcome.FAILURE);
            activeAction = AgentAction.WAIT;
            return;
        }
        resourceTarget = target.position();
        if (action == AgentAction.COLLECT_RESOURCE) {
            beginCollectingResource(minecraft);
        } else {
            beginAttempt(minecraft, action, BuiltInRegistries.ITEM.getKey(minecraft.level
                    .getBlockState(resourceTarget).getBlock().asItem()).toString());
            currentPath = target.path();
            pathIndex = 0;
            stuckAttempts = 0;
            resetProgressTracking(minecraft);
        }
    }

    private static final Random RANDOM = new Random();
    private static final ActionAttemptTracker attempts = new ActionAttemptTracker();
    private static final long ATTEMPT_TIMEOUT_MS = 30_000;
    private static final long CONFIRMATION_TIMEOUT_MS = 1_500;
    private static long confirmationDeadline;
    private static BlockPos placementTarget;
    private static GameState lastObservedState;
    private static net.minecraft.client.multiplayer.ClientLevel attemptLevel;

    private static void beginAttempt(Minecraft minecraft, AgentAction action, String target) {
        lastObservedState = GameState.observe(minecraft);
        attemptLevel = minecraft.level;
        attempts.begin(lastObservedState, action, target, System.currentTimeMillis());
        confirmationDeadline = 0;
        placementTarget = null;
    }

    private static void finishAttempt(Minecraft minecraft, ActionOutcome outcome) {
        attempts.finish(GameState.observe(minecraft), outcome, System.currentTimeMillis());
        confirmationDeadline = 0;
    }

    private static void awaitConfirmation() {
        activeAction = AgentAction.WAIT;
        confirmationDeadline = System.currentTimeMillis() + CONFIRMATION_TIMEOUT_MS;
    }

    public static List<AgentExperience> drainExperiences() {
        return attempts.drainCompleted();
    }

    public static void cancel(Minecraft minecraft) {
        if (attempts.isPending()) {
            GameState state = minecraft.player != null && minecraft.level != null
                    && minecraft.level == attemptLevel ? GameState.observe(minecraft) : lastObservedState;
            attempts.finish(state, ActionOutcome.CANCELLED, System.currentTimeMillis());
        }
        stopMovement(minecraft);
        CraftingExecutor.reset();
        activeAction = AgentAction.WAIT;
        resourceTarget = null;
        currentPath = Collections.emptyList();
        confirmationDeadline = 0;
        placementTarget = null;
    }


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

        if (minecraft.player == null || minecraft.level == null) {
            cancel(minecraft);
            return;
        }

        if (trainingSession) throw new IllegalStateException("Experimental policy owns the executor");
        BlockPos existingTarget = resourceTarget;
        cancel(minecraft);
        if (action == AgentAction.COLLECT_RESOURCE) resourceTarget = existingTarget;
        activeAction = action;

        switch (action) {

            case EXPLORE ->
                    startExploring(minecraft);

            case FIND_RESOURCE, MOVE_TO_RESOURCE ->
                    startMovingToResource(minecraft);

            case COLLECT_RESOURCE ->
                    beginCollectingResource(minecraft);
            case CRAFT -> {
                beginAttempt(minecraft, AgentAction.CRAFT, null);
                boolean started = CraftingExecutor.craft(minecraft);
                attempts.setTarget(CraftingExecutor.getLastCraftTarget());
                activeAction = AgentAction.WAIT;
                if (!started) finishAttempt(minecraft, ActionOutcome.FAILURE);
            }
            case PLACE_BLOCK -> {
                beginAttempt(minecraft, AgentAction.PLACE_BLOCK, "minecraft:crafting_table");
                if (placeInventoryBlock(minecraft)) {
                    awaitConfirmation();
                } else {
                    activeAction = AgentAction.WAIT;
                    finishAttempt(minecraft, ActionOutcome.FAILURE);
                }
            }

            case WAIT -> {
                // Do nothing.
            }

            default -> {
                beginAttempt(minecraft, action, null);
                finishAttempt(minecraft, ActionOutcome.FAILURE);
                activeAction = AgentAction.WAIT;
            }
        }
    }


    // =========================================================
    // EXPLORATION
    // =========================================================

    private static void startExploring(
            Minecraft minecraft
    ) {

        beginAttempt(minecraft, AgentAction.EXPLORE, null);
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
        GameState before = attempts.getBeforeState();
        double dx = minecraft.player.getX() - before.getX();
        double dz = minecraft.player.getZ() - before.getZ();
        finishAttempt(minecraft, dx * dx + dz * dz > 0.25
                ? ActionOutcome.SUCCESS : ActionOutcome.FAILURE);
        activeAction = AgentAction.WAIT;
    }
}


    // =========================================================
    // RESOURCE SEARCH
    // =========================================================

    private static void startMovingToResource(
            Minecraft minecraft
    ) {

        stopMovement(minecraft);

        beginAttempt(minecraft, AgentAction.MOVE_TO_RESOURCE, null);
        PathChoice choice = findBestReachableTree(minecraft);

        if (choice == null) {

            System.out.println(
                    "[AI Agent] No reachable tree found nearby"
            );

            finishAttempt(minecraft, ActionOutcome.FAILURE);
            activeAction = AgentAction.EXPLORE;
            startExploring(minecraft);

            return;
        }

        resourceTarget = choice.target;
        attempts.setTarget(BuiltInRegistries.ITEM.getKey(minecraft.level
                .getBlockState(resourceTarget).getBlock().asItem()).toString());

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
            if (trainingSession) {
                BlockPos end = path.getLast();
                Vec3 endpointEye = new Vec3(end.getX() + 0.5, end.getY() + 1.62, end.getZ() + 0.5);
                if (!canMineFrom(minecraft, candidate, endpointEye)) continue;
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

        if ((trainingSession && !trainingSessionValid(minecraft))
                || minecraft.player == null || minecraft.level == null
                || (attempts.isPending() && minecraft.level != attemptLevel)) {
            cancel(minecraft);
            return;
        }
        lastObservedState = GameState.observe(minecraft);
        if (attempts.hasTimedOut(System.currentTimeMillis(), ATTEMPT_TIMEOUT_MS)) {
            finishAttempt(minecraft, ActionOutcome.TIMEOUT);
            cancel(minecraft);
            return;
        }
        if (CraftingExecutor.isCrafting()) {
            CraftingExecutor.craft(minecraft);
            if (!CraftingExecutor.isCrafting()) {
                if (CraftingExecutor.didTimeOut()) {
                    finishAttempt(minecraft, ActionOutcome.TIMEOUT);
                } else {
                    awaitConfirmation();
                }
            }
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

        if (attempts.isPending() && confirmationDeadline != 0) {
            GameState after = GameState.observe(minecraft);
            boolean success = switch (attempts.getAction()) {
                case CRAFT, COLLECT_RESOURCE -> attempts.targetWasAcquired(after);
                case PLACE_BLOCK -> placementTarget != null && minecraft.level
                        .getBlockState(placementTarget).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE);
                default -> false;
            };
            if (success) {
                finishAttempt(minecraft, ActionOutcome.SUCCESS);
            } else if (System.currentTimeMillis() >= confirmationDeadline) {
                finishAttempt(minecraft, ActionOutcome.FAILURE);
            }
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
            finishAttempt(minecraft, ActionOutcome.FAILURE);
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

            finishAttempt(minecraft, ActionOutcome.FAILURE);
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

            if (distance < 0.7 && (!trainingSession
                    || Math.abs(minecraft.player.getY() - node.getY()) < 0.75)) {
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

            boolean reached = !trainingSession
                    || canMineFrom(minecraft, resourceTarget, minecraft.player.getEyePosition());
            finishAttempt(minecraft, reached ? ActionOutcome.SUCCESS : ActionOutcome.FAILURE);
            if (trainingSession) {
                activeAction = AgentAction.WAIT; // The policy, not the executor, chooses whether to mine.
            } else {
                activeAction = AgentAction.COLLECT_RESOURCE;
                beginCollectingResource(minecraft);
            }

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
        finishAttempt(minecraft, ActionOutcome.FAILURE);
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

        if (trainingSession) activeAction = AgentAction.WAIT;
        else startMovingToResource(minecraft);
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
        String targetId = resourceTarget == null ? null : BuiltInRegistries.ITEM.getKey(
                minecraft.level.getBlockState(resourceTarget).getBlock().asItem()).toString();
        beginAttempt(minecraft, AgentAction.COLLECT_RESOURCE, targetId);
        stopMovement(minecraft);

        if (resourceTarget == null) {
            finishAttempt(minecraft, ActionOutcome.FAILURE);
            activeAction = AgentAction.WAIT;
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
            finishAttempt(minecraft, ActionOutcome.FAILURE);
            stopMining(minecraft);

            activeAction =
                    AgentAction.WAIT;

            return;
        }

        // Disappearance ends the mining input, then inventory confirms collection.
        if (!minecraft.level
                .getBlockState(resourceTarget)
                .is(BlockTags.LOGS)) {

            stopMining(minecraft);

            // A broken block is not proof that its drop reached inventory.
            System.out.println("[AI Agent] Log disappeared; waiting for inventory confirmation");

            resourceTarget = null;

            currentPath =
                    Collections.emptyList();

            pathIndex = 0;
            awaitConfirmation();
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


    BlockPos placementPos = ground.above();
    placementTarget = placementPos.immutable();


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

        return activeAction != AgentAction.WAIT
                || attempts.isPending() || CraftingExecutor.isCrafting();
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
