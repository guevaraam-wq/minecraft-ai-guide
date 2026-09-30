package minecraft_ai_guide.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;

public class CraftingGuide {

    public static boolean isCraftingTableOpen(
            Minecraft minecraft
    ) {
        return minecraft.gui.screen() instanceof CraftingScreen;
    }
}