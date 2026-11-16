package com.coatt.tascade.world;

import com.coatt.tascade.world.StructureChests.Chest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTables;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.DesertTempleGenerator;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class DesertTempleLoot {
  static final int MIN_IRON = 7;
  static final int MIN_GOLD = 2;
  private static final List<TascadeLoot.Floor> FLOORS = Arrays.asList(
      new TascadeLoot.Floor(MIN_IRON, Items.IRON_INGOT),
      new TascadeLoot.Floor(MIN_GOLD, Items.GOLD_INGOT));

  private DesertTempleLoot() {
  }

  public static boolean isDesertTempleTable(Identifier table) {
    return LootTables.DESERT_PYRAMID_CHEST.equals(table);
  }

  public static List<ItemStack> topUp(ServerWorld world, BlockPos pos) {
    // the chests are below the temple's bounding box, so method_28388 never finds it
    List<StructureStart<?>> temples = world.getStructureAccessor()
        .getStructuresWithChildren(ChunkSectionPos.from(pos), StructureFeature.DESERT_PYRAMID)
        .collect(Collectors.toList());
    for (StructureStart<?> temple : temples) {
      List<Chest> chests = new ArrayList<>();
      synchronized (temple.getChildren()) {
        for (StructurePiece piece : temple.getChildren()) {
          if (piece instanceof DesertTempleGenerator) chests.addAll(chests(piece.getBoundingBox()));
        }
      }
      if (chests.stream().anyMatch(chest -> chest.pos.equals(pos))) {
        long pickSeed = StructureChests.pickSeed(world, ChunkPos.toLong(temple.getChunkX(), temple.getChunkZ()));
        return StructureChests.topUp(chests, pos, pickSeed, chest -> StructureChests.roll(world, chest), FLOORS);
      }
    }
    return new ArrayList<>();
  }

  static List<Chest> chests(BlockBox box) {
    BlockPos middle = new BlockPos(box.minX + 10, box.minY - 11, box.minZ + 10);
    return Arrays.asList(
        new Chest(middle.add(-2, 0, 0), LootTables.DESERT_PYRAMID_CHEST),
        new Chest(middle.add(0, 0, -2), LootTables.DESERT_PYRAMID_CHEST),
        new Chest(middle.add(0, 0, 2), LootTables.DESERT_PYRAMID_CHEST),
        new Chest(middle.add(2, 0, 0), LootTables.DESERT_PYRAMID_CHEST));
  }
}
