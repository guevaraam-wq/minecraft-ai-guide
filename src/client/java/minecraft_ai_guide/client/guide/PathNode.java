package minecraft_ai_guide.client.guide;

import net.minecraft.core.BlockPos;

public class PathNode {

    private final BlockPos position;
    private PathNode parent;

    private double distanceFromStart;
    private double estimatedDistanceToGoal;

    public PathNode(BlockPos position) {
        this.position = position;
    }

    public BlockPos getPosition() {
        return position;
    }

    public PathNode getParent() {
        return parent;
    }

    public void setParent(PathNode parent) {
        this.parent = parent;
    }

    public double getDistanceFromStart() {
        return distanceFromStart;
    }

    public void setDistanceFromStart(double distanceFromStart) {
        this.distanceFromStart = distanceFromStart;
    }

    public double getEstimatedDistanceToGoal() {
        return estimatedDistanceToGoal;
    }

    public void setEstimatedDistanceToGoal(
            double estimatedDistanceToGoal
    ) {
        this.estimatedDistanceToGoal = estimatedDistanceToGoal;
    }

    public double getTotalCost() {
        return distanceFromStart + estimatedDistanceToGoal;
    }
}
