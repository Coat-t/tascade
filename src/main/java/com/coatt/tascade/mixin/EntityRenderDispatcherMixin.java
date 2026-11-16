package com.coatt.tascade.mixin;

import com.coatt.tascade.replay.play.ReplayPlayer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
  @Inject(at = @At("HEAD"), method = "shouldRender", cancellable = true)
  private <E extends Entity> void tascade$replayHidden(E entity, Frustum frustum, double x, double y, double z,
                                                       CallbackInfoReturnable<Boolean> cir) {
    if (ReplayPlayer.hidesEntity(entity)) cir.setReturnValue(false);
  }
}
