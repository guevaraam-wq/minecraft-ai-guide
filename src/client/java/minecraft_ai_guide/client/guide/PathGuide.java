package minecraft_ai_guide.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;

import java.util.List;

public class PathGuide {

    public static void showPath(
            Minecraft minecraft,
            BlockPos target
    ) {

        if (minecraft.player == null ||
                minecraft.level == null ||
                target == null) {
            return;
        }

        BlockPos start =
                minecraft.player.blockPosition();

        List<BlockPos> path =
                Pathfinder.findPath(
                        minecraft,
                        start,
                        target
                );

        if (path.isEmpty()) {
            return;
        }

        for (BlockPos pathPosition : path) {

            double x = pathPosition.getX() + 0.5;
            double y = pathPosition.getY() + 0.15;
            double z = pathPosition.getZ() + 0.5;

            minecraft.level.addParticle(
                    ParticleTypes.HAPPY_VILLAGER,
                    x,
                    y,
                    z,
                    0,
                    0,
                    0
            );
        }
    }
}