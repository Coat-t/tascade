package com.coatt.tascade.mixin;

import com.coatt.tascade.TascadeKeys;
import com.coatt.tascade.replay.play.ReplayPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(GameOptions.class)
public abstract class GameOptionsMixin {
  @Mutable @Shadow @Final public KeyBinding[] keysAll;

  // registered before options.txt is read so they load from it
  @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/options/GameOptions;load()V"))
  private void tascade$addKeyBindings(MinecraftClient client, File optionsFile, CallbackInfo ci) {
    keysAll = ArrayUtils.addAll(keysAll, TascadeKeys.all());
  }

  // while a replay applies the runner's settings, don't save them over the viewer's
  @Inject(at = @At("HEAD"), method = "write", cancellable = true)
  private void tascade$saveViewerSettings(CallbackInfo ci) {
    if (ReplayPlayer.writeViewerOptions((GameOptions) (Object) this)) ci.cancel();
  }
}
