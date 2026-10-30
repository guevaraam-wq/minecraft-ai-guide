package minecraft_ai_guide.agent;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.random.RandomGenerator;

/** Tabular Q-learning over completed executor attempts, not demonstration steps. */
public final class LogQLearningPolicy {
    private static final String FORMAT = "one-log-q-v1";
    private record Entry(LogLearningState state, AgentAction action) {}
    public record Update(double reward, double previousQ, double newQ, boolean terminal, boolean qUpdated) {}
    public record Decision(AgentAction action, boolean exploratory) {}
    private final Map<Entry, Double> table = new HashMap<>();
    private final QLearningConfig config;
    private final RandomGenerator random;

    public LogQLearningPolicy(QLearningConfig config, RandomGenerator random) {
        this.config = config;
        this.random = random;
    }

    public double value(LogLearningState state, AgentAction action) {
        return table.getOrDefault(new Entry(state, action), 0.0);
    }

    public AgentAction choose(LogLearningState state) { return decide(state, EpisodeMode.TRAINING).action(); }

    public AgentAction greedy(LogLearningState state) { return decide(state, EpisodeMode.EVALUATION).action(); }

    public Decision decide(LogLearningState state, EpisodeMode mode) {
        List<AgentAction> actions = state.applicableActions();
        if (actions.isEmpty()) return new Decision(null, false);
        boolean explore = mode.learns() && random.nextDouble() < config.epsilon();
        if (explore) return new Decision(actions.get(random.nextInt(actions.size())), true);
        double best = Double.NEGATIVE_INFINITY;
        List<AgentAction> tied = new ArrayList<>();
        for (AgentAction action : actions) {
            double q = value(state, action);
            if (q > best) { best = q; tied.clear(); }
            if (q == best) tied.add(action);
        }
        // Random greedy ties are not epsilon exploration, including during evaluation.
        return new Decision(tied.get(random.nextInt(tied.size())), false);
    }

    public Update learn(LogLearningState before, AgentExperience attempt, LogLearningState after) {
        return process(EpisodeMode.TRAINING, before, attempt, after, new EpisodeGoal(0));
    }

    /** Evaluation computes identical outcome rewards without inserting/updating any Q entries. */
    public Update process(EpisodeMode mode, LogLearningState before, AgentExperience attempt,
            LogLearningState after, EpisodeGoal goal) {
        if (attempt.getOutcome() == ActionOutcome.CANCELLED) return null;
        if (!before.applicableActions().contains(attempt.getAction()))
            throw new IllegalArgumentException("Action was not applicable to the decision state");
        if (before.hasLog() != goal.achieved(attempt.getBeforeState())
                || after.hasLog() != goal.achieved(attempt.getAfterState()))
            throw new IllegalArgumentException("Experience does not match encoded inventory baseline");

        boolean acquired = attempt.succeeded() && attempt.getAction() == AgentAction.COLLECT_RESOURCE
                && attempt.targetWasAcquired()
                && attempt.getAfterState().getLogCount() > attempt.getBeforeState().getLogCount();
        double reward = acquired && after.hasLog() ? 10.0
                : !attempt.succeeded() || (attempt.getAction() == AgentAction.COLLECT_RESOURCE && !acquired)
                    ? -1.0 : -0.05;
        double old = value(before, attempt.getAction());
        if (!mode.learns()) return new Update(reward, old, old, after.hasLog(), false);
        double bootstrap = after.applicableActions().stream()
                .mapToDouble(action -> value(after, action)).max().orElse(0);
        double updated = old + config.learningRate()
                * (reward + (after.hasLog() ? 0 : config.discountFactor() * bootstrap) - old);
        if (!Double.isFinite(updated)) throw new IllegalArgumentException("Nonfinite Q update");
        table.put(new Entry(before, attempt.getAction()), updated);
        return new Update(reward, old, updated, after.hasLog(), true);
    }

    /** Stable content identity, independent of Properties timestamps and insertion order. */
    public String fingerprint() {
        String content = FORMAT + "\n" + table.entrySet().stream()
                .map(e -> e.getKey().state().key() + "." + e.getKey().action() + "=" + e.getValue())
                .sorted().collect(java.util.stream.Collectors.joining("\n"));
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    public int size() { return table.size(); }
    public QLearningConfig config() { return config; }

    public void load(Path file) throws IOException {
        if (!Files.exists(file)) { table.clear(); return; }
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) { p.load(reader); }
        if (!FORMAT.equals(p.getProperty("format"))) throw new IOException("Unsupported Q-table format");
        Map<Entry, Double> loaded = new HashMap<>();
        try {
            for (String key : p.stringPropertyNames()) {
                if (key.equals("format")) continue;
                if (!key.startsWith("q.")) throw new IllegalArgumentException("Unknown Q-table field");
                int separator = key.lastIndexOf('.');
                LogLearningState state = LogLearningState.parse(key.substring(2, separator));
                AgentAction action = AgentAction.valueOf(key.substring(separator + 1));
                double value = Double.parseDouble(p.getProperty(key));
                if (!Double.isFinite(value) || !state.applicableActions().contains(action))
                    throw new IllegalArgumentException("Invalid Q-table entry");
                loaded.put(new Entry(state, action), value);
            }
        } catch (RuntimeException e) { throw new IOException("Invalid Q-table; original file preserved", e); }
        table.clear();
        table.putAll(loaded);
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Properties p = new Properties();
        p.setProperty("format", FORMAT);
        table.forEach((entry, q) -> p.setProperty("q." + entry.state().key() + "." + entry.action(), q.toString()));
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "one-log-q-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) { p.store(writer, FORMAT); }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
