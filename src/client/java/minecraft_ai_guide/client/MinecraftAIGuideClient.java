package minecraft_ai_guide.client;

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

        boolean hasLog = minecraft.player.getInventory()
                .contains(stack -> stack.is(ItemTags.LOGS));

        String nextStep;

        if (hasLog) {
            nextStep = "Log collected! Next step: Make wooden planks";
        } else {
            nextStep = "Next step: Collect a log";
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
                nextStep,
                10,
                22,
                0xFFFFFFFF,
                true
        );
    }
}