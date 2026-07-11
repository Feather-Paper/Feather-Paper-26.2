package net.minecraft.world.level.pathfinder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class PathfindingContext {
    private final CollisionGetter level;
    public @Nullable PathTypeCache cache; // Kaiiju - petal - async path processing - private -> public-f
    private final BlockPos mobPosition;
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    public PathfindingContext(final CollisionGetter level, final Mob mob) {
        this.level = level;
        if (mob.level() instanceof ServerLevel serverLevel) {
            this.cache = serverLevel.getPathTypeCache();
        } else {
            this.cache = null;
        }

        this.mobPosition = mob.blockPosition();
    }

    public PathType getPathTypeFromState(final int x, final int y, final int z) {
        BlockPos pos = this.mutablePos.set(x, y, z);
        return this.cache == null ? WalkNodeEvaluator.leaf$pathType(this.level, pos) : this.cache.getOrCompute(this.level, pos); // Leaf - Cache block state tags
    }

    public BlockState getBlockState(final BlockPos pos) {
        return this.level.getBlockState(pos);
    }

    public CollisionGetter level() {
        return this.level;
    }

    public BlockPos mobPosition() {
        return this.mobPosition;
    }
}
