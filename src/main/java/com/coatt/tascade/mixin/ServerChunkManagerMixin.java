package com.coatt.tascade.mixin;

import com.coatt.tascade.world.MobSpawns;
import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.server.world.ServerChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkManager.class)
public abstract class ServerChunkManagerMixin {
  @Inject(method = "tickChunks", at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V",
      shift = At.Shift.AFTER))
  private void tascade$spawnTurns(CallbackInfo ci) {
    if (WorldSeeds.active()) {
      MobSpawns.spawnTurns();
    }
  }
}
