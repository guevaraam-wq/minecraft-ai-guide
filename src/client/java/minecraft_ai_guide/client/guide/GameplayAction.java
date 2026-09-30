package minecraft_ai_guide.client.guide;

public class GameplayAction {

    private final String type;
    private final String itemId;
    private final int amount;
    private final long timestamp;

    public GameplayAction(
            String type,
            String itemId,
            int amount
    ) {
        this.type = type;
        this.itemId = itemId;
        this.amount = amount;
        this.timestamp = System.currentTimeMillis();
    }

    public String getType() {
        return type;
    }

    public String getItemId() {
        return itemId;
    }

    public int getAmount() {
        return amount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return type
                + " "
                + amount
                + "x "
                + itemId;
    }
}
