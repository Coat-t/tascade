package com.coatt.tascade.mixin;

import com.coatt.tascade.replay.play.ReplayPlayer;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
  @Inject(at = @At("TAIL"), method = "update")
  private void tascade$replayCamera(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
                                    float tickDelta, CallbackInfo ci) {
    ReplayPlayer.overrideCamera((Camera) (Object) this);
  }
}
