package minecraft_ai_guide.client;
import minecraft_ai_guide.client.guide.GuideStep;
import minecraft_ai_guide.client.guide.StoneAxeGoal;
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
		GuideStep currentStep = StoneAxeGoal.getNextStep(minecraft);

        

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
    }
}