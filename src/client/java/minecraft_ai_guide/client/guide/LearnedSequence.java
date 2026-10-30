package minecraft_ai_guide.client.guide;

import minecraft_ai_guide.agent.AgentAction;
import minecraft_ai_guide.agent.GameState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LearnedSequence {

    private static final List<LearnedStep> steps =
            new ArrayList<>();

    public static void learnFrom(
            List<GameplayAction> actions
    ) {

        steps.clear();

        /*
         * Merge repeated gains while preserving the
         * order in which item types first appeared.
         */
        Map<String, Integer> gained =
                new LinkedHashMap<>();

        Map<String, Integer> lost =
                new LinkedHashMap<>();

        for (GameplayAction action : actions) {

            if ("GAINED".equals(action.getType())) {

                gained.merge(
                        action.getItemId(),
                        action.getAmount(),
                        Integer::sum
                );

            } else if ("LOST".equals(action.getType())) {

                lost.merge(
                        action.getItemId(),
                        action.getAmount(),
                        Integer::sum
                );
            }
        }

        for (Map.Entry<String, Integer> entry
                : gained.entrySet()) {

            String itemId =
                    entry.getKey();

            int amount =
                    entry.getValue();

            AgentAction learnedAction =
                    inferAction(
                            itemId,
                            lost
                    );

            steps.add(
                    new LearnedStep(
                            learnedAction,
                            itemId,
                            amount
                    )
            );
        }

        System.out.println(
                "[AI Sequence] Learned "
                        + steps.size()
                        + " meaningful steps:"
        );

        for (LearnedStep step : steps) {
            System.out.println(
                    "[AI Sequence] "
                            + step
            );
        }
    }

    private static AgentAction inferAction(
            String gainedItem,
            Map<String, Integer> lost
    ) {

        /*
         * If something was consumed while another
         * useful item appeared, treat the output as
         * a crafting result.
         *
         * Raw logs are treated as collected resources.
         */
        if (gainedItem.endsWith("_log")
                || gainedItem.endsWith("_stem")
                || gainedItem.contains("raw_")) {

            return AgentAction.COLLECT_RESOURCE;
        }

        if (!lost.isEmpty()) {
            return AgentAction.CRAFT;
        }

        return AgentAction.COLLECT_RESOURCE;
    }

    public static LearnedStep getNextStep(
            GameState state
    ) {

        if (state == null) {
            return null;
        }

        for (LearnedStep step : steps) {

            int owned =
                    state.getInventory()
                            .getOrDefault(
                                    step.getItemId(),
                                    0
                            );

            if (owned < step.getAmount()) {
                return step;
            }
        }

        return null;
    }

    public static List<LearnedStep> getSteps() {
        return new ArrayList<>(steps);
    }

    public static int size() {
        return steps.size();
    }

    private LearnedSequence() {
    }
}
