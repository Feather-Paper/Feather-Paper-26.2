package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.pathfinder.Path;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableLong;

public class SetClosestHomeAsWalkTarget {
    private static final int CACHE_TIMEOUT = 40;
    private static final int BATCH_SIZE = 5;
    private static final int RATE = 20;
    private static final int OK_DISTANCE_SQR = 4;

    public static BehaviorControl<PathfinderMob> create(final float speedModifier) {
        return new SetClosestHomeAsWalkTargetBehavior(speedModifier);
    }

                // Kaiiju start - petal - async path processing
                private static final class SetClosestHomeAsWalkTargetBehavior extends OneShot<PathfinderMob> {
                    private final Long2LongMap batchCache = new Long2LongOpenHashMap();
                    private long lastUpdate = 0L;
                    private final float speedModifier;
                    private  @org.jspecify.annotations.Nullable Path pending;
                    private int stateInt;

                    private SetClosestHomeAsWalkTargetBehavior(final float speedModifier) {
                        this.speedModifier = speedModifier;
                    }

                    @Override
                    public Set<MemoryModuleType<?>> getRequiredMemories() {
                        return Set.of(MemoryModuleType.WALK_TARGET, MemoryModuleType.HOME);
                    }

                    @Override
                    public boolean trigger(final net.minecraft.server.level.ServerLevel level, final PathfinderMob body, final long gameTime) {
                        if (!body.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.VALUE_ABSENT)) {
                            return false;
                        }
                        if (!body.getBrain().checkMemory(MemoryModuleType.HOME, net.minecraft.world.entity.ai.memory.MemoryStatus.VALUE_ABSENT)) {
                            return false;
                        }

                        if (pending != null && pending.isProcessed()) {
                            processPath(speedModifier, batchCache, lastUpdate, body, level, level.getPoiManager(), stateInt, pending);
                            pending = null;
                        }

                        if (level.getGameTime() - lastUpdate < RATE) {
                            return false;
                        } else {
                        PoiManager poiManager = level.getPoiManager();
                        Optional<BlockPos> closest = poiManager.findClosest(
                            p -> p.is(PoiTypes.HOME), body.blockPosition(), AcquirePoi.SCAN_RANGE, PoiManager.Occupancy.ANY
                        );
                        if (!closest.isEmpty() && !(closest.get().distSqr(body.blockPosition()) <= OK_DISTANCE_SQR)) {
                            MutableInt triedCount = new MutableInt(0);
                            lastUpdate = (level.getGameTime() + level.getRandom().nextInt(20));
                            Predicate<BlockPos> cacheTest = pos -> {
                                long key = pos.asLong();
                                if (batchCache.containsKey(key)) {
                                    return false;
                                }

                                if (triedCount.incrementAndGet() >= BATCH_SIZE) {
                                    return false;
                                }

                                batchCache.put(key, lastUpdate + CACHE_TIMEOUT);
                                return true;
                            };
                            // Leaf start - Re-route SetClosestHomeAsWalkTarget's poi finding to paper's faster logic
                            java.util.List<Pair<Holder<PoiType>, BlockPos>> pois = new java.util.ArrayList<>();
                            ca.spottedleaf.moonrise.patches.poi_lookup.PoiAccess.findAnyPoiPositions(poiManager, p -> p.is(PoiTypes.HOME), cacheTest, body.blockPosition(), AcquirePoi.SCAN_RANGE, PoiManager.Occupancy.ANY, false, Integer.MAX_VALUE, pois);
                            Path path = AcquirePoi.findPathToPois(body, new java.util.HashSet<>(pois));
                            // Leaf end - Re-route SetClosestHomeAsWalkTarget's poi finding to paper's faster logic
                            // Kaiiju start - petal - Async path processing
                            if (path != null && !path.isProcessed()) {
                                this.pending = path;
                                this.stateInt = triedCount.intValue();
                            } else {
                                processPath(speedModifier, batchCache, lastUpdate, body, level, poiManager, triedCount.intValue(), path);
                            }
                            // Kaiiju end - petal - Async path processing

                            return true;
                        } else {
                            return false;
                        }
                    }
                    }
                }
                // Kaiiju end - petal - async path processing

    // Kaiiju start - petal - async path processing
    // Extracted from `SetClosestHomeAsWalkTarget#create`
    private static void processPath(final float speedModifier,
                                    final Long2LongMap batchCache,
                                    final long lastUpdate,
                                    final PathfinderMob walkTarget,
                                    final net.minecraft.server.level.ServerLevel level,
                                    final PoiManager poiManager,
                                    final int triedCount,
                                    final @org.jspecify.annotations.Nullable Path path) {
        if (path != null && path.canReach()) {
            BlockPos targetPos = path.getTarget();
            Optional<Holder<PoiType>> type = poiManager.getType(targetPos);
            if (type.isPresent()) {
                walkTarget.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(targetPos, speedModifier, 1));
                level.debugSynchronizers().updatePoi(targetPos);
            }
        } else if (triedCount < BATCH_SIZE) {
            batchCache.long2LongEntrySet().removeIf(entry -> entry.getLongValue() < lastUpdate);
        }
    }
    // Kaiiju end - petal - async path processing
}
