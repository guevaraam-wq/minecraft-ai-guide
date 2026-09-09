package minecraft_ai_guide.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;

public class PathGuide {

    public static void showPath(Minecraft minecraft, BlockPos target) {

        if (minecraft.player == null ||
                minecraft.level == null ||
                target == null) {
            return;
        }

        double startX = minecraft.player.getX();
        double startZ = minecraft.player.getZ();

        double endX = target.getX() + 0.5;
        double endZ = target.getZ() + 0.5;

        double deltaX = endX - startX;
        double deltaZ = endZ - startZ;

        double horizontalDistance = Math.sqrt(
                deltaX * deltaX +
                deltaZ * deltaZ
        );

        int points = Math.max(1, (int) horizontalDistance);

        for (int i = 1; i <= points; i++) {

            double progress = (double) i / points;

            double x = startX + deltaX * progress;
            double z = startZ + deltaZ * progress;

            BlockPos groundPos = findGround(
                    minecraft,
                    (int) Math.floor(x),
                    minecraft.player.blockPosition().getY() + 6,
                    (int) Math.floor(z)
            );

            if (groundPos != null) {

                double particleY = groundPos.getY() + 1.15;

                minecraft.level.addParticle(
                        ParticleTypes.HAPPY_VILLAGER,
                        x,
                        particleY,
                        z,
                        0,
                        0,
                        0
                );
            }
        }
    }

    private static BlockPos findGround(
            Minecraft minecraft,
            int x,
            int startY,
            int z
    ) {

        for (int y = startY; y >= startY - 20; y--) {

            BlockPos currentPos = new BlockPos(x, y, z);
            BlockState blockState =
                    minecraft.level.getBlockState(currentPos);

            BlockPos abovePos = currentPos.above();
            BlockState aboveState =
                    minecraft.level.getBlockState(abovePos);

            if (!blockState.isAir() && aboveState.isAir()) {
                return currentPos;
            }
        }

        return null;
    }
}