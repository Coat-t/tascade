package com.coatt.tascade.world;

import net.minecraft.server.MinecraftServer;

import java.util.Random;

public final class EnderEyes {
  // distinct from the other roll kinds
  private static final long EYES = 8;
  static final int SAFE_THROW = 1;

  private EnderEyes() {
  }

  public static boolean nextDrops(MinecraftServer server) {
    int thrown = RunCounts.next(server, "eye");
    return thrown == SAFE_THROW
        || new Random(MobSpawns.mix(MobSpawns.mix(WorldSeeds.combinedSeed(server), EYES), thrown)).nextInt(5) > 0;
  }
}
