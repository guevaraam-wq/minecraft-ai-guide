package minecraft_ai_guide.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;

import java.util.Collections;
import java.util.List;

public class PathGuide {

    private static List<BlockPos> cachedPath =
            Collections.emptyList();

    private static BlockPos lastStart = null;
    private static BlockPos lastTarget = null;

    private static final double RECALCULATE_DISTANCE = 2.0;

    public static void showPath(
            Minecraft minecraft,
            BlockPos target
    ) {

        if (minecraft.player == null ||
                minecraft.level == null ||
                target == null) {
            return;
        }

        BlockPos currentPosition =
                minecraft.player.blockPosition();

        boolean targetChanged =
                lastTarget == null ||
                !lastTarget.equals(target);

        boolean playerMovedEnough =
                lastStart == null ||
                horizontalDistance(
                        currentPosition,
                        lastStart
                ) >= RECALCULATE_DISTANCE;

        if (targetChanged ||
                playerMovedEnough ||
                cachedPath.isEmpty()) {

            cachedPath =
                    Pathfinder.findPath(
                            minecraft,
                            currentPosition,
                            target
                    );

            lastStart = currentPosition.immutable();
            lastTarget = target.immutable();
        }

        drawPath(minecraft);
    }

    private static void drawPath(
            Minecraft minecraft
    ) {

        for (BlockPos pathPosition : cachedPath) {

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

    private static double horizontalDistance(
            BlockPos first,
            BlockPos second
    ) {

        double dx =
                first.getX() - second.getX();

        double dz =
                first.getZ() - second.getZ();

        return Math.sqrt(
                dx * dx +
                dz * dz
        );
    }
}