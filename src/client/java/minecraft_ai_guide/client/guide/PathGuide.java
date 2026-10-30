package minecraft_ai_guide.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import java.util.List;

/** Blue breadcrumbs only. Stable footstep and destination geometry is rendered by TargetHighlighter. */
public final class PathGuide {
    private static final DustParticleOptions BLUE = new DustParticleOptions(0x249CFF, 1.25F);

    public static void showRoute(Minecraft mc, List<BlockPos> path, BlockPos destination, BlockPos next) {
        if (mc.level == null || mc.player == null) return;
        // Only draw the currently traversable prefix, never through unmined terrain.
        for (int i = 1; i < Math.min(path.size(), 18); i++) {
            BlockPos from = path.get(i - 1), to = path.get(i);
            for (int dot = 0; dot < 5; dot++) {
                double t = dot / 5.0;
                mc.level.addParticle(BLUE, from.getX() + .5 + (to.getX() - from.getX()) * t,
                        from.getY() + .18 + (to.getY() - from.getY()) * t,
                        from.getZ() + .5 + (to.getZ() - from.getZ()) * t, 0, 0, 0);
            }
        }
        TargetHighlighter.setNavigation(destination, next);
    }
    public static void showPath(Minecraft mc, BlockPos target) {
        if (mc.player == null || mc.level == null || target == null) return;
        var world = Pathfinder.guideWorld(mc);
        var route = NavigationPlanner.plan(world, Pathfinder.cell(mc.player.blockPosition()), Pathfinder.cell(target),
                NavigationPlanner.Goal.INTERACT, false);
        var instruction = NavigationPlanner.next(world, route, Pathfinder.cell(mc.player.blockPosition()));
        showRoute(mc, instruction.walkPath().stream().map(Pathfinder::block).toList(), target,
                instruction.action() == NavigationPlanner.Action.WALK ? Pathfinder.block(instruction.marker()) : null);
    }
    public static void clearPath() { TargetHighlighter.clearNavigation(); }
    private PathGuide() {}
}
