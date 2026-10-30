package minecraft_ai_guide.agent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/** Synthetic fixtures verify experiment accounting, not real-world learning performance. */
public final class ExperimentTests {
    private static int checks;
    private static final QLearningConfig CONFIG = new QLearningConfig(1, .5, .9);

    public static void run() throws Exception {
        baselinesAndFrozenPolicy();
        countersAndCSV();
        System.out.println("Experiment regression checks passed: " + checks);
    }

    private static GameState logs(int count) {
        return new GameState(count == 0 ? Map.of() : Map.of("minecraft:oak_log", count), 0, 64, 0, false);
    }

    private static LogLearningState encode(int count, EpisodeGoal goal) {
        return LogLearningState.encode(logs(count), true, 2, true, goal);
    }

    private static AgentExperience attempt(AgentAction action, ActionOutcome outcome, int before, int after) {
        return new AgentExperience(logs(before), action, logs(after), 9999, "minecraft:oak_log", outcome, 10, 20);
    }

    private static LogQLearningPolicy policy() { return new LogQLearningPolicy(CONFIG, new Random(42)); }

    private static void baselinesAndFrozenPolicy() throws Exception {
        var baseline = new EpisodeGoal(8);
        check(!baseline.achieved(logs(8)) && !encode(8, baseline).hasLog(), "Existing eight logs do not finish a new episode");
        check(baseline.achieved(logs(9)) && encode(9, baseline).hasLog(), "One additional log is terminal");
        check(baseline.acquired(logs(8)) == 0 && baseline.acquired(logs(10)) == 2, "Only net new logs counted");
        check(!baseline.achieved(logs(7)) && !baseline.achieved(logs(8)), "Losing and regaining old logs is not success");
        var nextEpisode = new EpisodeGoal(9);
        check(!encode(9, nextEpisode).hasLog() && encode(10, nextEpisode).hasLog(), "Consecutive episodes need no inventory clearing");
        var p = policy();
        var success = attempt(AgentAction.COLLECT_RESOURCE, ActionOutcome.SUCCESS, 8, 9);
        var trained = p.process(EpisodeMode.TRAINING, encode(8, baseline), success, encode(9, baseline), baseline);
        check(trained.qUpdated() && trained.reward() == 10 && trained.newQ() == 5, "Training learns from baseline-relative acquisition");
        String fingerprint = p.fingerprint();
        for (int i = 0; i < 50; i++) {
            var decision = p.decide(encode(8, baseline), EpisodeMode.EVALUATION);
            check(decision.action() == AgentAction.COLLECT_RESOURCE && !decision.exploratory(),
                    "Evaluation epsilon=0 overrides configured epsilon=1");
        }
        int entries = p.size();
        var evaluated = p.process(EpisodeMode.EVALUATION, encode(8, baseline), success, encode(9, baseline), baseline);
        check(evaluated.reward() == trained.reward() && evaluated.terminal(), "Evaluation measures the same confirmed reward");
        check(!evaluated.qUpdated() && evaluated.previousQ() == evaluated.newQ(), "Evaluation skips Bellman updates");
        check(p.size() == entries && p.fingerprint().equals(fingerprint), "Evaluation leaves all Q entries unchanged");
        var failed = p.process(EpisodeMode.EVALUATION, encode(8, baseline),
                attempt(AgentAction.COLLECT_RESOURCE, ActionOutcome.FAILURE, 8, 8), encode(8, baseline), baseline);
        check(failed.reward() == -1 && !failed.qUpdated(), "Failed evaluation is measured without learning");
        check(p.process(EpisodeMode.EVALUATION, encode(8, baseline),
                attempt(AgentAction.COLLECT_RESOURCE, ActionOutcome.CANCELLED, 8, 8), encode(8, baseline), baseline) == null,
                "Evaluation cancellations do not invent rewards");
        var regained = p.process(EpisodeMode.EVALUATION, encode(7, baseline),
                attempt(AgentAction.COLLECT_RESOURCE, ActionOutcome.SUCCESS, 7, 8), encode(8, baseline), baseline);
        check(!regained.terminal() && regained.reward() != 10, "Old-log replacement cannot earn goal reward");
        var missing = policy();
        missing.process(EpisodeMode.EVALUATION, encode(8, baseline), success, encode(9, baseline), baseline);
        check(missing.size() == 0, "Evaluation cannot insert unseen Q entries");
        var trainingChoice = p.decide(encode(8, baseline), EpisodeMode.TRAINING);
        check(trainingChoice.exploratory(), "Training still uses configured epsilon");

        Path dir = Files.createTempDirectory("frozen-evaluation-");
        try {
            Path file = dir.resolve("one-log-q.properties");
            EpisodeMode.TRAINING.persist(p, file);
            byte[] saved = Files.readAllBytes(file);
            var modified = Files.getLastModifiedTime(file);
            EpisodeMode.EVALUATION.persist(p, file);
            check(java.util.Arrays.equals(saved, Files.readAllBytes(file)), "Evaluation persistence leaves Q bytes unchanged");
            check(modified.equals(Files.getLastModifiedTime(file)), "Evaluation does not rewrite Q-table timestamps");
            Path absent = dir.resolve("no-q-file.properties");
            EpisodeMode.EVALUATION.persist(missing, absent);
            check(!Files.exists(absent), "Untrained evaluation never creates a Q-table");
            var reloaded = policy();
            reloaded.load(file);
            check(reloaded.fingerprint().equals(fingerprint), "Checkpoint identity survives persistence");
        } finally { delete(dir); }
    }

    private static void countersAndCSV() throws Exception {
        var p = policy();
        var baseline = new EpisodeGoal(8);
        var clock = new AtomicLong();
        var metrics = new EpisodeMetrics("00000000-0000-0000-0000-000000000001", EpisodeMode.TRAINING,
                baseline, CONFIG, p.fingerprint(), 1000, clock::get);
        metrics.decision(new LogQLearningPolicy.Decision(AgentAction.MOVE_TO_RESOURCE, true));
        var failed = attempt(AgentAction.MOVE_TO_RESOURCE, ActionOutcome.TIMEOUT, 8, 8);
        var update = p.process(EpisodeMode.TRAINING, encode(8, baseline), failed, encode(8, baseline), baseline);
        metrics.completed(failed, update);
        metrics.decision(new LogQLearningPolicy.Decision(AgentAction.EXPLORE, false));
        var cancelled = attempt(AgentAction.EXPLORE, ActionOutcome.CANCELLED, 8, 8);
        metrics.completed(cancelled, null);
        clock.set(1_500_000_000L);
        var interrupted = metrics.snapshot(EpisodeMetrics.Result.ABORTED, 8, p.fingerprint(), "Stopped, \"manual\"\nsecond line");
        check(interrupted.decisions() == 2 && interrupted.completedActions() == 1, "Selected decisions and completed actions distinguished");
        check(interrupted.failedActions() == 1 && interrupted.cancelledActions() == 1, "Timeout failures and interruptions distinguished");
        check(interrupted.totalReward() == -1 && interrupted.qUpdates() == 1, "Rewards and actual updates counted exactly once");
        check(interrupted.explorationDecisions() == 1 && interrupted.exploreActions() == 1, "Epsilon exploration differs from EXPLORE action count");
        check(interrupted.elapsedMillis() == 1500 && !interrupted.success(), "Monotonic elapsed time and aborted outcome");
        boolean fabricated = false;
        try { metrics.snapshot(EpisodeMetrics.Result.SUCCESS, 9, p.fingerprint(), "unconfirmed"); }
        catch (IllegalStateException expected) { fabricated = true; }
        check(fabricated, "Success cannot be reported without confirmation");

        String frozen = p.fingerprint();
        var eval = new EpisodeMetrics("00000000-0000-0000-0000-000000000002", EpisodeMode.EVALUATION,
                baseline, CONFIG, frozen, 2000, clock::get);
        var choice = new LogQLearningPolicy.Decision(AgentAction.COLLECT_RESOURCE, false);
        var acquired = attempt(AgentAction.COLLECT_RESOURCE, ActionOutcome.SUCCESS, 8, 9);
        eval.decision(choice);
        var assessed = p.process(EpisodeMode.EVALUATION, encode(8, baseline), acquired, encode(9, baseline), baseline);
        eval.completed(acquired, assessed);
        clock.set(2_000_000_000L);
        var success = eval.snapshot(EpisodeMetrics.Result.SUCCESS, 9, frozen, "Confirmed new log");
        check(success.success() && success.endingLogs() - success.baselineLogs() == 1, "Baseline success appears in metrics");
        check(success.qUpdates() == 0 && success.epsilon() == 0 && success.explorationDecisions() == 0,
                "Evaluation records zero learning/exploration");
        check(success.elapsedMillis() == 500, "Each episode has a fresh time baseline");

        var pickupMetrics = new EpisodeMetrics(EpisodeMode.EVALUATION, baseline, CONFIG, frozen);
        pickupMetrics.decision(new LogQLearningPolicy.Decision(AgentAction.EXPLORE, false));
        var pickup = attempt(AgentAction.EXPLORE, ActionOutcome.SUCCESS, 8, 9);
        var pickupAssessment = p.process(EpisodeMode.EVALUATION, encode(8, baseline), pickup,
                encode(9, baseline), baseline);
        pickupMetrics.completed(pickup, pickupAssessment);
        var pickupRecord = pickupMetrics.snapshot(EpisodeMetrics.Result.SUCCESS, 9, frozen, "Observed pickup");
        check(pickupRecord.success() && !pickupRecord.creditedGoalReward(),
                "Log picked up during exploration is task success without invented collection credit");
        check(pickupRecord.totalReward() == -.05 && pickupRecord.qUpdates() == 0,
                "Measuring task success does not rewrite the action reward or learn during evaluation");
        var outside = new EpisodeMetrics(EpisodeMode.EVALUATION, baseline, CONFIG, frozen);
        outside.observe(logs(9));
        var outsideRecord = outside.snapshot(EpisodeMetrics.Result.SUCCESS, 9, frozen, "Inventory observed on stop");
        check(outsideRecord.success() && outsideRecord.completedActions() == 0 && outsideRecord.totalReward() == 0,
                "Observed task success without an attempt creates no fabricated experience/reward");

        Path dir = Files.createTempDirectory("experiment-csv-");
        try {
            var store = new ExperimentStore(dir);
            store.validate();
            check(store.readEpisodes().isEmpty(), "New experiment has no invented episodes");
            store.upsert(metrics.snapshot(EpisodeMetrics.Result.RUNNING, 8, p.fingerprint(), "Running"));
            store.upsert(interrupted);
            store.upsert(success);
            var rows = store.readEpisodes();
            check(rows.size() == 2, "Running/final checkpoints upsert by episode UUID");
            check(rows.stream().anyMatch(interrupted::equals), "CSV preserves quotes, commas and multiline stop reason");
            check(rows.stream().anyMatch(success::equals), "CSV preserves evaluation mode and all metrics");
            check(Files.readString(dir.resolve("episodes.csv")).startsWith("\"schema_version\",\"episode_id\",\"mode\""),
                    "CSV has a versioned documented header");
            store.transition(eval.id(), EpisodeMode.EVALUATION, 1, encode(8, baseline), choice, acquired,
                    encode(9, baseline), assessed);
            var transitions = ExperimentStore.parse(Files.readString(dir.resolve("transitions.csv")));
            check(transitions.get(1).get(0).equals(eval.id()) && transitions.get(1).get(1).equals("EVALUATION"),
                    "Transition and episode CSV use consistent identifiers/modes");
            check(transitions.get(1).getLast().equals("false"), "Evaluation transition explicitly records no Q update");
            String summary = Files.readString(dir.resolve("experiment-summary.md"));
            check(summary.contains("TRAINING | 1 | 0 | 0 | 1 | 0.0%"), "Aborted training stays visible in success denominator");
            check(summary.contains("EVALUATION | 1 | 1 | 0 | 0 | 100.0%"), "Summary uses measured per-mode outcomes");
            check(summary.contains("EVALUATION " + frozen), "Summary separates evaluation checkpoints");
            check(summary.contains("No improvement conclusion is inferred"), "Descriptive comparison does not claim learning improvement");
            check(ExperimentStore.summary(java.util.List.of()).contains("n/a"), "Empty cohorts do not invent success rates");
            store.validate();
            check(ExperimentStore.parse("\"\"").equals(java.util.List.of(java.util.List.of(""))),
                    "CSV retains an empty quoted final field without a newline");
            boolean malformedQuote = false;
            try { ExperimentStore.parse("\"quoted\"garbage\n"); }
            catch (java.io.IOException expected) { malformedQuote = true; }
            check(malformedQuote, "CSV rejects text after a closing quote");
            boolean truncated = false;
            try { ExperimentStore.parse("\"unfinished"); }
            catch (java.io.IOException expected) { truncated = true; }
            check(truncated, "Truncated CSV is not silently treated as valid data");
            String bad = "wrong,csv,schema\n";
            Files.writeString(dir.resolve("episodes.csv"), bad);
            boolean refused = false;
            try { store.upsert(success); } catch (java.io.IOException expected) { refused = true; }
            check(refused && Files.readString(dir.resolve("episodes.csv")).equals(bad), "Malformed CSV refuses updates without data loss");
        } finally { delete(dir); }
    }

    private static void delete(Path dir) throws Exception {
        try (var paths = Files.walk(dir)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
