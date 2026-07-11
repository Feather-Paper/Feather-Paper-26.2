package ca.spottedleaf.moonrise.patches.collisions.util;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

// Gale start - cache FluidOcclusionCacheKey hash
public final class FluidOcclusionCacheKey {
    private final BlockState first;
    private final BlockState second;
    private final Direction direction;
    private final boolean result;
    private final int hash;

    public FluidOcclusionCacheKey(BlockState first, BlockState second, Direction direction, boolean result) {
        this.first = first;
        this.second = second;
        this.direction = direction;
        this.result = result;
        this.hash = java.util.Objects.hash(first, second, direction, result);
    }

    public BlockState first() {
        return first;
    }

    public BlockState second() {
        return second;
    }

    public Direction direction() {
        return direction;
    }

    public boolean result() {
        return result;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FluidOcclusionCacheKey fluidOcclusionCacheKey
            && this.first == fluidOcclusionCacheKey.first
            && this.second == fluidOcclusionCacheKey.second
            && this.direction == fluidOcclusionCacheKey.direction;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "FluidOcclusionCacheKey[" +
            "first=" + first + ", " +
            "second=" + second + ", " +
            "direction=" + direction + ", " +
            "result=" + result + ']';
    }
    // Gale end - cache FluidOcclusionCacheKey hash
}
