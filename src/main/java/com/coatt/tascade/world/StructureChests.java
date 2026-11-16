package com.coatt.tascade.world;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

public final class StructureChests {
  private StructureChests() {
  }

  // never 0, which loot contexts take as "no seed"
  public static long chestSeed(ServerWorld world, BlockPos pos) {
    return chestSeed(world.getSeed(), pos);
  }

  static long chestSeed(long worldSeed, BlockPos pos) {
    long seed = mix(worldSeed, pos.asLong());
    return seed == 0 ? 1 : seed;
  }

  static long pickSeed(ServerWorld world, long chunkPos) {
    return mix(world.getSeed(), chunkPos);
  }

  static List<ItemStack> roll(ServerWorld world, Chest chest) {
    return world.getServer().getLootManager().getTable(chest.table).generateLoot(new LootContext.Builder(world)
        .parameter(LootContextParameters.POSITION, chest.pos)
        .random(chestSeed(world, chest.pos))
        .build(LootContextTypes.CHEST));
  }

  static List<ItemStack> topUp(List<Chest> chests, BlockPos pos, long pickSeed, Function<Chest, List<ItemStack>> roll,
      List<TascadeLoot.Floor> floors) {
    List<ItemStack> topUp = new ArrayList<>();
    if (chests.isEmpty() || !chests.get(new Random(pickSeed).nextInt(chests.size())).pos.equals(pos)) {
      return topUp;
    }
    List<ItemStack> loot = new ArrayList<>();
    for (Chest chest : chests) {
      loot.addAll(roll.apply(chest));
    }
    for (TascadeLoot.Floor floor : floors) {
      ItemStack shortfall = floor.shortfall(loot);
      if (shortfall != null) topUp.add(shortfall);
    }
    return topUp;
  }

  private static long mix(long seed, long value) {
    return HashCommon.murmurHash3(HashCommon.murmurHash3(seed) + value);
  }

  static final class Chest {
    final BlockPos pos;
    final Identifier table;

    Chest(BlockPos pos, Identifier table) {
      this.pos = pos;
      this.table = table;
    }
  }
}
