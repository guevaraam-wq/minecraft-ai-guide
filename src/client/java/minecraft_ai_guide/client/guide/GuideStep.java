package minecraft_ai_guide.client.guide;

public class GuideStep {

    private final String instruction;
    private final String stepId;

    public GuideStep(String stepId, String instruction) {
        this.stepId = stepId;
        this.instruction = instruction;
    }

    public String getStepId() {
        return stepId;
    }

    public String getInstruction() {
        return instruction;
    }
}