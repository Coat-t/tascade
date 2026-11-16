package com.coatt.tascade.mixin;

import com.coatt.tascade.replay.play.ReplayPlayer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
  @Inject(at = @At("HEAD"), method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z", cancellable = true)
  private void tascade$noRunnerNameTag(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
    if (ReplayPlayer.hidesNameTag(entity)) cir.setReturnValue(false);
  }
}
