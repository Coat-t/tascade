package com.coatt.tascade.mixin;

import com.coatt.tascade.replay.play.ReplayPlayer;
import com.coatt.tascade.replay.record.ClientSampler;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
  @Inject(at = @At("HEAD"), method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", cancellable = true)
  private void tascade$sound(SoundInstance sound, CallbackInfo ci) {
    if (ReplayPlayer.blocksSound(sound)) ci.cancel();
    else ClientSampler.sound(sound);
  }

  @Inject(at = @At("HEAD"), method = "play(Lnet/minecraft/client/sound/SoundInstance;I)V", cancellable = true)
  private void tascade$delayedSound(SoundInstance sound, int delay, CallbackInfo ci) {
    if (ReplayPlayer.blocksSound(sound)) ci.cancel();
    else ClientSampler.sound(sound);
  }
}
