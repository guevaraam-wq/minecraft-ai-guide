package minecraft_ai_guide.client.guide;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;

/** Destination and next action use both distinct shapes and text, not color alone. */
public final class TargetHighlighter {
    // Waypoint only: render through terrain without changing world visibility or path safety.
    private static final net.minecraft.client.renderer.rendertype.RenderType DESTINATION_TYPE =
            net.minecraft.client.renderer.rendertype.RenderType.create("guide_destination_marker",
                    net.minecraft.client.renderer.rendertype.RenderSetup.builder(
                            net.minecraft.client.renderer.RenderPipelines.register(
                                    com.mojang.blaze3d.pipeline.RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.LINES_SNIPPET)
                                            .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft-ai-guide", "pipeline/destination_marker"))
                                            .withDepthStencilState(java.util.Optional.empty()).build()))
                            .setOutputTarget(net.minecraft.client.renderer.rendertype.OutputTarget.ITEM_ENTITY_TARGET)
                            .createRenderSetup());
    private static BlockPos target;
    private static BlockPos destination;
    private static BlockPos nextFootstep;
    private static boolean excavation;
    private static boolean initialized;

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            var pose = context.poseStack();
            var camera = context.levelState().cameraRenderState.pos;
            BlockPos goal = destination;
            BlockPos foot = nextFootstep;
            if (goal != null) {
                var marker = NavigationMarkers.destination(Pathfinder.cell(goal), mc.player.getY());
                pose.pushPose();
                pose.translate(goal.getX() - camera.x, goal.getY() - camera.y, goal.getZ() - camera.z);
                for (var box : marker.boxes()) context.submitNodeCollector().submitShapeOutline(pose,
                        Shapes.box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()),
                        DESTINATION_TYPE, marker.color(), marker.width(), true);
                pose.popPose();
            }
            if (foot != null) {
                var marker = NavigationMarkers.footstep(Pathfinder.cell(foot));
                pose.pushPose();
                pose.translate(foot.getX() - camera.x, foot.getY() - camera.y, foot.getZ() - camera.z);
                for (var box : marker.boxes()) context.submitNodeCollector().submitShapeOutline(pose,
                        Shapes.box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()),
                        RenderTypes.SECONDARY_BLOCK_OUTLINE, marker.color(), marker.width(), true);
                pose.popPose();
            }
            BlockPos action = target;
            if (action != null && !mc.level.getBlockState(action).isAir()) {
                pose.pushPose();
                pose.translate(action.getX() - camera.x, action.getY() - camera.y, action.getZ() - camera.z);
                context.submitNodeCollector().submitShapeOutline(pose, Shapes.block(), RenderTypes.SECONDARY_BLOCK_OUTLINE,
                        excavation ? 0xFFFFA020 : 0xFFFFFF40, 2.5F, true);
                pose.popPose();
            }
        });
    }
    public static void setTarget(BlockPos position) { target = position == null ? null : position.immutable(); excavation = false; }
    public static void setExcavation(BlockPos position) { setTarget(position); excavation = true; }
    public static void setNavigation(BlockPos goal, BlockPos next) {
        destination = goal == null ? null : goal.immutable();
        nextFootstep = next == null ? null : next.immutable();
    }
    public static void clearNavigation() { destination = null; nextFootstep = null; }
    public static void clearTarget() { target = null; excavation = false; }
    public static BlockPos getTarget() { return target; }
    private TargetHighlighter() {}
}
