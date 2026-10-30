package minecraft_ai_guide.agent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;

/** Deterministic algorithm tests; fixture experiences are not gameplay measurements. */
public final class QLearningTests {
    private static int checks;
    private static final GameState EMPTY = new GameState(Map.of(), 0, 64, 0, false);
    private static final GameState LOG = new GameState(Map.of("minecraft:oak_log", 1), 0, 64, 0, false);
    private static final LogLearningState NONE = LogLearningState.encode(EMPTY, false, 0, false);
    private static final LogLearningState FAR = LogLearningState.encode(EMPTY, true, 12, false);
    private static final LogLearningState NEAR = LogLearningState.encode(EMPTY, true, 2, true);
    private static final LogLearningState TERMINAL = LogLearningState.encode(LOG, false, 0, false);

    public static void run() throws Exception {
        encodingAndMasks();
        updatesAndSelection();
        outcomes();
        persistenceAndConfig();
        eligibility();
        System.out.println("Q-learning regression checks passed: " + checks);
    }

    private static LogQLearningPolicy policy(double epsilon, double alpha, double gamma) {
        return new LogQLearningPolicy(new QLearningConfig(epsilon, alpha, gamma), new Random(42));
    }

    private static AgentExperience attempt(AgentAction action, ActionOutcome outcome, boolean acquired) {
        return new AgentExperience(EMPTY, action, acquired ? LOG : EMPTY, 9999,
                action == AgentAction.COLLECT_RESOURCE ? "minecraft:oak_log" : null, outcome, 10, 20);
    }

    private static void encodingAndMasks() {
        check(NONE.distance() == LogLearningState.Distance.NONE && !NONE.targetAvailable(), "Missing target");
        check(NEAR.distance() == LogLearningState.Distance.NEAR && NEAR.canMine(), "Near mineable target");
        check(LogLearningState.encode(EMPTY, true, 3, false).distance() == LogLearningState.Distance.NEAR,
                "Near boundary");
        check(LogLearningState.encode(EMPTY, true, 8, false).distance() == LogLearningState.Distance.MID,
                "Middle boundary");
        check(FAR.distance() == LogLearningState.Distance.FAR, "Far bucket");
        check(!NEAR.equals(FAR) && !NONE.equals(FAR), "Policy conditions on target and distance");
        check(TERMINAL.hasLog() && TERMINAL.applicableActions().isEmpty(), "Inventory terminal masks all actions");
        check(LogLearningState.encode(LOG, true, 2, true).equals(TERMINAL), "Canonical terminal encoding");
        check(LogLearningState.parse(FAR.key()).equals(FAR), "State key round-trip");
        check(NONE.applicableActions().equals(java.util.List.of(AgentAction.EXPLORE)), "No phantom navigation");
        check(FAR.applicableActions().contains(AgentAction.MOVE_TO_RESOURCE)
                && !FAR.applicableActions().contains(AgentAction.COLLECT_RESOURCE), "Mining requires reach and visibility");
        check(NEAR.applicableActions().contains(AgentAction.COLLECT_RESOURCE)
                && NEAR.applicableActions().contains(AgentAction.EXPLORE)
                && NEAR.applicableActions().contains(AgentAction.MOVE_TO_RESOURCE),
                "Mining, approaching, and exploring remain policy choices");
        check(!FAR.applicableActions().contains(AgentAction.CRAFT), "Goal action set excludes crafting");
        var modded = new GameState(Map.of("example:custom_trunk", 1), 0, 64, 0, false, 1);
        check(LogLearningState.encode(modded, false, 0, false).hasLog(), "Explicit tag-based log count supports nonstandard IDs");
        rejects(() -> LogLearningState.encode(EMPTY, true, Double.NaN, false), "Reject NaN distance");
        rejects(() -> LogLearningState.parse("0:0:FAR:1"), "Reject inconsistent persisted state");
    }

    private static void updatesAndSelection() {
        var p = policy(0, .5, .9);
        check(p.value(FAR, AgentAction.MOVE_TO_RESOURCE) == 0, "Unseen Q defaults to zero");
        var success = p.learn(NEAR, attempt(AgentAction.COLLECT_RESOURCE, ActionOutcome.SUCCESS, true), TERMINAL);
        close(success.reward(), 10, "Only confirmed goal acquisition receives goal reward");
        close(success.newQ(), 5, "Terminal update uses no bootstrap");
        var navigation = p.learn(FAR, attempt(AgentAction.MOVE_TO_RESOURCE, ActionOutcome.SUCCESS, false), NEAR);
        close(navigation.newQ(), 2.225, "Q update propagates discounted future acquisition value");
        check(p.choose(FAR) == AgentAction.MOVE_TO_RESOURCE, "Exploitation selects largest applicable Q");
        check(p.choose(NEAR) == AgentAction.COLLECT_RESOURCE, "Same policy makes a different state-conditioned choice");
        check(p.choose(NONE) == AgentAction.EXPLORE, "Unavailable action is never selected");
        check(p.choose(TERMINAL) == null, "No action after terminal state");
        var explorer = policy(1, .5, .9);
        explorer.learn(NEAR, attempt(AgentAction.COLLECT_RESOURCE, ActionOutcome.SUCCESS, true), TERMINAL);
        var explored = new HashSet<AgentAction>();
        for (int i = 0; i < 100; i++) explored.add(explorer.choose(NEAR));
        check(explored.equals(new HashSet<>(NEAR.applicableActions())), "Epsilon=1 samples all applicable actions");
        var tied = policy(0, .5, .9);
        var ties = new HashSet<AgentAction>();
        for (int i = 0; i < 100; i++) ties.add(tied.choose(FAR));
        check(ties.size() == 2, "Untrained greedy ties do not encode a fixed sequence");
        var negative = policy(0, 1, 1);
        negative.learn(NEAR, attempt(AgentAction.EXPLORE, ActionOutcome.FAILURE, false), NONE);
        negative.learn(NEAR, attempt(AgentAction.COLLECT_RESOURCE, ActionOutcome.FAILURE, false), NONE);
        negative.learn(NEAR, attempt(AgentAction.MOVE_TO_RESOURCE, ActionOutcome.FAILURE, false), NONE);
        var result = negative.learn(FAR, attempt(AgentAction.MOVE_TO_RESOURCE, ActionOutcome.SUCCESS, false), NEAR);
        close(result.newQ(), -1.05, "Bootstrap maximizes only applicable actions, even when all are negative");
        rejects(() -> p.learn(NONE, attempt(AgentAction.CRAFT, ActionOutcome.SUCCESS, false), NONE),
                "Reject out-of-scope experiences");
    }

    private static void outcomes() {
        var p = policy(0, 1, .9);
        var failed = p.learn(NEAR, attempt(AgentAction.COLLECT_RESOURCE, ActionOutcome.FAILURE, false), NONE);
        close(failed.reward(), -1, "Failure penalized independently of legacy reward=9999");
        var timed = p.learn(FAR, attempt(AgentAction.MOVE_TO_RESOURCE, ActionOutcome.TIMEOUT, false), NONE);
        close(timed.reward(), -1, "Timed-out actions penalized");
        double old = p.value(FAR, AgentAction.MOVE_TO_RESOURCE);
        check(p.learn(FAR, attempt(AgentAction.MOVE_TO_RESOURCE, ActionOutcome.CANCELLED, false), NONE) == null,
                "Manual stop cancellation is excluded");
        close(p.value(FAR, AgentAction.MOVE_TO_RESOURCE), old, "Cancellation leaves Q unchanged");
        close(p.learn(NEAR, attempt(AgentAction.COLLECT_RESOURCE, ActionOutcome.SUCCESS, false), NONE).reward(),
                -1, "Claimed success without acquisition is a failed collection");
        var unconfirmed = p.learn(NEAR, attempt(AgentAction.COLLECT_RESOURCE, ActionOutcome.FAILURE, true), TERMINAL);
        close(unconfirmed.reward(), -1, "Failed attempt with inventory gain cannot receive success reward");
        check(unconfirmed.terminal(), "Terminal inventory still ends the episode");
        close(unconfirmed.newQ(), -1, "Terminal failure does not bootstrap");
        rejects(() -> p.learn(FAR, attempt(AgentAction.MOVE_TO_RESOURCE, ActionOutcome.SUCCESS, false), TERMINAL),
                "Reject encoded terminal inconsistent with experience");
    }

    private static void persistenceAndConfig() throws Exception {
        Path directory = Files.createTempDirectory("one-log-q-test-");
        try {
            Path file = directory.resolve("world-a/one-log-q.properties");
            var p = policy(0, .5, .9);
            p.learn(NEAR, attempt(AgentAction.COLLECT_RESOURCE, ActionOutcome.SUCCESS, true), TERMINAL);
            p.save(file);
            var loaded = policy(0, .5, .9);
            loaded.load(file);
            close(loaded.value(NEAR, AgentAction.COLLECT_RESOURCE), 5, "Disk round-trip preserves Q");
            check(loaded.size() == p.size() && loaded.choose(NEAR) == AgentAction.COLLECT_RESOURCE,
                    "Policy decisions survive restart");
            loaded.load(directory.resolve("world-b/missing.properties"));
            check(loaded.size() == 0, "New world starts with an empty table");
            Files.writeString(file, "format=wrong-version\n");
            ioRejects(() -> loaded.load(file), "Reject incompatible Q-table schema");
            check(Files.readString(file).contains("wrong-version"), "Malformed table is not overwritten");
            Files.writeString(file, "format=one-log-q-v1\nq.0\\:1\\:NEAR\\:1.COLLECT_RESOURCE=NaN\n");
            ioRejects(() -> loaded.load(file), "Reject nonfinite/corrupt Q values");
            Path config = directory.resolve("config/learning.properties");
            check(QLearningConfig.load(config).equals(QLearningConfig.DEFAULT), "First activation creates defaults");
            Files.writeString(config, "epsilon=0.7\nlearningRate=0.3\ndiscountFactor=0.8\n");
            check(QLearningConfig.load(config).equals(new QLearningConfig(.7, .3, .8)), "Configurable epsilon/alpha/gamma");
            rejects(() -> new QLearningConfig(-.1, .5, .9), "Reject invalid epsilon");
            rejects(() -> new QLearningConfig(.1, 0, .9), "Reject zero learning rate");
            rejects(() -> new QLearningConfig(.1, .5, Double.NaN), "Reject nonfinite discount");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void eligibility() {
        check(!new OneLogTraining().isRunning(), "Training is disabled at startup");
        check(new TrainingEligibility(true, false, true, true, true, false, false).allowed(), "Local survival allowed");
        check(!new TrainingEligibility(false, false, true, true, true, false, false).allowed(), "Remote worlds refused");
        check(!new TrainingEligibility(true, true, true, true, true, false, false).allowed(), "LAN sharing refused");
        check(!new TrainingEligibility(true, false, false, true, true, false, false).allowed(), "Dead player refused");
        check(!new TrainingEligibility(true, false, true, false, true, false, false).allowed(), "Creative/spectator refused");
        check(!new TrainingEligibility(true, false, true, true, false, false, false).allowed(), "Lost focus stops actions");
        check(!new TrainingEligibility(true, false, true, true, true, true, false).allowed(), "Paused game stops actions");
        check(!new TrainingEligibility(true, false, true, true, true, false, true).allowed(), "Menu stops actions");
    }

    private static void rejects(Runnable operation, String message) {
        boolean rejected = false;
        try { operation.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
    }

    private interface IOOperation { void run() throws Exception; }
    private static void ioRejects(IOOperation operation, String message) throws Exception {
        boolean rejected = false;
        try { operation.run(); } catch (java.io.IOException expected) { rejected = true; }
        check(rejected, message);
    }

    private static void close(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-9, message + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
