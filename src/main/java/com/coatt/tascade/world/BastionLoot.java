package com.coatt.tascade.world;

import com.coatt.tascade.mixin.SinglePoolElementAccessor;
import com.coatt.tascade.world.StructureChests.Chest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.Structure;
import net.minecraft.structure.StructureManager;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureStart;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public final class BastionLoot {
  static final int MIN_OBSIDIAN = 5;
  static final int MIN_IRON = 6;
  private static final List<TascadeLoot.Floor> FLOORS = Arrays.asList(
      new TascadeLoot.Floor(MIN_OBSIDIAN, Items.OBSIDIAN),
      new TascadeLoot.Floor(MIN_IRON, Items.IRON_INGOT));

  private BastionLoot() {
  }

  public static boolean isBastionTable(Identifier table) {
    return "minecraft".equals(table.getNamespace()) && table.getPath().startsWith("chests/bastion_");
  }

  public static List<ItemStack> topUp(ServerWorld world, BlockPos pos) {
    StructureStart<?> bastion = world.getStructureAccessor().method_28388(pos, true, StructureFeature.BASTION_REMNANT);
    if (!bastion.hasChildren()) {
      return new ArrayList<>();
    }
    StructureManager templates = world.getStructureManager();
    List<Chest> chests = chests(bastion, element -> ((SinglePoolElementAccessor) element).tascade$getStructure(templates));
    if (chests.stream().noneMatch(chest -> chest.pos.equals(pos))) {
      return new ArrayList<>();
    }
    long pickSeed = StructureChests.pickSeed(world, ChunkPos.toLong(bastion.getChunkX(), bastion.getChunkZ()));
    return StructureChests.topUp(chests, pos, pickSeed, chest -> StructureChests.roll(world, chest), FLOORS);
  }

  static List<Chest> chests(StructureStart<?> bastion, Function<SinglePoolElement, Structure> template) {
    List<StructurePiece> pieces;
    // worldgen workers lock on this too
    synchronized (bastion.getChildren()) {
      pieces = new ArrayList<>(bastion.getChildren());
    }
    List<Chest> chests = new ArrayList<>();
    for (StructurePiece piece : pieces) {
      if (!(piece instanceof PoolStructurePiece)) continue;
      PoolStructurePiece poolPiece = (PoolStructurePiece) piece;
      if (!(poolPiece.getPoolElement() instanceof SinglePoolElement)) continue;
      // positioned the way vanilla positions a piece's jigsaw blocks
      StructurePlacementData placement = new StructurePlacementData().setRotation(poolPiece.getRotation());
      Structure pieceTemplate = template.apply((SinglePoolElement) poolPiece.getPoolElement());
      for (Structure.StructureBlockInfo info : pieceTemplate.getInfosForBlock(poolPiece.getPos(), placement, Blocks.CHEST)) {
        if (info.tag != null && info.tag.contains("LootTable", 8)) {
          chests.add(new Chest(info.pos, new Identifier(info.tag.getString("LootTable"))));
        }
      }
    }
    chests.sort(Comparator.comparing(chest -> chest.pos));
    return chests;
  }
}
