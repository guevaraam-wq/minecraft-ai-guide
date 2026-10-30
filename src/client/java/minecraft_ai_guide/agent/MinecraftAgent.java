package minecraft_ai_guide.agent;

import net.minecraft.client.Minecraft;

public class MinecraftAgent {
    

    private final ExperienceMemory memory =
            new ExperienceMemory();

    private final AgentGoalManager goalManager =
            new AgentGoalManager();


    private boolean running = false;

    private long lastDecisionTime = 0;

    private static final long DECISION_INTERVAL = 1000;
    private int decisionCount = 0;

private static final int MAX_DECISIONS = 30;


    // =========================================================
    // START
    // =========================================================

    public void start(Minecraft minecraft) {

        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        running = true;
        decisionCount = 0;

        ActionExecutor.cancel(minecraft);
        collectCompletedAttempts();

        lastDecisionTime = 0;

        System.out.println(
                "[AI Agent] Training started"
        );
    }


    // =========================================================
    // STOP
    // =========================================================

    public void stop(Minecraft minecraft) {
        ActionExecutor.cancel(minecraft);
        collectCompletedAttempts();
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
        if (!running) return;
        if (minecraft.player == null || minecraft.level == null) {
            stop(minecraft);
            return;
        }

        // Executors close attempts at their actual completion boundary, before
        // another action can start. Crafting/pickups include bounded confirmation.
        ActionExecutor.update(minecraft);
        collectCompletedAttempts();
        if (ActionExecutor.isBusy()) return;

        long now = System.currentTimeMillis();
        if (now - lastDecisionTime < DECISION_INTERVAL) return;
        if (++decisionCount > MAX_DECISIONS) {
            stop(minecraft);
            return;
        }

        GameState currentState = GameState.observe(minecraft);
        AgentGoal goal = goalManager.evaluate(currentState);
        AgentAction action = chooseAction(currentState, goal);
        ActionExecutor.execute(minecraft, action);
        collectCompletedAttempts();
        lastDecisionTime = now;
    }

    private void collectCompletedAttempts() {
        for (AgentExperience experience : ActionExecutor.drainExperiences()) {
            memory.add(experience);
            System.out.println("[AI Agent] " + experience.getAction()
                    + " outcome=" + experience.getOutcome()
                    + " target=" + experience.getActionTarget()
                    + " reward=" + experience.getReward());
        }
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

            if (experience.getOutcome() != ActionOutcome.CANCELLED
                    && experience.getReward() > bestReward) {

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
}
