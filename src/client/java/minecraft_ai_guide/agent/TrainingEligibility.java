package minecraft_ai_guide.agent;

/** Conditions checked both at activation and before every training action/tick. */
public record TrainingEligibility(boolean localWorld, boolean sharedWorld, boolean alive,
        boolean survival, boolean focused, boolean paused, boolean screenOpen) {
    public boolean allowed() {
        return localWorld && !sharedWorld && alive && survival && focused && !paused && !screenOpen;
    }
}
