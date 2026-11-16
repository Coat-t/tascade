package com.coatt.tascade.world;

import com.google.common.collect.ImmutableSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.Set;

// buried treasure and shipwreck seeds are found with the pie chart, so other chests and spawners nearby are removed
public final class Clutter {
  private static final Set<StructureFeature<?>> CHEST_STRUCTURES = ImmutableSet.of(
      StructureFeature.VILLAGE, StructureFeature.PILLAGER_OUTPOST, StructureFeature.MINESHAFT,
      StructureFeature.MANSION, StructureFeature.JUNGLE_PYRAMID, StructureFeature.DESERT_PYRAMID,
      StructureFeature.IGLOO, StructureFeature.SHIPWRECK, StructureFeature.OCEAN_RUIN,
      StructureFeature.BURIED_TREASURE, StructureFeature.RUINED_PORTAL);
  private static final int STRUCTURE_REACH = 96;
  private static final int DUNGEON_REACH = 8;

  private Clutter() {
  }

  public static boolean skipStart(StructureFeature<?> feature, ChunkPos chunk, Biome biome) {
    WorldSeeds.Clean clean = WorldSeeds.clean();
    if (clean == null || !CHEST_STRUCTURES.contains(feature)
        || biome.getCategory() == Biome.Category.NETHER || biome.getCategory() == Biome.Category.THEEND) {
      return false;
    }
    if (chunk.x == clean.keepChunkX && chunk.z == clean.keepChunkZ
        && Registry.STRUCTURE_FEATURE.getId(feature).toString().equals(clean.keepFeature)) {
      return false;
    }
    return near(clean, chunk.getStartX() + 8, chunk.getStartZ() + 8, STRUCTURE_REACH);
  }

  public static boolean skipDungeon(BlockPos pos) {
    WorldSeeds.Clean clean = WorldSeeds.clean();
    return clean != null && near(clean, pos.getX(), pos.getZ(), DUNGEON_REACH);
  }

  private static boolean near(WorldSeeds.Clean clean, int x, int z, int reach) {
    double r = clean.radius + reach;
    for (int[] c : clean.centers) {
      double dx = x - c[0], dz = z - c[1];
      if (dx * dx + dz * dz <= r * r) {
        return true;
      }
    }
    return false;
  }
}
