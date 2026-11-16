package com.coatt.tascade.mixin;

import com.coatt.tascade.chat.MatchChatOverlay;
import com.coatt.tascade.replay.play.ReplayPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
  @Shadow
  public abstract double getX();

  @Shadow
  public abstract double getY();

  @Inject(at = @At("HEAD"), method = "onMouseButton", cancellable = true)
  private void tascade$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
    if (action != GLFW.GLFW_PRESS) return;
    MinecraftClient client = MinecraftClient.getInstance();
    if (MatchChatOverlay.onMouseClick(client, tascade$scaledX(client), tascade$scaledY(client), button)) ci.cancel();
  }

  @Inject(at = @At("HEAD"), method = "onMouseScroll", cancellable = true)
  private void tascade$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
    if (vertical != 0 && ReplayPlayer.onScroll(Math.signum(vertical))) {
      ci.cancel();
      return;
    }
    MinecraftClient client = MinecraftClient.getInstance();
    if (MatchChatOverlay.onMouseScroll(client, tascade$scaledX(client), tascade$scaledY(client), vertical)) {
      ci.cancel();
    }
  }

  @Unique
  private double tascade$scaledX(MinecraftClient client) {
    Window window = client.getWindow();
    return getX() * window.getScaledWidth() / window.getWidth();
  }

  @Unique
  private double tascade$scaledY(MinecraftClient client) {
    Window window = client.getWindow();
    return getY() * window.getScaledHeight() / window.getHeight();
  }
}
