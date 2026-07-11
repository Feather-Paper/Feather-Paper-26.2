package net.minecraft.world.level;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class NaturalSpawner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MIN_SPAWN_DISTANCE = 24;
    public static final int SPAWN_DISTANCE_CHUNK = 8;
    public static final int SPAWN_DISTANCE_BLOCK = 128;
    public static final int INSCRIBED_SQUARE_SPAWN_DISTANCE_CHUNK = Mth.floor(8.0F / Mth.SQRT_OF_TWO);
    private static final int MAGIC_NUMBER = (int)Math.pow(17.0, 2.0);
    public static final MobCategory[] SPAWNING_CATEGORIES = Stream.of(MobCategory.values()).filter(c -> c != MobCategory.MISC).toArray(MobCategory[]::new);

    private NaturalSpawner() {
    }

    public static NaturalSpawner.SpawnState createState(
        final int spawnableChunkCount,
        final Iterable<Entity> entities,
        final NaturalSpawner.ChunkGetter chunkGetter,
        final LocalMobCapCalculator localMobCapCalculator
    ) {
        // Paper start - Optional per player mob spawns
        return createState(spawnableChunkCount, entities, chunkGetter, localMobCapCalculator, false);
    }

    @Deprecated // Leaf - optimize mob spawning
    public static NaturalSpawner.SpawnState createState(
        final int spawnableChunkCount,
        final Iterable<Entity> entities,
        final NaturalSpawner.ChunkGetter chunkGetter,
        final LocalMobCapCalculator localMobCapCalculator,
        final boolean countMobs
    ) {
        // Paper end - Optional per player mob spawns
        PotentialCalculator spawnPotential = new PotentialCalculator();
        Object2IntOpenHashMap<MobCategory> mobCounts = new Object2IntOpenHashMap<>();

        for (Entity entity : entities) {
            if (!(entity instanceof Mob mob && (mob.isPersistenceRequired() || mob.requiresCustomPersistence()))) {
                MobCategory category = entity.getType().getCategory();
                if (category != MobCategory.MISC) {
                    // Paper start - Only count natural spawns
                    if (!entity.level().paperConfig().entities.spawning.countAllMobsForSpawning &&
                        !(entity.spawnReason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL ||
                            entity.spawnReason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CHUNK_GEN)) {
                        continue;
                    }
                    // Paper end - Only count natural spawns
                    BlockPos pos = entity.blockPosition();
                    chunkGetter.query(ChunkPos.pack(pos), chunk -> {
                        MobSpawnSettings.MobSpawnCost mobSpawnCost = getRoughBiome(pos, chunk).getMobSettings().getMobSpawnCost(entity.getType());
                        if (mobSpawnCost != null) {
                            spawnPotential.addCharge(entity.blockPosition(), mobSpawnCost.charge());
                        }

                        if (localMobCapCalculator != null && entity instanceof Mob) { // Paper - Optional per player mob spawns
                            localMobCapCalculator.addMob(chunk.getPos(), category);
                        }

                        mobCounts.addTo(category, 1);
                        // Paper start - Optional per player mob spawns
                        if (countMobs) {
                            ((ServerLevel) chunk.getLevel()).getChunkSource().chunkMap.updatePlayerMobTypeMap(entity);
                        }
                        // Paper end - Optional per player mob spawns
                    });
                }
            }
        }

        return new NaturalSpawner.SpawnState(spawnableChunkCount, mobCounts, spawnPotential, localMobCapCalculator);
    }

    private static Biome getRoughBiome(final BlockPos pos, final ChunkAccess chunk) {
        return chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ())).value();
    }

    // CraftBukkit start - add server
    public static List<MobCategory> getFilteredSpawningCategories(
        final NaturalSpawner.SpawnState state, final boolean spawnFriendlies, final boolean spawnEnemies, final boolean spawnPersistent, final ServerLevel level
    ) {
        net.minecraft.world.level.storage.LevelData worlddata = level.getLevelData(); // CraftBukkit - Other mob type spawn tick rate
        // CraftBukkit end
        List<MobCategory> spawningCategories = new ArrayList<>(SPAWNING_CATEGORIES.length);

        for (MobCategory mobCategory : SPAWNING_CATEGORIES) {
            // CraftBukkit start - Use per-world spawn limits
            boolean spawnThisTick = true;
            int limit = mobCategory.getMaxInstancesPerChunk();
            org.bukkit.entity.SpawnCategory spawnCategory = org.bukkit.craftbukkit.util.CraftSpawnCategory.toBukkit(mobCategory);
            if (org.bukkit.craftbukkit.util.CraftSpawnCategory.isValidForLimits(spawnCategory)) {
                spawnThisTick = level.ticksPerSpawnCategory.getLong(spawnCategory) != 0 && worlddata.getGameTime() % level.ticksPerSpawnCategory.getLong(spawnCategory) == 0;
                limit = level.getWorld().getSpawnLimit(spawnCategory);
            }

            if (!spawnThisTick || limit == 0) {
                continue;
            }

            if ((spawnFriendlies || !mobCategory.isFriendly()) && (spawnEnemies || mobCategory.isFriendly()) && (spawnPersistent || !mobCategory.isPersistent()) && (level.paperConfig().entities.spawning.perPlayerMobSpawns || state.canSpawnForCategoryGlobal(mobCategory, limit))) { // Paper - Optional per player mob spawns; remove global check, check later during the local one
                // CraftBukkit end
                spawningCategories.add(mobCategory);
            }
        }

        return spawningCategories;
    }

    public static void spawnForChunk(
        final ServerLevel level, final LevelChunk chunk, final NaturalSpawner.SpawnState state, final List<MobCategory> spawningCategories
    ) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("spawner");

        ChunkGenerator generator = level.getChunkSource().getGenerator(); // Leaf - optimize mob spawning
        for (MobCategory mobCategory : spawningCategories) {
            // Paper start - Optional per player mob spawns
            final boolean canSpawn;
            int maxSpawns = Integer.MAX_VALUE;
            // Leaf start - throttle mob spawning
            if (net.feathermc.feather.config.modules.opt.ThrottleNaturalMobSpawning.enabled) {
                int spawnChance = net.feathermc.feather.config.modules.opt.ThrottleNaturalMobSpawning.spawnChance[mobCategory.ordinal()];
                long failedAttempt = net.feathermc.feather.config.modules.opt.ThrottleNaturalMobSpawning.failedAttempts[mobCategory.ordinal()];
                if (failedAttempt >= 0L
                    && chunk.failedSpawnAttempts[mobCategory.ordinal()] >= failedAttempt
                    && (level.random.nextInt() & Integer.MAX_VALUE) >= spawnChance) {
                    continue;
                }
            }
            // Leaf end - throttle mob spawning
            if (level.paperConfig().entities.spawning.perPlayerMobSpawns) {
                // Copied from getFilteredSpawningCategories
                int limit = mobCategory.getMaxInstancesPerChunk();
                org.bukkit.entity.SpawnCategory spawnCategory = org.bukkit.craftbukkit.util.CraftSpawnCategory.toBukkit(mobCategory);
                // Paper PR start - throttle failed spawn attempts
                boolean spawnThisTick = true;
                long ticksPerSpawn = level.ticksPerSpawnCategory.getLong(spawnCategory);
                long ticksPerSpawnTmp = ticksPerSpawn;
                io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.SpawningThrottle spawningThrottle = level.paperConfig().entities.spawning.spawningThrottle;
                java.util.OptionalInt threshold = spawningThrottle.failedAttemptsThreshold.value(); // Leaf - optimize mob spawning
                if (threshold.isPresent() && chunk.failedSpawnAttempts[mobCategory.ordinal()] > threshold.getAsInt()) { // Leaf - optimize mob spawning
                    ticksPerSpawn = Math.max(ticksPerSpawn, spawningThrottle.throttledTicksPerSpawn.getOrDefault(mobCategory, -1));
                }
                // Paper PR end - throttle failed spawn attempts
                if (org.bukkit.craftbukkit.util.CraftSpawnCategory.isValidForLimits(spawnCategory)) {
                    spawnThisTick = ticksPerSpawnTmp != 0 && level.getGameTime() % ticksPerSpawn == 0; // Paper PR - throttle failed spawn attempts
                    limit = level.getWorld().getSpawnLimit(spawnCategory);
                }

                if (!spawningThrottle.failedAttemptsThreshold.enabled() || spawnThisTick) { // Paper PR - throttle failed spawn attempts
                // Apply per-player limit
                int minDiff = Integer.MAX_VALUE;
                final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.server.level.ServerPlayer> inRange =
                    level.moonrise$getNearbyPlayers().getPlayers(chunk.getPos(), ca.spottedleaf.moonrise.common.misc.NearbyPlayers.NearbyMapType.TICK_VIEW_DISTANCE);
                if (inRange != null) {
                    final net.minecraft.server.level.ServerPlayer[] backingSet = inRange.getRawDataUnchecked();
                    for (int k = 0, len = inRange.size(); k < len; k++) {
                        minDiff = Math.min(limit - level.getChunkSource().chunkMap.getMobCountNear(backingSet[k], mobCategory), minDiff);
                    }
                }

                maxSpawns = (minDiff == Integer.MAX_VALUE) ? 0 : minDiff;
                canSpawn = maxSpawns > 0;
                } else { canSpawn = false; } // Paper PR - throttle failed spawn attempts
            } else {
                canSpawn = state.canSpawnForCategoryLocal(mobCategory, chunk.getPos());
            }
            if (canSpawn) {
                // Paper PR start - throttle failed spawn attempts
                int spawnCount = spawnCategoryForChunk(mobCategory, level, chunk, state::canSpawn, state::afterSpawn,
                    maxSpawns, level.paperConfig().entities.spawning.perPlayerMobSpawns ? level.getChunkSource().chunkMap::updatePlayerMobTypeMap : null, generator); // Leaf - optimize mob spawning
                if (spawnCount == 0) {
                    chunk.failedSpawnAttempts[mobCategory.ordinal()]++;
                } else {
                    chunk.failedSpawnAttempts[mobCategory.ordinal()] = 0;
                }
                // Paper end PR - throttle failed spawn attempts
                // Paper end - Optional per player mob spawns
            }
        }

        profiler.pop();
    }

    // Paper start - Add mobcaps commands
    public static int globalLimitForCategory(final ServerLevel level, final MobCategory category, final int spawnableChunkCount) {
        final int categoryLimit = level.getWorld().getSpawnLimitUnsafe(org.bukkit.craftbukkit.util.CraftSpawnCategory.toBukkit(category));
        if (categoryLimit < 1) {
            return categoryLimit;
        }
        return categoryLimit * spawnableChunkCount / NaturalSpawner.MAGIC_NUMBER;
    }
    // Paper end - Add mobcaps commands

    public static void spawnCategoryForChunk(
        final MobCategory mobCategory,
        final ServerLevel level,
        final LevelChunk chunk,
        final NaturalSpawner.SpawnPredicate extraTest,
        final NaturalSpawner.AfterSpawnCallback spawnCallback
    ) {
        // Paper start - Optional per player mob spawns
        spawnCategoryForChunk(mobCategory, level, chunk, extraTest, spawnCallback, Integer.MAX_VALUE, null);
    }
    public static void spawnCategoryForChunk(
        final MobCategory mobCategory,
        final ServerLevel level,
        final LevelChunk chunk,
        final NaturalSpawner.SpawnPredicate extraTest,
        final NaturalSpawner.AfterSpawnCallback spawnCallback,
        final int maxSpawns,
        final Consumer<Entity> trackEntity
    ) {
        // Paper PR start - throttle failed spawn attempts
        spawnCategoryForChunk(mobCategory, level, chunk, extraTest, spawnCallback, maxSpawns, trackEntity, level.getChunkSource().getGenerator()); // Leaf - optimize mob spawning
    }
    public static int spawnCategoryForChunk(
        final MobCategory mobCategory,
        final ServerLevel level,
        final LevelChunk chunk,
        final NaturalSpawner.SpawnPredicate extraTest,
        final NaturalSpawner.AfterSpawnCallback spawnCallback,
        final int maxSpawns,
        final Consumer<Entity> trackEntity,
        final ChunkGenerator generator // Leaf - optimize mob spawning
    ) {
        // Paper PR end - throttle failed spawn attempts
        // Paper end - Optional per player mob spawns
        BlockPos start = getRandomPosWithin(level, chunk);
        if (start.getY() >= level.getMinY() + 1) {
            return spawnCategoryForPosition(mobCategory, level, chunk, start, extraTest, spawnCallback, maxSpawns, trackEntity, generator); // Paper - Optional per player mob spawns // Paper PR - throttle failed spawn attempts // Leaf - optimize mob spawning
        }

        return 0; // Paper PR - throttle failed spawn attempts
    }

    @VisibleForDebug
    public static void spawnCategoryForPosition(final MobCategory mobCategory, final ServerLevel level, final BlockPos start) {
        spawnCategoryForPosition(mobCategory, level, level.getChunk(start), start, (type, chunk, pos) -> true, (mob, chunk) -> {});
    }

    public static void spawnCategoryForPosition(
        final MobCategory mobCategory,
        final ServerLevel level,
        final ChunkAccess chunk,
        final BlockPos start,
        final NaturalSpawner.SpawnPredicate extraTest,
        final NaturalSpawner.AfterSpawnCallback spawnCallback
    ) {
        // Paper start - Optional per player mob spawns
        spawnCategoryForPosition(mobCategory, level, chunk, start, extraTest, spawnCallback, Integer.MAX_VALUE, null);
    }
    public static void spawnCategoryForPosition(
        final MobCategory mobCategory,
        final ServerLevel level,
        final ChunkAccess chunk,
        final BlockPos start,
        final NaturalSpawner.SpawnPredicate extraTest,
        final NaturalSpawner.AfterSpawnCallback spawnCallback,
        final int maxSpawns,
        final @Nullable Consumer<Entity> trackEntity
        // Paper PR start - throttle failed spawn attempts
    ) {
        spawnCategoryForPosition(mobCategory, level, chunk, start, extraTest, spawnCallback, maxSpawns, trackEntity, level.getChunkSource().getGenerator()); // Leaf - optimize mob spawning
    }
    public static int spawnCategoryForPosition(
        final MobCategory mobCategory,
        final ServerLevel level,
        final ChunkAccess chunk,
        final BlockPos start,
        final NaturalSpawner.SpawnPredicate extraTest,
        final NaturalSpawner.AfterSpawnCallback spawnCallback,
        final int maxSpawns,
        final @Nullable Consumer<Entity> trackEntity,
        final ChunkGenerator generator // Leaf - optimize mob spawning
        // Paper PR end - throttle failed spawn attempts
    ) {
        // Leaf start - optimize mob spawning
        if (!(chunk instanceof LevelChunk levelChunk)) {
            // unreachable
            return 0;
        }
        // Paper end - Optional per player mob spawns
        StructureManager structureManager = level.structureManager();
        //ChunkGenerator generator = level.getChunkSource().getGenerator();
        int yStart = start.getY();
        final int posX = start.getX();
        final int posZ = start.getZ();
        int clusterSize = 0; // Paper PR - throttle failed spawn attempts
        BlockState state = ChunkPos.pack(start) == chunk.coordinateKey ? levelChunk.getBlockStateFinal(posX, yStart, posZ) : level.getBlockStateIfLoadedAndInBounds(start); // Paper - don't load chunks for mob spawn
        if (state != null && !state.isRedstoneConductor(chunk, start)) { // Paper - don't load chunks for mob spawn
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            //int clusterSize = 0; // Paper PR - throttle failed spawn attempts - move up

            // 3 * (2 + 3 * [1, 4] * 4)
            long rand = level.random.nextLong();
            int bits = 0;
            for (int groupCount = 0; groupCount < 3; groupCount++) {
                int x = posX;
                int z = posZ;
                //int ss = 6;
                MobSpawnSettings.SpawnerData currentSpawnData = null;
                SpawnGroupData groupData = null;
                //int max = Mth.ceil(level.random.nextFloat() * 4.0F);
                int max = (int) (((rand >>> bits) & 0x3L) + 1);
                bits += 2;
                if (bits >= 62) {
                    rand = level.random.nextLong();
                    bits = 0;
                }
                int groupSize = 0;

                for (int ll = 0; ll < max; ll++) {
                    int rand1 = 0, rand2 = 0, rand3 = 0, rand4 = 0, valuesNeeded = 4;
                    while (valuesNeeded > 0) {
                        // [0, 61] 3 remains
                        int threeBits = (int) ((rand >>> bits) & 0x7L);
                        bits += 3;
                        if (threeBits != 7 && threeBits != 6) {
                            switch (valuesNeeded) {
                                case 1 -> rand4 = threeBits;
                                case 2 -> rand3 = threeBits;
                                case 3 -> rand2 = threeBits;
                                default -> rand1 = threeBits;
                            }
                            valuesNeeded--;
                        }
                        if (bits >= 62) {
                            rand = level.random.nextLong();
                            bits = 0;
                        }
                    }
                    x += rand1 - rand2;
                    z += rand3 - rand4;
                    // x += level.random.nextInt(6) - level.random.nextInt(6);
                    // z += level.random.nextInt(6) - level.random.nextInt(6);
                    // Leaf end - optimize mob spawning
                    pos.set(x, yStart, z);
                    double xx = x + 0.5;
                    double zz = z + 0.5;
                    //Player nearestPlayer = level.getNearestPlayer(xx, yStart, zz, -1.0, level.purpurConfig.mobSpawningIgnoreCreativePlayers); // Purpur - mob spawning option to ignore creative players // Leaf - optimize mob spawning
                    double nearestPlayerDistanceSqr = level.natureSpawnChunkMap.nearest(level, xx, yStart, zz); // Leaf - optimize mob spawning
                    if (nearestPlayerDistanceSqr != Double.POSITIVE_INFINITY) { // Leaf - optimize mob spawning
                        //double nearestPlayerDistanceSqr = nearestPlayer.distanceToSqr(xx, yStart, zz);
                        if ((ChunkPos.pack(pos) == chunk.coordinateKey || level.isLoadedAndInBounds(pos)) && isRightDistanceToPlayerAndSpawnPoint(level, chunk, pos, nearestPlayerDistanceSqr)) { // Paper - don't load chunks for mob spawn // Leaf - optimize mob spawning
                            if (currentSpawnData == null) {
                                Optional<MobSpawnSettings.SpawnerData> nextSpawnData = getRandomSpawnMobAtWithChunk( // Leaf - optimize mob spawning
                                    level, structureManager, generator, mobCategory, level.random, pos, levelChunk // Leaf - optimize mob spawning
                                );
                                if (nextSpawnData.isEmpty()) {
                                    break;
                                }

                                currentSpawnData = nextSpawnData.get();
                                max = currentSpawnData.minCount() + level.random.nextInt(1 + currentSpawnData.maxCount() - currentSpawnData.minCount());
                            }

                            // Paper start - PreCreatureSpawnEvent
                            PreSpawnStatus doSpawning = isValidSpawnPostitionForTypeWithChunk(level, mobCategory, structureManager, generator, currentSpawnData, pos, nearestPlayerDistanceSqr, levelChunk); // Leaf - optimize mob spawning
                            // Paper start - per player mob count backoff
                            if (doSpawning == PreSpawnStatus.ABORT || doSpawning == PreSpawnStatus.CANCELLED) {
                                level.getChunkSource().chunkMap.updateFailurePlayerMobTypeMap(pos.getX() >> 4, pos.getZ() >> 4, mobCategory);
                            }
                            // Paper end - per player mob count backoff
                            if (doSpawning == PreSpawnStatus.ABORT) {
                                return clusterSize; // Paper PR - throttle failed spawn attempts
                            }
                            if (doSpawning == PreSpawnStatus.SUCCESS
                            // Paper end - PreCreatureSpawnEvent
                                && extraTest.test(currentSpawnData.type(), pos, chunk)) {
                                Mob mob = getMobForSpawn(level, currentSpawnData.type());
                                if (mob == null) {
                                    return clusterSize; // Paper PR - throttle failed spawn attempts
                                }

                                mob.snapTo(xx, yStart, zz, level.random.nextFloat() * 360.0F, 0.0F);
                                if (isValidPositionForMob(level, mob, nearestPlayerDistanceSqr)) {
                                    groupData = mob.finalizeSpawn(
                                        level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.NATURAL, groupData
                                    );
                                    // CraftBukkit start
                                    // SPIGOT-7045: Give ocelot babies back their special spawn reason. Note: This is the only modification required as ocelots count as monsters which means they only spawn during normal chunk ticking and do not spawn during chunk generation as starter mobs.
                                    level.addFreshEntityWithPassengers(mob, (mob instanceof net.minecraft.world.entity.animal.feline.Ocelot && !((org.bukkit.entity.Ageable) mob.getBukkitEntity()).isAdult()) ? org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.OCELOT_BABY : org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL);
                                    if (!mob.isRemoved()) {
                                        clusterSize++;
                                        groupSize++;
                                        spawnCallback.run(mob, chunk);
                                        // Paper start - Optional per player mob spawns
                                        if (trackEntity != null) {
                                            trackEntity.accept(mob);
                                        }
                                        // Paper end - Optional per player mob spawns
                                    }
                                    // CraftBukkit end
                                    if (clusterSize >= maxSpawns || clusterSize >= mob.getMaxSpawnClusterSize()) { // Paper - Optional per player mob spawns
                                        return clusterSize; // Paper PR - throttle failed spawn attempts
                                    }

                                    if (mob.isMaxGroupSizeReached(groupSize)) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return clusterSize; // Paper PR - throttle failed spawn attempts
    }

    private static boolean isRightDistanceToPlayerAndSpawnPoint(
        final ServerLevel level, final ChunkAccess chunk, final BlockPos.MutableBlockPos pos, final double nearestPlayerDistanceSqr
    ) {
        if (nearestPlayerDistanceSqr <= 576.0) {
            return false;
        }

        LevelData.RespawnData respawnData = level.getRespawnData();
        if (respawnData.dimension() == level.dimension()
            && respawnData.pos().closerToCenterThan(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), 24.0)) {
            return false;
        }

        ChunkPos chunkPos = ChunkPos.containing(pos);
        return Objects.equals(chunkPos, chunk.getPos()) || level.canSpawnEntitiesInChunk(chunkPos);
    }

    // Paper start - PreCreatureSpawnEvent
    private enum PreSpawnStatus {
        FAIL,
        SUCCESS,
        CANCELLED,
        ABORT
    }
    private static PreSpawnStatus isValidSpawnPostitionForType(
    // Paper end - PreCreatureSpawnEvent
        final ServerLevel level,
        final MobCategory mobCategory,
        final StructureManager structureManager,
        final ChunkGenerator generator,
        final MobSpawnSettings.SpawnerData currentSpawnData,
        final BlockPos.MutableBlockPos pos,
        final double nearestPlayerDistanceSqr
    ) {
        // Paper start - PreCreatureSpawnEvent
        EntityType<?> type = currentSpawnData.type();
        // Leaf start - Skip PreCreatureSpawnEvent if no listeners
        if (com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent.getHandlerList().getRegisteredListeners().length != 0) {
        com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent event = new com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent(
                org.bukkit.craftbukkit.util.CraftLocation.toBukkit(pos, level),
                org.bukkit.craftbukkit.entity.CraftEntityType.minecraftToBukkit(type), org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL
        );
        if (!event.callEvent()) {
            if (event.shouldAbortSpawn()) {
                return PreSpawnStatus.ABORT;
            }
            return PreSpawnStatus.CANCELLED;
        }
        }
        // Leaf end - Skip PreCreatureSpawnEvent if no listeners
        final boolean success = type.getCategory() != MobCategory.MISC
        // Paper end - PreCreatureSpawnEvent
            && (type.canSpawnFarFromPlayer() || !(nearestPlayerDistanceSqr > type.getCategory().getDespawnDistance() * type.getCategory().getDespawnDistance()))
            && type.canSummon()
            && canSpawnMobAt(level, structureManager, generator, mobCategory, currentSpawnData, pos)
            && SpawnPlacements.isSpawnPositionOk(type, level, pos)
            && SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, pos, level.random)
            && level.noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        return success ? PreSpawnStatus.SUCCESS : PreSpawnStatus.FAIL; // Paper - PreCreatureSpawnEvent
    }

    private static @Nullable Mob getMobForSpawn(final ServerLevel level, final EntityType<?> type) {
        try {
            if (type.create(level, EntitySpawnReason.NATURAL) instanceof Mob mob) {
                return mob;
            }

            LOGGER.warn("Can't spawn entity of type: {}", BuiltInRegistries.ENTITY_TYPE.getKey(type));
        } catch (Exception e) {
            LOGGER.warn("Failed to create mob", e);
            com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(e); // Paper - ServerExceptionEvent
        }

        return null;
    }

    private static boolean isValidPositionForMob(final ServerLevel level, final Mob mob, final double nearestPlayerDistanceSqr) {
        return (
                !(nearestPlayerDistanceSqr > mob.getType().getCategory().getDespawnDistance() * mob.getType().getCategory().getDespawnDistance())
                    || !mob.removeWhenFarAway(nearestPlayerDistanceSqr)
            )
            && mob.checkSpawnRules(level, EntitySpawnReason.NATURAL)
            && mob.checkSpawnObstruction(level);
    }

    private static Optional<MobSpawnSettings.SpawnerData> getRandomSpawnMobAt(
        final ServerLevel level,
        final StructureManager structureManager,
        final ChunkGenerator generator,
        final MobCategory mobCategory,
        final RandomSource random,
        final BlockPos pos
    ) {
        Holder<Biome> biome = net.feathermc.feather.config.modules.opt.OptimizeBiome.mobSpawn ? level.getBiomeCached(null, pos) : level.getBiome(pos); // Leaf - cache getBiome
        return mobCategory == MobCategory.WATER_AMBIENT && biome.is(BiomeTags.REDUCED_WATER_AMBIENT_SPAWNS) && random.nextFloat() < 0.98F
            ? Optional.empty()
            : mobsAt(level, structureManager, generator, mobCategory, pos, biome).getRandom(random);
    }

    private static boolean canSpawnMobAt(
        final ServerLevel level,
        final StructureManager structureManager,
        final ChunkGenerator generator,
        final MobCategory mobCategory,
        final MobSpawnSettings.SpawnerData spawnerData,
        final BlockPos pos
    ) {
        return mobsAt(level, structureManager, generator, mobCategory, pos, null).contains(spawnerData);
    }

    private static WeightedList<MobSpawnSettings.SpawnerData> mobsAt(
        final ServerLevel level,
        final StructureManager structureManager,
        final ChunkGenerator generator,
        final MobCategory mobCategory,
        final BlockPos pos,
        final @Nullable Holder<Biome> biome
    ) {
        return isInNetherFortressBounds(pos, level, mobCategory, structureManager)
            ? NetherFortressStructure.FORTRESS_ENEMIES
            : generator.getMobsAt(biome != null ? biome : (net.feathermc.feather.config.modules.opt.OptimizeBiome.mobSpawn ? level.getBiomeCached(null, pos) : level.getBiome(pos)), structureManager, mobCategory, pos); // Leaf - cache getBiome
    }

    public static boolean isInNetherFortressBounds(
        final BlockPos pos, final ServerLevel level, final MobCategory category, final StructureManager structureManager
    ) {
        // Leaf start - optimize mob spawning
        if (category == MobCategory.MONSTER) {
            BlockState blockState = level.getBlockStateIfLoaded(pos.below());
            if (blockState == null || !blockState.is(Blocks.NETHER_BRICKS)) return false;
            // Leaf end - optimize mob spawning
            Structure fortress = structureManager.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BuiltinStructures.FORTRESS);
            return fortress != null && structureManager.getStructureAt(pos, fortress).isValid();
        } else {
            return false;
        }
    }

    private static BlockPos getRandomPosWithin(final Level level, final LevelChunk chunk) {
        // Leaf start - optimize mob spawning
        RandomSource random = level.random;
        ChunkPos pos = chunk.getPos();
        int x = pos.getMinBlockX() + random.nextInt(16);
        int z = pos.getMinBlockZ() + random.nextInt(16);
        int topEmptyY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
        int y = Mth.randomBetweenInclusive(random, level.getMinY(), topEmptyY);
        // Leaf end - optimize mob spawning
        return new BlockPos(x, y, z);
    }

    public static boolean isValidEmptySpawnBlock(
        final BlockGetter level, final BlockPos pos, final BlockState blockState, final FluidState fluidState, final EntityType<?> type
    ) {
        return !blockState.isCollisionShapeFullBlock(level, pos)
            && !blockState.isSignalSource()
            && fluidState.isEmpty()
            && !blockState.is(BlockTags.PREVENT_MOB_SPAWNING_INSIDE)
            && !type.isBlockDangerous(blockState);
    }

    public static void spawnMobsForChunkGeneration(
        final ServerLevelAccessor level, final Holder<Biome> biome, final ChunkPos chunkPos, final RandomSource random
    ) {
        MobSpawnSettings mobSettings = biome.value().getMobSettings();
        WeightedList<MobSpawnSettings.SpawnerData> mobs = mobSettings.getMobs(MobCategory.CREATURE);
        if (!mobs.isEmpty() && level.getLevel().getGameRules().get(GameRules.SPAWN_MOBS)) {
            int xo = chunkPos.getMinBlockX();
            int zo = chunkPos.getMinBlockZ();

            while (random.nextFloat() < mobSettings.getCreatureProbability()) {
                Optional<MobSpawnSettings.SpawnerData> nextSpawnerData = mobs.getRandom(random);
                if (!nextSpawnerData.isEmpty()) {
                    MobSpawnSettings.SpawnerData spawnerData = nextSpawnerData.get();
                    int count = spawnerData.minCount() + random.nextInt(1 + spawnerData.maxCount() - spawnerData.minCount());
                    SpawnGroupData groupSpawnData = null;
                    int x = xo + random.nextInt(16);
                    int z = zo + random.nextInt(16);
                    int startX = x;
                    int startZ = z;

                    for (int i = 0; i < count; i++) {
                        boolean success = false;

                        for (int attempts = 0; !success && attempts < 4; attempts++) {
                            BlockPos pos = getTopNonCollidingPos(level, spawnerData.type(), x, z);
                            if (spawnerData.type().canSummon() && SpawnPlacements.isSpawnPositionOk(spawnerData.type(), level, pos)) {
                                float width = spawnerData.type().getWidth();
                                double fx = Mth.clamp(x, (double)xo + width, xo + 16.0 - width);
                                double fz = Mth.clamp(z, (double)zo + width, zo + 16.0 - width);
                                if (!level.noCollision(spawnerData.type().getSpawnAABB(fx, pos.getY(), fz))
                                    || !SpawnPlacements.checkSpawnRules(
                                        spawnerData.type(),
                                        level,
                                        EntitySpawnReason.CHUNK_GENERATION,
                                        BlockPos.containing(fx, pos.getY(), fz),
                                        level.getRandom()
                                    )) {
                                    continue;
                                }

                                Entity entity;
                                try {
                                    entity = spawnerData.type().create(level.getLevel(), EntitySpawnReason.NATURAL);
                                } catch (Exception e) {
                                    LOGGER.warn("Failed to create mob", e);
                                    com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(e); // Paper - ServerExceptionEvent
                                    continue;
                                }

                                if (entity == null) {
                                    continue;
                                }

                                entity.snapTo(fx, pos.getY(), fz, random.nextFloat() * 360.0F, 0.0F);
                                if (entity instanceof Mob mob
                                    && mob.checkSpawnRules(level, EntitySpawnReason.CHUNK_GENERATION)
                                    && mob.checkSpawnObstruction(level)) {
                                    groupSpawnData = mob.finalizeSpawn(
                                        level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.CHUNK_GENERATION, groupSpawnData
                                    );
                                    level.addFreshEntityWithPassengers(mob, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CHUNK_GEN); // CraftBukkit
                                    success = true;
                                }
                            }

                            x += random.nextInt(5) - random.nextInt(5);

                            for (z += random.nextInt(5) - random.nextInt(5);
                                x < xo || x >= xo + 16 || z < zo || z >= zo + 16;
                                z = startZ + random.nextInt(5) - random.nextInt(5)
                            ) {
                                x = startX + random.nextInt(5) - random.nextInt(5);
                            }
                        }
                    }
                }
            }
        }
    }

    private static BlockPos getTopNonCollidingPos(final LevelReader level, final EntityType<?> type, final int x, final int z) {
        int levelHeight = level.getHeight(SpawnPlacements.getHeightmapType(type), x, z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, levelHeight, z);
        if (level.dimensionType().hasCeiling()) {
            do {
                pos.move(Direction.DOWN);
            } while (!level.getBlockState(pos).isAir());

            do {
                pos.move(Direction.DOWN);
            } while (level.getBlockState(pos).isAir() && pos.getY() > level.getMinY());
        }

        return SpawnPlacements.getPlacementType(type).adjustSpawnPosition(level, pos.immutable());
    }

    @FunctionalInterface
    public interface AfterSpawnCallback {
        void run(final Mob mob, final ChunkAccess levelChunk);
    }

    @FunctionalInterface
    public interface ChunkGetter {
        void query(final long chunkKey, Consumer<LevelChunk> output);
    }

    @FunctionalInterface
    public interface SpawnPredicate {
        boolean test(final EntityType<?> type, final BlockPos blockPos, final ChunkAccess levelChunk);
    }

    // Leaf start - optimize mob spawning
    // Copied and modified from `createState`
    public static NaturalSpawner.SpawnState createStateAsync(
        final int spawnableChunkCount, final Iterable<Entity> entities, final ServerLevel level
    ) {
        // Paper end - Optional per player mob spawns
        PotentialCalculator spawnPotential = new PotentialCalculator();
        Object2IntOpenHashMap<MobCategory> mobCounts = new Object2IntOpenHashMap<>();
        it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<int[]> chunkCap = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        boolean countAllMobsForSpawning = level.paperConfig().entities.spawning.countAllMobsForSpawning;
        for (Entity entity : entities) {
            if (!(entity instanceof Mob mob && (mob.isPersistenceRequired() || mob.requiresCustomPersistence()))) {
                MobCategory category = entity.getType().getCategory();
                if (category != MobCategory.MISC) {
                    // Paper start - Only count natural spawns
                    if (!countAllMobsForSpawning &&
                        !(entity.spawnReason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL ||
                            entity.spawnReason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CHUNK_GEN)) {
                        continue;
                    }
                    // Paper end - Only count natural spawns
                    BlockPos pos = entity.blockPosition();
                    LevelChunk chunk = level.getChunkSource().fullChunks.get(ChunkPos.pack(pos));
                    if (chunk != null) {
                        MobSpawnSettings.MobSpawnCost mobSpawnCost = getRoughBiome(pos, chunk).getMobSettings().getMobSpawnCost(entity.getType());
                        if (mobSpawnCost != null) {
                            spawnPotential.addCharge(entity.blockPosition(), mobSpawnCost.charge());
                        }

                        mobCounts.addTo(category, 1);
                        final int index = entity.getType().getCategory().ordinal();
                        ++chunkCap.computeIfAbsent(chunk.coordinateKey, k -> new int[net.minecraft.server.level.ServerPlayer.MOBCATEGORY_TOTAL_ENUMS])[index];
                    }
                }
            }
        }

        return new NaturalSpawner.SpawnState(spawnableChunkCount, mobCounts, spawnPotential, null, chunkCap);
    }

    // Copied and modified from `isValidSpawnPostitionForType`
    private static PreSpawnStatus isValidSpawnPostitionForTypeWithChunk(
        // Paper end - PreCreatureSpawnEvent
        final ServerLevel level,
        final MobCategory mobCategory,
        final StructureManager structureManager,
        final ChunkGenerator generator,
        final MobSpawnSettings.SpawnerData currentSpawnData,
        final BlockPos.MutableBlockPos pos,
        final double nearestPlayerDistanceSqr,
        final LevelChunk chunk
    ) {
        // Paper start - PreCreatureSpawnEvent
        EntityType<?> type = currentSpawnData.type();
        // Leaf start - Skip PreCreatureSpawnEvent if no listeners
        if (com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent.getHandlerList().getRegisteredListeners().length != 0) {
        com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent event = new com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent(
            org.bukkit.craftbukkit.util.CraftLocation.toBukkit(pos, level),
            org.bukkit.craftbukkit.entity.CraftEntityType.minecraftToBukkit(type), org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL
        );
        if (!event.callEvent()) {
            if (event.shouldAbortSpawn()) {
                return PreSpawnStatus.ABORT;
            }
            return PreSpawnStatus.CANCELLED;
        }
        }
        // Leaf end - Skip PreCreatureSpawnEvent if no listeners
        final boolean success = type.getCategory() != MobCategory.MISC
            // Paper end - PreCreatureSpawnEvent
            && (type.canSpawnFarFromPlayer() || !(nearestPlayerDistanceSqr > type.getCategory().getDespawnDistance() * type.getCategory().getDespawnDistance()))
            && type.canSummon()
            && mobsAtWithChunk(level, structureManager, generator, mobCategory, pos, null, chunk).contains(currentSpawnData)
            && SpawnPlacements.isSpawnPositionOk(type, level, pos)
            && SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, pos, level.random)
            && level.noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        return success ? PreSpawnStatus.SUCCESS : PreSpawnStatus.FAIL; // Paper - PreCreatureSpawnEvent
    }

    // Copied and modified from `getRandomSpawnMobAt`
    private static Optional<MobSpawnSettings.SpawnerData> getRandomSpawnMobAtWithChunk(
        final ServerLevel level,
        final StructureManager structureManager,
        final ChunkGenerator generator,
        final MobCategory mobCategory,
        final RandomSource random,
        final BlockPos pos,
        final LevelChunk chunk
    ) {
        Holder<Biome> biome = net.feathermc.feather.config.modules.opt.OptimizeBiome.mobSpawn ? level.getBiomeCached(chunk, pos) : level.getBiome(pos); // Leaf - cache getBiome
        return mobCategory == MobCategory.WATER_AMBIENT && biome.is(BiomeTags.REDUCED_WATER_AMBIENT_SPAWNS) && random.nextFloat() < 0.98F
            ? Optional.empty()
            : mobsAtWithChunk(level, structureManager, generator, mobCategory, pos, biome, chunk).getRandom(random);
    }

    // Copied and modified from `mobsAt`
    private static WeightedList<MobSpawnSettings.SpawnerData> mobsAtWithChunk(
        final ServerLevel level,
        final StructureManager structureManager,
        final ChunkGenerator generator,
        final MobCategory mobCategory,
        final BlockPos pos,
        final @Nullable Holder<Biome> biome,
        final LevelChunk chunk
    ) {
        return isInNetherFortressBoundsChunk(pos, level, mobCategory, structureManager, chunk)
            ? NetherFortressStructure.FORTRESS_ENEMIES
            : generator.getMobsAtChunk(biome != null ? biome : (net.feathermc.feather.config.modules.opt.OptimizeBiome.mobSpawn ? level.getBiomeCached(chunk, pos) : level.getBiome(pos)), structureManager, mobCategory, pos, chunk); // Leaf - cache getBiome
    }

    // Copied and modified from `isInNetherFortressBounds`
    public static boolean isInNetherFortressBoundsChunk(
        final BlockPos pos, final ServerLevel level, final MobCategory mobCategory, final StructureManager structureManager, final LevelChunk chunk
    ) {
        if (mobCategory == MobCategory.MONSTER) {
            BlockState blockState = chunk.coordinateKey == ChunkPos.pack(pos) ? chunk.getBlockStateFinal(pos.getX(), pos.getY() - 1, pos.getZ()) : level.getBlockStateIfLoaded(pos.below());
            if (blockState == null || !blockState.is(Blocks.NETHER_BRICKS)) return false;
            Structure fortress = structureManager.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BuiltinStructures.FORTRESS);
            return fortress != null && structureManager.getStructureAt(pos, fortress).isValid();
        } else {
            return false;
        }
    }
    // Leaf end - optimize mob spawning

    public static class SpawnState {
        private final int spawnableChunkCount;
        private final Object2IntOpenHashMap<MobCategory> mobCategoryCounts;
        private final PotentialCalculator spawnPotential;
        private final Object2IntMap<MobCategory> unmodifiableMobCategoryCounts;
        private final LocalMobCapCalculator localMobCapCalculator;
        private @Nullable BlockPos lastCheckedPos;
        private @Nullable EntityType<?> lastCheckedType;
        private double lastCharge;
        public final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<int[]> chunkCap; // Leaf - optimize mob spawning

        private SpawnState(
            final int spawnableChunkCount,
            final Object2IntOpenHashMap<MobCategory> mobCategoryCounts,
            final PotentialCalculator spawnPotential,
            final LocalMobCapCalculator localMobCapCalculator
            // Leaf start - optimize mob spawning
        ) {
            this(spawnableChunkCount, mobCategoryCounts, spawnPotential, localMobCapCalculator, new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>());
        }
        SpawnState(
            final int spawnableChunkCount,
            final Object2IntOpenHashMap<MobCategory> mobCategoryCounts,
            final PotentialCalculator spawnPotential,
            final LocalMobCapCalculator localMobCapCalculator,
            final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<int[]> playerCap
            // Leaf end - optimize mob spawning
        ) {
            this.spawnableChunkCount = spawnableChunkCount;
            this.mobCategoryCounts = mobCategoryCounts;
            this.spawnPotential = spawnPotential;
            this.localMobCapCalculator = localMobCapCalculator;
            this.unmodifiableMobCategoryCounts = Object2IntMaps.unmodifiable(mobCategoryCounts);
            this.chunkCap = playerCap; // Leaf - optimize mob spawning
        }

        private boolean canSpawn(final EntityType<?> type, final BlockPos testPos, final ChunkAccess chunk) {
            this.lastCheckedPos = testPos;
            this.lastCheckedType = type;
            MobSpawnSettings.MobSpawnCost mobSpawnCost = NaturalSpawner.getRoughBiome(testPos, chunk).getMobSettings().getMobSpawnCost(type);
            if (mobSpawnCost == null) {
                this.lastCharge = 0.0;
                return true;
            } else {
                double charge = mobSpawnCost.charge();
                this.lastCharge = charge;
                double energyChange = this.spawnPotential.getPotentialEnergyChange(testPos, charge);
                return energyChange <= mobSpawnCost.energyBudget();
            }
        }

        private void afterSpawn(final Mob mob, final ChunkAccess chunk) {
            EntityType<?> type = mob.getType();
            BlockPos pos = mob.blockPosition();
            double charge;
            if (pos.equals(this.lastCheckedPos) && type == this.lastCheckedType) {
                charge = this.lastCharge;
            } else {
                MobSpawnSettings.MobSpawnCost mobSpawnCost = NaturalSpawner.getRoughBiome(pos, chunk).getMobSettings().getMobSpawnCost(type);
                if (mobSpawnCost != null) {
                    charge = mobSpawnCost.charge();
                } else {
                    charge = 0.0;
                }
            }

            this.spawnPotential.addCharge(pos, charge);
            MobCategory category = type.getCategory();
            this.mobCategoryCounts.addTo(category, 1);
            if (this.localMobCapCalculator != null) this.localMobCapCalculator.addMob(ChunkPos.containing(pos), category); // Paper - Optional per player mob spawns
        }

        public int getSpawnableChunkCount() {
            return this.spawnableChunkCount;
        }

        public Object2IntMap<MobCategory> getMobCategoryCounts() {
            return this.unmodifiableMobCategoryCounts;
        }

        // CraftBukkit start
        private boolean canSpawnForCategoryGlobal(final MobCategory mobCategory, final int limit) {
            int maxMobCount = limit * this.spawnableChunkCount / NaturalSpawner.MAGIC_NUMBER;
            // CraftBukkit end
            return this.mobCategoryCounts.getInt(mobCategory) < maxMobCount;
        }

        private boolean canSpawnForCategoryLocal(final MobCategory mobCategory, final ChunkPos chunkPos) {
            return this.localMobCapCalculator.canSpawn(mobCategory, chunkPos) || SharedConstants.DEBUG_IGNORE_LOCAL_MOB_CAP;
        }

        // Leaf start - optimize mob spawning
        public void applyPerPlayerMobCount(final ServerLevel level) {
            if (chunkCap.isEmpty()) {
                return;
            }
            final var iterator = chunkCap.long2ObjectEntrySet().fastIterator();
            final ca.spottedleaf.moonrise.common.misc.NearbyPlayers nearbyPlayers = level.moonrise$getNearbyPlayers();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                long chunk = entry.getLongKey();
                int[] cap = entry.getValue();
                ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.server.level.ServerPlayer> players = nearbyPlayers.getPlayersByChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), ca.spottedleaf.moonrise.common.misc.NearbyPlayers.NearbyMapType.TICK_VIEW_DISTANCE);
                if (players == null) {
                    continue;
                }
                int playersSize = players.size();
                net.minecraft.server.level.ServerPlayer[] playersRawDataUnchecked = players.getRawDataUnchecked();
                for (int i = 0; i < playersSize; i++) {
                    int[] p = playersRawDataUnchecked[i].mobCounts;
                    for (int j = 0; j < net.minecraft.server.level.ServerPlayer.MOBCATEGORY_TOTAL_ENUMS; j++) {
                        p[j] += cap[j];
                    }
                }
            }
        }
        // Leaf end - optimize mob spawning
    }
}
