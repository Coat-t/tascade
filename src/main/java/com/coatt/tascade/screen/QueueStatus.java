package com.coatt.tascade.screen;

import com.coatt.tascade.network.RunState;

public final class QueueStatus {
    public static final int COLOR_ACCENT = 0x7FFFD4;
    public static final int COLOR_TEXT = 16777215;
    public static final int COLOR_MUTED = 10066329;
    public static final int COLOR_ERROR = 16733525;

    private QueueStatus() {}

    public static boolean matchFound() {
        return RunState.queueState == RunState.QueueState.COUNTDOWN;
    }

    public static String status() {
        if (!matchFound()) return "Searching for opponent...";
        String opponent = RunState.opponentName;
        return opponent != null
                ? "Opponent: " + opponent + " (" + RunState.opponentElo + " elo)"
                : "Opponent found!";
    }

    public static String elapsed() {
        return elapsedTime() + " Elapsed";
    }

    public static String elapsedTime() {
        long started = RunState.queueStartedAtMs;
        long seconds = started > 0 ? Math.max(0, (System.currentTimeMillis() - started) / 1000) : 0;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    public static String players() {
        int size = RunState.queueSize;
        if (size <= 0) return null;
        return size + (size == 1 ? " Player" : " Players") + " in queue";
    }

    public static String connection() {
        if (!RunState.connected) {
            int attempt = RunState.reconnectAttempt;
            return attempt > 0 ? "Reconnecting...(" + attempt + ")" : "Reconnecting...";
        }
        long latency = RunState.latencyMs;
        return latency >= 0 ? latency + "ms" : "Measuring...";
    }

    public static int connectionColor() {
        return RunState.connected ? COLOR_MUTED : COLOR_ERROR;
    }
}
