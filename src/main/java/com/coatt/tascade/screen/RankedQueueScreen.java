package com.coatt.tascade.screen;

import com.coatt.tascade.TascadeConfig;
import com.coatt.tascade.chat.MatchChatDock;
import com.coatt.tascade.chat.MatchChatOverlay;
import com.coatt.tascade.modifier.RunModifier;
import com.coatt.tascade.modifier.RunModifiers;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.network.TascadeClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.Rect2i;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

@Environment(EnvType.CLIENT)
public class RankedQueueScreen extends TascadeScreen implements MatchChatDock {
    private final Screen parent;
    private final int bottomPadding = 10;
    private int statusBottom = 0;

    public RankedQueueScreen(Screen parent) {
        super(new LiteralText("Ranked queue"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addButton(new ButtonWidget(
                14,
                this.height - 20 - bottomPadding,
                80,
                20,
                new LiteralText("Cancel"),
                buttonWidget -> cancel()
        ));
        this.addButton(new ButtonWidget(
                100,
                this.height - 20 - bottomPadding,
                130,
                20,
                new LiteralText("Queue in Background"),
                buttonWidget -> this.client.openScreen(parent)
        ));
    }

    @Override
    public void onClose() {
        cancel();
    }

    public void cancel() {
        TascadeClient.getInstance().leaveQueue();
        if (this.client != null) {
            this.client.openScreen(this.parent);
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        this.drawTextWithShadow(matrices, this.textRenderer, this.title, 14, 14, QueueStatus.COLOR_ACCENT);

        int y = 40;
        if (!RunState.isQueued()) {
            y = line(matrices, "Not in queue", y, QueueStatus.COLOR_TEXT);
            if (RunState.lastError != null) y = line(matrices, RunState.lastError, y + 8, QueueStatus.COLOR_ERROR);
            statusBottom = y;
            super.render(matrices, mouseX, mouseY, delta);
            return;
        }
        y = line(matrices, QueueStatus.status(), y, QueueStatus.COLOR_TEXT);
        String progress = QueueStatus.matchFound()
                ? "Starting in " + RunState.countdownSeconds + "s"
                : QueueStatus.players();
        if (progress != null) y = line(matrices, progress, y, QueueStatus.COLOR_MUTED);
        y = line(matrices, QueueStatus.elapsed(), y, QueueStatus.COLOR_MUTED);
        y = line(matrices, QueueStatus.connection(), y, QueueStatus.connectionColor());
        if (TascadeConfig.cheats()) y = line(matrices, "Cheats on: this match won't be rated", y, QueueStatus.COLOR_MUTED);
        int modifiers = RunModifiers.selectedIds().size();
        if (modifiers > 0) {
            y = line(matrices, modifiers + (modifiers == 1 ? " run modifier: " : " run modifiers: ")
                    + RunModifier.format(RunModifiers.multiplier()) + " pp, pace "
                    + RunModifier.format(RunModifiers.pace()), y, QueueStatus.COLOR_MUTED);
        }

        if (RunState.lastError != null) {
            y = line(matrices, RunState.lastError, y + 8, QueueStatus.COLOR_ERROR);
        }
        statusBottom = y;

        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public Rect2i matchChatArea() {
        int bottom = this.height - 20 - bottomPadding - 6;
        int width = Math.min(MatchChatOverlay.dockedWidth(this.client), this.width - 28);
        return new Rect2i(14, statusBottom, width, bottom - statusBottom);
    }

    private int line(MatrixStack matrices, String text, int y, int color) {
        this.drawTextWithShadow(matrices, this.textRenderer, new LiteralText(text), 14, y, color);
        return y + 14;
    }
}
