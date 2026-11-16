package com.coatt.tascade.finish;

import com.coatt.tascade.hud.RunLabels;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.screen.RaceWaitScreen;
import com.coatt.tascade.screen.RoomResultScreen;
import com.coatt.tascade.screen.RunResultScreen;
import com.coatt.tascade.sound.MatchSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

// spectator, outcome title for 5s, then the results screen. client thread only
@Environment(EnvType.CLIENT)
public final class RunFinish {
    private static final long TITLE_MS = 5_000;
    private static final int TITLE_FADE_IN_TICKS = 5;
    private static final int TITLE_STAY_TICKS = 100;
    private static final int TITLE_FADE_OUT_TICKS = 10;
    private static final long RESPAWN_TIMEOUT_MS = 10_000;

    private static RunSummary summary = null;
    private static long titleShownAtMs = 0;
    private static boolean resultsOpened = false;
    private static boolean awaitingOutcome = false;
    private static boolean spectating = false;
    private static RunSummary awaySummary = null;
    private static long respawnRequestedAtMs = 0;

    private RunFinish() {}

    public static boolean isAwaitingOutcome() {
        return awaitingOutcome && summary == null;
    }

    public static boolean inTitlePhase() {
        return awaitingOutcome || (summary != null && !resultsOpened);
    }

    public static boolean isActive() {
        return awaitingOutcome || summary != null;
    }

    public static RunSummary summary() {
        return summary;
    }

    public static void awaitRespawn() {
        respawnRequestedAtMs = System.currentTimeMillis();
    }

    public static void awaitOutcome(MinecraftClient client) {
        awaitingOutcome = true;
        makeSpectator(client);
    }

    public static void awaiting(MinecraftClient client, boolean mine, long deadlineRtaMs, String opponentName) {
        // already over here, or not finished as far as this client knows
        if (summary != null || (mine && !awaitingOutcome)) return;
        String opponent = opponentName != null ? opponentName : "Your opponent";
        String deadline = RunLabels.time(deadlineRtaMs);
        InGameHud hud = client.inGameHud;
        if (mine) {
            LiteralText title = new LiteralText("Finished!");
            LiteralText subtitle = new LiteralText(opponent + " can still win until " + deadline);
            title.formatted(Formatting.YELLOW);
            subtitle.formatted(Formatting.GRAY);
            hud.setTitles(null, null, TITLE_FADE_IN_TICKS, TITLE_STAY_TICKS, TITLE_FADE_OUT_TICKS);
            hud.setTitles(null, subtitle, -1, -1, -1);
            hud.setTitles(title, null, -1, -1, -1);
        } else {
            hud.setOverlayMessage(new LiteralText(opponent + " finished · finish before " + deadline + " to win")
                    .formatted(Formatting.YELLOW), false);
        }
        MatchSounds.deadlineSet();
    }

    public static void solo(MinecraftClient client, long rtaMs, String playerName, String seedType,
                            String bastionType, Map<String, Long> splits, boolean unrated) {
        show(client, new RunSummary(RunSummary.Outcome.COMPLETED, false, false, rtaMs, playerName,
                playerName, null, seedType, bastionType, splits, Collections.emptyMap(), unrated));
    }

    public static void ranked(MinecraftClient client, RunSummary.Outcome outcome, boolean forfeit,
                              long timeMs, String timeHolder, String playerName, String opponentName,
                              String seedType, String bastionType,
                              Map<String, Long> playerSplits, Map<String, Long> opponentSplits,
                              int eloDelta, Integer newElo, double ppAwarded, double newTotalPP,
                              boolean unrated, boolean ratingKnown, boolean provisional) {
        RunSummary current = summary;
        if (!provisional && current != null && current.ranked && current.provisional
                && current.outcome == outcome && current.forfeit == forfeit) {
            current.timeMs = timeMs;
            current.provisional = false;
            if (ratingKnown) setRatings(current, eloDelta, newElo, ppAwarded, newTotalPP);
            return;
        }
        RunSummary ranked = new RunSummary(outcome, true, forfeit, timeMs, timeHolder, playerName,
                opponentName, seedType, bastionType, playerSplits, opponentSplits, unrated);
        ranked.provisional = provisional;
        if (ratingKnown) setRatings(ranked, eloDelta, newElo, ppAwarded, newTotalPP);
        show(client, ranked);
    }

    public static RunSummary rankedAway(RunSummary.Outcome outcome, boolean forfeit, long timeMs, String timeHolder,
                                        String playerName, String opponentName, String seedType, String bastionType,
                                        Map<String, Long> playerSplits, Map<String, Long> opponentSplits,
                                        boolean unrated) {
        RunSummary away = new RunSummary(outcome, true, forfeit, timeMs, timeHolder, playerName,
                opponentName, seedType, bastionType, playerSplits, opponentSplits, unrated);
        awaySummary = away;
        return away;
    }

  public static void room(MinecraftClient client, RunSummary.Outcome outcome, boolean forfeit, long timeMs,
                          int place, String seedType, String bastionType, Map<String, Long> splits) {
    if (summary != null && summary.room) return;
    String playerName = client.getSession().getUsername();
    RunSummary room = new RunSummary(outcome, false, forfeit, timeMs,
            outcome == RunSummary.Outcome.COMPLETED ? playerName : null, playerName, null, seedType, bastionType,
            splits, Collections.emptyMap(), true);
    room.room = true;
    room.place = place;
    room.ratingKnown = true;
    show(client, room);
  }

  public static void roomPlaced(MinecraftClient client, int place) {
    RunSummary current = summary;
    if (current == null || !current.room || current.place == place) return;
    current.place = place;
    if (!resultsOpened && titleShownAtMs > 0) {
      InGameHud hud = client.inGameHud;
      hud.setTitles(null, current.subtitle(), -1, -1, -1);
      hud.setTitles(current.title(), null, -1, -1, -1);
    }
  }

    public static void rankedRated(int eloDelta, Integer newElo, double ppAwarded, double newTotalPP) {
        RunSummary current = summary != null ? summary : awaySummary;
        if (current == null || !current.ranked) return;
        setRatings(current, eloDelta, newElo, ppAwarded, newTotalPP);
    }

    // ratingKnown goes last, the results screen reads the numbers once it's set
    private static void setRatings(RunSummary summary, int eloDelta, Integer newElo, double ppAwarded,
                                   double newTotalPP) {
        summary.eloDelta = eloDelta;
        summary.newElo = newElo;
        summary.ppAwarded = ppAwarded;
        summary.newTotalPP = newTotalPP;
        summary.ratingKnown = true;
    }

    public static void soloRecorded(double ppAwarded, double newTotalPP) {
        RunSummary current = summary;
        if (current == null || current.ranked) return;
        current.ppAwarded = ppAwarded;
        current.newTotalPP = newTotalPP;
        current.ratingKnown = true;
    }

    public static void tick(MinecraftClient client) {
        if (respawnRequestedAtMs > 0 && (respawned(client)
                || System.currentTimeMillis() - respawnRequestedAtMs > RESPAWN_TIMEOUT_MS)) {
            respawnRequestedAtMs = 0;
        }
        if (summary == null || resultsOpened) return;
        if (titleShownAtMs == 0) {
            if (respawnRequestedAtMs == 0) showTitle(client);
            return;
        }
        if (System.currentTimeMillis() - titleShownAtMs < TITLE_MS) return;
        resultsOpened = true;
        client.openScreen(summary.room ? new RoomResultScreen(summary) : new RunResultScreen(summary));
    }

    public static void clearTitle(MinecraftClient client) {
        client.inGameHud.setTitles(null, null, -1, -1, -1);
    }

    public static void reset() {
        summary = null;
        titleShownAtMs = 0;
        resultsOpened = false;
        awaitingOutcome = false;
        spectating = false;
        respawnRequestedAtMs = 0;
    }

    private static void show(MinecraftClient client, RunSummary finished) {
        summary = finished;
        awaySummary = null;
        awaitingOutcome = false;
        resultsOpened = false;
        titleShownAtMs = 0;
        makeSpectator(client);

        if (client.currentScreen instanceof RaceWaitScreen || client.currentScreen instanceof RunResultScreen
                || client.currentScreen instanceof RoomResultScreen) {
            client.openScreen(null);
        }
        if (respawnRequestedAtMs == 0) showTitle(client);
    }

    private static void showTitle(MinecraftClient client) {
        RunSummary finished = summary;
        titleShownAtMs = System.currentTimeMillis();
        // setTitles does one thing per call: timings, subtitle, then title (which starts it)
        InGameHud hud = client.inGameHud;
        hud.setTitles(null, null, TITLE_FADE_IN_TICKS, TITLE_STAY_TICKS, TITLE_FADE_OUT_TICKS);
        hud.setTitles(null, finished.subtitle(), -1, -1, -1);
        hud.setTitles(finished.title(), null, -1, -1, -1);
        if (finished.ranked) MatchSounds.result(finished.outcome);
        else if (finished.outcome == RunSummary.Outcome.COMPLETED) MatchSounds.result(RunSummary.Outcome.VICTORY);
        else if (finished.room && finished.outcome == RunSummary.Outcome.DEFEAT) MatchSounds.result(RunSummary.Outcome.DEFEAT);
    }

    private static boolean respawned(MinecraftClient client) {
        return client.world != null && client.player != null && client.interactionManager != null
                && client.world.getRegistryKey() == World.OVERWORLD
                && !(client.currentScreen instanceof DownloadingTerrainScreen)
                && client.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR;
    }

    private static void makeSpectator(MinecraftClient client) {
        if (spectating) return;
        IntegratedServer server = client.getServer();
        if (server == null || client.player == null) return;
        spectating = true;
        UUID id = client.player.getUuid();
        server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) player.setGameMode(GameMode.SPECTATOR);
        });
    }

    public static long elapsedSinceRaceStart() {
        long started = RunState.raceStartedAtMs;
        return started > 0 ? Math.max(0, System.currentTimeMillis() - started) : 0;
    }
}
