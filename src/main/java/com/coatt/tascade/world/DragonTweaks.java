package com.coatt.tascade.world;

import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

public final class DragonTweaks {
  public static final float MAX_HEIGHT_OFFSET = 15.0F;

  private static final Map<World, Random> HEIGHT_RANDOMS = new WeakHashMap<>();

  private DragonTweaks() {
  }

  public static float nextHeightRoll(EnderDragonEntity dragon) {
    if (!(dragon.world instanceof ServerWorld)) {
      return dragon.getRandom().nextFloat();
    }
    ServerWorld world = (ServerWorld) dragon.world;
    return HEIGHT_RANDOMS.computeIfAbsent(world, w -> new Random(world.getSeed())).nextFloat();
  }
}
