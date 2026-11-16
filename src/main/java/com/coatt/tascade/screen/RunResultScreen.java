package com.coatt.tascade.screen;

import com.coatt.tascade.chat.MatchChatDock;
import com.coatt.tascade.chat.MatchChatOverlay;
import com.coatt.tascade.finish.RunFinish;
import com.coatt.tascade.finish.RunSummary;
import com.coatt.tascade.hud.RunLabels;
import com.coatt.tascade.integration.RunRecorder;
import com.coatt.tascade.network.RunState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.Rect2i;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class RunResultScreen extends TascadeScreen implements MatchChatDock {
    private static final long FADE_MS = 600;
    private static final int LEFT = 14;
    private static final int LINE = 12;
    private static final int COLUMN_GAP = 16;
    private static final int COLOR_FASTER = 0x55FF55;
    private static final String NO_TIME = "-";

    private final RunSummary summary;
    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private int contentRight = 0;

    public RunResultScreen(RunSummary summary) {
        this(summary, null);
    }

    public RunResultScreen(RunSummary summary, Screen parent) {
        super(summary.title());
        this.summary = summary;
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (this.parent != null) {
            this.addButton(new ButtonWidget(LEFT, this.height - 20 - 10, 100, 20, new LiteralText("Back"),
                    buttonWidget -> this.onClose()));
            return;
        }
        this.addButton(new ButtonWidget(
                LEFT,
                this.height - 20 - 10,
                100,
                20,
                new LiteralText("Back to Menu"),
                buttonWidget -> {
                    if (this.client.world != null) {
                        this.client.world.disconnect();
                        this.client.disconnect();
                    }
                    this.client.openScreen(new TitleScreen());
                }
        ));
        this.addButton(new ButtonWidget(
                LEFT + 110,
                this.height - 20 - 10,
                100,
                20,
                new LiteralText("Spectate"),
                buttonWidget -> this.onClose()
        ));
    }

    @Override
    public void onClose() {
        if (this.parent != null) {
            this.client.openScreen(this.parent);
            return;
        }
        RunFinish.clearTitle(this.client);
        this.client.openScreen(null);
    }

    @Override
    protected float backgroundAlpha() {
        return this.parent != null ? 1f : fade();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        float alpha = fade();
        this.renderBackground(matrices);
        contentRight = LEFT + 2 * this.textRenderer.getWidth(summary.title());
        for (AbstractButtonWidget button : this.buttons) button.setAlpha(alpha);

        // whole-number scale
        matrices.push();
        matrices.scale(2f, 2f, 1f);
        this.textRenderer.drawWithShadow(matrices, summary.title(), LEFT / 2f, LEFT / 2f,
                withAlpha(QueueStatus.COLOR_TEXT, alpha));
        matrices.pop();

        int y = LEFT + 24;
        y = line(matrices, summary.subtitle(), LEFT, y, QueueStatus.COLOR_TEXT, alpha);
        y = line(matrices, new LiteralText(context()), LEFT, y, QueueStatus.COLOR_MUTED, alpha);
        y = line(matrices, new LiteralText(summary.reason()), LEFT, y, QueueStatus.COLOR_MUTED, alpha);

        y = splitTable(matrices, y + 8, alpha);
        rating(matrices, y + 8, alpha);

        super.render(matrices, mouseX, mouseY, delta);
    }

    private String context() {
        StringBuilder context = new StringBuilder(summary.ranked ? "Ranked" : "Solo");
        if (summary.seedType != null && !summary.seedType.isEmpty()) {
            context.append(" / ").append(RunLabels.seedType(summary.seedType));
        }
        if (summary.bastionType != null && !summary.bastionType.isEmpty()) {
            context.append(" / ").append(RunLabels.bastion(summary.bastionType));
        }
        return context.toString();
    }

    private int splitTable(MatrixStack matrices, int y, float alpha) {
        List<String> rows = new ArrayList<>();
        for (String split : RunRecorder.trackedSplits()) {
            if (summary.playerSplits.containsKey(split) || summary.opponentSplits.containsKey(split)) rows.add(split);
        }
        if (rows.isEmpty()) {
            return line(matrices, new LiteralText("No splits recorded"), LEFT, y, QueueStatus.COLOR_MUTED, alpha);
        }

        boolean versus = summary.ranked && summary.opponentName != null;
        int labelWidth = this.textRenderer.getWidth("Split");
        for (String split : rows) labelWidth = Math.max(labelWidth, this.textRenderer.getWidth(RunLabels.split(split)));
        int youX = LEFT + labelWidth + COLUMN_GAP;
        int timeWidth = Math.max(this.textRenderer.getWidth(RunLabels.time(0)), this.textRenderer.getWidth("You"));
        int opponentX = youX + timeWidth + COLUMN_GAP;

        text(matrices, new LiteralText("Split"), LEFT, y, QueueStatus.COLOR_MUTED, alpha);
        text(matrices, new LiteralText("You"), youX, y, QueueStatus.COLOR_MUTED, alpha);
        if (versus) text(matrices, new LiteralText(summary.opponentName), opponentX, y, QueueStatus.COLOR_MUTED, alpha);
        y += LINE;

        for (String split : rows) {
            Long mine = summary.playerSplits.get(split);
            Long theirs = summary.opponentSplits.get(split);
            text(matrices, RunLabels.split(split), LEFT, y, QueueStatus.COLOR_TEXT, alpha);
            text(matrices, new LiteralText(mine != null ? RunLabels.time(mine) : NO_TIME), youX, y,
                    timeColor(mine, theirs, versus), alpha);
            if (versus) {
                text(matrices, new LiteralText(theirs != null ? RunLabels.time(theirs) : NO_TIME), opponentX, y,
                        timeColor(theirs, mine, true), alpha);
            }
            y += LINE;
        }
        return y;
    }

    private void rating(MatrixStack matrices, int y, float alpha) {
        if (summary.unrated) {
            line(matrices, new LiteralText("Unrated"), LEFT, y, QueueStatus.COLOR_MUTED, alpha);
            return;
        }
        if (!summary.ratingKnown) {
            String error = RunState.lastError;
            if (error != null) line(matrices, new LiteralText(error), LEFT, y, QueueStatus.COLOR_ERROR, alpha);
            else line(matrices, new LiteralText("Loading results..."), LEFT, y, QueueStatus.COLOR_MUTED, alpha);
            return;
        }
        if (summary.ranked && summary.newElo != null) {
            String delta = (summary.eloDelta >= 0 ? "+" : "") + summary.eloDelta;
            y = line(matrices, new LiteralText("Elo: " + summary.newElo + " (" + delta + ")"), LEFT, y,
                    QueueStatus.COLOR_TEXT, alpha);
        }
        if (!Double.isNaN(summary.ppAwarded) && summary.ppAwarded > 0) {
            String total = Double.isNaN(summary.newTotalPP) ? "" : " (total " + RunLabels.pp(summary.newTotalPP) + ")";
            line(matrices, new LiteralText(RunLabels.pp(summary.ppAwarded) + total), LEFT, y,
                    QueueStatus.COLOR_TEXT, alpha);
        }
    }

    private static int timeColor(Long time, Long other, boolean versus) {
        if (time == null) return QueueStatus.COLOR_MUTED;
        if (!versus) return QueueStatus.COLOR_TEXT;
        return other == null || time <= other ? COLOR_FASTER : QueueStatus.COLOR_MUTED;
    }

    private int line(MatrixStack matrices, StringRenderable text, int x, int y, int rgb, float alpha) {
        text(matrices, text, x, y, rgb, alpha);
        return y + LINE;
    }

    private void text(MatrixStack matrices, StringRenderable text, int x, int y, int rgb, float alpha) {
        this.textRenderer.drawWithShadow(matrices, text, x, y, withAlpha(rgb, alpha));
        contentRight = Math.max(contentRight, x + this.textRenderer.getWidth(text));
    }

    @Override
    public Rect2i matchChatArea() {
        int right = this.width - LEFT;
        int left = Math.max(contentRight + COLUMN_GAP * 2, right - MatchChatOverlay.dockedWidth(this.client));
        int top = LEFT + 24;
        return new Rect2i(left, top, right - left, this.height - 10 - top);
    }

    private float fade() {
        return MathHelper.clamp((System.currentTimeMillis() - openedAt) / (float) FADE_MS, 0f, 1f);
    }

    // alpha below 4 renders fully opaque so the fade starts from 5
    private static int withAlpha(int rgb, float alpha) {
        int a = Math.max(5, Math.round(alpha * 255));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
