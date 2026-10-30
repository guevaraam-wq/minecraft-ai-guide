package minecraft_ai_guide.agent;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Versioned CSV with one upserted row per episode UUID, including interrupted episodes. */
public final class ExperimentStore {
    private static final List<String> HEADER = List.of("schema_version", "episode_id", "mode", "result",
            "success", "started_at_epoch_ms", "elapsed_ms", "decisions", "completed_actions", "failed_actions",
            "cancelled_actions", "total_reward", "exploration_decisions", "explore_actions", "q_updates",
            "baseline_logs", "ending_logs", "new_logs", "epsilon", "learning_rate", "discount_factor",
            "policy_start_sha256", "policy_end_sha256", "reason", "credited_goal_reward");
    private static final List<String> TRANSITIONS = List.of("episode_id", "mode", "decision", "state", "action",
            "epsilon_exploration", "outcome", "next_state", "reward", "q_before", "q_after", "q_updated");
    private final Path directory;

    public ExperimentStore(Path directory) { this.directory = directory; }

    public void validate() throws IOException {
        readEpisodes();
        Path transitions = directory.resolve("transitions.csv");
        if (Files.exists(transitions)) {
            var rows = parse(Files.readString(transitions));
            if (rows.isEmpty() || !rows.getFirst().equals(TRANSITIONS))
                throw new IOException("Unexpected transition CSV schema");
        }
    }

    public List<EpisodeMetrics.Record> readEpisodes() throws IOException {
        Path file = directory.resolve("episodes.csv");
        if (!Files.exists(file)) return new ArrayList<>();
        List<List<String>> rows = parse(Files.readString(file));
        if (rows.isEmpty() || !rows.getFirst().equals(HEADER)) throw new IOException("Unexpected episode CSV schema");
        List<EpisodeMetrics.Record> result = new ArrayList<>();
        var ids = new java.util.HashSet<String>();
        try {
            for (var row : rows.subList(1, rows.size())) {
                if (row.size() != HEADER.size() || !row.get(0).equals("1"))
                    throw new IllegalArgumentException("Bad CSV row");
                java.util.UUID.fromString(row.get(1));
                if (!ids.add(row.get(1))) throw new IllegalArgumentException("Duplicate episode UUID");
                var record = new EpisodeMetrics.Record(row.get(1), EpisodeMode.valueOf(row.get(2)),
                        EpisodeMetrics.Result.valueOf(row.get(3)), Long.parseLong(row.get(5)),
                        Long.parseLong(row.get(6)), Integer.parseInt(row.get(7)), Integer.parseInt(row.get(8)),
                        Integer.parseInt(row.get(9)), Integer.parseInt(row.get(10)), Double.parseDouble(row.get(11)),
                        Integer.parseInt(row.get(12)), Integer.parseInt(row.get(13)), Integer.parseInt(row.get(14)),
                        Integer.parseInt(row.get(15)), Integer.parseInt(row.get(16)), Double.parseDouble(row.get(18)),
                        Double.parseDouble(row.get(19)), Double.parseDouble(row.get(20)), row.get(21), row.get(22), row.get(23), parseBoolean(row.get(24)));
                if (!row.get(4).equals(Boolean.toString(record.success())) || !Double.isFinite(record.totalReward())
                        || record.decisions() < 0 || record.elapsedMillis() < 0
                        || Integer.parseInt(row.get(17)) != Math.max(0, record.endingLogs() - record.baselineLogs())
                        || (record.mode() == EpisodeMode.EVALUATION && (record.qUpdates() != 0 || record.epsilon() != 0)))
                    throw new IllegalArgumentException("Inconsistent CSV counters");
                result.add(record);
            }
        } catch (RuntimeException e) { throw new IOException("Invalid episode CSV; preserved", e); }
        return result;
    }

    public void upsert(EpisodeMetrics.Record episode) throws IOException {
        List<EpisodeMetrics.Record> records = readEpisodes();
        records.removeIf(record -> record.id().equals(episode.id()));
        records.add(episode);
        StringBuilder csv = new StringBuilder(row(HEADER));
        for (var r : records) csv.append(row(List.of("1", r.id(), r.mode(), r.result(), r.success(), r.startedAt(),
                r.elapsedMillis(), r.decisions(), r.completedActions(), r.failedActions(), r.cancelledActions(),
                r.totalReward(), r.explorationDecisions(), r.exploreActions(), r.qUpdates(), r.baselineLogs(),
                r.endingLogs(), Math.max(0, r.endingLogs() - r.baselineLogs()), r.epsilon(), r.alpha(), r.gamma(),
                r.policyStart(), r.policyEnd(), r.reason(), r.creditedGoalReward())));
        atomicWrite(directory.resolve("episodes.csv"), csv.toString());
        atomicWrite(directory.resolve("experiment-summary.md"), summary(records));
    }

    public void transition(String episode, EpisodeMode mode, int decision, LogLearningState before,
            LogQLearningPolicy.Decision choice, AgentExperience attempt, LogLearningState after,
            LogQLearningPolicy.Update update) throws IOException {
        Path file = directory.resolve("transitions.csv");
        if (!Files.exists(file)) atomicWrite(file, row(TRANSITIONS));
        Files.writeString(file, row(List.of(episode, mode, decision, before.key(), choice.action(),
                choice.exploratory(), attempt.getOutcome(), after.key(), update == null ? 0 : update.reward(),
                update == null ? "" : update.previousQ(), update == null ? "" : update.newQ(),
                update != null && update.qUpdated())), StandardOpenOption.APPEND);
    }

    public static String summary(List<EpisodeMetrics.Record> records) {
        StringBuilder text = new StringBuilder("# One-log experiment summary\n\n"
                + "Descriptive measurements only. Training versus evaluation alone does not establish improvement: "
                + "exploration, policy checkpoints, and world conditions differ. No improvement conclusion is inferred.\n\n"
                + "| Mode/checkpoint | Finished | Success | Failure | Aborted | Success rate | Mean decisions | Mean failed actions | Mean reward | Mean elapsed seconds | Epsilon exploration | Q updates | Credited goal rewards |\n"
                + "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (var mode : EpisodeMode.values()) {
            aggregate(text, mode.toString(), records.stream().filter(r -> r.mode() == mode).toList());
        }
        records.stream().filter(r -> r.mode() == EpisodeMode.EVALUATION).map(EpisodeMetrics.Record::policyStart)
                .distinct().sorted().forEach(hash -> aggregate(text, "EVALUATION " + hash,
                        records.stream().filter(r -> r.mode() == EpisodeMode.EVALUATION && r.policyStart().equals(hash)).toList()));
        long running = records.stream().filter(r -> r.result() == EpisodeMetrics.Result.RUNNING).count();
        text.append("\nAborted episodes count as unsuccessful in rates and remain separately visible. RUNNING rows (currently ")
                .append(running).append(") are excluded; after a crash they remain incomplete, not invented failures. "
                        + "Elapsed time and averages include all finished episodes. Check episode CSV reasons and "
                        + "match starting conditions before comparing evaluation checkpoints. Legacy TSV records are not inferred or migrated.\n");
        return text.toString();
    }

    private static void aggregate(StringBuilder text, String label, List<EpisodeMetrics.Record> input) {
        var rows = input.stream().filter(r -> r.result() != EpisodeMetrics.Result.RUNNING).toList();
        long success = rows.stream().filter(EpisodeMetrics.Record::success).count();
        long failures = rows.stream().filter(r -> r.result() == EpisodeMetrics.Result.FAILURE).count();
        long aborted = rows.stream().filter(r -> r.result() == EpisodeMetrics.Result.ABORTED).count();
        text.append("| ").append(label).append(" | ").append(rows.size()).append(" | ").append(success)
                .append(" | ").append(failures).append(" | ").append(aborted).append(" | ");
        if (rows.isEmpty()) { text.append("n/a | n/a | n/a | n/a | n/a | 0 | 0 | 0 |\n"); return; }
        text.append(String.format(Locale.ROOT, "%.1f%% | %.2f | %.2f | %.3f | %.3f | %d | %d | %d |%n",
                100.0 * success / rows.size(), rows.stream().mapToInt(EpisodeMetrics.Record::decisions).average().orElse(0),
                rows.stream().mapToInt(EpisodeMetrics.Record::failedActions).average().orElse(0),
                rows.stream().mapToDouble(EpisodeMetrics.Record::totalReward).average().orElse(0),
                rows.stream().mapToLong(EpisodeMetrics.Record::elapsedMillis).average().orElse(0) / 1000,
                rows.stream().mapToInt(EpisodeMetrics.Record::explorationDecisions).sum(),
                rows.stream().mapToInt(EpisodeMetrics.Record::qUpdates).sum(),
                rows.stream().filter(EpisodeMetrics.Record::creditedGoalReward).count()));
    }

    private static boolean parseBoolean(String value) {
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Bad CSV boolean");
        return Boolean.parseBoolean(value);
    }

    private static String row(List<?> values) {
        return values.stream().map(v -> "\"" + v.toString().replace("\"", "\"\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + "\r\n";
    }

    static List<List<String>> parse(String csv) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        boolean startedCell = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else if (c == '"') { quoted = false; closedQuote = true; }
                else cell.append(c);
            } else if (c == '"') {
                if (!cell.isEmpty() || closedQuote) throw new IOException("Invalid CSV quote");
                quoted = true;
                startedCell = true;
            } else if (c == ',') {
                cells.add(cell.toString()); cell.setLength(0); closedQuote = false; startedCell = false;
            }
            else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') i++;
                cells.add(cell.toString()); cell.setLength(0);
                rows.add(List.copyOf(cells)); cells.clear(); closedQuote = false; startedCell = false;
            } else {
                if (closedQuote) throw new IOException("Text after closing CSV quote");
                cell.append(c); startedCell = true;
            }
        }
        if (quoted) throw new IOException("Truncated CSV quoted field");
        if (!cells.isEmpty() || startedCell) { cells.add(cell.toString()); rows.add(List.copyOf(cells)); }
        return rows;
    }

    private static void atomicWrite(Path file, String content) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "experiment-", ".tmp");
        try {
            Files.writeString(temporary, content);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
