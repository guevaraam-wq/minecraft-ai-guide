package minecraft_ai_guide.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class Pathfinder {

    private static final int MAX_SEARCH_NODES = 2000;

    public static List<BlockPos> findPath(
            Minecraft minecraft,
            BlockPos start,
            BlockPos target
    ) {

        if (minecraft.level == null) {
            return Collections.emptyList();
        }

        PriorityQueue<PathNode> openSet =
                new PriorityQueue<>(
                        Comparator.comparingDouble(PathNode::getTotalCost)
                );

        Map<BlockPos, PathNode> allNodes = new HashMap<>();
        Set<BlockPos> closedSet = new HashSet<>();

        PathNode startNode = new PathNode(start);
        startNode.setDistanceFromStart(0);
        startNode.setEstimatedDistanceToGoal(
                heuristic(start, target)
        );

        openSet.add(startNode);
        allNodes.put(start, startNode);

        int searchedNodes = 0;

        while (!openSet.isEmpty()
                && searchedNodes < MAX_SEARCH_NODES) {

            PathNode current = openSet.poll();

            if (closedSet.contains(current.getPosition())) {
                continue;
            }

            searchedNodes++;

            BlockPos currentPos = current.getPosition();

            // We only need to get close to the target tree.
            if (horizontalDistance(currentPos, target) <= 1.5) {
                return reconstructPath(current);
            }

            closedSet.add(currentPos);

            for (BlockPos neighbor :
                    getNeighbors(minecraft, currentPos)) {

                if (closedSet.contains(neighbor)) {
                    continue;
                }

                double newDistance =
                        current.getDistanceFromStart()
                                + movementCost(currentPos, neighbor);

                PathNode neighborNode =
                        allNodes.get(neighbor);

                if (neighborNode == null) {

                    neighborNode = new PathNode(neighbor);

                    neighborNode.setParent(current);
                    neighborNode.setDistanceFromStart(newDistance);
                    neighborNode.setEstimatedDistanceToGoal(
                            heuristic(neighbor, target)
                    );

                    allNodes.put(neighbor, neighborNode);
                    openSet.add(neighborNode);

                } else if (newDistance
                        < neighborNode.getDistanceFromStart()) {

                    neighborNode.setParent(current);
                    neighborNode.setDistanceFromStart(newDistance);

                    // Re-add so the priority queue uses its new cost.
                    openSet.add(neighborNode);
                }
            }
        }

        // No safe path was found.
        return Collections.emptyList();
    }

    private static List<BlockPos> getNeighbors(
            Minecraft minecraft,
            BlockPos position
    ) {

        List<BlockPos> neighbors = new ArrayList<>();

        int[][] directions = {
                {1, 0},
                {-1, 0},
                {0, 1},
                {0, -1}
        };

        for (int[] direction : directions) {

            int x = position.getX() + direction[0];
            int z = position.getZ() + direction[1];

            // Same level
            BlockPos same =
                    new BlockPos(x, position.getY(), z);

            if (isWalkable(minecraft, same)) {
                neighbors.add(same);
                continue;
            }

            // One-block step upward
            BlockPos up =
                    new BlockPos(x, position.getY() + 1, z);

            if (isWalkable(minecraft, up)) {
                neighbors.add(up);
                continue;
            }

            // One-block step downward
            BlockPos down =
                    new BlockPos(x, position.getY() - 1, z);

            if (isWalkable(minecraft, down)) {
                neighbors.add(down);
            }
        }

        return neighbors;
    }

    private static boolean isWalkable(
            Minecraft minecraft,
            BlockPos feetPosition
    ) {

        if (minecraft.level == null) {
            return false;
        }

        BlockPos groundPosition = feetPosition.below();
        BlockPos headPosition = feetPosition.above();

        BlockState ground =
                minecraft.level.getBlockState(groundPosition);

        BlockState feet =
                minecraft.level.getBlockState(feetPosition);

        BlockState head =
                minecraft.level.getBlockState(headPosition);

        // Player needs something underneath them.
        if (ground.isAir()) {
            return false;
        }

        // Player needs room for both feet and head.
        if (!feet.isAir()) {
            return false;
        }

        if (!head.isAir()) {
            return false;
        }

        return true;
    }

    private static double heuristic(
            BlockPos from,
            BlockPos target
    ) {

        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();

        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double horizontalDistance(
            BlockPos first,
            BlockPos second
    ) {

        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();

        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double movementCost(
            BlockPos from,
            BlockPos to
    ) {

        // Slightly discourage unnecessary elevation changes.
        if (from.getY() != to.getY()) {
            return 1.25;
        }

        return 1.0;
    }

    private static List<BlockPos> reconstructPath(
            PathNode endNode
    ) {

        List<BlockPos> path = new ArrayList<>();

        PathNode current = endNode;

        while (current != null) {
            path.add(current.getPosition());
            current = current.getParent();
        }

        Collections.reverse(path);

        return path;
    }
}