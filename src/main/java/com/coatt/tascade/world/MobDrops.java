package com.coatt.tascade.world;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

// a world's n-th kill of a kind drops the same on every world with that seed. Only player kills count
public final class MobDrops {
  // distinct from the other roll kinds
  private static final long DROPS = 6;
  static final int IRON_GOLEM_IRON = 4;

  private MobDrops() {
  }

  public static void kill(LivingEntity mob, Runnable drop) {
    MinecraftServer server = ((ServerWorld) mob.world).getServer();
    String type = EntityType.getId(mob.getType()).toString();
    int kill = RunCounts.next(server, "kill/" + type);
    long seed = MobSpawns.mix(MobSpawns.mix(MobSpawns.mix(WorldSeeds.combinedSeed(server), DROPS), type.hashCode()), kill);
    MobSpawns.roll(seed, () -> {
      mob.getRandom().setSeed(MobSpawns.rolling().nextLong());
      drop.run();
    });
  }

  public static List<ItemStack> ironGolemLoot(List<ItemStack> loot) {
    loot.removeIf(stack -> stack.getItem() == Items.IRON_INGOT);
    loot.add(new ItemStack(Items.IRON_INGOT, IRON_GOLEM_IRON));
    return loot;
  }
}
