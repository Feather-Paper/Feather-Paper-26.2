package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.Optionull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public record SerializableChunkData(
    PalettedContainerFactory containerFactory,
    ChunkPos chunkPos,
    int minSectionY,
    long lastUpdateTime,
    long inhabitedTime,
    ChunkStatus chunkStatus,
    BlendingData.@Nullable Packed blendingData,
    @Nullable BelowZeroRetrogen belowZeroRetrogen,
    UpgradeData upgradeData,
    long @Nullable [] carvingMask,
    Map<Heightmap.Types, long[]> heightmaps,
    ChunkAccess.PackedTicks packedTicks,
    @Nullable ShortList[] postProcessingSections,
    boolean lightCorrect,
    List<SerializableChunkData.SectionData> sectionData,
    List<CompoundTag> entities,
    List<CompoundTag> blockEntities,
    CompoundTag structureData
    , net.minecraft.nbt.@Nullable Tag persistentDataContainer // CraftBukkit - persistentDataContainer
    , long @Nullable [] failedSpawnAttempts // Paper PR - throttle failed spawn attempts
) {
    private static final Codec<List<SavedTick<Block>>> BLOCK_TICKS_CODEC = SavedTick.codec(BuiltInRegistries.BLOCK.byNameCodec()).listOf();
    private static final Codec<List<SavedTick<Fluid>>> FLUID_TICKS_CODEC = SavedTick.codec(BuiltInRegistries.FLUID.byNameCodec()).listOf();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_UPGRADE_DATA = "UpgradeData";
    private static final String BLOCK_TICKS_TAG = "block_ticks";
    private static final String FLUID_TICKS_TAG = "fluid_ticks";
    public static final String X_POS_TAG = "xPos";
    public static final String Z_POS_TAG = "zPos";
    public static final String HEIGHTMAPS_TAG = "Heightmaps";
    public static final String IS_LIGHT_ON_TAG = "isLightOn";
    public static final String SECTIONS_TAG = "sections";
    public static final String BLOCK_LIGHT_TAG = "BlockLight";
    public static final String SKY_LIGHT_TAG = "SkyLight";

    // Paper start - guard against serializing mismatching coordinates
    // TODO Note: This needs to be re-checked each update
    public static ChunkPos getChunkCoordinate(final CompoundTag chunkData) {
        final int dataVersion = NbtUtils.getDataVersion(chunkData);
        if (dataVersion < 2842) { // Level tag is removed after this version
            final CompoundTag levelData = chunkData.getCompoundOrEmpty("Level");
            return new ChunkPos(levelData.getIntOr("xPos", 0), levelData.getIntOr("zPos", 0));
        } else {
            return new ChunkPos(chunkData.getIntOr("xPos", 0), chunkData.getIntOr("zPos", 0));
        }
    }
    // Paper end - guard against serializing mismatching coordinates
    // Paper start - Attempt to recalculate regionfile header if it is corrupt
    // TODO: Check on update
    public static long getLastWorldSaveTime(final CompoundTag chunkData) {
        final int dataVersion = NbtUtils.getDataVersion(chunkData);
        if (dataVersion < 2842) { // Level tag is removed after this version
            final CompoundTag levelData = chunkData.getCompoundOrEmpty("Level");
            return levelData.getLongOr("LastUpdate", 0L);
        } else {
            return chunkData.getLongOr("LastUpdate", 0L);
        }
    }
    // Paper end - Attempt to recalculate regionfile header if it is corrupt

    // Paper start - Do not let the server load chunks from newer versions
    private static final int CURRENT_DATA_VERSION = net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version();
    private static final boolean JUST_CORRUPT_IT = Boolean.getBoolean("Paper.ignoreWorldDataVersion");
    // Paper end - Do not let the server load chunks from newer versions

    public static SerializableChunkData parse(
        final LevelHeightAccessor levelHeight, final PalettedContainerFactory containerFactory, final CompoundTag chunkData
    ) {
        net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) levelHeight; // Paper - Anti-Xray This is is seemingly only called from ChunkMap, where, we have a server level. We'll fight this later if needed.
        if (chunkData.getString("Status").isEmpty()) {
            return null;
        }

        // Paper start - Do not let the server load chunks from newer versions
        chunkData.getInt("DataVersion").ifPresent(dataVersion -> {
            if (!JUST_CORRUPT_IT && dataVersion > CURRENT_DATA_VERSION) {
                new RuntimeException("Server attempted to load chunk saved with newer version of minecraft! " + dataVersion + " > " + CURRENT_DATA_VERSION).printStackTrace();
                System.exit(1);
            }
        });
        // Paper end - Do not let the server load chunks from newer versions
        ChunkPos chunkPos = new ChunkPos(chunkData.getIntOr("xPos", 0), chunkData.getIntOr("zPos", 0)); // Paper - guard against serializing mismatching coordinates; diff on change, see ChunkSerializer#getChunkCoordinate
        long lastUpdateTime = chunkData.getLongOr("LastUpdate", 0L);
        long inhabitedTime = chunkData.getLongOr("InhabitedTime", 0L);
        ChunkStatus status = chunkData.read("Status", ChunkStatus.CODEC).orElse(ChunkStatus.EMPTY);
        UpgradeData upgradeData = chunkData.getCompound("UpgradeData").map(tag -> new UpgradeData(tag, levelHeight)).orElse(UpgradeData.EMPTY);
        boolean lightCorrect = status.isOrAfter(ChunkStatus.LIGHT) && (chunkData.get("isLightOn") != null && chunkData.getIntOr(ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.STARLIGHT_VERSION_TAG, -1) == ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.STARLIGHT_LIGHT_VERSION); // Paper - starlight
        BlendingData.Packed blendingData = chunkData.read("blending_data", BlendingData.Packed.CODEC).orElse(null);
        BelowZeroRetrogen belowZeroRetrogen = chunkData.read("below_zero_retrogen", BelowZeroRetrogen.CODEC).orElse(null);
        long[] carvingMask = chunkData.getLongArray("carving_mask").orElse(null);
        Map<Heightmap.Types, long[]> heightmaps = new EnumMap<>(Heightmap.Types.class);
        chunkData.getCompound("Heightmaps").ifPresent(heightmapsTag -> {
            for (Heightmap.Types type : status.heightmapsAfter()) {
                heightmapsTag.getLongArray(type.getSerializationKey()).ifPresent(longs -> heightmaps.put(type, longs));
            }
        });
        List<SavedTick<Block>> blockTicks = SavedTick.filterTickListForChunk(chunkData.read("block_ticks", BLOCK_TICKS_CODEC).orElse(List.of()), chunkPos);
        List<SavedTick<Fluid>> fluidTicks = SavedTick.filterTickListForChunk(chunkData.read("fluid_ticks", FLUID_TICKS_CODEC).orElse(List.of()), chunkPos);
        ChunkAccess.PackedTicks packedTicks = new ChunkAccess.PackedTicks(blockTicks, fluidTicks);
        ListTag postProcessTags = chunkData.getListOrEmpty("PostProcessing");
        ShortList[] postProcessingSections = new ShortList[postProcessTags.size()];

        for (int sectionIndex = 0; sectionIndex < postProcessTags.size(); sectionIndex++) {
            ListTag offsetsTag = postProcessTags.getList(sectionIndex).orElse(null);
            if (offsetsTag != null && !offsetsTag.isEmpty()) {
                ShortList packedOffsets = new ShortArrayList(offsetsTag.size());

                for (int i = 0; i < offsetsTag.size(); i++) {
                    packedOffsets.add(offsetsTag.getShortOr(i, (short)0));
                }

                postProcessingSections[sectionIndex] = packedOffsets;
            }
        }

        // Paper PR start - throttle failed spawn attempts
        long[] failedSpawnAttemptsData = null;
        if (chunkData.contains("Paper.FailedSpawnAttempts")) {
            failedSpawnAttemptsData = new long[net.minecraft.world.entity.MobCategory.values().length];
            CompoundTag failedSpawnAttemptsTag = chunkData.getCompoundOrEmpty("Paper.FailedSpawnAttempts");
            for (net.minecraft.world.entity.MobCategory mobCategory : net.minecraft.world.level.NaturalSpawner.SPAWNING_CATEGORIES) {
                if (failedSpawnAttemptsTag.contains(mobCategory.getSerializedName())) {
                    failedSpawnAttemptsData[mobCategory.ordinal()] = failedSpawnAttemptsTag.getLongOr(mobCategory.getSerializedName(), 0);
                }
            }
        }
        // Paper PR end - throttle failed spawn attempts

        List<CompoundTag> entities = chunkData.getList("entities").stream().flatMap(ListTag::compoundStream).toList();
        List<CompoundTag> blockEntities = chunkData.getList("block_entities").stream().flatMap(ListTag::compoundStream).toList();
        CompoundTag structureData = chunkData.getCompoundOrEmpty("structures");
        ListTag sectionTags = chunkData.getListOrEmpty("sections");
        List<SerializableChunkData.SectionData> sectionData = new ArrayList<>(sectionTags.size());
        Codec<PalettedContainer<Holder<Biome>>> biomesCodec = containerFactory.biomeContainerRWCodec(); // CraftBukkit - read/write
        Codec<PalettedContainer<BlockState>> blockStatesCodec = containerFactory.blockStatesContainerCodec();

        for (int i = 0; i < sectionTags.size(); i++) {
            Optional<CompoundTag> maybeSectionTag = sectionTags.getCompound(i);
            if (!maybeSectionTag.isEmpty()) {
                CompoundTag sectionTag = maybeSectionTag.get();
                int y = sectionTag.getByteOr("Y", (byte)0);
                LevelChunkSection section;
                if (y >= levelHeight.getMinSectionY() && y <= levelHeight.getMaxSectionY()) {
                    final BlockState[] presetBlockStates = serverLevel.chunkPacketBlockController.getPresetBlockStates(serverLevel, chunkPos, y); // Paper - Anti-Xray - Add preset block states
                    final Codec<PalettedContainer<BlockState>> antiXrayBlockStateCodec = presetBlockStates == null ? blockStatesCodec : PalettedContainer.codecRW(BlockState.CODEC, containerFactory.blockStatesStrategy(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), presetBlockStates); // Paper - Anti-Xray
                    PalettedContainer<BlockState> blocks = sectionTag.getCompound("block_states")
                        .map(
                            container -> antiXrayBlockStateCodec.parse(NbtOps.INSTANCE, container) // Paper - Anti-Xray
                                .promotePartial(msg -> logErrors(chunkPos, y, msg))
                                .getOrThrow(SerializableChunkData.ChunkReadException::new)
                        )
                        .orElseGet(containerFactory::createForBlockStates);
                    PalettedContainer<Holder<Biome>> biomes = sectionTag.getCompound("biomes") // CraftBukkit - read/write
                        .map(
                            container -> biomesCodec.parse(NbtOps.INSTANCE, container)
                                .promotePartial(msg -> logErrors(chunkPos, y, msg))
                                .getOrThrow(SerializableChunkData.ChunkReadException::new)
                        )
                        .orElseGet(containerFactory::createForBiomes);
                    section = new LevelChunkSection(blocks, biomes);
                } else {
                    section = null;
                }

                DataLayer blockLight = sectionTag.getByteArray("BlockLight").map(DataLayer::new).orElse(null);
                DataLayer skyLight = sectionTag.getByteArray("SkyLight").map(DataLayer::new).orElse(null);
                // Paper start - starlight
                SerializableChunkData.SectionData serializableChunkData = new SerializableChunkData.SectionData(y, section, blockLight, skyLight);
                if (sectionTag.contains(ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.BLOCKLIGHT_STATE_TAG)) {
                    ((ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData)(Object)serializableChunkData).starlight$setBlockLightState(sectionTag.getIntOr(ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.BLOCKLIGHT_STATE_TAG, 0));
                }

                if (sectionTag.contains(ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.SKYLIGHT_STATE_TAG)) {
                    ((ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData)(Object)serializableChunkData).starlight$setSkyLightState(sectionTag.getIntOr(ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.SKYLIGHT_STATE_TAG, 0));
                }
                sectionData.add(serializableChunkData);
                // Paper end - starlight
            }
        }

        return new SerializableChunkData(
            containerFactory,
            chunkPos,
            levelHeight.getMinSectionY(),
            lastUpdateTime,
            inhabitedTime,
            status,
            blendingData,
            belowZeroRetrogen,
            upgradeData,
            carvingMask,
            heightmaps,
            packedTicks,
            postProcessingSections,
            lightCorrect,
            sectionData,
            entities,
            blockEntities,
            structureData
            , chunkData.get("ChunkBukkitValues") // CraftBukkit - ChunkBukkitValues
            , failedSpawnAttemptsData // Paper PR - throttle failed spawn attempts
        );
    }

    // Paper start - starlight
    private ProtoChunk loadStarlightLightData(final ServerLevel world, final ProtoChunk ret) {

        final boolean hasSkyLight = world.dimensionType().hasSkyLight();
        final int minSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinLightSection(world);

        final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] blockNibbles = ca.spottedleaf.moonrise.patches.starlight.light.StarLightEngine.getFilledEmptyLight(world);
        final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] skyNibbles = ca.spottedleaf.moonrise.patches.starlight.light.StarLightEngine.getFilledEmptyLight(world);

        if (!this.lightCorrect) {
            ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)ret).starlight$setBlockNibbles(blockNibbles);
            ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)ret).starlight$setSkyNibbles(skyNibbles);
            return ret;
        }

        try {
            for (final SerializableChunkData.SectionData sectionData : this.sectionData) {
                final int y = sectionData.y();
                final DataLayer blockLight = sectionData.blockLight();
                final DataLayer skyLight = sectionData.skyLight();

                final int blockState = ((ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData)(Object)sectionData).starlight$getBlockLightState();
                final int skyState = ((ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData)(Object)sectionData).starlight$getSkyLightState();

                if (blockState >= 0) {
                    if (blockLight != null) {
                        blockNibbles[y - minSection] = new ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray(ca.spottedleaf.moonrise.common.util.MixinWorkarounds.clone(blockLight.getData()), blockState); // clone for data safety
                    } else {
                        blockNibbles[y - minSection] = new ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray(null, blockState);
                    }
                }

                if (skyState >= 0 && hasSkyLight) {
                    if (skyLight != null) {
                        skyNibbles[y - minSection] = new ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray(ca.spottedleaf.moonrise.common.util.MixinWorkarounds.clone(skyLight.getData()), skyState); // clone for data safety
                    } else {
                        skyNibbles[y - minSection] = new ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray(null, skyState);
                    }
                }
            }

            ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)ret).starlight$setBlockNibbles(blockNibbles);
            ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)ret).starlight$setSkyNibbles(skyNibbles);
        } catch (final Throwable thr) {
            ret.setLightCorrect(false);

            LOGGER.error("Failed to parse light data for chunk " + ret.getPos() + " in world '" + ca.spottedleaf.moonrise.common.util.WorldUtil.getWorldName(world) + "'", thr);
        }

        return ret;
    }
    // Paper end - starlight

    public ProtoChunk read(final ServerLevel level, final PoiManager poiManager, final RegionStorageInfo regionInfo, final ChunkPos pos) {
        if (!Objects.equals(pos, this.chunkPos)) {
            LOGGER.error("Chunk file at {} is in the wrong location; relocating. (Expected {}, got {})", pos, pos, this.chunkPos);
            level.getServer().reportMisplacedChunk(this.chunkPos, pos, regionInfo);
        }

        int sectionCount = level.getSectionsCount();
        LevelChunkSection[] sections = new LevelChunkSection[sectionCount];
        boolean skyLight = level.dimensionType().hasSkyLight();
        ChunkSource chunkSource = level.getChunkSource();
        LevelLightEngine lightEngine = chunkSource.getLightEngine();
        PalettedContainerFactory containerFactory = level.palettedContainerFactory();
        boolean loadedAnyLight = false;

        for (SerializableChunkData.SectionData section : this.sectionData) {
            SectionPos sectionPos = SectionPos.of(pos, section.y);
            if (section.chunkSection != null) {
                sections[level.getSectionIndexFromSectionY(section.y)] = section.chunkSection;
                //poiManager.checkConsistencyWithBlocks(sectionPos, section.chunkSection); // Paper - rewrite chunk system
            }

            boolean hasBlockLight = section.blockLight != null;
            boolean hasSkyLight = skyLight && section.skyLight != null;
            if (hasBlockLight || hasSkyLight) {
                if (!loadedAnyLight) {
                    lightEngine.retainData(pos, true);
                    loadedAnyLight = true;
                }

                if (hasBlockLight) {
                    lightEngine.queueSectionData(LightLayer.BLOCK, sectionPos, section.blockLight);
                }

                if (hasSkyLight) {
                    lightEngine.queueSectionData(LightLayer.SKY, sectionPos, section.skyLight);
                }
            }
        }

        ChunkType chunkType = this.chunkStatus.getChunkType();
        ChunkAccess chunk;
        if (chunkType == ChunkType.LEVELCHUNK) {
            LevelChunkTicks<Block> blockTicks = new LevelChunkTicks<>(this.packedTicks.blocks());
            LevelChunkTicks<Fluid> fluidTicks = new LevelChunkTicks<>(this.packedTicks.fluids());
            chunk = new LevelChunk(
                level.getLevel(),
                pos,
                this.upgradeData,
                blockTicks,
                fluidTicks,
                this.inhabitedTime,
                sections,
                postLoadChunk(level, this.entities, this.blockEntities),
                BlendingData.unpack(this.blendingData)
            );
        } else {
            ProtoChunkTicks<Block> blockTicks = ProtoChunkTicks.load(this.packedTicks.blocks());
            ProtoChunkTicks<Fluid> fluidTicks = ProtoChunkTicks.load(this.packedTicks.fluids());
            ProtoChunk protoChunk = new ProtoChunk(
                pos, this.upgradeData, sections, blockTicks, fluidTicks, level, containerFactory, BlendingData.unpack(this.blendingData)
            );
            chunk = protoChunk;
            chunk.setInhabitedTime(this.inhabitedTime);
            if (this.belowZeroRetrogen != null) {
                protoChunk.setBelowZeroRetrogen(this.belowZeroRetrogen);
            }

            protoChunk.setPersistedStatus(this.chunkStatus);
            if (this.chunkStatus.isOrAfter(ChunkStatus.INITIALIZE_LIGHT)) {
                protoChunk.setLightEngine(lightEngine);
            }
        }

        // CraftBukkit start - load chunk persistent data from nbt - SPIGOT-6814: Already load PDC here to account for 1.17 to 1.18 chunk upgrading.
        if (this.persistentDataContainer instanceof CompoundTag compoundTag) {
            chunk.persistentDataContainer.putAll(compoundTag);
        }
        // CraftBukkit end

        chunk.setLightCorrect(this.lightCorrect);
        EnumSet<Heightmap.Types> toPrime = EnumSet.noneOf(Heightmap.Types.class);

        for (Heightmap.Types type : chunk.getPersistedStatus().heightmapsAfter()) {
            long[] heightmap = this.heightmaps.get(type);
            if (heightmap != null) {
                chunk.setHeightmap(type, heightmap);
            } else {
                toPrime.add(type);
            }
        }

        Heightmap.primeHeightmaps(chunk, toPrime);
        chunk.setAllStarts(unpackStructureStart(StructurePieceSerializationContext.fromLevel(level), this.structureData, level.getSeed()));
        chunk.setAllReferences(unpackStructureReferences(level.registryAccess(), pos, this.structureData));

        for (int sectionIndex = 0; sectionIndex < this.postProcessingSections.length; sectionIndex++) {
            ShortList postProcessingSection = this.postProcessingSections[sectionIndex];
            if (postProcessingSection != null) {
                chunk.addPackedPostProcess(postProcessingSection, sectionIndex);
            }
        }

        // Paper PR start - throttle failed spawn attempts
        long[] failedSpawnAttemptsData = this.failedSpawnAttempts;
        if (failedSpawnAttemptsData != null) {
            System.arraycopy(failedSpawnAttemptsData, 0, chunk.failedSpawnAttempts, 0, failedSpawnAttemptsData.length);
        }
        // Paper PR end - throttle failed spawn attempts

        if (chunkType == ChunkType.LEVELCHUNK) {
            return this.loadStarlightLightData(level, new ImposterProtoChunk((LevelChunk)chunk, false)); // Paper - starlight
        }

        ProtoChunk protoChunk = (ProtoChunk)chunk;

        for (CompoundTag entity : this.entities) {
            protoChunk.addEntity(entity);
        }

        for (CompoundTag blockEntity : this.blockEntities) {
            protoChunk.setBlockEntityNbt(blockEntity);
        }

        if (this.carvingMask != null) {
            protoChunk.setCarvingMask(new CarvingMask(this.carvingMask, chunk.getMinY()));
        }

        return this.loadStarlightLightData(level, protoChunk); // Paper - starlight
    }

    private static void logErrors(final ChunkPos pos, final int sectionY, final String message) {
        LOGGER.error("Recoverable errors when loading section [{}, {}, {}]: {}", pos.x(), sectionY, pos.z(), message);
    }

    public static SerializableChunkData copyOf(final ServerLevel level, final ChunkAccess chunk) {
        if (!chunk.canBeSerialized()) {
            throw new IllegalArgumentException("Chunk can't be serialized: " + chunk);
        }

        ChunkPos pos = chunk.getPos();
        // Paper start - starlight
        final int minLightSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinLightSection(level);
        final int maxLightSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMaxLightSection(level);
        final int minBlockSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinSection(level);

        // Leaf start - Optimize chunkUnload
        // Pre-allocate with correct capacity to avoid resizing
        final int expectedSectionCount = maxLightSection - minLightSection + 1;
        List<SerializableChunkData.SectionData> sectionData = new ArrayList<>(expectedSectionCount);
        // Leaf end - Optimize chunkUnload

        final LevelChunkSection[] chunkSections = chunk.getSections();
        final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] blockNibbles = ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)chunk).starlight$getBlockNibbles();
        final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] skyNibbles = ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)chunk).starlight$getSkyNibbles();

        for (int lightSection = minLightSection; lightSection <= maxLightSection; ++lightSection) {
            final int lightSectionIdx = lightSection - minLightSection;
            final int blockSectionIdx = lightSection - minBlockSection;

            final LevelChunkSection chunkSection = (blockSectionIdx >= 0 && blockSectionIdx < chunkSections.length) ? chunkSections[blockSectionIdx].copy() : null;
            final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray.SaveState blockNibble = blockNibbles[lightSectionIdx].getSaveState();
            final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray.SaveState skyNibble = skyNibbles[lightSectionIdx].getSaveState();

            if (chunkSection == null && blockNibble == null && skyNibble == null) {
                continue;
            }

            // Leaf start - Optimize chunkUnload
            DataLayer blockDataLayer = null;
            if (blockNibble != null && blockNibble.data != null) {
                blockDataLayer = new DataLayer(blockNibble.data);
            }

            DataLayer skyDataLayer = null;
            if (skyNibble != null && skyNibble.data != null) {
                skyDataLayer = new DataLayer(skyNibble.data);
            }
            // Leaf end - Optimize chunkUnload

            final SerializableChunkData.SectionData section = new SerializableChunkData.SectionData(
                lightSection, chunkSection, blockDataLayer, skyDataLayer // Leaf - Optimize chunkUnload
            );

            if (blockNibble != null) {
                ((ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData)(Object)section).starlight$setBlockLightState(blockNibble.state);
            }

            if (skyNibble != null) {
                ((ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData)(Object)section).starlight$setSkyLightState(skyNibble.state);
            }

            sectionData.add(section); // Leaf - Optimize chunkUnload - diff on change
        }
        // Paper end - starlight

        // Leaf start - Optimize chunkUnload
        // Pre-allocate block entities list with exact size needed
        final int blockEntityCount = chunk.getBlockEntitiesPos().size();
        List<CompoundTag> blockEntities = blockEntityCount > 0 ? new ArrayList<>(blockEntityCount) : java.util.Collections.emptyList();

        if (blockEntityCount > 0) // Leaf - Optimize chunkUnload
        for (BlockPos blockPos : chunk.getBlockEntitiesPos()) {
            CompoundTag blockEntityTag = chunk.getBlockEntityNbtForSaving(blockPos, level.registryAccess());
            if (blockEntityTag != null) {
                blockEntities.add(blockEntityTag);
            }
        }

        // Leaf start - Optimize chunkUnload
        // For entities, use an initial estimated capacity if it's a ProtoChunk
        List<CompoundTag> entities;
        long[] carvingMask = null;
        if (chunk.getPersistedStatus().getChunkType() == ChunkType.PROTOCHUNK) {
            ProtoChunk protoChunk = (ProtoChunk)chunk;
            // Leaf start - Optimize chunkUnload
            int entitySize = protoChunk.getEntities().size();
            if (entitySize > 0) {
                entities = new ArrayList<>(Math.max(16, entitySize));
                entities.addAll(protoChunk.getEntities());
            } else {
                entities = java.util.Collections.emptyList();
            }
            // Leaf end - Optimize chunkUnload
            CarvingMask existingMask = protoChunk.getCarvingMask();
            if (existingMask != null) {
                carvingMask = existingMask.toArray();
            }
        } else {
            entities = java.util.Collections.emptyList(); // Leaf - Optimize chunkUnload
        }

        Map<Heightmap.Types, long[]> heightmaps = new EnumMap<>(Heightmap.Types.class);

        for (Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (chunk.getPersistedStatus().heightmapsAfter().contains(entry.getKey())) {
                long[] data = entry.getValue().getRawData();
                heightmaps.put(entry.getKey(), Arrays.copyOf(data, data.length)); // Leaf - Optimize chunkUnload
            }
        }

        ChunkAccess.PackedTicks ticksForSerialization = chunk.getTicksForSerialization(level.getGameTime());
        // Leaf start - Optimize chunkUnload - remove stream
        ShortList[] postProcessing = chunk.getPostProcessing();
        ShortList[] postProcessingSections = new ShortList[postProcessing.length];
        for (int i = 0; i < postProcessing.length; i++) {
            ShortList shorts = postProcessing[i];
            if (shorts != null && !shorts.isEmpty()) {
                postProcessingSections[i] = new ShortArrayList(shorts);
            }
        }
        // Leaf end - Optimize chunkUnload - remove stream
        CompoundTag structureData = packStructureData(StructurePieceSerializationContext.fromLevel(level), pos, chunk.getAllStarts(), chunk.getAllReferences());
        // CraftBukkit start - store chunk persistent data in nbt
        CompoundTag persistentDataContainer = null;
        if (!chunk.persistentDataContainer.isEmpty()) { // SPIGOT-6814: Always save PDC to account for 1.17 to 1.18 chunk upgrading.
            persistentDataContainer = chunk.persistentDataContainer.toTagCompound();
        }
        // CraftBukkit end
        final long[] failedSpawnAttemptsData = chunk.failedSpawnAttempts; // Paper PR - throttle failed spawn attempts
        return new SerializableChunkData(
            level.palettedContainerFactory(),
            pos,
            chunk.getMinSectionY(),
            level.getGameTime(),
            chunk.getInhabitedTime(),
            chunk.getPersistedStatus(),
            Optionull.map(chunk.getBlendingData(), BlendingData::pack),
            chunk.getBelowZeroRetrogen(),
            chunk.getUpgradeData().copy(),
            carvingMask,
            heightmaps,
            ticksForSerialization,
            postProcessingSections,
            chunk.isLightCorrect(),
            sectionData,
            entities,
            blockEntities,
            structureData
            , persistentDataContainer // CraftBukkit - persistentDataContainer
            , failedSpawnAttemptsData // Paper PR - throttle failed spawn attempts
        );
    }

    public CompoundTag write() {
        CompoundTag tag = NbtUtils.addCurrentDataVersion(new CompoundTag());
        tag.putInt("xPos", this.chunkPos.x());
        tag.putInt("yPos", this.minSectionY);
        tag.putInt("zPos", this.chunkPos.z());
        tag.putLong("LastUpdate", this.lastUpdateTime); // Paper - Diff on change
        tag.putLong("InhabitedTime", this.inhabitedTime);
        tag.putString("Status", BuiltInRegistries.CHUNK_STATUS.getKey(this.chunkStatus).toString());
        tag.storeNullable("blending_data", BlendingData.Packed.CODEC, this.blendingData);
        tag.storeNullable("below_zero_retrogen", BelowZeroRetrogen.CODEC, this.belowZeroRetrogen);
        if (!this.upgradeData.isEmpty()) {
            tag.put("UpgradeData", this.upgradeData.write());
        }

        ListTag sectionTags = new ListTag();
        Codec<PalettedContainer<BlockState>> blockStatesCodec = this.containerFactory.blockStatesContainerCodec();
        Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = this.containerFactory.biomeContainerCodec();

        for (SerializableChunkData.SectionData section : this.sectionData) {
            CompoundTag sectionTag = new CompoundTag();
            LevelChunkSection chunkSection = section.chunkSection;
            if (chunkSection != null) {
                sectionTag.store("block_states", blockStatesCodec, chunkSection.getStates());
                sectionTag.store("biomes", biomeCodec, chunkSection.getBiomes());
            }

            if (section.blockLight != null) {
                sectionTag.putByteArray("BlockLight", section.blockLight.getData());
            }

            if (section.skyLight != null) {
                sectionTag.putByteArray("SkyLight", section.skyLight.getData());
            }

            // Paper start - starlight
            final int blockState = ((ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData)(Object)section).starlight$getBlockLightState();
            final int skyState = ((ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData)(Object)section).starlight$getSkyLightState();

            if (blockState > 0) {
                sectionTag.putInt(ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.BLOCKLIGHT_STATE_TAG, blockState);
            }

            if (skyState > 0) {
                sectionTag.putInt(ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.SKYLIGHT_STATE_TAG, skyState);
            }
            // Paper end - starlight

            if (!sectionTag.isEmpty()) {
                sectionTag.putByte("Y", (byte)section.y);
                sectionTags.add(sectionTag);
            }
        }

        tag.put("sections", sectionTags);
        if (this.lightCorrect) {
            tag.putBoolean("isLightOn", true);
        }

        ListTag blockEntityTags = new ListTag();
        blockEntityTags.addAll(this.blockEntities);
        tag.put("block_entities", blockEntityTags);
        if (this.chunkStatus.getChunkType() == ChunkType.PROTOCHUNK) {
            ListTag entityTags = new ListTag();
            entityTags.addAll(this.entities);
            tag.put("entities", entityTags);
            if (this.carvingMask != null) {
                tag.putLongArray("carving_mask", this.carvingMask);
            }
        }

        saveTicks(tag, this.packedTicks);
        tag.put("PostProcessing", packOffsets(this.postProcessingSections));
        CompoundTag heightmapsTag = new CompoundTag();
        this.heightmaps.forEach((type, data) -> heightmapsTag.put(type.getSerializationKey(), new LongArrayTag(data)));
        tag.put("Heightmaps", heightmapsTag);
        tag.put("structures", this.structureData);
        // CraftBukkit start - store chunk persistent data in nbt
        if (this.persistentDataContainer != null) { // SPIGOT-6814: Always save PDC to account for 1.17 to 1.18 chunk upgrading.
            tag.put("ChunkBukkitValues", this.persistentDataContainer);
        }
        // CraftBukkit end
        // Paper PR start - throttle failed spawn attempts
        CompoundTag failedSpawnAttemptsTag = new CompoundTag();
        long[] failedSpawnAttemptsData = this.failedSpawnAttempts;
        if (failedSpawnAttemptsData != null) {
            for (net.minecraft.world.entity.MobCategory mobCategory : net.minecraft.world.entity.MobCategory.values()) {
                long failedAttempts = failedSpawnAttemptsData[mobCategory.ordinal()];
                if (failedAttempts > 0) {
                    failedSpawnAttemptsTag.putLong(mobCategory.getSerializedName(), failedAttempts);
                }
            }
        }
        if (!failedSpawnAttemptsTag.isEmpty()) {
            tag.put("Paper.FailedSpawnAttempts", failedSpawnAttemptsTag);
        }
        // Paper PR end - throttle failed spawn attempts
        // Paper start - starlight
        if (this.lightCorrect && !this.chunkStatus.isBefore(net.minecraft.world.level.chunk.status.ChunkStatus.LIGHT)) {
            // clobber vanilla value to force vanilla to relight
            tag.putBoolean("isLightOn", false);
            // store our light version
            tag.putInt(ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.STARLIGHT_VERSION_TAG, ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.STARLIGHT_LIGHT_VERSION);
        }
        // Paper end - starlight
        return tag;
    }

    private static void saveTicks(final CompoundTag levelData, final ChunkAccess.PackedTicks ticksForSerialization) {
        levelData.store("block_ticks", BLOCK_TICKS_CODEC, ticksForSerialization.blocks());
        levelData.store("fluid_ticks", FLUID_TICKS_CODEC, ticksForSerialization.fluids());
    }

    public static ChunkStatus getChunkStatusFromTag(final @Nullable CompoundTag tag) {
        return tag != null ? tag.read("Status", ChunkStatus.CODEC).orElse(ChunkStatus.EMPTY) : ChunkStatus.EMPTY;
    }

    private static LevelChunk.@Nullable PostLoadProcessor postLoadChunk(
        final ServerLevel level, final List<CompoundTag> entities, final List<CompoundTag> blockEntities
    ) {
        return entities.isEmpty() && blockEntities.isEmpty()
            ? null
            : levelChunk -> {
                if (!entities.isEmpty()) {
                    try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(levelChunk.problemPath(), LOGGER)) {
                        level.addLegacyChunkEntities(
                            EntityType.loadEntitiesRecursive(TagValueInput.create(reporter, level.registryAccess(), entities), level, EntitySpawnReason.LOAD)
                        );
                    }
                }

                for (CompoundTag entityTag : blockEntities) {
                    boolean keepPacked = entityTag.getBooleanOr("keepPacked", false);
                    if (keepPacked) {
                        levelChunk.setBlockEntityNbt(entityTag);
                    } else {
                        BlockPos pos = BlockEntity.getPosFromTag(levelChunk.getPos(), entityTag);
                        BlockEntity blockEntity = BlockEntity.loadStatic(pos, levelChunk.getBlockState(pos), entityTag, level.registryAccess());
                        if (blockEntity != null) {
                            levelChunk.setBlockEntity(blockEntity);
                        }
                    }
                }
            };
    }

    private static CompoundTag packStructureData(
        final StructurePieceSerializationContext context,
        final ChunkPos pos,
        final Map<Structure, StructureStart> starts,
        final Map<Structure, LongSet> references
    ) {
        CompoundTag outTag = new CompoundTag();
        CompoundTag startsTag = new CompoundTag();
        Registry<Structure> structuresRegistry = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);

        for (Entry<Structure, StructureStart> entry : starts.entrySet()) {
            Identifier key = structuresRegistry.getKey(entry.getKey());
            startsTag.put(key.toString(), entry.getValue().createTag(context, pos));
        }

        outTag.put("starts", startsTag);
        CompoundTag referencesTag = new CompoundTag();

        for (Entry<Structure, LongSet> entry : references.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                Identifier key = structuresRegistry.getKey(entry.getKey());
                referencesTag.putLongArray(key.toString(), entry.getValue().toLongArray());
            }
        }

        outTag.put("References", referencesTag);
        return outTag;
    }

    private static Map<Structure, StructureStart> unpackStructureStart(final StructurePieceSerializationContext context, final CompoundTag tag, final long seed) {
        Map<Structure, StructureStart> outmap = Maps.newHashMap();
        Registry<Structure> structuresRegistry = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        CompoundTag startsTag = tag.getCompoundOrEmpty("starts");

        for (String key : startsTag.keySet()) {
            Identifier id = Identifier.tryParse(key);
            Structure startFeature = structuresRegistry.getValue(id);
            if (startFeature == null) {
                LOGGER.error("Unknown structure start: {}", id);
            } else {
                StructureStart start = StructureStart.loadStaticStart(context, startsTag.getCompoundOrEmpty(key), seed);
                if (start != null) {
                    // CraftBukkit start - load persistent data for structure start
                    net.minecraft.nbt.Tag persistentBase = startsTag.getCompoundOrEmpty(key).get("StructureBukkitValues");
                    if (persistentBase instanceof CompoundTag compoundTag) {
                        start.persistentDataContainer.putAll(compoundTag);
                    }
                    // CraftBukkit end
                    outmap.put(startFeature, start);
                }
            }
        }

        return outmap;
    }

    private static Map<Structure, LongSet> unpackStructureReferences(final RegistryAccess registryAccess, final ChunkPos pos, final CompoundTag tag) {
        Map<Structure, LongSet> outmap = Maps.newHashMap();
        Registry<Structure> structuresRegistry = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        CompoundTag referencesTag = tag.getCompoundOrEmpty("References");
        referencesTag.forEach((key, entry) -> {
            Identifier structureId = Identifier.tryParse(key);
            Structure structureType = structuresRegistry.getValue(structureId);
            if (structureType == null) {
                LOGGER.warn("Found reference to unknown structure '{}' in chunk {}, discarding", structureId, pos);
            } else {
                Optional<long[]> longArray = entry.asLongArray();
                if (!longArray.isEmpty()) {
                    outmap.put(structureType, new LongOpenHashSet(Arrays.stream(longArray.get()).filter(chunkLongPos -> {
                        ChunkPos refPos = ChunkPos.unpack(chunkLongPos);
                        if (refPos.getChessboardDistance(pos) > 8) {
                            LOGGER.warn("Found invalid structure reference [ {} @ {} ] for chunk {}.", structureId, refPos, pos);
                            return false;
                        } else {
                            return true;
                        }
                    }).toArray()));
                }
            }
        });
        return outmap;
    }

    private static ListTag packOffsets(final @Nullable ShortList[] sections) {
        ListTag listTag = new ListTag();

        for (ShortList offsetList : sections) {
            ListTag offsetsTag = new ListTag();
            if (offsetList != null) {
                for (int i = 0; i < offsetList.size(); i++) {
                    offsetsTag.add(ShortTag.valueOf(offsetList.getShort(i)));
                }
            }

            listTag.add(offsetsTag);
        }

        return listTag;
    }

    public static class ChunkReadException extends NbtException {
        public ChunkReadException(final String message) {
            super(message);
        }
    }

    // Paper start - starlight - convert from record
    public static final class SectionData implements ca.spottedleaf.moonrise.patches.starlight.storage.StarlightSectionData { // Paper - starlight - our diff
        private final int y;
        private final @Nullable LevelChunkSection chunkSection;
        private final @Nullable DataLayer blockLight;
        private final @Nullable DataLayer skyLight;

        // Paper start - starlight - our diff
        private int blockLightState = -1;
        private int skyLightState = -1;

        @Override
        public final int starlight$getBlockLightState() {
            return this.blockLightState;
        }

        @Override
        public final void starlight$setBlockLightState(final int state) {
            this.blockLightState = state;
        }

        @Override
        public final int starlight$getSkyLightState() {
            return this.skyLightState;
        }

        @Override
        public final void starlight$setSkyLightState(final int state) {
            this.skyLightState = state;
        }
        // Paper end - starlight - our diff

        public SectionData(int y, @Nullable LevelChunkSection chunkSection, @Nullable DataLayer blockLight, @Nullable DataLayer skyLight) {
            this.y = y;
            this.chunkSection = chunkSection;
            this.blockLight = blockLight;
            this.skyLight = skyLight;
        }

        public int y() {
            return this.y;
        }

        public @Nullable LevelChunkSection chunkSection() {
            return this.chunkSection;
        }

        public @Nullable DataLayer blockLight() {
            return this.blockLight;
        }

        public @Nullable DataLayer skyLight() {
            return this.skyLight;
        }
        // Paper end - starlight - convert from record
    }

    // Paper PR start - throttle failed spawn attempts
    // For plugin compatibility
    public SerializableChunkData(
        final PalettedContainerFactory containerFactory,
        final ChunkPos chunkPos,
        final int minSectionY,
        final long lastUpdateTime,
        final long inhabitedTime,
        final ChunkStatus chunkStatus,
        final BlendingData.@Nullable Packed blendingData,
        final @Nullable BelowZeroRetrogen belowZeroRetrogen,
        final UpgradeData upgradeData,
        final long @Nullable [] carvingMask,
        final Map<Heightmap.Types, long[]> heightmaps,
        final ChunkAccess.PackedTicks packedTicks,
        final @Nullable ShortList[] postProcessingSections,
        final boolean lightCorrect,
        final List<SerializableChunkData.SectionData> sectionData,
        final List<CompoundTag> entities,
        final List<CompoundTag> blockEntities,
        final CompoundTag structureData
        , final net.minecraft.nbt.@Nullable Tag persistentDataContainer // CraftBukkit - persistentDataContainer
    ) {
        this(containerFactory,
            chunkPos,
            minSectionY,
            lastUpdateTime,
            inhabitedTime,
            chunkStatus,
            blendingData,
            belowZeroRetrogen,
            upgradeData,
            carvingMask,
            heightmaps,
            packedTicks,
            postProcessingSections,
            lightCorrect,
            sectionData,
            entities,
            blockEntities,
            structureData,
            persistentDataContainer,
            null);
    }
    // Paper PR end - throttle failed spawn attempts
}
