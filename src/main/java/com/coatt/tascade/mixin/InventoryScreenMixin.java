package com.coatt.tascade.mixin;

import com.coatt.tascade.hud.RaceHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.StringRenderable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
  @Inject(method = "render", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/gui/screen/ingame/InventoryScreen;drawMouseoverTooltip(Lnet/minecraft/client/util/math/MatrixStack;II)V"))
  private void tascade$renderRaceHud(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    Screen self = (Screen) (Object) this;
    if (!RaceHud.drawnOver(self)) return;
    List<StringRenderable> tooltip = RaceHud.render(MinecraftClient.getInstance(), matrices, mouseX, mouseY);
    if (tooltip != null) self.renderTooltip(matrices, tooltip, mouseX, mouseY);
  }
}
