package com.coatt.tascade.mixin;

import com.coatt.tascade.chat.MatchChat;
import com.coatt.tascade.chat.MatchChatOverlay;
import com.coatt.tascade.replay.play.ReplayPlayer;
import com.coatt.tascade.replay.record.ClientSampler;
import com.coatt.tascade.screen.QueueStatusOverlay;
import com.coatt.tascade.screen.TascadeScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
  @Inject(at = @At("TAIL"), method = "render")
  private void tascade$renderQueueOverlay(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
    MinecraftClient client = MinecraftClient.getInstance();
    ClientSampler.frame(client);
    // frames keep coming while a world generates but ticks don't
    MatchChat.applyPending();
    int top = TascadeScreen.overlayTop(client.currentScreen);
    int belowQueueOverlay = QueueStatusOverlay.render(client, top);
    MatchChatOverlay.render(client, belowQueueOverlay, top);
  }

  @Inject(at = @At("RETURN"), method = "getFov", cancellable = true)
  private void tascade$fov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
    if (!changingFov) return;
    if (ReplayPlayer.isActive()) cir.setReturnValue(ReplayPlayer.overrideFov(cir.getReturnValue()));
    else ClientSampler.onFov(cir.getReturnValue());
  }
}
