package com.coatt.tascade.screen;

import com.coatt.tascade.TascadeConfig;
import com.coatt.tascade.chat.MatchChatDock;
import com.coatt.tascade.chat.MatchChatOverlay;
import com.coatt.tascade.hud.HudScale;
import com.coatt.tascade.network.UpdateCheck;
import com.coatt.tascade.replay.gui.ReplayListScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ConfirmChatLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.options.SkinOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.Rect2i;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public class MainScreen extends TascadeScreen implements MatchChatDock {
  private final Screen parent;
  private final int bottomPadding = 10;
  private static final int BUTTON_WIDTH = 90;
  private static final int BUTTON_STEP = 30;
  private static final int NOTICE_GAP = 12;
  private static final int NOTICE_Y = 6;
  private static final int VERSION_MARGIN = 2;

  private ButtonWidget playButton;

  public MainScreen (Screen parent) {
    super(new LiteralText("Tascade"));
    this.parent = parent;
  }

  @Override
  protected void init() {
    int left = 14;
    int right = this.width - 14 - BUTTON_WIDTH;
    int bottom = this.height - 20 - bottomPadding;

    this.addButton(menuButton(left, bottom, "Back", buttonWidget -> this.client.openScreen(parent)));
    playButton = this.addButton(menuButton(left, bottom - BUTTON_STEP, "Play", buttonWidget -> this.client.openScreen(
            UpdateCheck.blocked() != null ? new UpdateRequiredScreen(this) : new PlayScreen(this))));

    ButtonWidget settings = this.addButton(menuButton(right, bottom, "Settings",
            buttonWidget -> this.client.openScreen(TascadeConfig.screen(this))));
    settings.active = TascadeConfig.loaded();
    this.addButton(menuButton(right, bottom - BUTTON_STEP, "Leaderboards",
            buttonWidget -> this.client.openScreen(new LeaderboardScreen(this))));
    this.addButton(menuButton(right, bottom - 2 * BUTTON_STEP, "Profile",
            buttonWidget -> this.client.openScreen(new ProfileScreen(this))));
    this.addButton(menuButton(right, bottom - 3 * BUTTON_STEP, "Replays",
            buttonWidget -> this.client.openScreen(new ReplayListScreen(this))));
  }

  @Override
  protected Identifier backgroundImage() {
    return BACKGROUND;
  }

  private static ButtonWidget menuButton(int x, int y, String label, ButtonWidget.PressAction onPress) {
    return new ButtonWidget(x, y, BUTTON_WIDTH, 20, new LiteralText(label), onPress);
  }

  @Override
  public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
    this.renderBackground(matrices);
    renderTopBar(matrices);
    String notice = updateNotice();
    if (notice != null) {
      int color = overNotice(notice, mouseX, mouseY) ? QueueStatus.COLOR_TEXT : QueueStatus.COLOR_ACCENT;
      this.drawStringWithShadow(matrices, this.textRenderer, notice, noticeX(), NOTICE_Y, color);
    }
    renderVersion(matrices);
    super.render(matrices, mouseX, mouseY, delta);
  }

  private void renderVersion(MatrixStack matrices) {
    float scale = HudScale.oneStepSmaller(this.client);
    float y = this.height - VERSION_MARGIN - this.textRenderer.fontHeight * scale;
    matrices.push();
    matrices.translate(HudScale.snapToScreenPixel(this.client, VERSION_MARGIN), HudScale.snapToScreenPixel(this.client, y), 0);
    matrices.scale(scale, scale, 1f);
    this.textRenderer.drawWithShadow(matrices, "Tascade v" + UpdateCheck.currentVersion(), 0, 0, QueueStatus.COLOR_MUTED);
    matrices.pop();
  }

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (super.mouseClicked(mouseX, mouseY, button)) return true;
    String notice = updateNotice();
    if (button != 0 || notice == null || !overNotice(notice, mouseX, mouseY)) return false;
    this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    this.client.openScreen(new ConfirmChatLinkScreen(open -> {
      if (open) Util.getOperatingSystem().open(UpdateCheck.DOWNLOAD_URL);
      this.client.openScreen(this);
    }, UpdateCheck.DOWNLOAD_URL, true));
    return true;
  }

  private static String updateNotice() {
    String version = UpdateCheck.newerVersion();
    return version == null ? null : "Tascade " + version + " is out! click to download";
  }

  @Override
  public Rect2i matchChatArea() {
    int top = TOP_BAR_HEIGHT + 8;
    int bottom = this.height - 20 - bottomPadding - BUTTON_STEP - 6;
    int width = Math.min(MatchChatOverlay.dockedWidth(this.client), this.width - 28 - BUTTON_WIDTH - 14);
    return new Rect2i(14, top, width, bottom - top);
  }

  private int noticeX() {
    return 14 + this.textRenderer.getWidth("Tascade (Beta)") + NOTICE_GAP;
  }

  private boolean overNotice(String notice, double mouseX, double mouseY) {
    int x = noticeX();
    return mouseX >= x && mouseX < x + this.textRenderer.getWidth(notice)
            && mouseY >= NOTICE_Y - 1 && mouseY < NOTICE_Y + this.textRenderer.fontHeight;
  }
}
