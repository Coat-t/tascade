package com.coatt.tascade.screen;

import com.coatt.tascade.hud.RunLabels;
import com.coatt.tascade.network.RunResume;
import com.coatt.tascade.network.TascadeClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

@Environment(EnvType.CLIENT)
public class RejoinScreen extends TascadeScreen {
  private static final int BUTTON_WIDTH = 120;
  private static final int LINE_HEIGHT = 12;

  private final Screen parent;
  private final TascadeClient.RejoinOffer offer;

  public RejoinScreen(Screen parent, TascadeClient.RejoinOffer offer) {
    super(new LiteralText(offer.left.ranked ? "Rejoin your match?" : "Rejoin your run?"));
    this.parent = parent;
    this.offer = offer;
  }

  public Screen parent() {
    return parent;
  }

  public TascadeClient.RejoinOffer offer() {
    return offer;
  }

  @Override
  protected void init() {
    int y = this.height / 2 + 30;
    this.addButton(new ButtonWidget(this.width / 2 - BUTTON_WIDTH - 5, y, BUTTON_WIDTH, 20, new LiteralText("Rejoin"),
            buttonWidget -> TascadeClient.getInstance().rejoin(offer)));
    this.addButton(new ButtonWidget(this.width / 2 + 5, y, BUTTON_WIDTH, 20,
            new LiteralText(offer.left.ranked ? "Forfeit" : "Abandon"), buttonWidget -> {
              TascadeClient.getInstance().declineRejoin(offer);
              this.client.openScreen(parent);
            }));
  }

  @Override
  public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
    this.renderBackground(matrices);
    renderTopBar(matrices);
    RunResume.Saved left = offer.left;
    String still = left.ranked
            ? "Your ranked match" + (left.opponentName != null ? " against " + left.opponentName : "") + " is still on."
            : "Your solo run is still on.";
    String clock = "The race has been going for "
            + RunLabels.time(System.currentTimeMillis() - offer.raceStartedAtMs).replaceAll("\\.\\d+$", "") + ".";
    String[] lines = {still, clock, "You'll pick up from where the world was last saved."};
    int y = this.height / 2 - 10 - (lines.length + 1) * LINE_HEIGHT;
    drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, y, QueueStatus.COLOR_TEXT);
    for (String line : lines) {
      y += LINE_HEIGHT;
      drawCenteredString(matrices, this.textRenderer, line, this.width / 2, y, QueueStatus.COLOR_MUTED);
    }
    super.render(matrices, mouseX, mouseY, delta);
  }

  // closing would leave the run held with nobody in it
  @Override
  public boolean shouldCloseOnEsc() {
    return false;
  }
}
