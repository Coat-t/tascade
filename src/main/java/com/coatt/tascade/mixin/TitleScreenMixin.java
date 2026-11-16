package com.coatt.tascade.mixin;

import com.coatt.tascade.Main;
import com.coatt.tascade.screen.IconButton;
import com.coatt.tascade.screen.MainScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

  protected TitleScreenMixin(Text title) {
    super(title);
  }

  // 32px icon drawn at 16 GUI px so it stays crisp at every scale
  @Inject(at = @At("HEAD"), method = "init")
  private void onInit(CallbackInfo ci) {
    this.addButton(new IconButton(
            this.width / 2 + 100 + 2,
            this.height / 4 + 48 + 1,
            18,
            Main.id("icon.png"),
            32,
            new LiteralText("Tascade"),
            buttonWidget -> this.client.openScreen(new MainScreen(this))
    ));
  }
}
