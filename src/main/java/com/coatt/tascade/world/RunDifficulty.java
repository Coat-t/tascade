package com.coatt.tascade.world;

import net.minecraft.world.Difficulty;

public final class RunDifficulty {
  public static final Difficulty INSTEAD_OF_PEACEFUL = Difficulty.HARD;

  private RunDifficulty() {
  }

  public static boolean forbidden(Difficulty difficulty) {
    return difficulty == Difficulty.PEACEFUL && WorldSeeds.active();
  }
}
