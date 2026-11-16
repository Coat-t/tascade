package com.coatt.tascade.world;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.registry.Registry;

import java.util.List;
import java.util.function.Supplier;

// a world's n-th mined block of a kind drops the same on every world with that seed
public final class BlockDrops {
  // distinct from the other roll kinds
  private static final long MINED = 7;

  private BlockDrops() {
  }

  public static List<ItemStack> mined(BlockState state, ServerWorld world, Supplier<List<ItemStack>> drops) {
    MinecraftServer server = world.getServer();
    String block = Registry.BLOCK.getId(state.getBlock()).toString();
    int mined = RunCounts.next(server, "mined/" + block);
    long seed = MobSpawns.mix(MobSpawns.mix(MobSpawns.mix(WorldSeeds.combinedSeed(server), MINED), block.hashCode()), mined);
    Object[] loot = new Object[1];
    MobSpawns.roll(seed, () -> loot[0] = drops.get());
    @SuppressWarnings("unchecked")
    List<ItemStack> stacks = (List<ItemStack>) loot[0];
    return stacks;
  }
}
