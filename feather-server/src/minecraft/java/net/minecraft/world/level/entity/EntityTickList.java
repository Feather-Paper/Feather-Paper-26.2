package net.minecraft.world.level.entity;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public class EntityTickList {
    public final ca.spottedleaf.moonrise.common.list.IteratorSafeOrderedReferenceSet<net.minecraft.world.entity.Entity> entities = new ca.spottedleaf.moonrise.common.list.IteratorSafeOrderedReferenceSet<>(); // Paper - rewrite chunk system // Pufferfish - private->public

    // Leaf start - SparklyPaper - parallel world ticking
    // Used to track async entity additions/removals/loops
    private final net.minecraft.server.level.@Nullable ServerLevel serverLevel;

    public EntityTickList() {
        this(null);
    }

    public EntityTickList(final net.minecraft.server.level.@Nullable ServerLevel serverLevel) {
        this.serverLevel = serverLevel;
    }
    // Leaf end - SparklyPaper - parallel world ticking

    private void ensureActiveIsNotIterated() {
        // Paper - rewrite chunk system
    }

    public void add(final Entity entity) {
        if (net.feathermc.feather.config.modules.async.SparklyPaperParallelWorldTicking.enabled) ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(entity, "Asynchronous entity ticklist addition"); // Leaf - SparklyPaper - parallel world ticking (additional concurrency issues logs)
        this.ensureActiveIsNotIterated();
        this.entities.add(entity); // Paper - rewrite chunk system
    }

    public void remove(final Entity entity) {
        if (net.feathermc.feather.config.modules.async.SparklyPaperParallelWorldTicking.enabled) ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(entity, "Asynchronous entity ticklist removal"); // Leaf - SparklyPaper - parallel world ticking (additional concurrency issues logs)
        this.ensureActiveIsNotIterated();
        this.entities.remove(entity); // Paper - rewrite chunk system
    }

    public boolean contains(final Entity entity) {
        return this.entities.contains(entity); // Paper - rewrite chunk system
    }

    public void forEach(final Consumer<Entity> output) {
        if (net.feathermc.feather.config.modules.async.SparklyPaperParallelWorldTicking.enabled) ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(serverLevel, "Asynchronous entity ticklist iteration"); // Leaf - SparklyPaper - parallel world ticking (additional concurrency issues logs)
        // Paper start - rewrite chunk system
        // To ensure nothing weird happens with dimension travelling, do not iterate over new entries...
        // (by dfl iterator() is configured to not iterate over new entries)
        final ca.spottedleaf.moonrise.common.list.IteratorSafeOrderedReferenceSet.Iterator<Entity> iterator = this.entities.iterator();
        try {
            while (iterator.hasNext()) {
                output.accept(iterator.next());
            }
        } finally {
            iterator.finishedIterating();
        }
        // Paper end - rewrite chunk system
    }
}
