package net.minecraft.world.phys.shapes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

public class EntityCollisionContext implements CollisionContext {
    private final boolean placement; // Gale - Airplane - make EntityCollisionContext a live representation - remove these and pray no plugin uses them
    private final boolean alwaysCollideWithFluid;
    private final @Nullable Entity entity;

    protected EntityCollisionContext(
        final boolean descending,
        final boolean placement,
        final double entityBottom,
        final ItemStack heldItem,
        final boolean alwaysCollideWithFluid,
        final @Nullable Entity entity
    ) {
        this.placement = placement; // Gale - Airplane - make EntityCollisionContext a live representation - remove these and pray no plugin uses them
        this.alwaysCollideWithFluid = alwaysCollideWithFluid;
        this.entity = entity;
    }

    @Deprecated
    protected EntityCollisionContext(final Entity entity, final boolean alwaysCollideWithFluid, final boolean placement) {
        // Gale start - Airplane - make EntityCollisionContext a live representation - remove unneeded things
        this.entity = entity;
        this.alwaysCollideWithFluid = alwaysCollideWithFluid;
        this.placement = placement;
        // Gale end - Airplane - make EntityCollisionContext a live representation - remove unneeded things
    }

    @Override
    public boolean isHoldingItem(final Item item) {
        return this.entity instanceof LivingEntity livingEntity ? livingEntity.getMainHandItem().is(item) : ItemStack.EMPTY.is(item); // Gale - Airplane - make EntityCollisionContext a live representation
    }

    @Override
    public boolean alwaysCollideWithFluid() {
        return this.alwaysCollideWithFluid;
    }

    @Override
    public boolean canStandOnFluid(final FluidState fluidStateAbove, final FluidState fluid) {
        return this.entity instanceof LivingEntity livingEntity && livingEntity.canStandOnFluid(fluid) && !fluidStateAbove.getType().isSame(fluid.getType());
    }

    @Override
    public VoxelShape getCollisionShape(final BlockState state, final CollisionGetter collisionGetter, final BlockPos pos) {
        return state.getCollisionShape(collisionGetter, pos, this);
    }

    @Override
    public boolean isDescending() {
        return this.entity != null && this.entity.isDescending(); // Gale - Airplane - make EntityCollisionContext a live representation
    }

    @Override
    public boolean isAbove(final VoxelShape shape, final BlockPos pos, final boolean defaultValue) {
        return (this.entity == null ? -Double.MAX_VALUE : entity.getY()) > (double) pos.getY() + shape.max(Direction.Axis.Y) - (double) 1.0E-5F; // Gale - Airplane - make EntityCollisionContext a live representation
    }

    public @Nullable Entity getEntity() {
        return this.entity;
    }

    @Override
    public boolean isPlacement() {
        return this.placement;
    }

    protected static class Empty extends EntityCollisionContext {
        protected static final CollisionContext WITHOUT_FLUID_COLLISIONS = new EntityCollisionContext.Empty(false);
        protected static final CollisionContext WITH_FLUID_COLLISIONS = new EntityCollisionContext.Empty(true);

        public Empty(final boolean alwaysCollideWithFluid) {
            super(false, false, -Double.MAX_VALUE, ItemStack.EMPTY, alwaysCollideWithFluid, null);
        }

        @Override
        public boolean isAbove(final VoxelShape shape, final BlockPos pos, final boolean defaultValue) {
            return defaultValue;
        }
    }
}
