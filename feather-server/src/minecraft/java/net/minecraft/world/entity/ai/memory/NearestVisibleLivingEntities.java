package net.minecraft.world.entity.ai.memory;

import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.Sensor;

public class NearestVisibleLivingEntities {
    private static final NearestVisibleLivingEntities EMPTY = new NearestVisibleLivingEntities();
    public final List<LivingEntity> nearbyEntities;
    public final Predicate<LivingEntity> lineOfSightTest;

    private NearestVisibleLivingEntities() {
        this.nearbyEntities = List.of();
        this.lineOfSightTest = ignored -> false;
    }

    public NearestVisibleLivingEntities(final ServerLevel level, final LivingEntity body, final List<LivingEntity> livingEntities) {
        this.nearbyEntities = livingEntities;
        Object2BooleanOpenHashMap<LivingEntity> cache = new Object2BooleanOpenHashMap<>(livingEntities.size());
        Predicate<LivingEntity> targetTest = targetEntity -> Sensor.isEntityTargetable(level, body, targetEntity);
        this.lineOfSightTest = otherEntity -> cache.computeIfAbsent(otherEntity, targetTest);
    }

    public static NearestVisibleLivingEntities empty() {
        return EMPTY;
    }

    @VisibleForDebug
    public List<LivingEntity> nearbyEntities() {
        return this.nearbyEntities;
    }

    public Optional<LivingEntity> findClosest(final Predicate<LivingEntity> filter) {
        // Leaf start - Optimize SetLookAndInteract and NearestVisibleLivingEntities
        if (this.nearbyEntities.isEmpty()) return Optional.empty();
        for (int i = 0, size = this.nearbyEntities.size(); i < size; i++) {
            LivingEntity nearbyEntity = this.nearbyEntities.get(i);
            if (filter.test(nearbyEntity) && this.lineOfSightTest.test(nearbyEntity)) {
                // For SetLookAndInteract we can optimize further since distanceSq check
                // is already in the predicate - see if there's a chance to return early
                return Optional.of(nearbyEntity);
            }
        }
        // Leaf end - Optimize SetLookAndInteract and NearestVisibleLivingEntities

        return Optional.empty();
    }

    public Iterable<LivingEntity> findAll(final Predicate<LivingEntity> filter) {
        return Iterables.filter(this.nearbyEntities, entity -> filter.test(entity) && this.lineOfSightTest.test(entity)); // Leaf - Optimize baby villager sensor - diff on change
    }

    public Stream<LivingEntity> find(final Predicate<LivingEntity> filter) {
        return this.nearbyEntities.stream().filter(entity -> filter.test(entity) && this.lineOfSightTest.test(entity));
    }

    public boolean contains(final LivingEntity targetEntity) {
        return this.nearbyEntities.contains(targetEntity) && this.lineOfSightTest.test(targetEntity);
    }

    public boolean contains(final Predicate<LivingEntity> filter) {
        for (LivingEntity nearbyEntity : this.nearbyEntities) {
            if (filter.test(nearbyEntity) && this.lineOfSightTest.test(nearbyEntity)) {
                return true;
            }
        }

        return false;
    }
}
