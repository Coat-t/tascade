package com.coatt.tascade.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;

import java.util.List;

@Environment(EnvType.CLIENT)
public class RunNoticeScreen extends TascadeScreen {
  private static final int LINE_HEIGHT = 12;
  private static final int MAX_TEXT_WIDTH = 320;

  private final Screen parent;
  private final String reason;

  public RunNoticeScreen(Screen parent, String title, String reason) {
    super(new LiteralText(title));
    this.parent = parent;
    this.reason = reason;
  }

  public static RunNoticeScreen lostConnection(Screen parent, boolean ranked, boolean endedByServer) {
    String reason;
    if (endedByServer) {
      reason = ranked
              ? "When you reconnected, Tascade no longer had this match, so it can't be finished."
              : "When you reconnected, Tascade had already ended this run, so it can't be recorded.";
    } else {
      reason = ranked
              ? "You were offline for over two minutes, so you forfeited the match."
              : "You were offline for over two minutes, so the run was cancelled.";
    }
    return new RunNoticeScreen(parent, "Lost connection to Tascade", reason);
  }

  public static RunNoticeScreen lostRoomRace(Screen parent, boolean endedByServer) {
    return new RunNoticeScreen(parent, "Lost connection to Tascade", endedByServer
            ? "When you reconnected, the room's race had gone on without you."
            : "You were offline for over two minutes, so you're out of the room's race.");
  }

  public static RunNoticeScreen removedFromRoom(Screen parent, String reason) {
    if ("kicked".equals(reason)) return new RunNoticeScreen(parent, "Removed from the room", "The host kicked you from the room.");
    if ("banned".equals(reason)) return new RunNoticeScreen(parent, "Removed from the room", "The host banned you from the room, so you can't join it again.");
    if ("closed".equals(reason)) return new RunNoticeScreen(parent, "The room was closed", "The room you were in no longer exists.");
    if ("dropped".equals(reason)) return new RunNoticeScreen(parent, "You left the room", "You were offline too long, so the room let you go.");
    return null;
  }

  public static RunNoticeScreen worldChanged(Screen parent, boolean ranked) {
    return new RunNoticeScreen(parent, ranked ? "Your match couldn't be rejoined" : "Your run couldn't be rejoined",
            "Its world was changed while the game was closed, so "
                    + (ranked ? "you forfeited the match." : "the run was cancelled."));
  }

  public static RunNoticeScreen endedWhileAway(Screen parent, boolean ranked, String reason) {
    return new RunNoticeScreen(parent,
            ranked ? "Your match ended while you were away" : "Your run ended while you were away", reason);
  }

  @Override
  protected void init() {
    this.addButton(new ButtonWidget(14, this.height - 20 - 10, 80, 20, new LiteralText("Back"), buttonWidget -> onClose()));
  }

  @Override
  public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
    this.renderBackground(matrices);
    renderTopBar(matrices);
    List<StringRenderable> lines = this.textRenderer.wrapLines(new LiteralText(reason),
            Math.min(MAX_TEXT_WIDTH, this.width - 28));
    int y = this.height / 2 - (lines.size() + 1) * LINE_HEIGHT / 2;
    drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, y, QueueStatus.COLOR_TEXT);
    for (StringRenderable line : lines) {
      y += LINE_HEIGHT;
      this.textRenderer.drawWithShadow(matrices, line, this.width / 2f - this.textRenderer.getWidth(line) / 2f, y,
              QueueStatus.COLOR_MUTED);
    }
    super.render(matrices, mouseX, mouseY, delta);
  }

  @Override
  public void onClose() {
    if (this.client != null) this.client.openScreen(this.parent);
  }
}
