package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.pathfinder.Path;
import org.apache.commons.lang3.mutable.MutableLong;
import org.jspecify.annotations.Nullable;

public class AcquirePoi {
    public static final int SCAN_RANGE = 48;

    public static BehaviorControl<PathfinderMob> create(
        final Predicate<Holder<PoiType>> poiType,
        final MemoryModuleType<GlobalPos> memoryToAcquire,
        final boolean onlyIfAdult,
        final Optional<Byte> onPoiAcquisitionEvent,
        final BiPredicate<ServerLevel, BlockPos> validPoi
    ) {
        return create(poiType, memoryToAcquire, memoryToAcquire, onlyIfAdult, onPoiAcquisitionEvent, validPoi);
    }

    public static BehaviorControl<PathfinderMob> create(
        final Predicate<Holder<PoiType>> poiType,
        final MemoryModuleType<GlobalPos> memoryToAcquire,
        final boolean onlyIfAdult,
        final Optional<Byte> onPoiAcquisitionEvent
    ) {
        return create(poiType, memoryToAcquire, memoryToAcquire, onlyIfAdult, onPoiAcquisitionEvent, (l, p) -> true);
    }

    public static BehaviorControl<PathfinderMob> create(
        final Predicate<Holder<PoiType>> poiType,
        final MemoryModuleType<GlobalPos> memoryToValidate,
        final MemoryModuleType<GlobalPos> memoryToAcquire,
        final boolean onlyIfAdult,
        final Optional<Byte> onPoiAcquisitionEvent,
        final BiPredicate<ServerLevel, BlockPos> validPoi
    ) {
        return new AcquirePoiBehavior(poiType, memoryToValidate, memoryToAcquire, onPoiAcquisitionEvent, validPoi, onlyIfAdult);
    }

                // Kaiiju start - petal - async path processing
                private static final class AcquirePoiBehavior extends OneShot<PathfinderMob> {
                    private final Predicate<Holder<PoiType>> poiType;
                    private final MemoryModuleType<GlobalPos> memoryToValidate;
                    private final MemoryModuleType<GlobalPos> memoryToAcquire;
                    private final boolean onlyIfAdult;
                    private final Optional<Byte> onPoiAcquisitionEvent;
                    private final BiPredicate<ServerLevel, BlockPos> validPoi;
                    private final Long2ObjectMap<AcquirePoi.JitteredLinearRetry> batchCache = new Long2ObjectOpenHashMap<>();
                    private long nextScheduledStart;
                    private @org.jspecify.annotations.Nullable Path pending;
                    private @org.jspecify.annotations.Nullable Set<Pair<Holder<PoiType>, BlockPos>> stateSet;

                    private AcquirePoiBehavior(final Predicate<Holder<PoiType>> poiType, final MemoryModuleType<GlobalPos> memoryToValidate, final MemoryModuleType<GlobalPos> memoryToAcquire, final Optional<Byte> onPoiAcquisitionEvent, final BiPredicate<ServerLevel, BlockPos> validPoi, final boolean onlyIfAdult) {
                        this.poiType = poiType;
                        this.memoryToValidate = memoryToValidate;
                        this.memoryToAcquire = memoryToAcquire;
                        this.onPoiAcquisitionEvent = onPoiAcquisitionEvent;
                        this.onlyIfAdult = onlyIfAdult;
                        this.validPoi = validPoi;
                    }

                    @Override
                    public Set<MemoryModuleType<?>> getRequiredMemories() {
                        return memoryToAcquire == memoryToValidate ? Set.of(memoryToAcquire) : Set.of(memoryToValidate);
                    }

                    @Override
                    public boolean trigger(final ServerLevel level, final PathfinderMob body, final long timestamp) {
                        if (!body.getBrain().checkMemory(memoryToValidate, net.minecraft.world.entity.ai.memory.MemoryStatus.VALUE_ABSENT)) {
                            return false;
                        }
                        if (memoryToValidate != memoryToAcquire) {
                            if (!body.getBrain().checkMemory(memoryToAcquire, net.minecraft.world.entity.ai.memory.MemoryStatus.VALUE_ABSENT)) {
                                return false;
                            }
                        }
                        RandomSource random = level.getRandom();
                        if (pending != null && stateSet != null && pending.isProcessed()) {
                            processPath(poiType, onPoiAcquisitionEvent, batchCache, level, body, memoryToAcquire, timestamp, level.getPoiManager(), stateSet, pending, random);
                            pending = null;
                            stateSet = null;
                        }

                        if (onlyIfAdult && body.isBaby()) {
                            return false;
                        }

                        if (nextScheduledStart == 0L) {
                            nextScheduledStart = level.getGameTime() + random.nextInt(20);
                            return false;
                        }

                        if (level.getGameTime() < nextScheduledStart) {
                            return false;
                        }

                        nextScheduledStart = timestamp + 20L + random.nextInt(20);
                        if (level.paperConfig().entities.behavior.stuckEntityPoiRetryDelay.enabled() && body.getNavigation().isStuck()) nextScheduledStart += level.paperConfig().entities.behavior.stuckEntityPoiRetryDelay.intValue(); // Paper - Next stuck check delay config
                        PoiManager poiManager = level.getPoiManager();
                        batchCache.long2ObjectEntrySet().removeIf(entry -> !entry.getValue().isStillValid(timestamp));
                        Predicate<BlockPos> cacheTest = pos -> {
                            AcquirePoi.JitteredLinearRetry retryMarker = batchCache.get(pos.asLong());
                            if (retryMarker == null) {
                                return true;
                            }

                            if (!retryMarker.shouldRetry(timestamp)) {
                                return false;
                            }

                            retryMarker.markAttempt(timestamp);
                            return true;
                        };
                        // Paper start - optimise POI searches
                        java.util.List<Pair<Holder<PoiType>, BlockPos>> poiPositionsRaw = new java.util.ArrayList<>();
                        ca.spottedleaf.moonrise.patches.poi_lookup.PoiAccess.findNearestPoiPositions(poiManager, poiType, cacheTest, body.blockPosition(), level.purpurConfig.villagerAcquirePoiSearchRadius, Double.MAX_VALUE, PoiManager.Occupancy.HAS_SPACE, ca.spottedleaf.moonrise.patches.poi_lookup.PoiAccess.LOAD_FOR_SEARCHING, 5, poiPositionsRaw); // Purpur - Configurable villager search radius
                        Set<Pair<Holder<PoiType>, BlockPos>> poiPositions = new java.util.HashSet<>(poiPositionsRaw.size());
                        for (Pair<Holder<PoiType>, BlockPos> pair : poiPositionsRaw) {
                            if (validPoi.test(level, pair.getSecond())) {
                                poiPositions.add(pair);
                            }
                        }
                        // Paper end - optimise POI searches
                        // Kaiiju start - petal - async path processing
                        Path path = findPathToPois(body, poiPositions);
                        if (path != null && !path.isProcessed()) {
                            this.pending = path;
                            this.stateSet = poiPositions;
                        } else {
                            processPath(poiType, onPoiAcquisitionEvent, batchCache, level, body, memoryToAcquire, timestamp, poiManager, poiPositions, path, random);
                        }
                        // Kaiiju end - petal - async path processing
                        return true;
                    }
                }
    // Kaiiju end - petal - async path processing

    // Kaiiju start - petal - async path processing
    // Extracted from `AcquirePoi#create`
    private static void processPath(final Predicate<Holder<PoiType>> poiType,
                                    final Optional<Byte> onPoiAcquisitionEvent,
                                    final Long2ObjectMap<JitteredLinearRetry> batchCache,
                                    final ServerLevel level,
                                    final PathfinderMob walkTarget,
                                    final MemoryModuleType<GlobalPos> memoryToAcquire,
                                    final long timestamp,
                                    final PoiManager poiManager,
                                    final Set<Pair<Holder<PoiType>, BlockPos>> poiPositions,
                                    final @org.jspecify.annotations.Nullable Path path,
                                    final RandomSource random) {
        if (path != null && path.canReach()) {
            BlockPos targetPos = path.getTarget();
            poiManager.getType(targetPos).ifPresent(type -> {
                poiManager.take(poiType, (t, poiPos) -> poiPos.equals(targetPos), targetPos, 1);
                walkTarget.getBrain().setMemory(memoryToAcquire, GlobalPos.of(level.dimension(), targetPos));
                onPoiAcquisitionEvent.ifPresent(event -> level.broadcastEntityEvent(walkTarget, event));
                batchCache.clear();
                level.debugSynchronizers().updatePoi(targetPos);
            });
        } else {
            for (Pair<Holder<PoiType>, BlockPos> p : poiPositions) {
                batchCache.computeIfAbsent(p.getSecond().asLong(), key -> new AcquirePoi.JitteredLinearRetry(random, timestamp));
            }
        }
    }
    // Kaiiju end - petal - async path processing

    public static @Nullable Path findPathToPois(final Mob body, final Set<Pair<Holder<PoiType>, BlockPos>> pois) {
        if (pois.isEmpty()) {
            return null;
        }

        Set<BlockPos> targets = new HashSet<>();
        int maxRange = 1;

        for (Pair<Holder<PoiType>, BlockPos> p : pois) {
            maxRange = Math.max(maxRange, p.getFirst().value().validRange());
            targets.add(p.getSecond());
        }

        return body.getNavigation().createPath(targets, maxRange);
    }

    private static class JitteredLinearRetry {
        private static final int MIN_INTERVAL_INCREASE = 40;
        private static final int MAX_INTERVAL_INCREASE = 80;
        private static final int MAX_RETRY_PATHFINDING_INTERVAL = 400;
        private final RandomSource random;
        private long previousAttemptTimestamp;
        private long nextScheduledAttemptTimestamp;
        private int currentDelay;

        public JitteredLinearRetry(final RandomSource random, final long firstAttemptTimestamp) {
            this.random = random;
            this.markAttempt(firstAttemptTimestamp);
        }

        public void markAttempt(final long timestamp) {
            this.previousAttemptTimestamp = timestamp;
            int suggestedDelay = this.currentDelay + this.random.nextInt(40) + 40;
            this.currentDelay = Math.min(suggestedDelay, 400);
            this.nextScheduledAttemptTimestamp = timestamp + this.currentDelay;
        }

        public boolean isStillValid(final long timestamp) {
            return timestamp - this.previousAttemptTimestamp < 400L;
        }

        public boolean shouldRetry(final long timestamp) {
            return timestamp >= this.nextScheduledAttemptTimestamp;
        }

        @Override
        public String toString() {
            return "RetryMarker{, previousAttemptAt="
                + this.previousAttemptTimestamp
                + ", nextScheduledAttemptAt="
                + this.nextScheduledAttemptTimestamp
                + ", currentDelay="
                + this.currentDelay
                + "}";
        }
    }
}
