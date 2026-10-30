package minecraft_ai_guide.agent;

import java.util.List;

/** Small, versioned abstraction for an episode-relative gain of one log. */
public record LogLearningState(boolean hasLog, boolean targetAvailable, Distance distance,
        boolean canMine) {
    public enum Distance { NONE, NEAR, MID, FAR }

    public LogLearningState {
        if (distance == null || targetAvailable != (distance != Distance.NONE)
                || (canMine && !targetAvailable)) throw new IllegalArgumentException("Invalid log state");
    }

    public static LogLearningState encode(GameState snapshot, boolean targetAvailable,
            double distance, boolean canMine) {
        return encode(snapshot, targetAvailable, distance, canMine, new EpisodeGoal(0));
    }

    public static LogLearningState encode(GameState snapshot, boolean targetAvailable,
            double distance, boolean canMine, EpisodeGoal goal) {
        if (goal.achieved(snapshot)) return new LogLearningState(true, false, Distance.NONE, false);
        if (!targetAvailable) return new LogLearningState(false, false, Distance.NONE, false);
        if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid distance");
        return new LogLearningState(false, true,
                distance <= 3 ? Distance.NEAR : distance <= 8 ? Distance.MID : Distance.FAR, canMine);
    }

    public List<AgentAction> applicableActions() {
        if (hasLog) return List.of();
        if (canMine) return List.of(AgentAction.EXPLORE, AgentAction.MOVE_TO_RESOURCE,
                AgentAction.COLLECT_RESOURCE);
        if (targetAvailable) return List.of(AgentAction.EXPLORE, AgentAction.MOVE_TO_RESOURCE);
        return List.of(AgentAction.EXPLORE);
    }

    public String key() { return (hasLog ? "1" : "0") + ":" + (targetAvailable ? "1" : "0")
            + ":" + distance + ":" + (canMine ? "1" : "0"); }

    public static LogLearningState parse(String key) {
        String[] parts = key.split(":", -1);
        if (parts.length != 4) throw new IllegalArgumentException("Invalid state key");
        return new LogLearningState(bit(parts[0]), bit(parts[1]), Distance.valueOf(parts[2]), bit(parts[3]));
    }

    private static boolean bit(String value) {
        if (!value.equals("0") && !value.equals("1")) throw new IllegalArgumentException("Invalid bit");
        return value.equals("1");
    }
}
