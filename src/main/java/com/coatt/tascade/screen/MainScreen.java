package com.coatt.tascade.screen;

import com.coatt.tascade.Main;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.options.SkinOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;

@Environment(EnvType.CLIENT)
public class MainScreen extends Screen {
  private final Screen parent;
  private final int bottomPadding = 10;

  private ButtonWidget playButton;

  public MainScreen (Screen parent) {
    super(new LiteralText("Tascade"));
    this.parent = parent;
  }

  @Override
  protected void init() {
    this.addButton(new ButtonWidget(
      14,
      this.height - 20 - bottomPadding,
      80,
      20,
      new LiteralText("Back"),
      buttonWidget -> this.client.openScreen(parent)
    ));
    playButton = this.addButton(new ButtonWidget(
            14,
            this.height - 40 - bottomPadding - 10,
            80,
            20,
            new LiteralText("Play"),
            buttonWidget -> this.client.openScreen(new PlayScreen(this))
    ));
  }

  private void profileComponent(MatrixStack matrices) {
    if (Main.profileStatus == Main.ProfileState.SUCCESS && Main.nickname != null && Main.pp != null && Main.elo != null) {
      int nicknameTextWidth = this.textRenderer.getWidth(Main.nickname);
      int ppTextWidth = this.textRenderer.getWidth(Main.pp.toString() + "pp");
      int eloTextWidth = this.textRenderer.getWidth(Main.elo.toString() + "elo");
      this.drawTextWithShadow(matrices, this.textRenderer, new LiteralText(Main.nickname), this.width - 14 - nicknameTextWidth, 14, 16777215);
      this.drawTextWithShadow(matrices, this.textRenderer, new LiteralText(Main.pp.toString() + "pp"), this.width - 14 - nicknameTextWidth - 10 - ppTextWidth, 14, 10066329);
      this.drawTextWithShadow(matrices, this.textRenderer, new LiteralText(Main.elo.toString() + "elo"), this.width - 14 - nicknameTextWidth - 10 - ppTextWidth - 10 - eloTextWidth, 14, 10066329);
    } else if (Main.profileStatus == Main.ProfileState.LOADING) {
      this.drawTextWithShadow(matrices, this.textRenderer, new LiteralText("Loading profile..."), this.width - 14 - this.textRenderer.getWidth("Loading profile..."), 14, 10066329);
    } else {
      this.drawTextWithShadow(matrices, this.textRenderer, new LiteralText("Failed to load"), this.width - 14 - this.textRenderer.getWidth("Failed to load"), 14, 10043970);
    }
  }

  @Override
  public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
    this.renderBackground(matrices);
    profileComponent(matrices);
    this.drawTextWithShadow(matrices, this.textRenderer, this.title, 14, 14, 16777215);
    super.render(matrices, mouseX, mouseY, delta);
  }
}
