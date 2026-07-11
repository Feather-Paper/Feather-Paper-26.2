package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.AbstractDoubleList;

public class CubePointRange extends AbstractDoubleList {
    private final int size; // Gale - replace parts by size in CubePointRange
    private final double scale; // Gale - Lithium - replace division by multiplication in CubePointRange

    public CubePointRange(final int parts) {
        if (parts <= 0) {
            throw new IllegalArgumentException("Need at least 1 part");
        }

        this.size = parts + 1; // Gale - replace parts by size in CubePointRange
        this.scale = 1.0D / parts; // Gale - Lithium - replace division by multiplication in CubePointRange
    }

    @Override
    public double getDouble(final int index) {
        return index * this.scale; // Gale - Lithium - replace division by multiplication in CubePointRange
    }

    @Override
    public int size() {
        return this.size; // Gale - replace parts by size in CubePointRange
    }
}
