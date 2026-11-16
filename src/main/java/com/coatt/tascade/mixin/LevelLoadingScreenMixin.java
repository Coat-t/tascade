package com.coatt.tascade.mixin;

import com.coatt.tascade.network.RoomState;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.network.TascadeClient;
import com.coatt.tascade.sound.MatchSounds;
import net.minecraft.client.gui.WorldGenerationProgressTracker;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {

  @Shadow @Final private WorldGenerationProgressTracker progressProvider;

  protected LevelLoadingScreenMixin(Text title) {
    super(title);
  }

  @Inject(at = @At("TAIL"), method = "render")
  private void tascade$opponentProgress(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    MatchSounds.onLoadingScreen();
    if (!RunState.isVersus()) return;
    TascadeClient.getInstance().reportWorldProgress(MathHelper.clamp(progressProvider.getProgressPercentage(), 0, 100));
    if (RunState.isRoom()) {
      StringBuilder line = new StringBuilder();
      for (RoomState.Racer racer : RoomState.others()) {
        if (racer.worldProgress < 0) continue;
        if (line.length() > 0) line.append(" - ");
        line.append(racer.username).append(' ').append(racer.worldProgress).append('%');
      }
      if (line.length() == 0) return;
      int y = this.height / 2 + 30 + progressProvider.getSize() + 10;
      for (net.minecraft.text.StringRenderable wrapped : this.textRenderer.wrapLines(new net.minecraft.text.LiteralText(line.toString()), this.width - 28)) {
        this.textRenderer.drawWithShadow(matrices, wrapped, (this.width - this.textRenderer.getWidth(wrapped)) / 2f, y, 10066329);
        y += 10;
      }
      return;
    }

    int progress = RunState.opponentWorldProgress;
    String opponent = RunState.opponentName;
    if (progress < 0 || opponent == null) return;
    // vanilla centres the chunk map 30px below the middle at 2px per chunk
    int y = this.height / 2 + 30 + progressProvider.getSize() + 10;
    this.drawCenteredString(matrices, this.textRenderer, opponent + "'s world: " + progress + "%",
            this.width / 2, y, 10066329);
  }
}
