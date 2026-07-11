package net.minecraft.util.worldupdate;

import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;

public record FileToUpgrade(abomination.IRegionFile file, List<ChunkPos> chunksToUpgrade) { // Luminol - Configurable region file format
}
