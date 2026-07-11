package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CactusBlock extends Block implements BonemealableBlock { // Purpur - bonemealable cactus
    public static final MapCodec<CactusBlock> CODEC = simpleCodec(CactusBlock::new);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;
    public static final int MAX_AGE = 15;
    private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 16.0);
    private static final VoxelShape SHAPE_COLLISION = Block.column(14.0, 0.0, 15.0);
    private static final int MAX_CACTUS_GROWING_HEIGHT = 3;
    private static final int ATTEMPT_GROW_CACTUS_FLOWER_AGE = 8;
    private static final double ATTEMPT_GROW_CACTUS_FLOWER_SMALL_CACTUS_CHANCE = 0.1;
    private static final double ATTEMPT_GROW_CACTUS_FLOWER_TALL_CACTUS_CHANCE = 0.25;

    @Override
    public MapCodec<CactusBlock> codec() {
        return CODEC;
    }

    protected CactusBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void tick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected void randomTick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
        // Pluto start - Decrease chunk/block lookups
        final int x = pos.getX();
        final int z = pos.getZ();
        net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkIfLoaded(x >> 4, z >> 4);
        if (chunk == null) return;

        final int y = pos.getY();
        if (chunk.getBlockState(x, y + 1, z).isAir()) {
            // Pluto end - Decrease chunk/block lookups
            int height = 1;
            int age = state.getValue(AGE);

            while (chunk.getBlockState(pos.below(height)).is(this)) { // Pluto - Decrease chunk/block lookups
                if (++height == level.paperConfig().maxGrowthHeight.cactus && age == 15) { // Paper - Configurable cactus/bamboo/reed growth height
                    return;
                }
            }

            // Pluto start - Decrease chunk/block lookups
            BlockPos above = null;
            if (age == 8 && this.canSurvive(this.defaultBlockState(), level, above = pos.above())) {
                // Pluto end - Decrease chunk/block lookups
                double chanceToGrowFlower = height >= level.paperConfig().maxGrowthHeight.cactus ? 0.25 : 0.1; // Paper - Configurable cactus/bamboo/reed growth height
                if (random.nextDouble() <= chanceToGrowFlower) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, above, Blocks.CACTUS_FLOWER.defaultBlockState(), Block.UPDATE_ALL); // Paper - block grow event
                }
            } else if (age == 15 && height < level.paperConfig().maxGrowthHeight.cactus) { // Paper - Configurable cactus/bamboo/reed growth height
                // Pluto start - Check if the cactus can even survive being placed
                if (net.feathermc.feather.config.modules.opt.CheckSurvivalBeforeGrowth.cactusCheckSurvivalBeforeGrowth && !canSurvive(level, above = pos.above())) { // Pluto - Decrease chunk/block lookups
                    level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, above, Block.getId(state));
                    // We're going to fake the block breaking to match vanilla standards.
                    for (net.minecraft.world.item.ItemStack drop : Block.getDrops(state, level, pos, null)) { // Use base cactus since we don't place a block
                        Block.popResource(level, above, drop);
                    }
                    level.setBlock(pos, state.setValue(CactusBlock.AGE, 0), Block.UPDATE_NONE);
                    return;
                }
                // Pluto end - Check if the cactus can even survive being placed
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, above == null ? above = pos.above() : above, this.defaultBlockState(), Block.UPDATE_ALL)) return; // Paper - block grow event // Pluto - Decrease chunk/block lookups
                BlockState aboveBlock = state.setValue(AGE, 0);
                level.setBlock(pos, aboveBlock, Block.UPDATE_NONE);
                level.neighborChanged(aboveBlock, above, this, null, false);
            }

            if (age < 15) {
                level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_NONE);
            }
        }
    }

    @Override
    protected VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return SHAPE_COLLISION;
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected BlockState updateShape(
        final BlockState state,
        final LevelReader level,
        final ScheduledTickAccess ticks,
        final BlockPos pos,
        final Direction directionToNeighbour,
        final BlockPos neighbourPos,
        final BlockState neighbourState,
        final RandomSource random
    ) {
        if (!state.canSurvive(level, pos)) {
            ticks.scheduleTick(pos, this, 1);
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected boolean canSurvive(final BlockState state, final LevelReader level, final BlockPos pos) {
        // Pluto start - Check if the cactus can even survive being placed
        return canSurvive(level, pos);
    }
    protected boolean canSurvive(final LevelReader level, final BlockPos pos) {
        // Pluto end - Check if the cactus can even survive being placed
        for (Direction direction : Direction.Plane.HORIZONTAL.faces) { // Pluto - Expose Direction$Plane's faces
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if ((level.getWorldBorder().world == null || level.getWorldBorder().world.purpurConfig.cactusBreaksFromSolidNeighbors) && neighbor.isSolid() || level.getFluidState(pos.relative(direction)).is(FluidTags.LAVA)) { // Purpur - Cactus breaks from solid neighbors config
                return false;
            }
        }

        BlockState belowState = level.getBlockState(pos.below());
        return (belowState.is(this) || belowState.is(BlockTags.SUPPORTS_CACTUS)) && !level.getBlockState(pos.above()).liquid();
    }

    @Override
    protected void entityInside(
        final BlockState state,
        final Level level,
        final BlockPos pos,
        final Entity entity,
        final InsideBlockEffectApplier effectApplier,
        final boolean isPrecise
    ) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        entity.hurt(level.damageSources().cactus().eventBlockDamager(level, pos), 1.0F); // CraftBukkit
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected boolean isPathfindable(final BlockState state, final PathComputationType type) {
        return false;
    }

    // Purpur start - bonemealable cactus
    @Override
    public boolean isValidBonemealTarget(final LevelReader world, final BlockPos pos, final BlockState state) {
        if (!((Level) world).purpurConfig.cactusAffectedByBonemeal || !world.isEmptyBlock(pos.above())) return false;

        int cactusHeight = 0;
        while (world.getBlockState(pos.below(cactusHeight)).is(this)) {
            cactusHeight++;
        }

        return cactusHeight < ((Level) world).paperConfig().maxGrowthHeight.cactus;
    }

    @Override
    public boolean isBonemealSuccess(Level world, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, BlockState state) {
        int cactusHeight = 0;
        while (world.getBlockState(pos.below(cactusHeight)).is(this)) {
            cactusHeight++;
        }
        for (int i = 0; i <= world.paperConfig().maxGrowthHeight.cactus - cactusHeight; i++) {
            world.setBlockAndUpdate(pos.above(i), state.setValue(CactusBlock.AGE, 0));
        }
    }
    // Purpur end - bonemealable cactus
}
