package net.minecraft.world.level.biome;

import com.google.common.hash.Hashing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.util.Mth;

public class BiomeManager {
    public static final int CHUNK_CENTER_QUART = QuartPos.fromBlock(8);
    private static final int ZOOM_BITS = 2;
    private static final int ZOOM = 4;
    private static final int ZOOM_MASK = 3;
    private final BiomeManager.NoiseBiomeSource noiseBiomeSource;
    private final long biomeZoomSeed;
    private static final double MAX_OFFSET = 0.4500000001D; // Leaf - Carpet-Fixes - Optimized getBiome method
    private static final double[] QUART_OFFSETS = new double[]{0.0, 0.25, 0.5, 0.75}; // Leaf - Optimized getBiome method - eliminate divisions
    // Leaf start - cache getBiome
    private final Holder<Biome>[] biomeCache;
    private final long[] biomeCachePos;
    // Leaf end - cache getBiome

    public BiomeManager(final BiomeManager.NoiseBiomeSource noiseBiomeSource, final long seed) {
        this.noiseBiomeSource = noiseBiomeSource;
        this.biomeZoomSeed = seed;
        // Leaf start - cache getBiome
        if (net.feathermc.feather.config.modules.opt.OptimizeBiome.enabled && noiseBiomeSource instanceof net.minecraft.world.level.Level) {
            biomeCache = new Holder[65536];
            biomeCachePos = new long[65536];
        } else {
            biomeCache = null;
            biomeCachePos = null;
        }
        // Leaf end - cache getBiome
    }

    // Leaf start - Replace SHA-256 with XXHash
    private static final net.jpountz.xxhash.XXHash64 XXHASH = net.jpountz.xxhash.XXHashFactory.fastestInstance().hash64();
    public static long obfuscateSeed(final long seed) {
        if (net.feathermc.feather.config.modules.opt.FastBiomeManagerSeedObfuscation.enabled) {
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(8);
            buf.putLong(0, seed);
            return XXHASH.hash(buf, net.feathermc.feather.config.modules.opt.FastBiomeManagerSeedObfuscation.seedObfuscationKey);
        } else {
            return Hashing.sha256().hashLong(seed).asLong();
        }
        // Leaf end - Replace SHA-256 with XXHash
    }

    public BiomeManager withDifferentSource(final BiomeManager.NoiseBiomeSource biomeSource) {
        return new BiomeManager(biomeSource, this.biomeZoomSeed);
    }

    public Holder<Biome> getBiome(final BlockPos pos) {
        return getBiome(null, pos.getX() - 2, pos.getY() - 2, pos.getZ() - 2); // Leaf - cache getBiome
    }
    // Leaf start - cache getBiome
    public Holder<Biome> getBiomeCached(final net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable LevelChunk chunk, final BlockPos pos) {
        if (biomeCache == null) {
            return getBiome(pos);
        }
        int xMinus2 = pos.getX() - 2;
        int yMinus2 = pos.getY() - 2;
        int zMinus2 = pos.getZ() - 2;
        long packedPos = BlockPos.asLong(xMinus2 >> 2, yMinus2 >> 2, zMinus2 >> 2);
        int hash = (int) (it.unimi.dsi.fastutil.HashCommon.mix(packedPos) & 65535L);
        if (biomeCachePos[hash] == packedPos) {
            Holder<Biome> biome = biomeCache[hash];
            if (biome != null) {
                return biome;
            }
        }

        Holder<Biome> biome = getBiome(chunk, xMinus2, yMinus2, zMinus2);

        biomeCache[hash] = biome;
        biomeCachePos[hash] = packedPos;

        return biome;
    }
    private Holder<Biome> getBiome(final net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable LevelChunk chunk, final int xMinus2, final int yMinus2, final int zMinus2) {
        // Leaf start - Carpet-Fixes - Optimized getBiome method
        int x = xMinus2 >> 2; // BlockPos to BiomePos
        int y = yMinus2 >> 2;
        int z = zMinus2 >> 2;
        // Leaf start - Optimized getBiome method - eliminate divisions
        // any integer & 3 falls in [0,1,2,3]
        // then the division by 4 result is deterministic [0, 0.25, 0.5, 0.75]
        double quartX = QUART_OFFSETS[xMinus2 & 3];
        double quartY = QUART_OFFSETS[yMinus2 & 3];
        double quartZ = QUART_OFFSETS[zMinus2 & 3];
        // Leaf end - Optimized getBiome method - eliminate divisions
        int smallestX = 0;
        double smallestDist = Double.POSITIVE_INFINITY;
        for (int biomeX = 0; biomeX < 8; biomeX++) {
            boolean everyOtherQuad = (biomeX & 4) == 0; // 1 1 1 1 0 0 0 0
            boolean everyOtherPair = (biomeX & 2) == 0; // 1 1 0 0 1 1 0 0
            boolean everyOther = (biomeX & 1) == 0; // 1 0 1 0 1 0 1 0
            double quartXX = everyOtherQuad ? quartX : quartX - 1.0; //[-1.0,-0.75,-0.5,-0.25,0.0,0.25,0.5,0.75]
            double quartYY = everyOtherPair ? quartY : quartY - 1.0;
            double quartZZ = everyOther ? quartZ : quartZ - 1.0;

            //This code block is new
            double maxQuartYY = 0.0, maxQuartZZ = 0.0;
            if (biomeX != 0) {
                maxQuartYY = Mth.square(Math.max(quartYY + MAX_OFFSET, Math.abs(quartYY - MAX_OFFSET)));
                maxQuartZZ = Mth.square(Math.max(quartZZ + MAX_OFFSET, Math.abs(quartZZ - MAX_OFFSET)));
                double maxQuartXX = Mth.square(Math.max(quartXX + MAX_OFFSET, Math.abs(quartXX - MAX_OFFSET)));
                if (smallestDist < maxQuartXX + maxQuartYY + maxQuartZZ) continue;
            }
            int xx = everyOtherQuad ? x : x + 1;
            int yy = everyOtherPair ? y : y + 1;
            int zz = everyOther ? z : z + 1;

            //I transferred the code from method_38106 to here, so I could call continue halfway through
            long seed = LinearCongruentialGenerator.next(this.biomeZoomSeed, xx);
            seed = LinearCongruentialGenerator.next(seed, yy);
            seed = LinearCongruentialGenerator.next(seed, zz);
            seed = LinearCongruentialGenerator.next(seed, xx);
            seed = LinearCongruentialGenerator.next(seed, yy);
            seed = LinearCongruentialGenerator.next(seed, zz);
            double offsetX = getFiddle(seed);
            double sqrX = Mth.square(quartXX + offsetX);
            if (biomeX != 0 && smallestDist < sqrX + maxQuartYY + maxQuartZZ) continue; //skip the rest of the loop
            seed = LinearCongruentialGenerator.next(seed, this.biomeZoomSeed);
            double offsetY = getFiddle(seed);
            double sqrY = Mth.square(quartYY + offsetY);
            if (biomeX != 0 && smallestDist < sqrX + sqrY + maxQuartZZ) continue; // skip the rest of the loop
            seed = LinearCongruentialGenerator.next(seed, this.biomeZoomSeed);
            double offsetZ = getFiddle(seed);
            double biomeDist = sqrX + sqrY + Mth.square(quartZZ + offsetZ);

            if (smallestDist > biomeDist) {
                smallestX = biomeX;
                smallestDist = biomeDist;
            }
        }
        int x1 = (smallestX & 4) == 0 ? x : x + 1;
        int y1 = (smallestX & 2) == 0 ? y : y + 1;
        int z1 = (smallestX & 1) == 0 ? z : z + 1;
        if (chunk != null && chunk.locX == x1 >> 2 && chunk.locZ == z1 >> 2) {
            return chunk.getNoiseBiome(x1, y1, z1);
        }
        return this.noiseBiomeSource.getNoiseBiome(x1, y1, z1);
        // Leaf end - Carpet-Fixes - Optimized getBiome method
    }
    // Leaf end - cache getBiome

    public Holder<Biome> getNoiseBiomeAtPosition(final double x, final double y, final double z) {
        int quartX = QuartPos.fromBlock(Mth.floor(x));
        int quartY = QuartPos.fromBlock(Mth.floor(y));
        int quartZ = QuartPos.fromBlock(Mth.floor(z));
        return this.getNoiseBiomeAtQuart(quartX, quartY, quartZ);
    }

    public Holder<Biome> getNoiseBiomeAtPosition(final BlockPos blockPos) {
        int quartX = QuartPos.fromBlock(blockPos.getX());
        int quartY = QuartPos.fromBlock(blockPos.getY());
        int quartZ = QuartPos.fromBlock(blockPos.getZ());
        return this.getNoiseBiomeAtQuart(quartX, quartY, quartZ);
    }

    public Holder<Biome> getNoiseBiomeAtQuart(final int quartX, final int quartY, final int quartZ) {
        return this.noiseBiomeSource.getNoiseBiome(quartX, quartY, quartZ);
    }

    private static double getFiddledDistance(
        final long seed, final int xRandom, final int yRandom, final int zRandom, final double distanceX, final double distanceY, final double distanceZ
    ) {
        long rval = seed;
        rval = LinearCongruentialGenerator.next(rval, xRandom);
        rval = LinearCongruentialGenerator.next(rval, yRandom);
        rval = LinearCongruentialGenerator.next(rval, zRandom);
        rval = LinearCongruentialGenerator.next(rval, xRandom);
        rval = LinearCongruentialGenerator.next(rval, yRandom);
        rval = LinearCongruentialGenerator.next(rval, zRandom);
        double fiddleX = getFiddle(rval);
        rval = LinearCongruentialGenerator.next(rval, seed);
        double fiddleY = getFiddle(rval);
        rval = LinearCongruentialGenerator.next(rval, seed);
        double fiddleZ = getFiddle(rval);
        return Mth.square(distanceZ + fiddleZ) + Mth.square(distanceY + fiddleY) + Mth.square(distanceX + fiddleX);
    }

    // Leaf start - optimise getBiome
    private static final double[] FIDDLE_TABLE = new double[1024];
    static {
        for (int i = 0; i < 1024; i++) {
            FIDDLE_TABLE[i] = (i - 512) * (0.9 / 1024.0);
        }
    }
    private static double getFiddle(final long rval) {
        return FIDDLE_TABLE[(int)(rval >>> 24) & 1023]; // Paper - avoid floorMod, fp division, and fp subtraction // Leaf - optimise getBiome
    }

    public interface NoiseBiomeSource {
        Holder<Biome> getNoiseBiome(final int quartX, final int quartY, final int quartZ);
    }
}
