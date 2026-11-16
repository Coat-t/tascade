package com.coatt.tascade.replay.gui;

import com.coatt.tascade.replay.play.ReplayPlayer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;

public final class Timeline {
    private Timeline() {}

    public static void draw(MatrixStack matrices, ReplayPlayer player, int x, int y, int width, int height) {
        DrawableHelper.fill(matrices, x - 1, y - 1, x + width + 1, y + height + 1, 0xA0000000);
        for (ReplayPlayer.Band band : player.bands()) {
            int from = x + px(player, band.start, width);
            int to = x + px(player, band.end, width);
            DrawableHelper.fill(matrices, from, y, Math.max(from + 1, to), y + height, 0xB0000000 | band.color);
        }
        int head = x + px(player, player.time(), width);
        DrawableHelper.fill(matrices, x, y, head, y + height, 0x40FFFFFF);
        for (ReplayPlayer.Marker marker : player.markers()) {
            int mx = x + px(player, marker.time, width);
            if (marker.major) {
                DrawableHelper.fill(matrices, mx, y - 3, mx + 1, y + height + 3, 0xFF000000 | marker.color);
            } else {
                DrawableHelper.fill(matrices, mx, y + height / 2, mx + 1, y + height, 0xFF000000 | marker.color);
            }
        }
        DrawableHelper.fill(matrices, head - 1, y - 4, head + 1, y + height + 4, 0xFFFFFFFF);
    }

    public static double timeAt(ReplayPlayer player, int x, int width, double mouseX) {
        double fraction = Math.max(0, Math.min(1, (mouseX - x) / width));
        return fraction * player.duration;
    }

    public static ReplayPlayer.Marker markerNear(ReplayPlayer player, int x, int width, double mouseX) {
        ReplayPlayer.Marker nearest = null;
        double best = 3.5;
        for (ReplayPlayer.Marker marker : player.markers()) {
            double distance = Math.abs(x + px(player, marker.time, width) - mouseX);
            if (distance < best || (distance == best && marker.major)) {
                best = distance;
                nearest = marker;
            }
        }
        return nearest;
    }

    public static ReplayPlayer.Band bandAt(ReplayPlayer player, double time) {
        for (ReplayPlayer.Band band : player.bands()) {
            if (time >= band.start && time < band.end) return band;
        }
        return null;
    }

    private static int px(ReplayPlayer player, double time, int width) {
        return (int) Math.round(Math.max(0, Math.min(1, time / player.duration)) * width);
    }
}
