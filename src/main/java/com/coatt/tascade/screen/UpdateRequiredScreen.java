package com.coatt.tascade.screen;

import com.coatt.tascade.network.UpdateCheck;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.ConfirmChatLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public class UpdateRequiredScreen extends TascadeScreen {
  private static final int BUTTON_WIDTH = 120;
  private static final int LINE_HEIGHT = 12;

  private final Screen parent;

  public UpdateRequiredScreen(Screen parent) {
    super(new LiteralText("Update Tascade"));
    this.parent = parent;
  }

  @Override
  protected void init() {
    int y = this.height / 2 + 10;
    this.addButton(new ButtonWidget(this.width / 2 - BUTTON_WIDTH - 5, y, BUTTON_WIDTH, 20, new LiteralText("Download"),
            buttonWidget -> this.client.openScreen(new ConfirmChatLinkScreen(open -> {
              if (open) Util.getOperatingSystem().open(UpdateCheck.DOWNLOAD_URL);
              this.client.openScreen(this);
            }, UpdateCheck.DOWNLOAD_URL, true))));
    this.addButton(new ButtonWidget(this.width / 2 + 5, y, BUTTON_WIDTH, 20, new LiteralText("Open mods folder"),
            buttonWidget -> Util.getOperatingSystem().open(FabricLoader.getInstance().getGameDir().resolve("mods").toFile())));
    this.addButton(new ButtonWidget(14, this.height - 20 - 10, 80, 20, new LiteralText("Back"), buttonWidget -> onClose()));
  }

  @Override
  public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
    this.renderBackground(matrices);
    renderTopBar(matrices);
    String[] lines = lines();
    int y = this.height / 2 - 10 - lines.length * LINE_HEIGHT;
    for (int i = 0; i < lines.length; i++) {
      drawCenteredString(matrices, this.textRenderer, lines[i], this.width / 2, y + i * LINE_HEIGHT,
              i == 0 ? QueueStatus.COLOR_TEXT : QueueStatus.COLOR_MUTED);
    }
    super.render(matrices, mouseX, mouseY, delta);
  }

  private static String[] lines() {
    String latest = UpdateCheck.latestVersion();
    String current = UpdateCheck.currentVersion();
    if (UpdateCheck.blocked() == UpdateCheck.Blocked.UNRELEASED) {
      return new String[] {
              "This Tascade (" + current + ") isn't a released version",
              "Only released versions can play matches.",
              "Download Tascade " + latest + " to replace it in your mods folder."
      };
    }
    return new String[] {
            "Tascade " + latest + " is out",
            "This version (" + current + ") can't play matches any more.",
            "Download the new one to replace it in your mods folder."
    };
  }

  @Override
  public void onClose() {
    if (this.client != null) this.client.openScreen(this.parent);
  }
}
