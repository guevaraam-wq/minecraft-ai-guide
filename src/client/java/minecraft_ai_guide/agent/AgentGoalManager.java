package minecraft_ai_guide.agent;

public class AgentGoalManager {

    private static final AgentGoal OVERALL_GOAL =
            AgentGoal.COMPLETE_GAME;


    private AgentGoal currentSubgoal =
            AgentGoal.ACQUIRE_WOOD;


    public AgentGoal getOverallGoal() {

        return OVERALL_GOAL;
    }


    public AgentGoal getCurrentSubgoal() {

        return currentSubgoal;
    }


    public AgentGoal evaluate(
            GameState state
    ) {

        int logs =
                state.countItemsContaining(
                        "_log"
                );

        int planks =
                state.countItemsContaining(
                        "_planks"
                );


        // -----------------------------------------------------
        // NO WOOD YET
        // -----------------------------------------------------

        if (logs == 0
                && planks == 0) {

            currentSubgoal =
                    AgentGoal.ACQUIRE_WOOD;

            return currentSubgoal;
        }


        // -----------------------------------------------------
        // WOOD ACQUIRED
        // -----------------------------------------------------

        /*
         * The overall objective is still COMPLETE_GAME.
         *
         * DEVELOP_TOOLS represents the current stage
         * of progression.
         *
         * A crafting table is a prerequisite that
         * may be discovered while trying to satisfy
         * this goal; it is not itself the goal.
         */
        currentSubgoal =
                AgentGoal.DEVELOP_TOOLS;


        return currentSubgoal;
    }
}