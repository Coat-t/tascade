package com.coatt.tascade.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.options.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
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

  @Inject(at = @At("HEAD"), method = "init")
  private void onInit (CallbackInfo ci) {
    this.addButton(new ButtonWidget(
            this.width / 2 - 100 + 200 + 2,
            this.height / 4 + 48,
            20,
            20,
            new LiteralText("T"),
            buttonWidget -> this.client.openScreen(new OptionsScreen(this, this.client.options))
    ));
  }
}
