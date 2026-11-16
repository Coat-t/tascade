package com.coatt.tascade.mixin;

import com.coatt.tascade.network.RunState;
import com.coatt.tascade.screen.RaceWaitScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// looks like RaceWaitScreen so the dirt background never flashes between screens
@Mixin(ProgressScreen.class)
public abstract class ProgressScreenMixin extends Screen {

  @Shadow @Nullable private Text title;
  @Shadow private boolean done;

  protected ProgressScreenMixin(Text title) {
    super(title);
  }

  @Inject(at = @At("HEAD"), method = "render", cancellable = true)
  private void tascade$raceBackdrop(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    if (done || !RaceWaitScreen.worldGenerated()) return;
    ci.cancel();
    RaceWaitScreen.renderHeld(this, matrices, RunState.raceCountdown, title != null ? title.getString() : "");
  }
}
