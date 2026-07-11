package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

public class AmphibiousPathNavigation extends PathNavigation {
    public AmphibiousPathNavigation(final Mob mob, final Level level) {
        super(mob, level);
    }

    // Kaiiju start - petal - async path processing
    private static final net.feathermc.feather.async.path.NodeEvaluatorGenerator NODE_EVALUATOR_GENERATOR = (net.feathermc.feather.async.path.NodeEvaluatorFeatures nodeEvaluatorFeatures) -> {
        AmphibiousNodeEvaluator nodeEvaluator = new AmphibiousNodeEvaluator(false);
        nodeEvaluator.setCanPassDoors(nodeEvaluatorFeatures.canPassDoors());
        nodeEvaluator.setCanFloat(nodeEvaluatorFeatures.canFloat());
        nodeEvaluator.setCanWalkOverFences(nodeEvaluatorFeatures.canWalkOverFences());
        nodeEvaluator.setCanOpenDoors(nodeEvaluatorFeatures.canOpenDoors());
        return nodeEvaluator;
    };
    // Kaiiju end - petal - async path processing

    @Override
    protected PathFinder createPathFinder(final int maxVisitedNodes) {
        this.nodeEvaluator = new AmphibiousNodeEvaluator(false);
        // Kaiiju start - petal - async path processing
        if (net.feathermc.feather.config.modules.async.AsyncPathfinding.enabled) {
            return new PathFinder(this.nodeEvaluator, maxVisitedNodes, NODE_EVALUATOR_GENERATOR);
        }
        // Kaiiju end - petal - async path processing
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canUpdatePath() {
        return true;
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
        return this.mob.isInLiquid() && isClearForMovementBetween(this.mob, startPos, stopPos, false);
    }

    @Override
    public boolean isStableDestination(final BlockPos pos) {
        return !this.level.getBlockState(pos.below()).isAir();
    }

    @Override
    public void setCanFloat(final boolean canFloat) {
    }

    @Override
    public boolean canNavigateGround() {
        return true;
    }
}
