package minecraft_ai_guide.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;

public class TargetFinder {

    private static final int SEARCH_RADIUS = 20;

    public static BlockPos findNearestLog(Minecraft minecraft) {

        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }

        BlockPos playerPos = minecraft.player.blockPosition();

        BlockPos nearestLog = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {

                    BlockPos currentPos = playerPos.offset(x, y, z);

                    if (minecraft.level
                            .getBlockState(currentPos)
                            .is(BlockTags.LOGS)) {

                        double distance =
                                playerPos.distSqr(currentPos);

                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearestLog = currentPos.immutable();
                        }
                    }
                }
            }
        }

        return nearestLog;
    }
}