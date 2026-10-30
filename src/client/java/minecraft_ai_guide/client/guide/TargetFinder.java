package minecraft_ai_guide.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;

public class TargetFinder {

    public record GuideTarget(BlockPos position, NavigationPlanner.Route route) {}

    /** Prefer a currently walkable approach over any route requiring excavation. */
    public static GuideTarget findGuideTarget(Minecraft mc, java.util.Set<String> sources, int radius, int height,
            boolean excavation, NavigationPlanner.World world) {
        BlockPos start = mc.player.blockPosition();
        java.util.List<BlockPos> all = new java.util.ArrayList<>();
        for (int x = -radius; x <= radius; x++) for (int y = -height; y <= height; y++) for (int z = -radius; z <= radius; z++) {
            BlockPos pos = start.offset(x, y, z);
            if (mc.level.isOutsideBuildHeight(pos) || !mc.level.hasChunkAt(pos)) continue;
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
            if (sources.contains(id)) all.add(pos.immutable());
        }
        all.sort(java.util.Comparator.comparingDouble(start::distSqr));
        if (all.isEmpty()) return null;
        // Include candidates below/above the player instead of testing only the nearest surface layer.
        java.util.LinkedHashSet<BlockPos> candidates = new java.util.LinkedHashSet<>(all.stream().limit(4).toList());
        java.util.Set<Integer> levels = new java.util.HashSet<>();
        for (BlockPos pos : all) if (levels.add(pos.getY())) { candidates.add(pos); if (candidates.size() >= 12) break; }
        for (BlockPos pos : all) { if (candidates.size() >= 12) break; candidates.add(pos); }
        NavigationPlanner.Goal goal = excavation ? NavigationPlanner.Goal.HARVEST : NavigationPlanner.Goal.INTERACT;
        GuideTarget inaccessible = null;
        for (BlockPos pos : candidates) {
            var route = NavigationPlanner.plan(world, Pathfinder.cell(start), Pathfinder.cell(pos), goal, false);
            if (route.status() == NavigationPlanner.Status.REACHABLE_NOW) return new GuideTarget(pos, route);
            if (inaccessible == null) inaccessible = new GuideTarget(pos, route);
        }
        if (excavation) for (BlockPos pos : candidates) {
            var route = NavigationPlanner.plan(world, Pathfinder.cell(start), Pathfinder.cell(pos), goal, true);
            if (route.status() != NavigationPlanner.Status.INACCESSIBLE) return new GuideTarget(pos, route);
        }
        return inaccessible;
    }

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

    public static BlockPos findNearestStone(
            Minecraft minecraft
    ) {

        if (minecraft.player == null
                || minecraft.level == null) {
            return null;
        }

        BlockPos playerPos =
                minecraft.player.blockPosition();

        BlockPos bestStone = null;
        double bestScore = Double.MAX_VALUE;

        double yawRadians =
                Math.toRadians(
                        minecraft.player.getYRot()
                );

        double lookX =
                -Math.sin(yawRadians);

        double lookZ =
                Math.cos(yawRadians);

        for (int x = -SEARCH_RADIUS;
             x <= SEARCH_RADIUS;
             x++) {

            for (int y = -10; y <= 10; y++) {

                for (int z = -SEARCH_RADIUS;
                     z <= SEARCH_RADIUS;
                     z++) {

                    BlockPos currentPos =
                            playerPos.offset(
                                    x,
                                    y,
                                    z
                            );

                    var state =
                            minecraft.level
                                    .getBlockState(
                                            currentPos
                                    );

                    /*
                     * Stone drops cobblestone when mined
                     * normally with the appropriate tool.
                     *
                     * Existing cobblestone can also be
                     * collected directly.
                     */
                    if (!state.is(Blocks.STONE)
                            && !state.is(Blocks.COBBLESTONE)) {
                        continue;
                    }

                    double deltaX =
                            currentPos.getX()
                                    + 0.5
                                    - minecraft.player.getX();

                    double deltaZ =
                            currentPos.getZ()
                                    + 0.5
                                    - minecraft.player.getZ();

                    double horizontalDistance =
                            Math.sqrt(
                                    deltaX * deltaX
                                            + deltaZ * deltaZ
                            );

                    if (horizontalDistance == 0) {
                        continue;
                    }

                    double directionX =
                            deltaX / horizontalDistance;

                    double directionZ =
                            deltaZ / horizontalDistance;

                    double dot =
                            lookX * directionX
                                    + lookZ * directionZ;

                    /*
                     * Prefer visible-direction targets,
                     * but don't require them to be
                     * perfectly centered.
                     */
                    if (dot < 0.10) {
                        continue;
                    }

                    double distance =
                            playerPos.distSqr(
                                    currentPos
                            );

                    double directionPenalty =
                            (1.0 - dot) * 50.0;

                    /*
                     * Slightly favor stone near the
                     * player's current Y level.
                     */
                    double verticalPenalty =
                            Math.abs(y) * 4.0;

                    double score =
                            distance
                                    + directionPenalty
                                    + verticalPenalty;

                    if (score < bestScore) {

                        bestScore = score;

                        bestStone =
                                currentPos.immutable();
                    }
                }
            }
        }

        /*
         * If nothing useful is in front of the player,
         * fall back to nearby stone in any direction.
         */
        if (bestStone == null) {

            double nearestDistance =
                    Double.MAX_VALUE;

            for (int x = -SEARCH_RADIUS;
                 x <= SEARCH_RADIUS;
                 x++) {

                for (int y = -10; y <= 10; y++) {

                    for (int z = -SEARCH_RADIUS;
                         z <= SEARCH_RADIUS;
                         z++) {

                        BlockPos currentPos =
                                playerPos.offset(
                                        x,
                                        y,
                                        z
                                );

                        var state =
                                minecraft.level
                                        .getBlockState(
                                                currentPos
                                        );

                        if (!state.is(Blocks.STONE)
                                && !state.is(
                                        Blocks.COBBLESTONE
                                )) {
                            continue;
                        }

                        double distance =
                                playerPos.distSqr(
                                        currentPos
                                );

                        if (distance
                                < nearestDistance) {

                            nearestDistance =
                                    distance;

                            bestStone =
                                    currentPos
                                            .immutable();
                        }
                    }
                }
            }
        }

        return bestStone;
    }

}