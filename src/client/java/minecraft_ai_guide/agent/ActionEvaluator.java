package minecraft_ai_guide.agent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ActionEvaluator {

    /*
     * Estimate how useful each action has been from
     * the agent's previous experiences.
     *
     * This is intentionally simple for now.
     * We are building the learning system in layers.
     */
    public static AgentAction chooseBestAction(
            AgentGoal goal,
            GameState currentState,
            ExperienceMemory memory
    ) {

        List<AgentExperience> experiences =
                memory.getExperiences();

        if (experiences.isEmpty()) {
            return null;
        }

        Map<AgentAction, Double> rewardTotals =
                new EnumMap<>(AgentAction.class);

        Map<AgentAction, Integer> actionCounts =
                new EnumMap<>(AgentAction.class);


        for (AgentExperience experience : experiences) {

            if (experience.getOutcome() == ActionOutcome.CANCELLED) continue;
            AgentAction action = experience.getAction();

            /*
             * Ignore WAIT because it does not
             * actively advance the agent.
             */
            if (action == AgentAction.WAIT) {
                continue;
            }

            rewardTotals.merge(
                    action,
                    experience.getReward(),
                    Double::sum
            );

            actionCounts.merge(
                    action,
                    1,
                    Integer::sum
            );
        }


        AgentAction bestAction = null;
        double bestScore =
                Double.NEGATIVE_INFINITY;


        for (AgentAction action
                : rewardTotals.keySet()) {

            double totalReward =
                    rewardTotals.get(action);

            int count =
                    actionCounts.get(action);

            double averageReward =
                    totalReward / count;


            /*
             * Eventually this score will also
             * consider:
             *
             * - current subgoal
             * - inventory
             * - nearby resources
             * - danger
             * - progress toward game completion
             *
             * For now we begin with observed
             * average reward.
             */
            double score =
                    averageReward;


            System.out.println(
                    "[AI Evaluation] "
                            + action
                            + " score="
                            + score
                            + " observations="
                            + count
            );


            if (score > bestScore) {

                bestScore =
                        score;

                bestAction =
                        action;
            }
        }


        if (bestAction != null) {

            System.out.println(
                    "[AI Evaluation] Best learned action for "
                            + goal
                            + ": "
                            + bestAction
                            + " score="
                            + bestScore
            );
        }


        return bestAction;
    }


    private ActionEvaluator() {

        // Utility class.
    }
}
