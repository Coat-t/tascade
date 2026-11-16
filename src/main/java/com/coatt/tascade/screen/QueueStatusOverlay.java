package com.coatt.tascade.screen;

import com.coatt.tascade.network.RunState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;

@Environment(EnvType.CLIENT)
public final class QueueStatusOverlay {
    private static final int MARGIN = 4;
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int BACKGROUND = 0x90000000;
    private static final String SEPARATOR = " · ";

    private QueueStatusOverlay() {}

    public static int render(MinecraftClient client, int top) {
        if (!RunState.isQueued()) return 0;
        if (client.currentScreen instanceof RankedQueueScreen) return 0;

        String[][] lines = {
                {"Ranked", SEPARATOR, QueueStatus.connection()},
                {QueueStatus.status(), SEPARATOR, QueueStatus.elapsedTime()},
        };
        int[][] colors = {
                {QueueStatus.COLOR_ACCENT, QueueStatus.COLOR_MUTED, QueueStatus.connectionColor()},
                {QueueStatus.COLOR_TEXT, QueueStatus.COLOR_MUTED, QueueStatus.COLOR_MUTED},
        };

        TextRenderer textRenderer = client.textRenderer;
        int width = 0;
        for (String[] line : lines) {
            int lineWidth = 0;
            for (String segment : line) lineWidth += textRenderer.getWidth(segment);
            width = Math.max(width, lineWidth);
        }

        MatrixStack matrices = new MatrixStack();
        int left = MARGIN;
        int panelTop = top + MARGIN;
        int right = left + PADDING * 2 + width;
        int bottom = panelTop + PADDING * 2 + lines.length * LINE_HEIGHT - 2;

        // the panel at the very top
        RenderSystem.disableDepthTest();
        DrawableHelper.fill(matrices, left, panelTop, right, bottom, BACKGROUND);
        for (int i = 0; i < lines.length; i++) {
            int x = left + PADDING;
            int y = panelTop + PADDING + i * LINE_HEIGHT;
            for (int j = 0; j < lines[i].length; j++) {
                textRenderer.drawWithShadow(matrices, lines[i][j], x, y, colors[i][j]);
                x += textRenderer.getWidth(lines[i][j]);
            }
        }
        RenderSystem.enableDepthTest();
        return bottom;
    }
}
