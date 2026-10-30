package minecraft_ai_guide.agent;

/** Net tagged-log gain relative to this episode, never the entire session. */
public record EpisodeGoal(int baselineLogs) {
    public EpisodeGoal {
        if (baselineLogs < 0) throw new IllegalArgumentException("Negative inventory baseline");
    }

    public int acquired(GameState snapshot) { return Math.max(0, snapshot.getLogCount() - baselineLogs); }
    public boolean achieved(GameState snapshot) { return acquired(snapshot) > 0; }
}
