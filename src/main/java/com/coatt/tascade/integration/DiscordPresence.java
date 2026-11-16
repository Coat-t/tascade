package com.coatt.tascade.integration;

import com.coatt.tascade.Main;
import com.coatt.tascade.TascadeConfig;
import com.coatt.tascade.finish.RunFinish;
import com.coatt.tascade.finish.RunSummary;
import com.coatt.tascade.hud.RunLabels;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.replay.gui.ReplayControlScreen;
import com.coatt.tascade.replay.play.ReplayPlayer;
import com.coatt.tascade.screen.QueueStatus;
import net.minecraft.client.MinecraftClient;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DiscordPresence {
    private static final String CLIENT_ID = "1559840896738926614";
    private static final String LARGE_IMAGE = "logo";

    private static final int UPDATE_INTERVAL_TICKS = 40;
    // Discord allows five updates per 20s
    private static final long MIN_SEND_INTERVAL_MS = 5_000;
    private static final long RECONNECT_INTERVAL_MS = 20_000;
    private static final long SESSION_STARTED_AT_MS = ManagementFactory.getRuntimeMXBean().getStartTime();

    private static final Object LOCK = new Object();
    private static JSONObject wanted = null;
    private static boolean changed = false;

    private static int ticks = 0;
    private static String lastWanted = null;
    private static final String OFF = "";

    private DiscordPresence() {}

    public static void init() {
        if (CLIENT_ID.isEmpty()) {
            Main.LOGGER.info("No Discord application set!");
            return;
        }
        Thread worker = new Thread(DiscordPresence::run, "Tascade-Discord");
        worker.setDaemon(true);
        worker.start();
    }

    public static void tick(MinecraftClient client) {
        if (CLIENT_ID.isEmpty() || ++ticks < UPDATE_INTERVAL_TICKS) return;
        ticks = 0;
        JSONObject activity = TascadeConfig.discordPresence() ? activity(client) : null;
        String json = activity != null ? activity.toString() : OFF;
        if (json.equals(lastWanted)) return;
        lastWanted = json;
        synchronized (LOCK) {
            wanted = activity;
            changed = true;
            LOCK.notifyAll();
        }
    }

    private static JSONObject activity(MinecraftClient client) {
        ReplayPlayer replay = ReplayPlayer.get();
        if (replay != null) {
            return replay(client, replay.meta);
        }
        RunSummary summary = RunFinish.summary();
        if (summary != null) {
            return finished(summary);
        }
        if (RunFinish.isActive()) {
            return activity(rankedDetails(), "Finished (awaiting result)");
        }
        if (RunState.isRunning()) {
            String details = RunState.isRanked() ? rankedDetails() : RunState.isRoom() ? roomDetails() : "Solo run";
            if (RunState.waitingForRaceStart) {
                return activity(details, RunState.raceCountdown > 0 ? "Starting..." : "Loading world");
            }
            return activity(details, progress());
        }
        if (RunState.isQueued()) {
            String state = QueueStatus.matchFound() ? "Match found" : "Searching for opponent";
            return activity("In the ranked queue", state);
        }
        if (client.world != null) {
            return activity("Playing Minecraft", null);
        }
        return activity("In the menus", profile());
    }

    private static String roomDetails() {
        int players = com.coatt.tascade.network.RoomState.racers().size();
        return "Custom room" + (players > 1 ? " · " + players + " players" : "");
    }

    private static String rankedDetails() {
        String opponent = RunState.opponentName;
        return opponent != null
                ? me() + " vs " + opponent + " (" + RunState.opponentElo + " elo)"
                : "Ranked match";
    }

    private static String me() {
        return MinecraftClient.getInstance().getSession().getUsername();
    }

    private static String progress() {
        Map<String, Long> reached = RunRecorder.splitTimes();
        List<String> route = RunRecorder.trackedSplits();
        for (int i = route.size() - 1; i >= 0; i--) {
            String split = route.get(i);
            if (reached.containsKey(split)) return RunLabels.split(split).getString();
        }
        String seedType = RunState.seedType;
        return seedType != null ? RunLabels.seedType(seedType) + " seed" : "In the Overworld";
    }

    private static JSONObject finished(RunSummary summary) {
        String details = summary.ranked
                ? (summary.opponentName != null ? me() + " vs " + summary.opponentName : "Ranked match")
                : summary.room ? "Custom room" : "Solo run";
        return activity(details, summary.title().getString() + " (" + RunLabels.time(summary.timeMs)+ ")");
    }

    private static JSONObject replay(MinecraftClient client, JSONObject meta) {
        String runner = meta.optString("playerName", "");
        boolean own = runner.isEmpty() || runner.equals(client.getSession().getUsername());
        String details = own ? "Watching a replay" : "Watching " + runner + "'s replay";
        String run = "RANKED".equals(meta.optString("mode"))
                ? (meta.has("opponentName") ? (runner.isEmpty() ? me() : runner) + " vs " + meta.optString("opponentName") : "Ranked match")
                : "Solo run";
        String state = run + " · " + ReplayControlScreen.outcome(meta);
        if (meta.has("finalRtaMs")) state += " (" + RunLabels.time(meta.optLong("finalRtaMs")) + ")";
        if (own) return activity(details, state);
        UUID runnerId;
        try {
            runnerId = UUID.fromString(meta.optString("playerUuid", ""));
        } catch (IllegalArgumentException e) {
            runnerId = null;
        }
        return activity(details, state, runnerId, runner);
    }

    private static String profile() {
        Integer elo = Main.elo;
        Integer pp = Main.pp;
        if (Main.profileStatus != Main.ProfileState.SUCCESS || elo == null || pp == null) return null;
        return elo + " elo - " + RunLabels.pp(pp);
    }

    private static JSONObject activity(String details, String state) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = client.getSession().getUsername();
        Integer elo = Main.elo;
        if (Main.profileStatus == Main.ProfileState.SUCCESS && elo != null) text += " - " + elo + " elo";
        return activity(details, state, client.getSession().getProfile().getId(), text);
    }

    private static JSONObject activity(String details, String state, UUID player, String playerText) {
        JSONObject activity = new JSONObject();
        activity.put("details", clip(details));
        if (state != null) activity.put("state", clip(state));
        activity.put("timestamps", new JSONObject().put("start", SESSION_STARTED_AT_MS));
        JSONObject assets = new JSONObject();
        assets.put("large_image", LARGE_IMAGE);
        assets.put("large_text", "Tascade - 1.16.1 speedrunning mod");
        String head = player != null ? PlayerHeads.url(MinecraftClient.getInstance(), player) : null;
        if (head != null) {
            assets.put("small_image", head);
            assets.put("small_text", clip(playerText));
        }
        activity.put("assets", assets);
        return activity;
    }

    private static String clip(String text) {
        return text.length() <= 128 ? text : text.substring(0, 127) + "…";
    }

    private static void run() {
        DiscordIpc ipc = null;
        long lastSentAtMs = 0;
        long nextConnectAtMs = 0;
        boolean warned = false;
        while (true) {
            JSONObject activity;
            try {
                synchronized (LOCK) {
                    while (!changed) LOCK.wait();
                }
                long wait = Math.max(lastSentAtMs + MIN_SEND_INTERVAL_MS, nextConnectAtMs) - System.currentTimeMillis();
                if (wait > 0) Thread.sleep(wait);
                synchronized (LOCK) {
                    activity = wanted;
                    changed = false;
                }
            } catch (InterruptedException e) {
                break;
            }

            if (activity == null) {
                if (ipc != null) {
                    try {
                        ipc.setActivity(null);
                    } catch (IOException | RuntimeException e) {
                    }
                    ipc.close();
                    ipc = null;
                    Main.LOGGER.info("Discord rich presence turned off");
                }
                lastSentAtMs = System.currentTimeMillis();
                continue;
            }

            try {
                if (ipc == null) {
                    ipc = DiscordIpc.connect(CLIENT_ID);
                    Main.LOGGER.info("Connected to Discord for rich presence");
                    warned = false;
                }
                ipc.setActivity(activity);
                lastSentAtMs = System.currentTimeMillis();
            } catch (IOException e) {
                if (ipc != null) {
                    ipc.close();
                    ipc = null;
                    Main.LOGGER.info("Lost the connection to Discord: {}", e.getMessage());
                } else if (!warned) {
                    Main.LOGGER.info("Discord rich presence unavailable: {}", e.getMessage());
                    warned = true;
                }
                nextConnectAtMs = System.currentTimeMillis() + RECONNECT_INTERVAL_MS;
                requeue(activity);
            } catch (RuntimeException e) {
                Main.LOGGER.warn("Discord rejected the rich presence", e);
                lastSentAtMs = System.currentTimeMillis();
            }
        }
        if (ipc != null) ipc.close();
    }

    private static void requeue(JSONObject activity) {
        synchronized (LOCK) {
            if (changed) return;
            wanted = activity;
            changed = true;
        }
    }
}
