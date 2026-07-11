package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.pathfinder.Path;

public class NearestBedSensor extends Sensor<Mob> {
    private static final int CACHE_TIMEOUT = 40;
    private static final int BATCH_SIZE = 5;
    private static final int RATE = 20;
    private final Long2LongMap batchCache = new Long2LongOpenHashMap();
    private int triedCount;
    private long lastUpdate;
    @org.jspecify.annotations.Nullable Path pending;  // Kaiiju - petal - async path processing

    public NearestBedSensor() {
        super(20);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.NEAREST_BED);
    }

    @Override
    protected void doTick(final ServerLevel level, final Mob body) {
        // Kaiiju start - petal - async path processing
        if (pending != null && pending.isProcessed()) {
            processPath(body, level.getPoiManager(), pending);
            pending = null;
        }
        // Kaiiju end - petal - async path processing
        if (body.isBaby()) {
            this.triedCount = 0;
            this.lastUpdate = level.getGameTime() + level.getRandom().nextInt(20);
            PoiManager poiManager = level.getPoiManager();
            Predicate<BlockPos> cacheTest = pos -> {
                long key = pos.asLong();
                if (this.batchCache.containsKey(key)) {
                    return false;
                }

                if (++this.triedCount >= 5) {
                    return false;
                }

                this.batchCache.put(key, this.lastUpdate + 40L);
                return true;
            };
            Set<Pair<Holder<PoiType>, BlockPos>> pois = poiManager.findAllWithType(
                    e -> e.is(PoiTypes.HOME), cacheTest, body.blockPosition(), level.purpurConfig.villagerNearestBedSensorSearchRadius, PoiManager.Occupancy.ANY // Purpur - Configurable villager search radius
                )
                .collect(Collectors.toSet());

            Path path = AcquirePoi.findPathToPois(body, pois);
            // Kaiiju start - petal - async path processing
            if (path != null && !path.isProcessed()) {
                this.pending = path;
            } else {
                processPath(body, poiManager, path);
            }
        }
    }
    // Kaiiju end - petal - async path processing

    // Kaiiju start - petal - async path processing
    private void processPath(final Mob body, final PoiManager poiManager, final @org.jspecify.annotations.Nullable Path path) {
        if (path != null && path.canReach()) {
            BlockPos targetPos = path.getTarget();
            Optional<Holder<PoiType>> type = poiManager.getType(targetPos);
            if (type.isPresent()) {
                body.getBrain().setMemory(MemoryModuleType.NEAREST_BED, targetPos);
            }
        } else if (this.triedCount < 5) {
            this.batchCache.long2LongEntrySet().removeIf(entry -> entry.getLongValue() < this.lastUpdate);
        }
    }
    // Kaiiju end - petal - async path processing
}
