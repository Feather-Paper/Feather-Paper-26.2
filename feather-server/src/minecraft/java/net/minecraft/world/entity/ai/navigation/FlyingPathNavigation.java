package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

public class FlyingPathNavigation extends PathNavigation {
    public FlyingPathNavigation(final Mob mob, final Level level) {
        super(mob, level);
    }

    // Kaiiju start - petal - async path processing
    private static final net.feathermc.feather.async.path.NodeEvaluatorGenerator NODE_EVALUATOR_GENERATOR = (net.feathermc.feather.async.path.NodeEvaluatorFeatures nodeEvaluatorFeatures) -> {
        FlyNodeEvaluator nodeEvaluator = new FlyNodeEvaluator();
        nodeEvaluator.setCanPassDoors(nodeEvaluatorFeatures.canPassDoors());
        nodeEvaluator.setCanFloat(nodeEvaluatorFeatures.canFloat());
        nodeEvaluator.setCanWalkOverFences(nodeEvaluatorFeatures.canWalkOverFences());
        nodeEvaluator.setCanOpenDoors(nodeEvaluatorFeatures.canOpenDoors());
        return nodeEvaluator;
    };
    // Kaiiju end - petal - async path processing

    @Override
    protected PathFinder createPathFinder(final int maxVisitedNodes) {
        this.nodeEvaluator = new FlyNodeEvaluator();
        // Kaiiju start - petal - async path processing
        if (net.feathermc.feather.config.modules.async.AsyncPathfinding.enabled) {
            return new PathFinder(this.nodeEvaluator, maxVisitedNodes, NODE_EVALUATOR_GENERATOR);
        }
        // Kaiiju end - petal - async path processing
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canMoveDirectly(final Vec3 startPos, final Vec3 stopPos) {
        return isClearForMovementBetween(this.mob, startPos, stopPos, true);
    }

    @Override
    protected boolean canUpdatePath() {
        return this.canFloat() && this.mob.isInLiquid() || !this.mob.isPassenger();
    }

    @Override
    protected Vec3 getTempMobPos() {
        return this.mob.position();
    }

    @Override
    public Path createPath(final Entity target, final int reachRange) {
        return this.createPath(target.blockPosition(), target, reachRange); // Paper - EntityPathfindEvent
    }

    @Override
    public void tick() {
        this.tick++;
        if (this.hasDelayedRecomputation) {
            this.recomputePath();
        }
        if (this.path != null && !this.path.isProcessed()) return; // Kaiiju - petal - async path processing

        if (!this.isDone()) {
            if (this.canUpdatePath()) {
                this.followThePath();
            } else if (this.path != null && !this.path.isDone()) {
                Vec3 pos = this.path.getNextEntityPos(this.mob);
                if (this.mob.getBlockX() == Mth.floor(pos.x) && this.mob.getBlockY() == Mth.floor(pos.y) && this.mob.getBlockZ() == Mth.floor(pos.z)) {
                    this.path.advance();
                }
            }

            if (!this.isDone()) {
                Vec3 target = this.path.getNextEntityPos(this.mob);
                this.mob.getMoveControl().setWantedPosition(target.x, target.y, target.z, this.speedModifier);
            }
        }
    }

    @Override
    public boolean isStableDestination(final BlockPos pos) {
        return this.level.getBlockState(pos).entityCanStandOn(this.level, pos, this.mob);
    }

    @Override
    public boolean canNavigateGround() {
        return false;
    }
}
