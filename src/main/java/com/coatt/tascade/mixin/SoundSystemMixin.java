package com.coatt.tascade.mixin;

import com.coatt.tascade.replay.play.ReplayPlayer;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// scales pitch past vanilla's clamp to match replay speed
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
  @Inject(at = @At("RETURN"), method = "getAdjustedPitch(Lnet/minecraft/client/sound/SoundInstance;)F", cancellable = true)
  private void tascade$replayPitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
    if (sound instanceof ReplayPlayer.RecordedSound) {
      cir.setReturnValue(cir.getReturnValueF() * ((ReplayPlayer.RecordedSound) sound).speed);
    }
  }
}
