package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

public class GoToPotentialJobSite extends Behavior<Villager> {
    private static final int TICKS_UNTIL_TIMEOUT = 1200;
    private final float speedModifier;

    public GoToPotentialJobSite(final float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.POTENTIAL_JOB_SITE, MemoryStatus.VALUE_PRESENT), TICKS_UNTIL_TIMEOUT);
        this.speedModifier = speedModifier;
    }

    @Override
    protected boolean checkExtraStartConditions(final ServerLevel level, final Villager body) {
        return body.getBrain()
            .getActiveNonCoreActivity()
            .map(activity -> activity == Activity.IDLE || activity == Activity.WORK || activity == Activity.PLAY)
            .orElse(true);
    }

    @Override
    protected boolean canStillUse(final ServerLevel level, final Villager body, final long timestamp) {
        return body.getBrain().hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE);
    }

    @Override
    protected void tick(final ServerLevel level, final Villager body, final long timestamp) {
        BehaviorUtils.setWalkAndLookTargetMemories(body, body.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).get().pos(), this.speedModifier, 1);
    }

    @Override
    protected void stop(final ServerLevel level, final Villager body, final long timestamp) {
        Optional<GlobalPos> potentialJobSitePos = body.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        potentialJobSitePos.ifPresent(globalPos -> {
            BlockPos pos = globalPos.pos();
            // Leaf start - SparklyPaper - parallel world ticking
            if (net.feathermc.feather.config.modules.async.SparklyPaperParallelWorldTicking.enabled) {
                ServerLevel entityLevel = level; // Villager's current level
                ServerLevel poiLevel = entityLevel.getServer().getLevel(globalPos.dimension()); // POI's actual level

                if (poiLevel != null) {
                    Runnable poiOperationsTask = () -> {
                        PoiManager manager = poiLevel.getPoiManager();
                        if (manager.exists(pos, p -> true)) {
                            manager.release(pos);
                        }
                    };

                    // DebugPackets.sendPoiTicketCountPacket uses the entity's level for its PoiManager context.
                    Runnable debugPacketTask = () -> level.debugSynchronizers().updatePoi(pos);

                    // Schedule POI operations on the POI's level thread, using POI's chunk coordinates for locality
                    poiLevel.moonrise$getChunkTaskScheduler().scheduleChunkTask(pos.getX() >> 4, pos.getZ() >> 4, poiOperationsTask, ca.spottedleaf.concurrentutil.util.Priority.BLOCKING);
                    // Schedule debug packet on the entity's level thread, using entity's chunk coordinates for locality
                    entityLevel.moonrise$getChunkTaskScheduler().scheduleChunkTask(body.chunkPosition().x(), body.chunkPosition().z(), debugPacketTask, ca.spottedleaf.concurrentutil.util.Priority.BLOCKING);
                }
            } else {
                ServerLevel serverLevel = level.getServer().getLevel(globalPos.dimension());
                if (serverLevel != null) {
                    PoiManager manager = serverLevel.getPoiManager();
                    if (manager.exists(pos, p -> true)) {
                        manager.release(pos);
                    }

                    level.debugSynchronizers().updatePoi(pos);
                }
            }
            // Leaf end - SparklyPaper - parallel world ticking
        });
        body.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
    }
}
