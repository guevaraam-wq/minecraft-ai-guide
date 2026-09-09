package minecraft_ai_guide.client;
import minecraft_ai_guide.client.guide.PathGuide;
import net.minecraft.core.BlockPos;
import minecraft_ai_guide.client.guide.TargetFinder;
import net.minecraft.core.BlockPos;
import minecraft_ai_guide.client.guide.GuideStep;
import minecraft_ai_guide.client.guide.StoneAxeTaskGraph;
import minecraft_ai_guide.client.guide.TaskNode;
import minecraft_ai_guide.client.guide.TaskPlanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;

public class MinecraftAIGuideClient implements ClientModInitializer {

    private static final Identifier GUIDE_HUD =
            Identifier.fromNamespaceAndPath(
                    "minecraft-ai-guide",
                    "accessibility_guide"
            );
	private static final TaskNode STONE_AXE_GOAL =
        StoneAxeTaskGraph.createGraph();
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
    }

    private static void renderGuide(
            GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }
		GuideStep currentStep =
        TaskPlanner.getNextStep(
                minecraft,
                STONE_AXE_GOAL
        );
		String targetText = "";
		String directionText = "";

if (currentStep.getStepId().equals("collect_log")) {

    long currentTime = System.currentTimeMillis();

if (currentTime - lastTargetSearch >= TARGET_SEARCH_INTERVAL
        || cachedTreeTarget == null) {

    cachedTreeTarget =
            TargetFinder.findNearestLog(minecraft);

    lastTargetSearch = currentTime;
}

BlockPos treePos = cachedTreeTarget;

    if (treePos != null) {

		if (currentTime - lastPathUpdate >= PATH_UPDATE_INTERVAL) {
    		PathGuide.showPath(minecraft, treePos);
    		lastPathUpdate = currentTime;
		}

        double distance = minecraft.player.blockPosition()
                .distSqr(treePos);

        int blocksAway = (int) Math.sqrt(distance);

        targetText = "Tree found: " + blocksAway + " blocks away";

        double deltaX =
                treePos.getX() - minecraft.player.getX();

        double deltaZ =
                treePos.getZ() - minecraft.player.getZ();

        double targetAngle =
                Math.toDegrees(Math.atan2(-deltaX, deltaZ));

        double playerAngle =
                minecraft.player.getYRot();

        double angleDifference =
                targetAngle - playerAngle;

        while (angleDifference > 180) {
            angleDifference -= 360;
        }

        while (angleDifference < -180) {
            angleDifference += 360;
        }

        if (angleDifference > -25 && angleDifference < 25) {
            directionText = "Direction: Straight ahead";
        } else if (angleDifference >= 25 && angleDifference < 155) {
            directionText = "Direction: Left";
        } else if (angleDifference <= -25 && angleDifference > -155) {
            directionText = "Direction: Right";
        } else {
            directionText = "Direction: Behind you";
        }

    } else {
        targetText = "No tree found nearby";
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
    }
}