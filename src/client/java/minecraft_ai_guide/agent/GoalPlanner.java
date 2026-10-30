package minecraft_ai_guide.agent;

import net.minecraft.client.Minecraft;

public class GoalPlanner {

    /*
     * High-level goal selected for the current guide session.
     *
     * For now we use stone_axe because it gives us a clean,
     * testable progression chain. The important difference
     * from the old StoneAxeTaskGraph is that the dependencies
     * come from MinecraftRecipePlanner / Minecraft recipe data.
     */
    private static String currentGoal =
            "minecraft:stone_axe";

    private static MinecraftRecipePlanner.PlanNode
            currentPlan = null;

    private static String plannedGoal = null;


    public static void setGoal(
            Minecraft minecraft,
            String itemId
    ) {

        currentGoal = itemId;

        rebuild(
                minecraft
        );
    }


    public static String getGoal() {
        return currentGoal;
    }


    public static void rebuild(
            Minecraft minecraft
    ) {

        if (minecraft == null
                || minecraft.player == null) {

            return;
        }

        currentPlan =
                MinecraftRecipePlanner.buildPlan(
                        minecraft,
                        currentGoal
                );

        plannedGoal =
                currentGoal;

        System.out.println(
                "[Goal Planner] Goal: "
                        + currentGoal
        );

        System.out.println(
                "[Goal Planner] Next objective: "
                        + getNextInstruction(
                                minecraft
                        )
        );
    }


    public static void ensurePlan(
            Minecraft minecraft
    ) {

        if (currentPlan == null
                || plannedGoal == null
                || !plannedGoal.equals(
                        currentGoal
                )) {

            rebuild(
                    minecraft
            );
        }
    }


    public static String getNextInstruction(
            Minecraft minecraft
    ) {

        ensurePlan(
                minecraft
        );

        if (currentPlan == null) {

            return "Waiting for Minecraft data...";
        }

        String instruction =
                MinecraftRecipePlanner
                        .getNextInstruction(
                                minecraft,
                                currentPlan
                        );

        if (instruction == null
                || instruction.isBlank()) {

            return "Goal complete";
        }

        return instruction;
    }


    public static boolean isGoalComplete(
            Minecraft minecraft
    ) {

        String instruction =
                getNextInstruction(
                        minecraft
                );

        return instruction != null
                && instruction.startsWith(
                        "Goal complete:"
                );
    }


    public static void clearPlan() {

        currentPlan = null;
        plannedGoal = null;
    }


    private GoalPlanner() {
    }
}
