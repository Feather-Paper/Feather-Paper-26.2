package net.minecraft.world.entity.ai.sensing;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

public class Sensing {
    private final Mob mob;
    private final it.unimi.dsi.fastutil.ints.Int2IntMap seen = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap(2); // Gale end - initialize line of sight cache with low capacity // Gale - Petal - reduce line of sight cache lookups - merge sets

    // Gale start - Petal - reduce line of sight updates - expiring entity id lists
    private final it.unimi.dsi.fastutil.ints.IntList @org.jspecify.annotations.Nullable [] expiring;
    private int currentCacheAddIndex = 0;
    private int nextToExpireIndex = 1;
    // Gale end - Petal - reduce line of sight updates - expiring entity id lists

    public Sensing(final Mob mob) {
        this.mob = mob;
        // Gale start - Petal - reduce line of sight updates - expiring entity id lists
        int updateLineOfSightInterval = org.galemc.gale.configuration.GaleGlobalConfiguration.get().smallOptimizations.reducedIntervals.updateEntityLineOfSight;
        if (updateLineOfSightInterval <= 1) {
            this.expiring = null;
        } else {
            this.expiring = new it.unimi.dsi.fastutil.ints.IntList[updateLineOfSightInterval];

            for (int i = 0; i < updateLineOfSightInterval; i++) {
                this.expiring[i] = new it.unimi.dsi.fastutil.ints.IntArrayList(0);
            }
        }
        // Gale end - Petal - reduce line of sight updates - expiring entity id lists
    }

    public void tick() {
        if (this.expiring == null) { // Gale - Petal - reduce line of sight updates
        this.seen.clear();
            // Gale start - Petal - reduce line of sight updates
        } else {
            var expiringNow = this.expiring[this.nextToExpireIndex];

            // Leaf start - Use direct iteration on Sensing.tick
            var iterator = expiringNow.iterator();
            while (iterator.hasNext()) {
                this.seen.remove(iterator.nextInt());
            }
            // Leaf end - Use direct iteration on Sensing.tick
            expiringNow.clear();

            this.currentCacheAddIndex++;

            if (this.currentCacheAddIndex == this.expiring.length) {
                this.currentCacheAddIndex = 0;
            }

            this.nextToExpireIndex++;

            if (this.nextToExpireIndex == this.expiring.length) {
                this.nextToExpireIndex = 0;
            }
        }
        // Gale end - Petal - reduce line of sight updates
    }

    public boolean hasLineOfSight(final Entity target) {
        int targetId = target.getId();
        // Gale start - Petal - reduce line of sight cache lookups - merge sets
        int cached = this.seen.get(targetId);

        if (cached == 1) {
            // Gale end - Petal - reduce line of sight cache lookups - merge sets
            return true;
        }

        if (cached == 2) { // Gale - Petal - reduce line of sight cache lookups - merge sets
            return false;
        }

        ProfilerFiller profiler = Profiler.get();
        profiler.push("hasLineOfSight");
        boolean hasLineOfSight = this.mob.hasLineOfSight(target);
        profiler.pop();
        if (hasLineOfSight) {
            this.seen.put(targetId, 1); // Gale - Petal - reduce line of sight cache lookups - merge sets
        } else {
            this.seen.put(targetId, 2); // Gale - Petal - reduce line of sight cache lookups - merge sets
        }

        // Gale start - Petal - reduce line of sight updates
        if (this.expiring != null) {
            this.expiring[this.currentCacheAddIndex].add(targetId);
        }
        // Gale end - Petal - reduce line of sight updates

        return hasLineOfSight;
    }
}
