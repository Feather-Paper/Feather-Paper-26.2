package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.jspecify.annotations.Nullable;

public class Beardifier implements DensityFunctions.BeardifierOrMarker {
    public static final int BEARD_KERNEL_RADIUS = 12;
    private static final int BEARD_KERNEL_SIZE = 24;
    private static final float[] BEARD_KERNEL = Util.make(new float[13824], kernel -> {
        for (int zi = 0; zi < 24; zi++) {
            for (int xi = 0; xi < 24; xi++) {
                for (int yi = 0; yi < 24; yi++) {
                    kernel[zi * 24 * 24 + xi * 24 + yi] = (float)computeBeardContribution(xi - 12, yi - 12, zi - 12);
                }
            }
        }
    });
    public static final Beardifier EMPTY = new Beardifier(List.of(), List.of(), null);
    private final List<Beardifier.Rigid> pieces;
    private final List<JigsawJunction> junctions;
    private final @Nullable BoundingBox affectedBox;

    public static Beardifier forStructuresInChunk(final StructureManager structureManager, final ChunkPos chunkPos) {
        List<StructureStart> structureStarts = structureManager.startsForStructure(chunkPos, s -> s.terrainAdaptation() != TerrainAdjustment.NONE);
        if (structureStarts.isEmpty()) {
            return EMPTY;
        }

        int chunkStartBlockX = chunkPos.getMinBlockX();
        int chunkStartBlockZ = chunkPos.getMinBlockZ();
        List<Beardifier.Rigid> rigids = new ArrayList<>();
        List<JigsawJunction> junctions = new ArrayList<>();
        BoundingBox anyPieceBoundingBox = null;

        for (StructureStart start : structureStarts) {
            TerrainAdjustment terrainAdjustment = start.getStructure().terrainAdaptation();

            for (StructurePiece piece : start.getPieces()) {
                if (piece.isCloseToChunk(chunkPos, 12)) {
                    if (piece instanceof PoolElementStructurePiece poolPiece) {
                        StructureTemplatePool.Projection projection = poolPiece.getElement().getProjection();
                        if (projection == StructureTemplatePool.Projection.RIGID) {
                            rigids.add(new Beardifier.Rigid(poolPiece.getBoundingBox(), terrainAdjustment, poolPiece.getGroundLevelDelta()));
                            anyPieceBoundingBox = includeBoundingBox(anyPieceBoundingBox, piece.getBoundingBox());
                        }

                        for (JigsawJunction junction : poolPiece.getJunctions()) {
                            int junctionX = junction.getSourceX();
                            int junctionZ = junction.getSourceZ();
                            if (junctionX > chunkStartBlockX - 12
                                && junctionZ > chunkStartBlockZ - 12
                                && junctionX < chunkStartBlockX + 15 + 12
                                && junctionZ < chunkStartBlockZ + 15 + 12) {
                                junctions.add(junction);
                                BoundingBox junctionBox = new BoundingBox(new BlockPos(junctionX, junction.getSourceGroundY(), junctionZ));
                                anyPieceBoundingBox = includeBoundingBox(anyPieceBoundingBox, junctionBox);
                            }
                        }
                    } else {
                        rigids.add(new Beardifier.Rigid(piece.getBoundingBox(), terrainAdjustment, 0));
                        anyPieceBoundingBox = includeBoundingBox(anyPieceBoundingBox, piece.getBoundingBox());
                    }
                }
            }
        }

        if (anyPieceBoundingBox == null) {
            return EMPTY;
        }

        BoundingBox affectedBox = anyPieceBoundingBox.inflatedBy(24);
        return new Beardifier(List.copyOf(rigids), List.copyOf(junctions), affectedBox);
    }

    private static BoundingBox includeBoundingBox(final @Nullable BoundingBox encompassingBox, final BoundingBox newBox) {
        return encompassingBox == null ? newBox : BoundingBox.encapsulating(encompassingBox, newBox);
    }

    @VisibleForTesting
    public Beardifier(final List<Beardifier.Rigid> pieces, final List<JigsawJunction> junctions, final @Nullable BoundingBox affectedBox) {
        this.pieces = pieces;
        this.junctions = junctions;
        this.affectedBox = affectedBox;
    }

    @Override
    public void fillArray(final double[] output, final DensityFunction.ContextProvider contextProvider) {
        if (this.affectedBox == null) {
            Arrays.fill(output, 0.0);
        } else {
            DensityFunctions.BeardifierOrMarker.super.fillArray(output, contextProvider);
        }
    }

    // Leaf start - C2ME - Optimize world gen math
    private Beardifier.Rigid[] c2me$pieceArray;
    private JigsawJunction[] c2me$junctionArray;
    private void c2me$initArrays() {
        this.c2me$pieceArray = this.pieces.toArray(Beardifier.Rigid[]::new);
        this.c2me$junctionArray = this.junctions.toArray(JigsawJunction[]::new);
    }
    // Leaf end - C2ME - Optimize world gen math

    @Override
    public double compute(final DensityFunction.FunctionContext context) {
        if (this.affectedBox == null) {
            return 0.0;
        }

        // Leaf start - C2ME - Optimize world gen math
        int blockX = context.blockX();
        int blockY = context.blockY();
        int blockZ = context.blockZ();

        if (!this.affectedBox.isInside(blockX, blockY, blockZ)) return 0.0;
        if (this.c2me$pieceArray == null || this.c2me$junctionArray == null) this.c2me$initArrays();

        double density = 0.0;

        for (Beardifier.Rigid piece : this.c2me$pieceArray) {
            BoundingBox blockBox = piece.box();
            int groundLevelDelta = piece.groundLevelDelta();
            int distanceXToBox = Math.max(0, Math.max(blockBox.minX() - blockX, blockX - blockBox.maxX()));
            int distanceZToBox = Math.max(0, Math.max(blockBox.minZ() - blockZ, blockZ - blockBox.maxZ()));
            int groundY = blockBox.minY() + groundLevelDelta;
            int distanceToGround = blockY - groundY;
            density += switch (piece.terrainAdjustment()) { // 2 switch statement merged
                case NONE -> 0.0;
                case BURY -> getBuryContribution(distanceXToBox, (double) distanceToGround / 2.0, distanceZToBox);
                case BEARD_THIN -> getBeardContribution(distanceXToBox, distanceToGround, distanceZToBox, distanceToGround) * 0.8;
                case BEARD_BOX -> getBeardContribution(distanceXToBox, Math.max(0, Math.max(groundY - blockY, blockY - blockBox.maxY())), distanceZToBox, distanceToGround) * 0.8;
                case ENCAPSULATE -> getBuryContribution((double) distanceXToBox / 2.0, (double) Math.max(0, Math.max(blockBox.minY() - blockY, blockY - blockBox.maxY())) / 2.0, (double) distanceZToBox / 2.0) * 0.8;
            };
        }
        for (JigsawJunction jigsawJunction : this.c2me$junctionArray) {
            int deltaX = blockX - jigsawJunction.getSourceX();
            int deltaY = blockY - jigsawJunction.getSourceGroundY();
            int deltaZ = blockZ - jigsawJunction.getSourceZ();
            density += getBeardContribution(deltaX, deltaY, deltaZ, deltaY) * 0.4;
        }

        return density;
        // Leaf end - C2ME - Optimize world gen math
    }

    @Override
    public double minValue() {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    public double maxValue() {
        return Double.POSITIVE_INFINITY;
    }

    private static double getBuryContribution(final double dx, final double dy, final double dz) {
        // Leaf start - C2ME - Optimize world gen math
        // Optimize method for beardifier
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 6.0) {
            return 0.0;
        } else {
            return 1.0 - len / 6.0;
        }
        // Leaf end - C2ME - Optimize world gen math
    }

    private static double getBeardContribution(final int dx, final int dy, final int dz, final int yToGround) {
        int xi = dx + 12;
        int yi = dy + 12;
        int zi = dz + 12;
        if (isInKernelRange(xi) && isInKernelRange(yi) && isInKernelRange(zi)) {
            double dyWithOffset = yToGround + 0.5;
            double distanceSqr = Mth.lengthSquared(dx, dyWithOffset, dz);
            double value = -dyWithOffset * Mth.fastInvSqrt(distanceSqr / 2.0) / 2.0;
            return value * BEARD_KERNEL[zi * 24 * 24 + xi * 24 + yi];
        } else {
            return 0.0;
        }
    }

    private static boolean isInKernelRange(final int xi) {
        return xi >= 0 && xi < 24;
    }

    private static double computeBeardContribution(final int dx, final int dy, final int dz) {
        return computeBeardContribution(dx, dy + 0.5, dz);
    }

    private static double computeBeardContribution(final int dx, final double dy, final int dz) {
        double distanceSqr = Mth.lengthSquared(dx, dy, dz);
        return Math.pow(Math.E, -distanceSqr / 16.0);
    }

    @VisibleForTesting
    public record Rigid(BoundingBox box, TerrainAdjustment terrainAdjustment, int groundLevelDelta) {
    }
}
