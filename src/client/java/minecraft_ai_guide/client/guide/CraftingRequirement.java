package minecraft_ai_guide.client.guide;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CraftingRequirement {

    private final Item item;
    private final TagKey<Item> itemTag;
    private final int amount;
    private final String displayName;

    // Requirement for one specific item
    public CraftingRequirement(
            Item item,
            int amount,
            String displayName
    ) {
        this.item = item;
        this.itemTag = null;
        this.amount = amount;
        this.displayName = displayName;
    }

    // Requirement for an item category/tag
    public CraftingRequirement(
            TagKey<Item> itemTag,
            int amount,
            String displayName
    ) {
        this.item = null;
        this.itemTag = itemTag;
        this.amount = amount;
        this.displayName = displayName;
    }

    public boolean matches(ItemStack stack) {

        if (item != null) {
            return stack.is(item);
        }

        if (itemTag != null) {
            return stack.is(itemTag);
        }

        return false;
    }

    public int getAmount() {
        return amount;
    }

    public String getDisplayName() {
        return displayName;
    }
}