package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class MoveToTargetSink extends Behavior<Mob> {
    private static final int MAX_COOLDOWN_BEFORE_RETRYING = 40;
    private int remainingCooldown;
    private @Nullable Path path;
    private boolean finishedProcessing; // Kaiiju - petal - async path processing - track when path is processed
    private @Nullable BlockPos lastTargetPos;
    private float speedModifier;

    public MoveToTargetSink() {
        this(150, 250);
    }

    public MoveToTargetSink(final int minTimeout, final int maxTimeout) {
        super(
            ImmutableMap.of(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                MemoryStatus.REGISTERED,
                MemoryModuleType.PATH,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.VALUE_PRESENT
            ),
            minTimeout,
            maxTimeout
        );
    }

    @Override
    protected boolean checkExtraStartConditions(final ServerLevel level, final Mob body) {
        if (this.remainingCooldown > 0) {
            this.remainingCooldown--;
            return false;
        }

        Brain<?> brain = body.getBrain();
        WalkTarget walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET).get();
        boolean reachedTarget = this.reachedTarget(body, walkTarget);
        if (!net.feathermc.feather.config.modules.async.AsyncPathfinding.enabled && !reachedTarget && this.tryComputePath(body, walkTarget, level.getGameTime())) { // Kaiiju - petal - async path processing - Async path processing means we can't know if the path is reachable here
            this.lastTargetPos = walkTarget.getTarget().currentBlockPosition();
            return true;
        } else if (net.feathermc.feather.config.modules.async.AsyncPathfinding.enabled && !reachedTarget) { return true; // Kaiiju - petal - async path processing
        }

        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        if (reachedTarget) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        }

        return false;
    }

    @Override
    protected boolean canStillUse(final ServerLevel level, final Mob body, final long timestamp) {
        if (net.feathermc.feather.config.modules.async.AsyncPathfinding.enabled && !this.finishedProcessing) return true; // Kaiiju - petal - async path processing - wait for processing
        if (this.path != null && this.lastTargetPos != null) {
            Optional<WalkTarget> walkTarget = body.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
            boolean isSpectator = walkTarget.map(MoveToTargetSink::isWalkTargetSpectator).orElse(false);
            PathNavigation navigation = body.getNavigation();
            return !navigation.isDone() && walkTarget.isPresent() && !this.reachedTarget(body, walkTarget.get()) && !isSpectator;
        } else {
            return false;
        }
    }

    @Override
    protected void stop(final ServerLevel level, final Mob body, final long timestamp) {
        if (body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
            && !this.reachedTarget(body, body.getBrain().getMemory(MemoryModuleType.WALK_TARGET).get())
            && body.getNavigation().isStuck()) {
            this.remainingCooldown = level.getRandom().nextInt(MAX_COOLDOWN_BEFORE_RETRYING);
        }

        body.getNavigation().stop();
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.PATH);
        this.path = null;
    }

    @Override
    protected void start(final ServerLevel level, final Mob body, final long timestamp) {
        // Kaiiju start - petal - async path processing - start processing
        if (net.feathermc.feather.config.modules.async.AsyncPathfinding.enabled) {
            Brain<?> brain = body.getBrain();
            WalkTarget walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET).get();

            this.finishedProcessing = false;
            this.lastTargetPos = walkTarget.getTarget().currentBlockPosition();
            this.path = this.computePath(body, walkTarget);
            return;
        }
        // Kaiiju end - petal - async path processing
        body.getBrain().setMemory(MemoryModuleType.PATH, this.path);
        body.getNavigation().moveTo(this.path, this.speedModifier);
    }

    @Override
    protected void tick(final ServerLevel level, final Mob body, final long timestamp) {
        // Kaiiju start - petal - async path processing
        if (net.feathermc.feather.config.modules.async.AsyncPathfinding.enabled) {
            if (this.path != null && !this.path.isProcessed()) return; // wait for processing

            if (!this.finishedProcessing) {
                this.finishedProcessing = true;

                Brain<?> brain = body.getBrain();
                boolean canReach = this.path != null && this.path.canReach();
                if (canReach) {
                    brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
                } else if (!brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
                    brain.setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, timestamp);
                }

                if (!canReach) {
                    Optional<WalkTarget> walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET);

                    if (walkTarget.isEmpty()) return;

                    BlockPos blockPos = walkTarget.get().getTarget().currentBlockPosition();
                    Vec3 vec3 = DefaultRandomPos.getPosTowards((PathfinderMob) body, 10, 7, Vec3.atBottomCenterOf(blockPos), (float) Math.PI / 2F);
                    if (vec3 != null) {
                        // try recalculating the path using a random position
                        this.path = body.getNavigation().createPath(vec3.x, vec3.y, vec3.z, 0);
                        this.finishedProcessing = false;
                        return;
                    }
                }

                body.getBrain().setMemory(MemoryModuleType.PATH, this.path);
                body.getNavigation().moveTo(this.path, this.speedModifier);
            }

            Path path = body.getNavigation().getPath();
            Brain<?> brain = body.getBrain();

            if (path != null && this.lastTargetPos != null && brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                WalkTarget walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET).get(); // we know isPresent = true
                if (walkTarget.getTarget().currentBlockPosition().distSqr(this.lastTargetPos) > 4.0D) {
                    this.start(level, body, timestamp);
                }
            }
        } else {
            // Kaiiju end - petal - async path processing
        Path newPath = body.getNavigation().getPath();
        Brain<?> brain = body.getBrain();
        if (this.path != newPath) {
            this.path = newPath;
            brain.setMemory(MemoryModuleType.PATH, newPath);
        }

        if (newPath != null && this.lastTargetPos != null) {
            WalkTarget walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET).get();
            if (walkTarget.getTarget().currentBlockPosition().distSqr(this.lastTargetPos) > 4.0 && this.tryComputePath(body, walkTarget, level.getGameTime())) {
                this.lastTargetPos = walkTarget.getTarget().currentBlockPosition();
                this.start(level, body, timestamp);
            }
        }
        } // Kaiiju - petal - async path processing
    }

    // Kaiiju start - petal - async path processing
    private @Nullable Path computePath(Mob body, WalkTarget walkTarget) {
        BlockPos targetPos = walkTarget.getTarget().currentBlockPosition();
        // don't pathfind outside region
        //if (!io.papermc.paper.util.TickThread.isTickThreadFor((ServerLevel) body.level(), targetPos)) return null; // Leaf - Don't need this
        this.speedModifier = walkTarget.getSpeedModifier();
        Brain<?> brain = body.getBrain();
        if (this.reachedTarget(body, walkTarget)) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        }
        return body.getNavigation().createPath(targetPos, 0);
    }
    // Kaiiju end - petal - async path processing

    private boolean tryComputePath(final Mob body, final WalkTarget walkTarget, final long timestamp) {
        BlockPos targetPos = walkTarget.getTarget().currentBlockPosition();
        this.path = body.getNavigation().createPath(targetPos, 0);
        this.speedModifier = walkTarget.getSpeedModifier();
        Brain<?> brain = body.getBrain();
        if (this.reachedTarget(body, walkTarget)) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        } else {
            boolean canReach = this.path != null && this.path.canReach();
            if (canReach) {
                brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            } else if (!brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
                brain.setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, timestamp);
            }

            if (this.path != null) {
                return true;
            }

            Vec3 partialStep = DefaultRandomPos.getPosTowards((PathfinderMob)body, 10, 7, Vec3.atBottomCenterOf(targetPos), (float) (Math.PI / 2));
            if (partialStep != null) {
                this.path = body.getNavigation().createPath(partialStep.x, partialStep.y, partialStep.z, 0);
                return this.path != null;
            }
        }

        return false;
    }

    private boolean reachedTarget(final Mob body, final WalkTarget walkTarget) {
        return walkTarget.getTarget().currentBlockPosition().distManhattan(body.blockPosition()) <= walkTarget.getCloseEnoughDist();
    }

    private static boolean isWalkTargetSpectator(final WalkTarget walkTarget) {
        return walkTarget.getTarget() instanceof EntityTracker entityTracker && entityTracker.getEntity().isSpectator();
    }
}
