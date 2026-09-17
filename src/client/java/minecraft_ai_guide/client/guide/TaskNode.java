package minecraft_ai_guide.client.guide;

import java.util.ArrayList;
import java.util.List;

public class TaskNode {

    private final String id;
    private final String instruction;

    private final List<TaskNode> dependencies =
            new ArrayList<>();

    private final List<CraftingRequirement> requirements =
            new ArrayList<>();

    public TaskNode(
            String id,
            String instruction
    ) {
        this.id = id;
        this.instruction = instruction;
    }

    public void addDependency(TaskNode dependency) {
        dependencies.add(dependency);
    }

    public void addRequirement(
            CraftingRequirement requirement
    ) {
        requirements.add(requirement);
    }

    public String getId() {
        return id;
    }

    public String getInstruction() {
        return instruction;
    }

    public List<TaskNode> getDependencies() {
        return dependencies;
    }

    public List<CraftingRequirement> getRequirements() {
        return requirements;
    }
}