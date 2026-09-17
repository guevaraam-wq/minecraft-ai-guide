package minecraft_ai_guide.client.guide;

import net.minecraft.world.item.Item;

public class CraftingRequirement {

    private final Item item;
    private final int amount;
    private final String displayName;

    public CraftingRequirement(
            Item item,
            int amount,
            String displayName
    ) {
        this.item = item;
        this.amount = amount;
        this.displayName = displayName;
    }

    public Item getItem() {
        return item;
    }

    public int getAmount() {
        return amount;
    }

    public String getDisplayName() {
        return displayName;
    }
}