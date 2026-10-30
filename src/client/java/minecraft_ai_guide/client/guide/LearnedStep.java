package minecraft_ai_guide.client.guide;

import minecraft_ai_guide.agent.AgentAction;

public class LearnedStep {

    private final AgentAction action;
    private final String itemId;
    private final int amount;

    public LearnedStep(
            AgentAction action,
            String itemId,
            int amount
    ) {
        this.action = action;
        this.itemId = itemId;
        this.amount = amount;
    }

    public AgentAction getAction() {
        return action;
    }

    public String getItemId() {
        return itemId;
    }

    public int getAmount() {
        return amount;
    }

    public String getInstruction() {

        String name =
                itemId
                        .replace("minecraft:", "")
                        .replace("_", " ");

        if (action == AgentAction.CRAFT) {
            return "Craft " + amount + " " + name;
        }

        if (action == AgentAction.PLACE_BLOCK) {
            return "Place " + name;
        }

        if (action == AgentAction.COLLECT_RESOURCE) {
            return "Collect " + amount + " " + name;
        }

        return action + " " + name;
    }

    @Override
    public String toString() {
        return action
                + " "
                + amount
                + "x "
                + itemId;
    }
}
