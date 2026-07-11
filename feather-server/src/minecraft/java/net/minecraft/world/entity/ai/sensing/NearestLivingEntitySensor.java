package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.phys.AABB;

public class NearestLivingEntitySensor<T extends LivingEntity> extends Sensor<T> {
    @Override
    protected void doTick(final ServerLevel level, final T body) {
        double followRange = body.getAttributeValue(Attributes.FOLLOW_RANGE);
        AABB boundingBox = body.getBoundingBox().inflate(followRange, followRange, followRange);
        // Leaf start - fast bit radix sort
        it.unimi.dsi.fastutil.objects.ObjectArrayList<LivingEntity> livingEntities = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel) level).moonrise$getEntityLookup().getEntities(LivingEntity.class, body, boundingBox, livingEntities, LivingEntity::isAlive);
        level.fastBitRadixSort.sort(livingEntities.elements(), livingEntities.size(), body.position());
        // Leaf end - fast bit radix sort
        Brain<?> brain = body.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, livingEntities);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, new NearestVisibleLivingEntities(level, body, livingEntities));
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
    }
}
