package com.coatt.tascade.replay.gui;

import com.coatt.tascade.hud.RunLabels;
import com.coatt.tascade.replay.play.ReplayPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ReplayHud {
    private static final int TEXT = 0xFFFFFF;
    private static final int MUTED = 0xA0A0A0;

    private ReplayHud() {}

    public static void render(MatrixStack matrices, ReplayPlayer player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;
        TextRenderer text = client.textRenderer;
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        String action = player.recentAction();
        if (action != null) {
            int w = text.getWidth(action);
            int y = height / 2 + 24;
            DrawableHelper.fill(matrices, width / 2 - w / 2 - 4, y - 3, width / 2 + w / 2 + 4, y + 11, 0x90000000);
            text.drawWithShadow(matrices, action, width / 2f - w / 2f, y, TEXT);
        }
        if (!player.overlayShown) return;
        if (!client.options.debugEnabled) renderInfo(matrices, player);

        String state = player.isPlaying() ? "Playing " + player.speedLabel() : "Paused";
        String clock = player.formatRunTime(player.time()) + " / " + player.formatRunTime(player.duration);
        String view = player.isPov() ? "Runner's view" : "Free camera";
        String first = state + "  " + clock;
        String resolution = player.runnerResolution();
        String second = view + (resolution != null ? "  ·  " + resolution : "") + "  ·  Esc: controls";
        int widest = Math.max(text.getWidth(first), text.getWidth(second));
        int y = width - 4 - widest < width / 2 + 96 ? height - 72 : height - 22;
        drawRight(matrices, text, first, width - 4, y, TEXT);
        drawRight(matrices, text, second, width - 4, y + 10, MUTED);

        if (player.msSinceAction() < 2500) {
            Timeline.draw(matrices, player, 4, y - 8, width - 8, 3);
        }
    }

    public static void renderInfo(MatrixStack matrices, ReplayPlayer player) {
        TextRenderer text = MinecraftClient.getInstance().textRenderer;
        JSONObject meta = player.meta;
        List<String> lines = new ArrayList<>();
        String mode = "RANKED".equals(meta.optString("mode")) ? "Ranked mode" : "Solo mode";
        String head = meta.has("opponentName")
                ? meta.optString("playerName", "Player") + " vs " + meta.optString("opponentName")
                : meta.optString("playerName", "Player") + " - " + mode;
        lines.add(head);
        String outcome = ReplayControlScreen.outcome(meta);
        if (meta.has("finalRtaMs")) outcome += " " + RunLabels.time(meta.optLong("finalRtaMs"));
        lines.add(outcome);
        String seed = meta.has("seedType") ? RunLabels.seedType(meta.optString("seedType")) : "";
        if (meta.has("bastionType")) seed += (seed.isEmpty() ? "" : " / ") + RunLabels.bastion(meta.optString("bastionType"));
        if (!seed.isEmpty()) lines.add(seed);
        if (meta.has("startedAt")) {
            lines.add(new SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.ENGLISH).format(new Date(meta.optLong("startedAt"))));
        }
        int width = 0;
        for (String line : lines) width = Math.max(width, text.getWidth(line));
        DrawableHelper.fill(matrices, 4, 4, 12 + width, 8 + lines.size() * 10 + 2, 0x90000000);
        for (int i = 0; i < lines.size(); i++) {
            text.drawWithShadow(matrices, lines.get(i), 8, 8 + i * 10, i == 0 ? TEXT : MUTED);
        }
    }

    private static void drawRight(MatrixStack matrices, TextRenderer text, String line, int right, int y, int color) {
        int w = text.getWidth(line);
        DrawableHelper.fill(matrices, right - w - 3, y - 2, right + 2, y + 9, 0x70000000);
        text.drawWithShadow(matrices, line, right - w, y, color);
    }
}
