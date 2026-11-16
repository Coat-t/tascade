package com.coatt.tascade.replay.gui;

import com.coatt.tascade.screen.QueueStatus;
import com.coatt.tascade.screen.TascadeScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

public final class ReplayLoadingScreen extends TascadeScreen {
    private final Screen parent;
    private volatile float progress = 0;
    private String error = null;

    public ReplayLoadingScreen(Screen parent) {
        super(new LiteralText("Loading replay"));
        this.parent = parent;
    }

    public void setProgress(float progress) {
        this.progress = progress;
    }

    public void fail(String message) {
        this.error = message;
        init(this.client, this.width, this.height);
    }

    @Override
    protected void init() {
        if (error != null) {
            this.addButton(new ButtonWidget(this.width / 2 - 45, this.height / 2 + 20, 90, 20, new LiteralText("Back"),
                    button -> this.client.openScreen(parent)));
        }
    }

    @Override
    public void onClose() {
        this.client.openScreen(parent);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        if (error != null) {
            drawCentered(matrices, "Couldn't open this replay", this.height / 2 - 16, QueueStatus.COLOR_TEXT);
            drawCentered(matrices, error, this.height / 2 - 4, QueueStatus.COLOR_ERROR);
        } else {
            drawCentered(matrices, "Loading replay... " + Math.round(progress * 100) + "%", this.height / 2 - 10, QueueStatus.COLOR_TEXT);
            int barWidth = 160;
            int x = this.width / 2 - barWidth / 2;
            int y = this.height / 2 + 4;
            fill(matrices, x, y, x + barWidth, y + 3, 0x60FFFFFF);
            fill(matrices, x, y, x + Math.round(barWidth * progress), y + 3, 0xFFFFFFFF);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    private void drawCentered(MatrixStack matrices, String line, int y, int color) {
        this.textRenderer.drawWithShadow(matrices, line, this.width / 2f - this.textRenderer.getWidth(line) / 2f, y, color);
    }
}
