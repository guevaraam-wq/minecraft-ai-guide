package minecraft_ai_guide.agent;

public enum EpisodeMode {
    TRAINING, EVALUATION;

    public boolean learns() { return this == TRAINING; }

    public void persist(LogQLearningPolicy policy, java.nio.file.Path file) throws java.io.IOException {
        if (learns()) policy.save(file);
    }
}
