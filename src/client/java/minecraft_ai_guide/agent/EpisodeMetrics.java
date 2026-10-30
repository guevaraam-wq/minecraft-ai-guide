package minecraft_ai_guide.agent;

import java.util.UUID;
import java.util.function.LongSupplier;

/** Episode-local counters. Time uses a monotonic clock; timestamps use UTC epoch milliseconds. */
public final class EpisodeMetrics {
    public enum Result { RUNNING, SUCCESS, FAILURE, ABORTED }
    public record Record(String id, EpisodeMode mode, Result result, long startedAt, long elapsedMillis,
            int decisions, int completedActions, int failedActions, int cancelledActions, double totalReward,
            int explorationDecisions, int exploreActions, int qUpdates, int baselineLogs, int endingLogs,
            double epsilon, double alpha, double gamma, String policyStart, String policyEnd, String reason, boolean creditedGoalReward) {
        public Record {
            UUID.fromString(id);
            java.util.Objects.requireNonNull(mode);
            java.util.Objects.requireNonNull(result);
            java.util.Objects.requireNonNull(reason);
            new QLearningConfig(epsilon, alpha, gamma);
            if (startedAt < 0 || elapsedMillis < 0 || decisions < 0 || completedActions < 0 || failedActions < 0
                    || cancelledActions < 0 || completedActions + cancelledActions > decisions
                    || failedActions > completedActions || explorationDecisions < 0 || explorationDecisions > decisions
                    || exploreActions < 0 || exploreActions > decisions || qUpdates < 0 || qUpdates > completedActions
                    || baselineLogs < 0 || endingLogs < 0 || !Double.isFinite(totalReward)
                    || !policyStart.matches("[0-9a-f]{64}") || !policyEnd.matches("[0-9a-f]{64}")
                    || (mode == EpisodeMode.EVALUATION && (qUpdates != 0 || epsilon != 0
                        || explorationDecisions != 0 || !policyStart.equals(policyEnd)))) {
                throw new IllegalArgumentException("Invalid episode metrics or frozen-policy mutation");
            }
        }
        public boolean success() { return result == Result.SUCCESS; }
    }

    private final String id;
    private final EpisodeMode mode;
    private final EpisodeGoal goal;
    private final QLearningConfig config;
    private final String policyStart;
    private final long startedAt;
    private final LongSupplier clock;
    private final long startNanos;
    private int decisions, completed, failed, cancelled, exploratory, exploreActions, updates;
    private double reward;
    private boolean success;
    private boolean credited;

    public EpisodeMetrics(EpisodeMode mode, EpisodeGoal goal, QLearningConfig config, String policyStart) {
        this(UUID.randomUUID().toString(), mode, goal, config, policyStart,
                System.currentTimeMillis(), System::nanoTime);
    }

    EpisodeMetrics(String id, EpisodeMode mode, EpisodeGoal goal, QLearningConfig config,
            String policyStart, long startedAt, LongSupplier clock) {
        UUID.fromString(id);
        this.id = id;
        this.mode = mode;
        this.goal = goal;
        this.config = config;
        this.policyStart = policyStart;
        this.startedAt = startedAt;
        this.clock = clock;
        this.startNanos = clock.getAsLong();
    }

    public void decision(LogQLearningPolicy.Decision decision) {
        if (decision.action() == null) throw new IllegalArgumentException("No action decision");
        if (!mode.learns() && decision.exploratory()) throw new IllegalArgumentException("Evaluation exploration");
        decisions++;
        if (decision.exploratory()) exploratory++;
        if (decision.action() == AgentAction.EXPLORE) exploreActions++;
    }

    public void completed(AgentExperience attempt, LogQLearningPolicy.Update update) {
        observe(attempt.getAfterState());
        if (attempt.getOutcome() == ActionOutcome.CANCELLED) { cancelled++; return; }
        if (update == null) throw new IllegalArgumentException("Completed attempt missing assessment");
        if (!mode.learns() && update.qUpdated()) throw new IllegalArgumentException("Evaluation Q update");
        completed++;
        if (!attempt.succeeded() || (attempt.getAction() == AgentAction.COLLECT_RESOURCE
                && !attempt.targetWasAcquired())) failed++;
        reward += update.reward();
        if (update.qUpdated()) updates++;
        credited |= update.terminal() && update.reward() == 10 && attempt.succeeded()
                && attempt.getAction() == AgentAction.COLLECT_RESOURCE && attempt.targetWasAcquired()
                && goal.achieved(attempt.getAfterState());
    }

    public int decisions() { return decisions; }
    public int updates() { return updates; }
    public void observe(GameState snapshot) { success |= goal.achieved(snapshot); }
    public boolean goalAchieved() { return success; }
    public boolean creditedGoalReward() { return credited; }
    public String id() { return id; }
    public long elapsedMillis() { return Math.max(0, (clock.getAsLong() - startNanos) / 1_000_000); }

    public Record snapshot(Result result, int endingLogs, String policyEnd, String reason) {
        if (result == Result.SUCCESS && !success) throw new IllegalStateException("No confirmed episode success");
        return new Record(id, mode, result, startedAt, elapsedMillis(), decisions, completed, failed, cancelled,
                reward, exploratory, exploreActions, updates, goal.baselineLogs(), endingLogs,
                mode.learns() ? config.epsilon() : 0, config.learningRate(), config.discountFactor(),
                policyStart, policyEnd, reason, credited);
    }
}
