package net.minecraft.network.protocol.game;

import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;

public class ClientboundLightUpdatePacketData {
    private static final StreamCodec<ByteBuf, byte[]> DATA_LAYER_STREAM_CODEC = ByteBufCodecs.byteArray(2048);

    // Leaf start - Rewrite ClientboundLightUpdatePacketData
    // Static constants to avoid allocations
    private static final byte[][] EMPTY_ARRAY = new byte[0][];

    // Pre-sized arrays to avoid dynamic resizing
    private static final ThreadLocal<byte[][]> SKY_BUFFER = ThreadLocal.withInitial(() -> new byte[256][]);
    private static final ThreadLocal<byte[][]> BLOCK_BUFFER = ThreadLocal.withInitial(() -> new byte[256][]);

    // Pre-cached BitSets with fixed size
    private final BitSet skyYMask;
    private final BitSet blockYMask;
    private final BitSet emptySkyYMask;
    private final BitSet emptyBlockYMask;
    // Fixed arrays with exact counts
    private final byte[][] skyUpdates;
    private final byte[][] blockUpdates;
    private final int skyUpdateCount;
    private final int blockUpdateCount;
    // Leaf end - Rewrite ClientboundLightUpdatePacketData

    public ClientboundLightUpdatePacketData(
        final ChunkPos chunkPos,
        final LevelLightEngine lightEngine,
        final @Nullable BitSet skyChangedLightSectionFilter,
        final @Nullable BitSet blockChangedLightSectionFilter
    ) {
        // Leaf start - Rewrite ClientboundLightUpdatePacketData
        int sectionCount = lightEngine.getLightSectionCount();

        // Round up to nearest long boundary (64 bits) to prevent BitSet expansion
        int longWords = (sectionCount + 63) >>> 6;
        int bitSetSize = longWords << 6;

        // Pre-size all BitSets to exact size needed
        this.skyYMask = new BitSet(bitSetSize);
        this.blockYMask = new BitSet(bitSetSize);
        this.emptySkyYMask = new BitSet(bitSetSize);
        this.emptyBlockYMask = new BitSet(bitSetSize);

        // Get buffer arrays from thread local storage to avoid allocations
        byte[][] skyBuffer = SKY_BUFFER.get();
        byte[][] blockBuffer = BLOCK_BUFFER.get();

        // Process all sections in a single pass
        int skyCount = 0;
        int blockCount = 0;
        int minLightSection = lightEngine.getMinLightSection();

        // Cache layer listeners to avoid repeated method calls
        var skyLayerListener = lightEngine.getLayerListener(LightLayer.SKY);
        var blockLayerListener = lightEngine.getLayerListener(LightLayer.BLOCK);

        // Single pass through all sections
        for (int sectionIndex = 0; sectionIndex < sectionCount; sectionIndex++) {
            int sectionY = minLightSection + sectionIndex;
            SectionPos sectionPos = SectionPos.of(chunkPos.x(), sectionY, chunkPos.z());

            // Process skylight
            if (skyChangedLightSectionFilter == null || skyChangedLightSectionFilter.get(sectionIndex)) {
                DataLayer skyData = skyLayerListener.getDataLayerData(sectionPos);
                if (skyData != null) {
                    if (skyData.isEmpty()) {
                        emptySkyYMask.set(sectionIndex);
                    } else {
                        skyYMask.set(sectionIndex);
                        // Store in buffer temporarily - only clone at the end
                        skyBuffer[skyCount++] = skyData.getData();
                    }
                }
            }

            // Process block light
            if (blockChangedLightSectionFilter == null || blockChangedLightSectionFilter.get(sectionIndex)) {
                DataLayer blockData = blockLayerListener.getDataLayerData(sectionPos);
                if (blockData != null) {
                    if (blockData.isEmpty()) {
                        emptyBlockYMask.set(sectionIndex);
                    } else {
                        blockYMask.set(sectionIndex);
                        // Store in buffer temporarily - only clone at the end
                        blockBuffer[blockCount++] = blockData.getData();
                    }
                }
            }
        }

        // Create final arrays with exact sizes
        if (skyCount > 0) {
            this.skyUpdates = new byte[skyCount][];
            // Clone only at the end to minimize work
            for (int i = 0; i < skyCount; i++) {
                this.skyUpdates[i] = skyBuffer[i].clone();
            }
        } else {
            this.skyUpdates = EMPTY_ARRAY;
        }

        if (blockCount > 0) {
            this.blockUpdates = new byte[blockCount][];
            // Clone only at the end to minimize work
            for (int i = 0; i < blockCount; i++) {
                this.blockUpdates[i] = blockBuffer[i].clone();
            }
        } else {
            this.blockUpdates = EMPTY_ARRAY;
        }

        this.skyUpdateCount = skyCount;
        this.blockUpdateCount = blockCount;
        // Leaf end - Rewrite ClientboundLightUpdatePacketData
    }

    public ClientboundLightUpdatePacketData(final FriendlyByteBuf input, final int x, final int z) {
        this.skyYMask = input.readBitSet();
        this.blockYMask = input.readBitSet();
        this.emptySkyYMask = input.readBitSet();
        this.emptyBlockYMask = input.readBitSet();
        // Leaf start - Rewrite ClientboundLightUpdatePacketData
        // Read lists directly as arrays to avoid intermediate collections
        List<byte[]> skyList = input.readList(DATA_LAYER_STREAM_CODEC);
        List<byte[]> blockList = input.readList(DATA_LAYER_STREAM_CODEC);

        int skySize = skyList.size();
        int blockSize = blockList.size();

        if (skySize > 0) {
            this.skyUpdates = skyList.toArray(new byte[skySize][]);
        } else {
            this.skyUpdates = EMPTY_ARRAY;
        }

        if (blockSize > 0) {
            this.blockUpdates = blockList.toArray(new byte[blockSize][]);
        } else {
            this.blockUpdates = EMPTY_ARRAY;
        }

        this.skyUpdateCount = skySize;
        this.blockUpdateCount = blockSize;
        // Leaf end - Rewrite ClientboundLightUpdatePacketData
    }

    public void write(final FriendlyByteBuf output) {
        output.writeBitSet(this.skyYMask);
        output.writeBitSet(this.blockYMask);
        output.writeBitSet(this.emptySkyYMask);
        output.writeBitSet(this.emptyBlockYMask);
        // Leaf start - Rewrite ClientboundLightUpdatePacketData
        // Avoid creating unnecessary objects when writing
        if (this.skyUpdateCount > 0) {
            // Use direct array access for efficiency
            output.writeVarInt(this.skyUpdateCount);
            for (int i = 0; i < this.skyUpdateCount; i++) {
                DATA_LAYER_STREAM_CODEC.encode(output, this.skyUpdates[i]);
            }
        } else {
            output.writeVarInt(0);
        }

        if (this.blockUpdateCount > 0) {
            // Use direct array access for efficiency
            output.writeVarInt(this.blockUpdateCount);
            for (int i = 0; i < this.blockUpdateCount; i++) {
                DATA_LAYER_STREAM_CODEC.encode(output, this.blockUpdates[i]);
            }
        } else {
            output.writeVarInt(0);
        }
        // Leaf end - Rewrite ClientboundLightUpdatePacketData
    }

    private void prepareSectionData(
        final ChunkPos pos,
        final LevelLightEngine lightEngine,
        final LightLayer layer,
        final int sectionIndex,
        final BitSet mask,
        final BitSet emptyMask,
        final List<byte[]> updates
    ) {
        DataLayer data = lightEngine.getLayerListener(layer).getDataLayerData(SectionPos.of(pos, lightEngine.getMinLightSection() + sectionIndex));
        if (data != null) {
            if (data.isEmpty()) {
                emptyMask.set(sectionIndex);
            } else {
                mask.set(sectionIndex); // Leaf - Rewrite ClientboundLightUpdatePacketData - diff on change
                updates.add(data.copy().getData());
            }
        }
    }

    public BitSet getSkyYMask() {
        return this.skyYMask;
    }

    public BitSet getEmptySkyYMask() {
        return this.emptySkyYMask;
    }

    public List<byte[]> getSkyUpdates() {
        return this.skyUpdateCount > 0 ? java.util.Arrays.asList(this.skyUpdates) : List.of(); // Leaf - Rewrite ClientboundLightUpdatePacketData
    }

    public BitSet getBlockYMask() {
        return this.blockYMask;
    }

    public BitSet getEmptyBlockYMask() {
        return this.emptyBlockYMask;
    }

    public List<byte[]> getBlockUpdates() {
        return this.blockUpdateCount > 0 ? java.util.Arrays.asList(this.blockUpdates) : List.of(); // Leaf - Rewrite ClientboundLightUpdatePacketData
    }
}
