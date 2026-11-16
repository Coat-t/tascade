package com.coatt.tascade.mixin;

import com.coatt.tascade.replay.RecordCodec;
import com.coatt.tascade.replay.record.ClientSampler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
  // the server doesn't send the player's own block-breaking progress
  @Inject(at = @At("HEAD"), method = "setBlockBreakingInfo")
  private void tascade$recordCrack(int entityId, BlockPos pos, int progress, CallbackInfo ci) {
    MinecraftClient client = MinecraftClient.getInstance();
    if (client.player != null && entityId == client.player.getEntityId()) {
      ClientSampler.blockParticles(RecordCodec.PARTICLES_CRACK, pos, progress);
    }
  }
}
