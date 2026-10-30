package minecraft_ai_guide.agent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Runs without a live world; a failed assertion exits the Gradle task nonzero. */
public final class FoundationTests {
    private static int checks;

    public static void main(String[] args) throws Exception {
        prerequisiteOrdering();
        attribution();
        terminalOutcomes();
        snapshotIsolation();
        System.out.println("Foundation regression checks passed: " + checks);
        QLearningTests.run();
        ExperimentTests.run();
        minecraft_ai_guide.client.guide.StoneAxeGuidanceTests.run();
        minecraft_ai_guide.client.guide.NavigationTests.run();
        minecraft_ai_guide.client.goals.TaskProgressionTests.run();
        minecraft_ai_guide.client.goals.SurvivalMonitorTests.run();
        minecraft_ai_guide.client.goals.EnvironmentalSurvivalTests.run();
        minecraft_ai_guide.client.goals.EmergencyShelterTests.run();
    }

    private static void prerequisiteOrdering() {
        var axe = node("stone_axe", false);
        var stone = node("cobblestone", true);
        var pickaxe = node("wooden_pickaxe", false);
        var planks = node("oak_planks", false);
        var log = node("oak_log", true);
        axe.addDependency(stone);
        stone.addDependency(pickaxe);
        pickaxe.addDependency(planks);
        planks.addDependency(log);

        check(next(axe, Set.of()) == log, "Resource prerequisites must descend to raw materials");
        check(next(axe, Set.of(log)) == planks, "Craft prerequisites before the tool");
        check(next(axe, Set.of(planks)) == pickaxe, "Craft the tool before collecting stone");
        check(next(axe, Set.of(pickaxe)) == stone, "Collect stone after the tool is owned");
        check(next(axe, Set.of(stone)) == axe, "Owned resources skip consumed prerequisites");
        check(next(axe, Set.of(axe)) == null, "Completed goal skips all prerequisites");
        check(next(log, Set.of()) == log, "Resources without prerequisites remain collectible");

        pickaxe.addAlternative("minecraft:iron_pickaxe");
        Map<String, Integer> inventory = Map.of("minecraft:iron_pickaxe", 1);
        check(MinecraftRecipePlanner.findNextAction(axe,
                n -> MinecraftRecipePlanner.hasEnough(n, id -> inventory.getOrDefault(id, 0))) == stone,
                "An appropriate alternative tool acquired after planning satisfies the prerequisite");
        var threeStone = new MinecraftRecipePlanner.PlanNode("minecraft:cobblestone", 3, true);
        check(!MinecraftRecipePlanner.hasEnough(threeStone, id -> 2), "Insufficient resource quantities remain incomplete");
        check(MinecraftRecipePlanner.hasEnough(threeStone, id -> 3), "Exact resource quantities satisfy the node");
    }

    private static MinecraftRecipePlanner.PlanNode node(String item, boolean resource) {
        return new MinecraftRecipePlanner.PlanNode("minecraft:" + item, 1, resource);
    }

    private static MinecraftRecipePlanner.PlanNode next(MinecraftRecipePlanner.PlanNode root,
            Set<MinecraftRecipePlanner.PlanNode> owned) {
        return MinecraftRecipePlanner.findNextAction(root, owned::contains);
    }

    private static GameState state(Map<String, Integer> inventory) {
        return new GameState(inventory, 0, 64, 0, false);
    }

    private static void attribution() {
        var tracker = new ActionAttemptTracker();
        var empty = state(Map.of());
        var log = state(Map.of("minecraft:oak_log", 1));
        var planks = state(Map.of("minecraft:oak_planks", 4));
        tracker.begin(empty, AgentAction.COLLECT_RESOURCE, "minecraft:oak_log", 100);
        tracker.finish(log, ActionOutcome.SUCCESS, 200);
        tracker.begin(log, AgentAction.CRAFT, null, 300);
        tracker.setTarget("minecraft:oak_planks");
        tracker.finish(planks, ActionOutcome.SUCCESS, 400);
        var experiences = tracker.drainCompleted();
        check(experiences.size() == 2, "Exactly one record per actual action");
        var first = experiences.get(0);
        var second = experiences.get(1);
        check(first.getBeforeState() == empty && first.getAfterState() == log,
                "Collection has its own state boundaries");
        check(second.getBeforeState() == log && second.getAfterState() == planks,
                "Crafting starts after collection, not at demonstration start");
        check(first.getAction() == AgentAction.COLLECT_RESOURCE && second.getAction() == AgentAction.CRAFT,
                "Movement/collection/crafting labels cannot share another action's experience");
        check(first.targetWasAcquired() && second.targetWasAcquired(), "Each action verifies its target");
        check(second.getStartedAt() == 300 && second.getEndedAt() == 400, "Bounded timestamps");
        double expected = RewardSystem.calculate(log, planks) + 0.50;
        check(Math.abs(second.getReward() - expected) < 1e-9, "Reward uses only this attempt's states");
        check(tracker.drainCompleted().isEmpty(), "Draining never duplicates experiences");
    }

    private static void terminalOutcomes() {
        var tracker = new ActionAttemptTracker();
        var empty = state(Map.of());
        var unrelatedGain = state(Map.of("minecraft:diamond", 1));
        tracker.begin(empty, AgentAction.CRAFT, "minecraft:stick", 100);
        check(!tracker.targetWasAcquired(unrelatedGain), "Unrelated pickup is not craft success");
        check(!tracker.hasTimedOut(109, 10) && tracker.hasTimedOut(110, 10), "Timeout boundary");
        tracker.finish(unrelatedGain, ActionOutcome.FAILURE, 110);
        tracker.finish(unrelatedGain, ActionOutcome.SUCCESS, 111);
        var failed = tracker.drainCompleted();
        check(failed.size() == 1 && !failed.get(0).succeeded(), "Failure is terminal and recorded once");
        check(failed.get(0).getReward() < 0, "Unrelated gains cannot reward failed crafting");

        tracker.begin(empty, AgentAction.COLLECT_RESOURCE, "minecraft:oak_log", 200);
        tracker.finish(empty, ActionOutcome.TIMEOUT, 230);
        check(tracker.drainCompleted().get(0).getOutcome() == ActionOutcome.TIMEOUT, "Timeout recorded");
        tracker.begin(empty, AgentAction.MOVE_TO_RESOURCE, null, 300);
        boolean rejected = false;
        try { tracker.begin(empty, AgentAction.CRAFT, null, 301); }
        catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "An unfinished action cannot be overwritten");
        tracker.finish(empty, ActionOutcome.CANCELLED, 302);
        var cancelled = tracker.drainCompleted().get(0);
        check(cancelled.getOutcome() == ActionOutcome.CANCELLED && cancelled.getReward() == 0,
                "Cancellation is explicit and does not claim failure or success");
        check(!tracker.isPending(), "Terminal attempts do not leak into the next action");

        tracker.begin(empty, AgentAction.CRAFT, "minecraft:stick", 400);
        tracker.finish(unrelatedGain, ActionOutcome.SUCCESS, 401);
        check(tracker.drainCompleted().get(0).getOutcome() == ActionOutcome.FAILURE,
                "Accepted input without targeted acquisition cannot claim success");

        var memory = new ExperienceMemory();
        tracker.begin(empty, AgentAction.MOVE_TO_RESOURCE, null, 500);
        var atTree = new GameState(Map.of(), 3, 64, 0, false);
        tracker.finish(atTree, ActionOutcome.SUCCESS, 510);
        tracker.begin(atTree, AgentAction.COLLECT_RESOURCE, "minecraft:oak_log", 510);
        var withLog = new GameState(Map.of("minecraft:oak_log", 1), 3, 64, 0, false);
        tracker.finish(withLog, ActionOutcome.SUCCESS, 520);
        tracker.drainCompleted().forEach(memory::add);
        check(memory.size() == 2, "Navigation and collection produce separate experiences");
        check(memory.getExperiences().get(0).getAfterState() == atTree
                && memory.getExperiences().get(1).getBeforeState() == atTree,
                "Action transition closes the previous snapshot before the next attempt starts");

        var cancelledMemory = new ExperienceMemory();
        tracker.begin(empty, AgentAction.EXPLORE, null, 600);
        tracker.finish(empty, ActionOutcome.CANCELLED, 601);
        tracker.drainCompleted().forEach(cancelledMemory::add);
        check(ActionEvaluator.chooseBestAction(AgentGoal.ACQUIRE_WOOD, empty, cancelledMemory) == null,
                "Cancellation does not affect recommendations");
    }

    private static void snapshotIsolation() {
        var inventory = new HashMap<String, Integer>();
        inventory.put("minecraft:oak_log", 1);
        var snapshot = state(inventory);
        inventory.clear();
        snapshot.getInventory().clear();
        check(snapshot.hasItem("minecraft:oak_log"), "Historical snapshots cannot mutate with inventory");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
