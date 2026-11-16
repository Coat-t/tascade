package com.coatt.tascade.mixin;

import com.coatt.tascade.hud.DelusionalHud;
import com.coatt.tascade.hud.RaceHud;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.replay.play.ReplayPlayer;
import com.coatt.tascade.replay.record.ClientSampler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
  @Shadow private int scaledWidth;
  @Shadow private int scaledHeight;

  @Shadow
  protected abstract PlayerEntity getCameraPlayer();

  @Inject(at = @At("HEAD"), method = "renderStatusBars")
  private void tascade$delusionalBegin(MatrixStack matrices, CallbackInfo ci) {
    DelusionalHud.begin(getCameraPlayer());
  }

  // profiler section names tell which bar is being drawn
  @Redirect(method = "renderStatusBars", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V"))
  private void tascade$delusionalSection(Profiler profiler, String name) {
    DelusionalHud.section(name);
    profiler.swap(name);
  }

  @Redirect(method = "renderStatusBars", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/gui/hud/InGameHud;drawTexture(Lnet/minecraft/client/util/math/MatrixStack;IIIIII)V"))
  private void tascade$delusionalHide(InGameHud hud, MatrixStack matrices, int x, int y, int u, int v, int width, int height) {
    if (!DelusionalHud.hides()) hud.drawTexture(matrices, x, y, u, v, width, height);
  }

  @Inject(at = @At("TAIL"), method = "renderStatusBars")
  private void tascade$delusionalCover(MatrixStack matrices, CallbackInfo ci) {
    DelusionalHud.render((InGameHud) (Object) this, matrices, getCameraPlayer(), this.scaledWidth, this.scaledHeight);
  }
  // vanilla hides the Tab list in solo singleplayer, runs use it for info
  @Redirect(method = "render",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;isInSingleplayer()Z"))
  private boolean tascade$showTabListInRuns(MinecraftClient client) {
    return client.isInSingleplayer() && !RunState.isRunning();
  }

  @Inject(at = @At("TAIL"), method = "render")
  private void tascade$renderRaceHud(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
    MinecraftClient client = MinecraftClient.getInstance();
    if (!RaceHud.drawnOver(client.currentScreen)) RaceHud.render(client, matrices);
    ReplayPlayer.renderHud(matrices, tickDelta);
  }

  @Inject(at = @At("HEAD"), method = "setOverlayMessage")
  private void tascade$recordActionBar(Text message, boolean tinted, CallbackInfo ci) {
    ClientSampler.actionBar(message, tinted);
  }
}
