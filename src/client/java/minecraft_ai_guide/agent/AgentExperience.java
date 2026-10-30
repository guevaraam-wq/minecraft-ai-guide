package minecraft_ai_guide.agent;

public class AgentExperience {

    private final GameState beforeState;
    private final AgentAction action;
    private final GameState afterState;
    private final double reward;
    private final ActionOutcome outcome;
    private final long startedAt;
    private final long endedAt;

    /*
     * Optional detail about the action.
     *
     * For CRAFT this stores the item the
     * agent attempted to produce, such as:
     *
     * minecraft:oak_planks
     * minecraft:wooden_pickaxe
     *
     * Other actions may leave this null.
     */
    private final String actionTarget;


    public AgentExperience(
            GameState beforeState,
            AgentAction action,
            GameState afterState,
            double reward,
            String actionTarget,
            ActionOutcome outcome,
            long startedAt,
            long endedAt
    ) {

        this.outcome = java.util.Objects.requireNonNull(outcome);
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.beforeState =
                beforeState;

        this.action =
                action;

        this.afterState =
                afterState;

        this.reward =
                reward;

        this.actionTarget =
                actionTarget;
    }


    public ActionOutcome getOutcome() { return outcome; }

    public long getStartedAt() { return startedAt; }

    public long getEndedAt() { return endedAt; }

    public boolean succeeded() { return outcome == ActionOutcome.SUCCESS; }

    public GameState getBeforeState() {
        return beforeState;
    }


    public AgentAction getAction() {
        return action;
    }


    public GameState getAfterState() {
        return afterState;
    }


    public double getReward() {
        return reward;
    }


    public String getActionTarget() {
        return actionTarget;
    }


    /*
     * Determines whether the targeted item
     * actually increased after the action.
     *
     * This is much stronger evidence of a
     * successful craft than simply checking
     * whether the inventory changed.
     */
    public boolean targetWasAcquired() {

        if (actionTarget == null) {
            return false;
        }

        int beforeCount =
                beforeState
                        .getInventory()
                        .getOrDefault(
                                actionTarget,
                                0
                        );

        int afterCount =
                afterState
                        .getInventory()
                        .getOrDefault(
                                actionTarget,
                                0
                        );

        return afterCount > beforeCount;
    }
}
