package minecraft_ai_guide.client;
import minecraft_ai_guide.agent.ActionExecutor;
import minecraft_ai_guide.agent.MinecraftAgent;
import minecraft_ai_guide.client.guide.CraftingGuide;
import minecraft_ai_guide.client.guide.CraftingRequirement;
import minecraft_ai_guide.client.guide.GameplayRecorder;
import minecraft_ai_guide.client.guide.GuideStep;
import minecraft_ai_guide.client.guide.PathGuide;
import minecraft_ai_guide.client.guide.StoneAxeTaskGraph;
import minecraft_ai_guide.client.guide.TargetFinder;
import minecraft_ai_guide.client.guide.TaskNode;
import minecraft_ai_guide.client.guide.TaskPlanner;
import minecraft_ai_guide.agent.ActionExecutor;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public class MinecraftAIGuideClient implements ClientModInitializer {

    private static final Identifier GUIDE_HUD =
            Identifier.fromNamespaceAndPath(
                    "minecraft-ai-guide",
                    "accessibility_guide"
            );

    private static final KeyMapping.Category GUIDE_CATEGORY =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath(
                            "minecraft-ai-guide",
                            "guide"
                    )
            );

    private static final TaskNode STONE_AXE_GOAL =
            StoneAxeTaskGraph.createGraph();

    private static KeyMapping startRecordingKey;
    private static KeyMapping stopRecordingKey;
    private static final MinecraftAgent AGENT =
        new MinecraftAgent();

    /*
     * TRAINING MODE:
     *     The autonomous agent controls the player
     *     and collects experiences.
     *
     * GUIDE MODE:
     *     The player controls Minecraft while the
     *     learned agent provides recommendations.
     */
    private static boolean guideMode = false;

    private static minecraft_ai_guide.agent.AgentAction cachedRecommendation =
            null;

    private static long lastRecommendationUpdate = 0;

    private static final long RECOMMENDATION_UPDATE_INTERVAL =
            1000;

    private static long lastPathUpdate = 0;
    private static final long PATH_UPDATE_INTERVAL = 500;

    private static BlockPos cachedTreeTarget = null;
    private static long lastTargetSearch = 0;
    private static final long TARGET_SEARCH_INTERVAL = 1000;

    @Override
    public void onInitializeClient() {

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                GUIDE_HUD,
                MinecraftAIGuideClient::renderGuide
        );

        startRecordingKey =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping(
                                "key.minecraft-ai-guide.start_recording",
                                InputConstants.Type.KEYSYM,
                                InputConstants.KEY_R,
                                GUIDE_CATEGORY
                        )
                );

        stopRecordingKey =
                KeyMappingHelper.registerKeyMapping(
                        new KeyMapping(
                                "key.minecraft-ai-guide.stop_recording",
                                InputConstants.Type.KEYSYM,
                                InputConstants.KEY_J,
                                GUIDE_CATEGORY
                        )
                );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

    while (startRecordingKey.consumeClick()) {

        System.out.println(
                "[Minecraft AI Guide] TRAINING MODE"
        );

        /*
         * Enter autonomous training mode.
         */
        guideMode = false;

        cachedRecommendation = null;

        AGENT.start(client);
    }

    while (stopRecordingKey.consumeClick()) {

        System.out.println(
                "[Minecraft AI Guide] GUIDE MODE"
        );

        /*
         * Stop autonomous control immediately.
         */
        AGENT.stop();

        ActionExecutor.stopMovement(client);

        /*
         * Preserve ExperienceMemory and switch to
         * player-controlled guidance mode.
         */
        guideMode = true;

        cachedRecommendation =
                AGENT.getLearnedRecommendation(
                        client
                );

        lastRecommendationUpdate =
                System.currentTimeMillis();

        System.out.println(
                "[Minecraft AI Guide] Player control restored."
        );

        System.out.println(
                "[Minecraft AI Guide] Learned recommendation: "
                        + cachedRecommendation
        );
    }

    AGENT.update(client);
});
    }

    private static TaskNode findTaskById(
            TaskNode task,
            String id
    ) {

        if (task.getId().equals(id)) {
            return task;
        }

        for (TaskNode dependency : task.getDependencies()) {

            TaskNode result =
                    findTaskById(
                            dependency,
                            id
                    );

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private static void renderGuide(
            GuiGraphicsExtractor graphics,
            net.minecraft.client.DeltaTracker deltaTracker
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        GuideStep currentStep =
                TaskPlanner.getNextStep(
                        minecraft,
                        STONE_AXE_GOAL
                );

        String craftingInstruction = "";

        if (currentStep
                .getStepId()
                .equals("craft_wooden_pickaxe")) {

            if (CraftingGuide
                    .isCraftingTableOpen(minecraft)) {

                craftingInstruction =
                        "Crafting table open - ready for the next step";

            } else {

                craftingInstruction =
                        "Next action: Open your crafting table";
            }
        }

        TaskNode currentTask =
                findTaskById(
                        STONE_AXE_GOAL,
                        currentStep.getStepId()
                );

        String targetText = "";
        String directionText = "";

        if (currentStep
                .getStepId()
                .equals("collect_log")) {

            long currentTime =
                    System.currentTimeMillis();

            if (currentTime - lastTargetSearch
                    >= TARGET_SEARCH_INTERVAL
                    || cachedTreeTarget == null) {

                cachedTreeTarget =
                        TargetFinder
                                .findNearestLog(
                                        minecraft
                                );

                lastTargetSearch =
                        currentTime;
            }

            BlockPos treePos =
                    cachedTreeTarget;

            if (treePos != null) {

                if (currentTime - lastPathUpdate
                        >= PATH_UPDATE_INTERVAL) {

                    PathGuide.showPath(
                            minecraft,
                            treePos
                    );

                    lastPathUpdate =
                            currentTime;
                }

                double distance =
                        minecraft.player
                                .blockPosition()
                                .distSqr(treePos);

                int blocksAway =
                        (int) Math.sqrt(distance);

                targetText =
                        "Tree found: "
                                + blocksAway
                                + " blocks away";

                double dx =
                        treePos.getX()
                                - minecraft.player.getX();

                double dz =
                        treePos.getZ()
                                - minecraft.player.getZ();

                double targetAngle =
                        Math.toDegrees(
                                Math.atan2(
                                        -dx,
                                        dz
                                )
                        );

                double playerYaw =
                        minecraft.player.getYRot();

                double difference =
                        targetAngle
                                - playerYaw;

                while (difference > 180) {
                    difference -= 360;
                }

                while (difference < -180) {
                    difference += 360;
                }

                if (difference >= -25
                        && difference <= 25) {

                    directionText =
                            "Direction: Straight ahead";

                } else if (difference > 25
                        && difference < 155) {

                    directionText =
                            "Direction: Turn left";

                } else if (difference < -25
                        && difference > -155) {

                    directionText =
                            "Direction: Turn right";

                } else {

                    directionText =
                            "Direction: Behind you";
                }

            } else {

                targetText =
                        "No tree found nearby";
            }
        }

        graphics.text(
                minecraft.font,
                "Goal: Craft a Stone Axe",
                10,
                10,
                0xFFFFFFFF,
                true
        );

        graphics.text(
                minecraft.font,
                currentStep.getInstruction(),
                10,
                22,
                0xFFFFFFFF,
                true
        );

        if (!targetText.isEmpty()) {

            graphics.text(
                    minecraft.font,
                    targetText,
                    10,
                    34,
                    0xFFFFFFFF,
                    true
            );
        }

        if (!directionText.isEmpty()) {

            graphics.text(
                    minecraft.font,
                    directionText,
                    10,
                    46,
                    0xFFFFFFFF,
                    true
            );
        }

        int requirementY = 58;

        if (currentTask != null
                && !currentTask
                        .getRequirements()
                        .isEmpty()) {

            graphics.text(
                    minecraft.font,
                    "Materials:",
                    10,
                    requirementY,
                    0xFFFFFFFF,
                    true
            );

            requirementY += 12;

            for (CraftingRequirement requirement
                    : currentTask.getRequirements()) {

                int amountOwned = 0;

                for (int i = 0;
                     i < minecraft.player
                             .getInventory()
                             .getContainerSize();
                     i++) {

                    var stack =
                            minecraft.player
                                    .getInventory()
                                    .getItem(i);

                    if (requirement.matches(stack)) {
                        amountOwned +=
                                stack.getCount();
                    }
                }

                boolean hasEnough =
                        amountOwned
                                >= requirement
                                .getAmount();

                String symbol =
                        hasEnough
                                ? "[X] "
                                : "[ ] ";

                String requirementText =
                        symbol
                                + requirement
                                .getDisplayName()
                                + ": "
                                + amountOwned
                                + "/"
                                + requirement
                                .getAmount();

                graphics.text(
                        minecraft.font,
                        requirementText,
                        10,
                        requirementY,
                        0xFFFFFFFF,
                        true
                );

                requirementY += 12;
            }
        }

        if (!craftingInstruction.isEmpty()) {

            graphics.text(
                    minecraft.font,
                    craftingInstruction,
                    10,
                    requirementY,
                    0xFFFFFFFF,
                    true
            );
        }
        // =========================================================
// AI LEARNING STATUS
// =========================================================

int aiStatusY =
        requirementY + 24;


graphics.text(
        minecraft.font,
        "AI Training:",
        10,
        aiStatusY,
        0xFFFFFFFF,
        true
);


String trainingStatus;

if (AGENT.isRunning()) {

    trainingStatus =
            "TRAINING - AI controlling player";

} else if (guideMode) {

    trainingStatus =
            "GUIDE - You control player";

} else {

    trainingStatus =
            "STOPPED";
}


graphics.text(
        minecraft.font,
        "Status: " + trainingStatus,
        10,
        aiStatusY + 12,
        0xFFFFFFFF,
        true
);


graphics.text(
        minecraft.font,
        "Experiences learned: "
                + AGENT.getExperienceCount(),
        10,
        aiStatusY + 24,
        0xFFFFFFFF,
        true
);


/*
 * Recommendation evaluation is deliberately
 * throttled.
 *
 * renderGuide() executes every rendered frame,
 * so calling the learning system directly here
 * would evaluate and print the policy dozens of
 * times per second.
 *
 * In GUIDE MODE we refresh only once per second.
 */
if (guideMode) {

    long now =
            System.currentTimeMillis();

    if (now - lastRecommendationUpdate
            >= RECOMMENDATION_UPDATE_INTERVAL) {

        cachedRecommendation =
                AGENT.getLearnedRecommendation(
                        minecraft
                );

        lastRecommendationUpdate =
                now;
    }
}

var learnedRecommendation =
        cachedRecommendation;


String recommendationText;

if (learnedRecommendation != null) {

    recommendationText =
            "AI recommends: "
                    + learnedRecommendation;

} else {

    recommendationText =
            "AI recommends: Learning...";
}


graphics.text(
        minecraft.font,
        recommendationText,
        10,
        aiStatusY + 36,
        0xFFFFFFFF,
        true
);


graphics.text(
        minecraft.font,
        "R: Train AI | J: Guide Mode",
        10,
        aiStatusY + 52,
        0xFFFFFFFF,
        true
);
    }
}
