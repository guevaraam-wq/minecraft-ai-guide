package minecraft_ai_guide.client.guide;

public class GoalManager {

    private static GuideGoal activeGoal;

    public static void setActiveGoal(GuideGoal goal) {
        activeGoal = goal;
    }

    public static GuideGoal getActiveGoal() {
        return activeGoal;
    }

    public static boolean hasActiveGoal() {
        return activeGoal != null;
    }

    public static void clearGoal() {
        activeGoal = null;
    }
}