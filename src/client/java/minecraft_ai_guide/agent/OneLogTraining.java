package minecraft_ai_guide.agent;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

/** One explicitly activated training or frozen-policy evaluation episode. */
public final class OneLogTraining {
    private final ExperienceMemory memory = new ExperienceMemory();
    private LogQLearningPolicy policy;
    private Path tableFile;
    private ExperimentStore store;
    private EpisodeMetrics metrics;
    private EpisodeGoal goal;
    private EpisodeMode mode = EpisodeMode.TRAINING;
    private boolean running;
    private String status = "OFF (F7 train; F9 evaluate; F8 stop)";
    private LogLearningState pendingState;
    private LogQLearningPolicy.Decision pendingChoice;
    private float health;
    private int endingLogs;

    public int experienceCount() { return memory.size(); }
    public boolean isRunning() { return running; }
    public EpisodeMode mode() { return mode; }
    public String status() { return status; }

    public void refuseActivation(String reason) {
        if (!running) status = "REFUSED: " + reason;
    }

    public void checkEnvironment(Minecraft minecraft) {
        if (!running) return;
        if (!ActionExecutor.trainingSessionValid(minecraft)) {
            stop(minecraft, "Stopped: world, focus, menu, or player state changed");
        } else if (minecraft.player.getHealth() < health) {
            stop(minecraft, "Stopped: player took damage");
        }
    }

    public void start(Minecraft minecraft) { start(minecraft, EpisodeMode.TRAINING); }

    public void start(Minecraft minecraft, EpisodeMode requestedMode) {
        if (running) return;
        if (minecraft_ai_guide.client.guide.GameplayRecorder.isRecording()) {
            refuseActivation("finish observation with J before starting an experiment");
            return;
        }
        if (!ActionExecutor.trainingAllowed(minecraft)) {
            refuseActivation("active, private local survival world required");
            return;
        }
        try {
            var config = QLearningConfig.load(minecraft.gameDirectory.toPath()
                    .resolve("config/minecraft-ai-guide/qlearning.properties"));
            var loaded = new LogQLearningPolicy(config, new Random());
            Path directory = minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
                    .resolve("minecraft-ai-guide");
            Path file = directory.resolve("one-log-q.properties");
            loaded.load(file);
            var experimentStore = new ExperimentStore(directory);
            experimentStore.validate(); // Refuse corrupt CSV without overwriting historical data.
            requestedMode.persist(loaded, file); // Evaluation never creates or rewrites the Q-table.
            policy = loaded;
            mode = requestedMode;
            tableFile = file;
            store = experimentStore;
            endingLogs = GameState.observe(minecraft).getLogCount();
            goal = new EpisodeGoal(endingLogs);
            metrics = new EpisodeMetrics(mode, goal, config, policy.fingerprint());
            memory.clear();
            pendingState = null;
            pendingChoice = null;
            health = minecraft.player.getHealth();
            running = true;
            checkpoint(EpisodeMetrics.Result.RUNNING, "Activated"); // Record the UUID before any action.
            ActionExecutor.beginTrainingSession(minecraft);
            status = mode + ": one NEW log; baseline=" + goal.baselineLogs() + "; F8 STOP";
            System.out.println("[One-log experiment] " + status + " episode=" + metrics.id());
        } catch (IOException | RuntimeException e) {
            if (running) stop(minecraft, "Stopped: activation error: " + e.getMessage());
            else refuseActivation(e.getMessage());
        }
    }

    public void stop(Minecraft minecraft, String reason) {
        stop(minecraft, reason, EpisodeMetrics.Result.ABORTED);
    }

    private void stop(Minecraft minecraft, String reason, EpisodeMetrics.Result result) {
        if (!running) return;
        // Release movement/mining first; persist only after controls are released.
        ActionExecutor.cancel(minecraft);
        String error = "";
        for (var attempt : ActionExecutor.drainExperiences()) {
            try { consume(minecraft, attempt); }
            catch (IOException | RuntimeException e) { error += "; transition error: " + e.getMessage(); }
        }
        if (ActionExecutor.trainingWorldMatches(minecraft)) {
            var snapshot = GameState.observe(minecraft);
            endingLogs = snapshot.getLogCount();
            metrics.observe(snapshot); // Goal observation is separate from action reward attribution.
        }
        ActionExecutor.endTrainingSession(minecraft);
        running = false;
        pendingState = null;
        pendingChoice = null;
        if (metrics.goalAchieved()) result = EpisodeMetrics.Result.SUCCESS;
        try { mode.persist(policy, tableFile); }
        catch (IOException e) { error += "; Q SAVE FAILED: " + e.getMessage(); }
        try { checkpoint(result, reason + error); }
        catch (IOException e) { error += "; METRICS SAVE FAILED: " + e.getMessage(); }
        var record = metrics.snapshot(result, endingLogs, policy.fingerprint(), reason);
        status = mode + " " + result + ": decisions=" + record.decisions() + ", failed=" + record.failedActions()
                + ", reward=" + record.totalReward() + ", updates=" + record.qUpdates() + "; " + reason + error;
        System.out.println("[One-log experiment] " + status);
    }

    public void tick(Minecraft minecraft) {
        if (!running) return;
        try { tickEpisode(minecraft); }
        catch (IOException | RuntimeException e) {
            stop(minecraft, "Stopped: experiment error: " + e.getMessage());
        }
    }

    private void tickEpisode(Minecraft minecraft) throws IOException {
        checkEnvironment(minecraft);
        if (!running) return;
        health = minecraft.player.getHealth();
        endingLogs = GameState.observe(minecraft).getLogCount();
        if (metrics.elapsedMillis() >= 120_000) {
            stop(minecraft, "Stopped: episode timeout", EpisodeMetrics.Result.FAILURE);
            return;
        }
        ActionExecutor.update(minecraft);
        for (var attempt : ActionExecutor.drainExperiences()) {
            consume(minecraft, attempt);
            if (metrics.goalAchieved()) {
                stop(minecraft, "Episode ended: new log observed; goal reward credited=" + metrics.creditedGoalReward(), EpisodeMetrics.Result.SUCCESS);
                return;
            }
            if (attempt.getOutcome() == ActionOutcome.CANCELLED) {
                stop(minecraft, "Stopped: attempt cancelled");
                return;
            }
        }
        if (ActionExecutor.isBusy()) return;
        if (metrics.decisions() >= 100) {
            stop(minecraft, "Stopped: decision limit", EpisodeMetrics.Result.FAILURE);
            return;
        }
        GameState snapshot = GameState.observe(minecraft);
        endingLogs = snapshot.getLogCount();
        var target = ActionExecutor.observeTrainingTarget(minecraft);
        pendingState = encode(snapshot, target);
        if (pendingState.hasLog()) {
            metrics.observe(snapshot);
            stop(minecraft, "Episode ended: inventory gain outside completed collection; no invented reward",
                    EpisodeMetrics.Result.SUCCESS);
            return;
        }
        pendingChoice = policy.decide(pendingState, mode);
        metrics.decision(pendingChoice);
        checkpoint(EpisodeMetrics.Result.RUNNING, "Decision selected");
        status = mode + ": " + pendingChoice.action() + "; decisions=" + metrics.decisions()
                + "; updates=" + metrics.updates() + "; epsilon=" + (mode.learns() ? policy.config().epsilon() : 0)
                + "; F8 STOP";
        ActionExecutor.executeTraining(minecraft, pendingChoice.action(), target);
    }

    private void consume(Minecraft minecraft, AgentExperience attempt) throws IOException {
        memory.add(attempt);
        if (pendingState == null || pendingChoice == null || pendingChoice.action() != attempt.getAction())
            throw new IllegalStateException("Executor/decision mismatch; no invented transition");
        endingLogs = attempt.getAfterState().getLogCount();
        var target = ActionExecutor.observeTrainingTarget(minecraft);
        var after = encode(attempt.getAfterState(), target);
        // A completed outcome can still be measured after losing focus/world access, but its
        // unavailable post-action perception must not produce a cross-world bootstrap update.
        EpisodeMode processingMode = mode.learns() && ActionExecutor.trainingSessionValid(minecraft)
                ? EpisodeMode.TRAINING : EpisodeMode.EVALUATION;
        var update = policy.process(processingMode, pendingState, attempt, after, goal);
        metrics.completed(attempt, update);
        var before = pendingState;
        var choice = pendingChoice;
        pendingState = null;
        pendingChoice = null;
        mode.persist(policy, tableFile);
        store.transition(metrics.id(), mode, metrics.decisions(), before, choice, attempt, after, update);
        checkpoint(EpisodeMetrics.Result.RUNNING, "Attempt completed");
    }

    private void checkpoint(EpisodeMetrics.Result result, String reason) throws IOException {
        store.upsert(metrics.snapshot(result, endingLogs, policy.fingerprint(), reason));
    }

    private LogLearningState encode(GameState snapshot, ActionExecutor.LogTarget target) {
        return LogLearningState.encode(snapshot, target != null,
                target == null ? 0 : target.distance(), target != null && target.canMine(), goal);
    }
}
