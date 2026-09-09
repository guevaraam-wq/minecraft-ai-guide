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

        BlockPos bestLog = null;
        double bestScore = Double.MAX_VALUE;

        double yawRadians = Math.toRadians(minecraft.player.getYRot());

        double lookX = -Math.sin(yawRadians);
        double lookZ = Math.cos(yawRadians);

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {

                    BlockPos currentPos = playerPos.offset(x, y, z);

                    if (!minecraft.level
                            .getBlockState(currentPos)
                            .is(BlockTags.LOGS)) {
                        continue;
                    }

                    double deltaX =
                            currentPos.getX() + 0.5 - minecraft.player.getX();

                    double deltaZ =
                            currentPos.getZ() + 0.5 - minecraft.player.getZ();

                    double horizontalDistance =
                            Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

                    if (horizontalDistance == 0) {
                        continue;
                    }

                    double directionX = deltaX / horizontalDistance;
                    double directionZ = deltaZ / horizontalDistance;

                    double dot =
                            lookX * directionX +
                            lookZ * directionZ;

                    // Ignore trees mostly behind the player.
                    if (dot < 0.25) {
                        continue;
                    }

                    double distance =
                            playerPos.distSqr(currentPos);

                    // Trees directly ahead get a better score.
                    double directionPenalty =
                            (1.0 - dot) * 100.0;

                    double score =
                            distance + directionPenalty;

                    if (score < bestScore) {
                        bestScore = score;
                        bestLog = currentPos.immutable();
                    }
                }
            }
        }

        // If there was no suitable tree in front,
        // fall back to the closest tree anywhere nearby.
        if (bestLog == null) {
            return findClosestLogAnywhere(minecraft);
        }

        return bestLog;
    }

    private static BlockPos findClosestLogAnywhere(
            Minecraft minecraft
    ) {

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