package minecraft_ai_guide.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Records bounded executor attempts, independently of human demonstrations. */
public final class ActionAttemptTracker {
    private GameState before;
    private AgentAction action;
    private String target;
    private long startedAt;
    private final List<AgentExperience> completed = new ArrayList<>();

    public void begin(GameState state, AgentAction action, String target, long now) {
        if (isPending()) {
            throw new IllegalStateException("Finish the current attempt before starting another");
        }
        this.before = Objects.requireNonNull(state);
        this.action = Objects.requireNonNull(action);
        this.target = target;
        this.startedAt = now;
    }

    // Recipe selection happens inside the crafting executor, after the start snapshot.
    public void setTarget(String target) {
        if (!isPending()) throw new IllegalStateException("No active attempt");
        this.target = target;
    }

    public boolean isPending() { return action != null; }
    public AgentAction getAction() { return action; }
    public GameState getBeforeState() { return before; }

    public boolean targetWasAcquired(GameState after) {
        return isPending() && target != null
                && after.getInventory().getOrDefault(target, 0)
                > before.getInventory().getOrDefault(target, 0);
    }

    public boolean hasTimedOut(long now, long timeoutMillis) {
        return isPending() && now - startedAt >= timeoutMillis;
    }

    public void finish(GameState after, ActionOutcome outcome, long now) {
        if (!isPending()) return; // Terminal events may be delivered more than once.
        Objects.requireNonNull(after);
        Objects.requireNonNull(outcome);
        // Targeted acquisition cannot succeed merely because an executor accepted input.
        if (outcome == ActionOutcome.SUCCESS
                && (action == AgentAction.CRAFT || action == AgentAction.COLLECT_RESOURCE)
                && !targetWasAcquired(after)) {
            outcome = ActionOutcome.FAILURE;
        }
        double reward = RewardSystem.calculate(before, after);
        if (outcome == ActionOutcome.SUCCESS) {
            if ((action == AgentAction.CRAFT || action == AgentAction.COLLECT_RESOURCE)
                    && targetWasAcquired(after)) reward += 0.50;
        } else {
            // Unrelated inventory gains must not turn a failed attempt into a success reward.
            reward = outcome == ActionOutcome.CANCELLED ? 0.0 : Math.min(-0.50, reward);
        }
        completed.add(new AgentExperience(before, action, after, reward, target, outcome,
                startedAt, Math.max(startedAt, now)));
        before = null;
        action = null;
        target = null;
    }

    public List<AgentExperience> drainCompleted() {
        List<AgentExperience> result = List.copyOf(completed);
        completed.clear();
        return result;
    }
}
