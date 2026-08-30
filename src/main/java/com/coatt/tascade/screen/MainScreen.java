package com.coatt.tascade.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

@Environment(EnvType.CLIENT)
public class MainScreen extends Screen {
  private final Screen parent;

  public MainScreen (Screen parent) {
    super(new LiteralText("Tascade"));
    this.parent = parent;
  }
  @Override
  protected void init() {

  }

  @Override
  public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
    this.renderBackground(matrices);
    this.drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 15, 16777215);
    super.render(matrices, mouseX, mouseY, delta);
  }
}
