package minecraft_ai_guide.client;
import minecraft_ai_guide.agent.ActionExecutor;
import minecraft_ai_guide.agent.MinecraftAgent;
import minecraft_ai_guide.agent.OneLogTraining;
import minecraft_ai_guide.client.guide.GameplayRecorder;
import com.mojang.blaze3d.platform.InputConstants;
import minecraft_ai_guide.client.guide.LearnedSequence;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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


    private static final minecraft_ai_guide.client.guide.StoneAxeGuide ACCESS_GUIDE =
            new minecraft_ai_guide.client.guide.StoneAxeGuide();

    private static final minecraft_ai_guide.client.goals.TaskGoalController TASKS =
            new minecraft_ai_guide.client.goals.TaskGoalController(ACCESS_GUIDE);
    private static KeyMapping taskMenuKey;
    private static final OneLogTraining TRAINING = new OneLogTraining();
    private static boolean trainingStopRequested;
    private static KeyMapping startTrainingKey;
    private static KeyMapping startEvaluationKey;
    private static KeyMapping stopTrainingKey;
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
     *     recipe-based accessibility guide provides one instruction.
     */
    private static boolean guideMode = true;

    @Override
    public void onInitializeClient() {

        minecraft_ai_guide.client.guide.TargetHighlighter.initialize();
        minecraft_ai_guide.client.goals.ShelterBuildMarker.initialize();
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

        taskMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.minecraft-ai-guide.task_menu", InputConstants.Type.KEYSYM, InputConstants.KEY_G, GUIDE_CATEGORY));
        startTrainingKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.minecraft-ai-guide.start_experimental_training", InputConstants.Type.KEYSYM,
                InputConstants.KEY_F7, GUIDE_CATEGORY));
        startEvaluationKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.minecraft-ai-guide.start_experimental_evaluation", InputConstants.Type.KEYSYM,
                InputConstants.KEY_F9, GUIDE_CATEGORY));
        stopTrainingKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.minecraft-ai-guide.stop_experimental_training", InputConstants.Type.KEYSYM,
                InputConstants.KEY_F8, GUIDE_CATEGORY));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING
                .register(client -> TRAINING.stop(client, "Stopped: client closing"));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
                .register((handler, client) -> TRAINING.stop(client, "Stopped: disconnected"));

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            while (stopTrainingKey.consumeClick()) {
                TRAINING.stop(client, "Stopped by player");
                trainingStopRequested = true;
            }
            TRAINING.checkEnvironment(client);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
    // Stop wins over start if both controls arrive in the same tick.
    boolean stopped = trainingStopRequested;
    trainingStopRequested = false;
    while (stopTrainingKey.consumeClick()) {
        TRAINING.stop(client, "Stopped by player");
        stopped = true;
    }


    while (startRecordingKey.consumeClick()) {
        TRAINING.stop(client, "Stopped: observation selected");
        stopped = true;


        System.out.println(
                "[Minecraft AI Guide] OBSERVATION MODE (player controlled)"
        );

        /*
         * PLAYER-CONTROLLED OBSERVATION
         *
         * The player remains in control.
         * We observe gameplay instead of allowing
         * the autonomous ActionExecutor to move,
         * jump, mine, or place blocks.
         */
        AGENT.stop(client);
        ActionExecutor.stopMovement(client);

        guideMode = false;
        ACCESS_GUIDE.reset();
        GameplayRecorder.startRecording(client);

        System.out.println(
                "[Minecraft AI Guide] Safe observation started. "
                        + "You still control the player."
        );
    }

    while (stopRecordingKey.consumeClick()) {
        TRAINING.stop(client, "Stopped: guide mode selected");
        stopped = true;

        System.out.println(
                "[Minecraft AI Guide] GUIDE MODE"
        );

        /*
         * Stop autonomous control immediately.
         */
        AGENT.stop(client);

        ActionExecutor.stopMovement(client);

        if (GameplayRecorder.isRecording()) {

    GameplayRecorder.stopRecording();

    // Inventory deltas remain useful for demonstration guidance, but they
    // cannot establish which action caused a change. Only executor attempts
    // are admitted to ExperienceMemory.
    LearnedSequence.learnFrom(GameplayRecorder.getActions());

}

        /*
         * Preserve ExperienceMemory and switch to
         * player-controlled guidance mode.
         */
        guideMode = true;

        ACCESS_GUIDE.reset();
        System.out.println("[Minecraft AI Guide] Adaptive stone-axe guidance; you control the player.");
    }

    while (taskMenuKey.consumeClick()) {
        TRAINING.stop(client, "Stopped: task selection opened");
        stopped = true;
        boolean conflict=java.util.Arrays.stream(client.options.keyMappings).anyMatch(key->key!=taskMenuKey && key.same(taskMenuKey));
        if(conflict) TASKS.menuNotice("Task key conflicts with another binding. Remap it in Controls.");
        else if(client.player!=null && client.level!=null && client.gui.screen()==null){
            AGENT.stop(client);
            if(GameplayRecorder.isRecording())GameplayRecorder.stopRecording();
            guideMode=true;
            TASKS.tick(client,true);
            client.gui.setScreen(new minecraft_ai_guide.client.goals.TaskMenuScreen(TASKS));
        }
    }
    while (startTrainingKey.consumeClick()) {
        if (!stopped && !TRAINING.isRunning()) {
            if (!GameplayRecorder.isRecording()) AGENT.stop(client);
            TRAINING.start(client);
        }
    }
    while (startEvaluationKey.consumeClick()) {
        if (!stopped && !TRAINING.isRunning()) {
            boolean conflict = java.util.Arrays.stream(client.options.keyMappings)
                    .anyMatch(key -> key != startEvaluationKey && key.same(startEvaluationKey));
            if (conflict) {
                TRAINING.refuseActivation("evaluation key conflicts; remap it in Controls > Minecraft AI Guide");
            } else {
                if (!GameplayRecorder.isRecording()) AGENT.stop(client);
                TRAINING.start(client, minecraft_ai_guide.agent.EpisodeMode.EVALUATION);
            }
        }
    }
    TRAINING.tick(client);
    GameplayRecorder.update(client);
    TASKS.tick(client, guideMode && !TRAINING.isRunning() && !GameplayRecorder.isRecording());

    /*
     * Autonomous agent updates are intentionally disabled
     * in safe observation mode.
     */
    if (!TRAINING.isRunning() && AGENT.isRunning()) {
        AGENT.update(client);
    }
});
    }

    private static void renderGuide(
            GuiGraphicsExtractor graphics,
            net.minecraft.client.DeltaTracker deltaTracker
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() instanceof minecraft_ai_guide.client.goals.TaskMenuScreen) return;
        String title = TASKS.title();
        String instruction;
        String detail = "";
        String controls = taskMenuKey.getTranslatedKeyMessage().getString()+": Tasks | "
                +stopRecordingKey.getTranslatedKeyMessage().getString()+": Return to guide";
        if (TRAINING.isRunning()) {
            title = "EXPERIMENTAL " + TRAINING.mode();
            instruction = TRAINING.status();
            controls = "F8: STOP autonomous experiment";
        } else if (GameplayRecorder.isRecording() || !guideMode) {
            instruction = "Press J to return to your selected goal.";
        } else {
            instruction = TASKS.instruction();
            detail = TASKS.detail();
            if(TASKS.selection().active() && !TASKS.selection().paused())detail = (detail.isEmpty()?"":detail+"\n")+"Why: "+TASKS.why();
        }
        graphics.text(minecraft.font, title, 10, 10, 0xFFFFFFFF, true);
        int y = 26;
        if(guideMode && !TRAINING.isRunning() && !GameplayRecorder.isRecording() && TASKS.temporary()){
            for(var line:minecraft.font.split(net.minecraft.network.chat.Component.literal("Temporary task: "+TASKS.nextStep().title()),
                    Math.max(100,Math.min(360,minecraft.getWindow().getGuiScaledWidth()-24)))){
                graphics.text(minecraft.font,line,10,y,0xFFFFFFFF,true);y+=12;
            }
        }
        int width = Math.max(100, Math.min(360, minecraft.getWindow().getGuiScaledWidth() - 24));
        for (var line : minecraft.font.split(net.minecraft.network.chat.Component.literal(instruction), width)) {
            graphics.text(minecraft.font, line, 10, y, 0xFFFFFFFF, true);
            y += 12;
        }
        if (!detail.isEmpty()) {
            for(var line:minecraft.font.split(net.minecraft.network.chat.Component.literal(detail),width)){
                graphics.text(minecraft.font,line,10,y+4,0xFFFFFFFF,true);y+=12;
            }
            y+=4;
        }
        if(guideMode && !TRAINING.isRunning() && !GameplayRecorder.isRecording() && !TASKS.reminder().isEmpty()){
            for(var line:minecraft.font.split(net.minecraft.network.chat.Component.literal(TASKS.reminder()),width)){
                graphics.text(minecraft.font,line,10,y+8,0xFFCCCCCC,true);y+=12;
            }
        }
        graphics.text(minecraft.font, controls, 10, y + 12, 0xFFCCCCCC, true);
        if(!TASKS.notice().isEmpty())graphics.text(minecraft.font, TASKS.notice(),10,y+26,0xFFFFFF80,true);
    }
}
