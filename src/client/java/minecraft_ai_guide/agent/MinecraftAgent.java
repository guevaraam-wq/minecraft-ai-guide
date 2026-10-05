package minecraft_ai_guide.agent;

import net.minecraft.client.Minecraft;

public class MinecraftAgent {
    

    private final ExperienceMemory memory =
            new ExperienceMemory();

    private final AgentGoalManager goalManager =
            new AgentGoalManager();

    private GameState previousState;
    private AgentAction previousAction;
    private String previousActionTarget;

    private boolean running = false;

    private long lastDecisionTime = 0;

    private static final long DECISION_INTERVAL = 1000;
    private int decisionCount = 0;

private static final int MAX_DECISIONS = 30;


    // =========================================================
    // START
    // =========================================================

    public void start(Minecraft minecraft) {

        if (minecraft.player == null) {
            return;
        }

        running = true;
        decisionCount = 0;

        previousState =
                GameState.observe(minecraft);

        previousAction = null;

        lastDecisionTime = 0;

        System.out.println(
                "[AI Agent] Training started"
        );
    }


    // =========================================================
    // STOP
    // =========================================================

    public void stop() {

        running = false;

        System.out.println(
                "[AI Agent] Training stopped"
        );

        System.out.println(
                "[AI Agent] Experiences collected: "
                        + memory.size()
        );
    }


    // =========================================================
    // UPDATE
    // =========================================================

    public void update(Minecraft minecraft) {

        if (!running
                || minecraft.player == null) {
            return;
        }

        /*
         * Allow the currently executing action
         * to continue.
         */
        ActionExecutor.update(minecraft);

        if (ActionExecutor.isBusy()) {
            return;
        }
        /*
 * Finish any crafting operation that is
 * currently waiting for its result.
 */
if (CraftingExecutor.isCrafting()) {

    CraftingExecutor.craft(minecraft);

    return;
}

        long now =
                System.currentTimeMillis();

        if (now - lastDecisionTime
                < DECISION_INTERVAL) {
            return;
        }


        // -----------------------------------------------------
        // Observe current state
        // -----------------------------------------------------

        GameState currentState =
                GameState.observe(minecraft);


        // -----------------------------------------------------
        // Determine current goal
        // -----------------------------------------------------

        AgentGoal currentGoal =
                goalManager.evaluate(
                        currentState
                );

        System.out.println(
                "[AI Agent] Overall goal: "
                        + goalManager.getOverallGoal()
        );

        System.out.println(
                "[AI Agent] Current subgoal: "
                        + currentGoal
        );


        // -----------------------------------------------------
        // Evaluate previous action
        if (previousState != null
        && previousAction != null) {

    double reward =
            RewardSystem.calculate(
                    previousState,
                    currentState
            );

    AgentExperience experience =
            new AgentExperience(
                    previousState,
                    previousAction,
                    currentState,
                    reward,
                    previousActionTarget
            );
            /*
 * A crafting attempt should only receive a
 * positive learning signal when the item it
 * intended to craft was actually acquired.
 */
if (previousAction == AgentAction.CRAFT
        && previousActionTarget != null) {

    if (experience.targetWasAcquired()) {

        reward += 0.50;

        System.out.println(
                "[AI Learning] Successful craft of "
                        + previousActionTarget
        );

    } else {

        reward -= 0.50;

        System.out.println(
                "[AI Learning] Failed craft of "
                        + previousActionTarget
        );
    }

    /*
     * Recreate the experience with the corrected
     * recipe-specific reward.
     */
    experience =
            new AgentExperience(
                    previousState,
                    previousAction,
                    currentState,
                    reward,
                    previousActionTarget
            );
}

    memory.add(
            experience
    );

    System.out.println(
            "[AI Agent] Result: "
                    + previousAction
                    + " reward="
                    + reward
    );

    if (previousActionTarget != null) {

        System.out.println(
                "[AI Agent] Action target: "
                        + previousActionTarget
                        + " acquired="
                        + experience.targetWasAcquired()
        );
    }
}
        


        // -----------------------------------------------------
        // Choose next action
        // -----------------------------------------------------
        decisionCount++;

if (decisionCount > MAX_DECISIONS) {

    System.out.println(
            "[AI Agent] Training session complete - "
                    + "automatic safety stop."
    );

    stop();
    ActionExecutor.stopMovement(minecraft);

    return;
}
        AgentAction nextAction =
                chooseAction(
                        currentState,
                        currentGoal
                );

        System.out.println(
                "[AI Agent] Chose: "
                        + nextAction
        );


        // -----------------------------------------------------
        // Execute action
        // -----------------------------------------------------

        ActionExecutor.execute(
                minecraft,
                nextAction
        );


        // -----------------------------------------------------
        // Remember state/action pair
        // -----------------------------------------------------

        previousState =
        currentState;

previousAction =
        nextAction;

/*
 * Remember what a CRAFT action was trying
 * to produce so the resulting experience
 * can be associated with that item.
 */
if (nextAction == AgentAction.CRAFT) {

    previousActionTarget =
            CraftingExecutor.getLastCraftTarget();

} else {

    previousActionTarget =
            null;
}

lastDecisionTime =
        now;
    }


    // =========================================================
    // ACTION SELECTION
    // =========================================================

    private AgentAction chooseAction(
            GameState state,
            AgentGoal goal
    ) {

        /*
         * BOOTSTRAP POLICY
         *
         * We give the agent enough initial behavior
         * to generate experiences without encoding
         * the complete Minecraft solution.
         */


        // -----------------------------------------------------
        // ACQUIRE WOOD
        // -----------------------------------------------------

        if (goal == AgentGoal.ACQUIRE_WOOD) {

            System.out.println(
                    "[AI Agent] Need wood - "
                            + "searching for a tree."
            );

            return AgentAction.MOVE_TO_RESOURCE;
        }


        // -----------------------------------------------------
        // DEVELOP TOOLS
        // -----------------------------------------------------

        if (goal == AgentGoal.DEVELOP_TOOLS) {

    /*
     * If Minecraft says there are currently
     * craftable recipes, try crafting.
     */
    /*
 * Check whether the agent has discovered recipes
 * that it has materials for, but cannot execute
 * because they require a 3x3 crafting grid.
 */
/*
 * Check whether useful recipes are blocked because
 * they require a 3x3 crafting environment.
 */
if (CraftingExecutor.needsCraftingTable(
        Minecraft.getInstance()
)) {

    System.out.println(
            "[AI Agent] Discovered blocked recipes: "
                    + "3x3 crafting environment required."
    );


    // =====================================================
    // TABLE ALREADY PLACED
    // =====================================================

    /*
     * A nearby crafting table means the prerequisite
     * has already been satisfied.
     *
     * Do NOT craft or place another table.
     */
    if (state.isCraftingTableNearby()) {

        System.out.println(
                "[AI Agent] Nearby crafting table detected."
        );

        System.out.println(
                "[AI Agent] Crafting environment already "
                        + "established - not making another table."
        );

        /*
         * We currently need a separate interaction step
         * before 3x3 recipes can actually be executed.
         *
         * For now, allow learned behavior/exploration
         * rather than creating duplicate tables.
         */
        AgentAction learnedAction =
                ActionEvaluator.chooseBestAction(
                        goal,
                        state,
                        memory
                );

        /*
         * Do not choose PLACE_BLOCK here.
         *
         * The required table is already in the world.
         */
        if (learnedAction != null
                && learnedAction
                != AgentAction.PLACE_BLOCK) {

            System.out.println(
                    "[AI Agent] Crafting environment ready. "
                            + "Evaluator selected: "
                            + learnedAction
            );

            return learnedAction;
        }

        System.out.println(
                "[AI Agent] Crafting environment ready - "
                        + "exploring next capability."
        );

        return AgentAction.EXPLORE;
    }


    // =====================================================
    // TABLE OWNED BUT NOT PLACED
    // =====================================================

    if (state.hasItem(
            "minecraft:crafting_table"
    )) {

        System.out.println(
                "[AI Agent] Crafting table in inventory "
                        + "- trying PLACE_BLOCK."
        );

        return AgentAction.PLACE_BLOCK;
    }


    // =====================================================
    // TABLE NOT YET ACQUIRED
    // =====================================================

    System.out.println(
            "[AI Agent] Crafting table prerequisite detected "
                    + "- need to obtain one."
    );
}
    if (!CraftingExecutor
            .getCraftableRecipes(
                    Minecraft.getInstance()
            )
            .isEmpty()) {

        System.out.println(
                "[AI Agent] Crafting options available."
        );

        return AgentAction.CRAFT;
    }

    /*
     * If nothing can currently be crafted,
     * ask ActionEvaluator what previous
     * experience suggests doing.
     */
    AgentAction learnedAction =
            ActionEvaluator.chooseBestAction(
                    goal,
                    state,
                    memory
            );

    if (learnedAction != null) {

        System.out.println(
                "[AI Agent] Evaluator selected: "
                        + learnedAction
        );

        return learnedAction;
    }

    /*
     * No useful experience yet.
     */
    System.out.println(
            "[AI Agent] No useful learned action "
                    + "for DEVELOP_TOOLS - exploring."
    );

    return AgentAction.EXPLORE;
        }


        // -----------------------------------------------------
        // OTHER GOALS
        // -----------------------------------------------------

        /*
         * Later these goals will receive their own
         * action spaces.
         */

        AgentAction learnedAction =
                findBestLearnedAction(
                        state
                );

        if (learnedAction != null) {

            System.out.println(
                    "[AI Agent] Using learned action: "
                            + learnedAction
            );

            return learnedAction;
        }

        return AgentAction.WAIT;
    }


    // =========================================================
    // SIMPLE LEARNED ACTION LOOKUP
    // =========================================================

    private AgentAction findBestLearnedAction(
            GameState currentState
    ) {

        AgentAction bestAction =
                null;

        double bestReward =
                0.0;

        for (AgentExperience experience
                : memory.getExperiences()) {

            /*
             * For this first learning version,
             * choose the action associated with the
             * highest positive reward.
             *
             * We will later make this state-specific
             * and goal-specific.
             */

            if (experience.getReward()
                    > bestReward) {

                bestReward =
                        experience.getReward();

                bestAction =
                        experience.getAction();
            }
        }

        if (bestAction != null) {

            System.out.println(
                    "[AI Agent] Best learned reward: "
                            + bestReward
                            + " from "
                            + bestAction
            );
        }

        return bestAction;
    }


    // =========================================================
    // ACCESSORS
    // =========================================================

    public ExperienceMemory getMemory() {
        return memory;
    }

    public boolean isRunning() {
        return running;
    }
    public AgentAction getLearnedRecommendation(
        Minecraft minecraft
) {

    if (minecraft == null
            || minecraft.player == null) {

        return null;
    }

    GameState state =
            GameState.observe(minecraft);

    AgentGoal goal =
            goalManager.evaluate(state);

    return ActionEvaluator.chooseBestAction(
            goal,
            state,
            memory
    );
}


public int getExperienceCount() {

    return memory.size();
}
public void learnFromObservation(
        GameState beforeState,
        GameState afterState,
        AgentAction observedAction,
        String target
) {

    if (beforeState == null
            || afterState == null
            || observedAction == null) {
        return;
    }

    double reward =
            RewardSystem.calculate(
                    beforeState,
                    afterState
            );

    /*
     * Human demonstrations are especially useful
     * when they actually acquire the intended item.
     */
    if (target != null) {

        int beforeCount =
                beforeState.getInventory()
                        .getOrDefault(target, 0);

        int afterCount =
                afterState.getInventory()
                        .getOrDefault(target, 0);

        if (afterCount > beforeCount) {
            reward += 1.0;
        }
    }

    AgentExperience experience =
            new AgentExperience(
                    beforeState,
                    observedAction,
                    afterState,
                    reward,
                    target
            );

    memory.add(experience);

    System.out.println(
            "[AI Learning] Learned from player: "
                    + observedAction
                    + " target="
                    + target
                    + " reward="
                    + reward
    );

    System.out.println(
            "[AI Learning] Experiences collected: "
                    + memory.size()
    );
}
}