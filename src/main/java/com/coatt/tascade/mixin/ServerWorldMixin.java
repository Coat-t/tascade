package com.coatt.tascade.mixin;

import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// gives the Nether its own seed for structures, carvers and decoration too
@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
  @Inject(at = @At("HEAD"), method = "getSeed", cancellable = true)
  private void tascade$netherSeed(CallbackInfoReturnable<Long> cir) {
    Long netherSeed = WorldSeeds.netherSeed();
    if (netherSeed != null && ((World) (Object) this).getRegistryKey() == World.NETHER) {
      cir.setReturnValue(netherSeed);
    }
  }

  @Inject(at = @At("RETURN"), method = "<init>")
  private void tascade$netherBiomeAccess(CallbackInfo ci) {
    Long netherSeed = WorldSeeds.netherSeed();
    ServerWorld self = (ServerWorld) (Object) this;
    if (netherSeed != null && self.getRegistryKey() == World.NETHER) {
      ((WorldAccessor) self).tascade$setBiomeAccess(
          new BiomeAccess(self, BiomeAccess.hashSeed(netherSeed), self.getDimension().getBiomeAccessType()));
    }
  }
}
