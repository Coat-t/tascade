package com.coatt.tascade.mixin;

import com.coatt.tascade.replay.RecordCodec;
import com.coatt.tascade.replay.play.ReplayPlayer;
import com.coatt.tascade.replay.record.ClientSampler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
  @Inject(at = @At("HEAD"), method = "addBlockBreakParticles", cancellable = true)
  private void tascade$breakParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
    if (ReplayPlayer.blocksBlockParticles()) ci.cancel();
    else ClientSampler.blockParticles(RecordCodec.PARTICLES_BREAK, pos, Block.getRawIdFromState(state));
  }

  @Inject(at = @At("HEAD"), method = "addBlockBreakingParticles", cancellable = true)
  private void tascade$miningParticles(BlockPos pos, Direction direction, CallbackInfo ci) {
    if (ReplayPlayer.blocksBlockParticles()) ci.cancel();
    else ClientSampler.blockParticles(RecordCodec.PARTICLES_MINING, pos, direction.getId());
  }
}
