package minecraft_ai_guide.client.guide;

public class GuideGoal {

    private final String id;
    private final String displayName;
    private final TaskNode rootTask;

    public GuideGoal(
            String id,
            String displayName,
            TaskNode rootTask
    ) {
        this.id = id;
        this.displayName = displayName;
        this.rootTask = rootTask;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TaskNode getRootTask() {
        return rootTask;
    }
}