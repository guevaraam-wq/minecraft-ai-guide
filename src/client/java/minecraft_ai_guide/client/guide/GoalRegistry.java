package minecraft_ai_guide.client.guide;

import java.util.ArrayList;
import java.util.List;

public class GoalRegistry {

    private static final List<GuideGoal> GOALS =
            new ArrayList<>();

    static {
        GOALS.add(
                new GuideGoal(
                        "stone_axe",
                        "Craft a Stone Axe",
                        StoneAxeTaskGraph.createGraph()
                )
        );
    }

    public static List<GuideGoal> getGoals() {
        return GOALS;
    }

    public static GuideGoal getGoalById(String id) {

        for (GuideGoal goal : GOALS) {
            if (goal.getId().equals(id)) {
                return goal;
            }
        }

        return null;
    }
}