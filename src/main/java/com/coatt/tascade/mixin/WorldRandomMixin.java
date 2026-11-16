package com.coatt.tascade.mixin;

import com.coatt.tascade.world.MobSpawns;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

// only on the rolling thread, worldgen workers share this random via ChunkRegion
@Mixin({World.class, ChunkRegion.class})
public abstract class WorldRandomMixin {
  @Inject(at = @At("HEAD"), method = "getRandom", cancellable = true)
  private void tascade$spawnRandom(CallbackInfoReturnable<Random> cir) {
    Random rolling = MobSpawns.rolling();
    if (rolling != null) {
      cir.setReturnValue(rolling);
    }
  }
}
