package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;
import net.minecraft.world.phys.Vec3;

public class WaterBoundPathNavigation extends PathNavigation {
    private boolean allowBreaching;

    public WaterBoundPathNavigation(final Mob mob, final Level level) {
        super(mob, level);
    }

    // Kaiiju start - petal - async path processing
    private static final net.feathermc.feather.async.path.NodeEvaluatorGenerator NODE_EVALUATOR_GENERATOR = (net.feathermc.feather.async.path.NodeEvaluatorFeatures nodeEvaluatorFeatures) -> {
        SwimNodeEvaluator nodeEvaluator = new SwimNodeEvaluator(nodeEvaluatorFeatures.allowBreaching());
        nodeEvaluator.setCanPassDoors(nodeEvaluatorFeatures.canPassDoors());
        nodeEvaluator.setCanFloat(nodeEvaluatorFeatures.canFloat());
        nodeEvaluator.setCanWalkOverFences(nodeEvaluatorFeatures.canWalkOverFences());
        nodeEvaluator.setCanOpenDoors(nodeEvaluatorFeatures.canOpenDoors());
        return nodeEvaluator;
    };
    // Kaiiju end - petal - async path processing

    @Override
    protected PathFinder createPathFinder(final int maxVisitedNodes) {
        this.allowBreaching = this.mob.is(EntityTypes.DOLPHIN);
        this.nodeEvaluator = new SwimNodeEvaluator(this.allowBreaching);
        this.nodeEvaluator.setCanPassDoors(false);
        // Kaiiju start - petal - async path processing
        if (net.feathermc.feather.config.modules.async.AsyncPathfinding.enabled) {
            return new PathFinder(this.nodeEvaluator, maxVisitedNodes, NODE_EVALUATOR_GENERATOR);
        }
        // Kaiiju end - petal - async path processing
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canUpdatePath() {
        return this.allowBreaching || this.mob.isInLiquid();
    }

    @Override
    protected Vec3 getTempMobPos() {
        return new Vec3(this.mob.getX(), this.mob.getY(0.5), this.mob.getZ());
    }

    @Override
    protected double getGroundY(final Vec3 target) {
        return target.y;
    }

    @Override
    protected boolean canMoveDirectly(final Vec3 startPos, final Vec3 stopPos) {
        return isClearForMovementBetween(this.mob, startPos, stopPos, false);
    }

    @Override
    public boolean isStableDestination(final BlockPos pos) {
        return !this.level.getBlockState(pos).isSolidRender();
    }

    @Override
    public void setCanFloat(final boolean canFloat) {
    }

    @Override
    public boolean canNavigateGround() {
        return false;
    }

    @Override
    public float getMaxVerticalDistanceToWaypoint() {
        return 0.5F;
    }
}
