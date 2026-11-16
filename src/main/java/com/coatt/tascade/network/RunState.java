package com.coatt.tascade.network;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// written from socket threads, so every field is volatile
public final class RunState {
  public enum Mode { NONE, SOLO, RANKED, ROOM }

  public enum QueueState { NONE, QUEUE, COUNTDOWN }

  public enum Vote { NONE, SEED, DRAW }

  public static volatile Mode mode = Mode.NONE;
  public static volatile String matchId = null;

  public static volatile QueueState queueState = QueueState.NONE;
  public static volatile int queueSize = 0;
  public static volatile int countdownSeconds = 0;
  public static volatile long queueStartedAtMs = 0;

  public static volatile boolean connected = false;
  public static volatile int reconnectAttempt = 0;
  public static volatile long latencyMs = -1;

  public static volatile String opponentName = null;
  public static volatile int opponentElo = 0;
  public static volatile String opponentUuid = null;
  public static final Map<String, Long> opponentSplits = new ConcurrentHashMap<>();
  public static final Map<String, Long> opponentGoals = new ConcurrentHashMap<>();
  public static volatile int opponentWorldProgress = -1;

  // dimension changes rejoin a world, which must not report ready again
  public static volatile boolean readySent = false;

  public static volatile boolean waitingForRaceStart = false;

  public static volatile int raceCountdown = -1;

  public static volatile String seedType = null;

  public static volatile String bastionType = null;

  public static volatile boolean cheats = false;

  public static volatile List<String> modifiers = Collections.emptyList();
  public static volatile double ppMultiplier = 1;
  public static volatile double pace = 1;
  public static volatile List<String> opponentModifiers = Collections.emptyList();
  public static volatile double opponentPace = 1;

  public static volatile long awaitingDeadlineRtaMs = -1;
  public static volatile boolean awaitingIsMine = false;

  public static volatile long raceStartedAtMs = 0;

  // mirrors the server's SEED_CHANGE_WINDOW_MS
  private static final long OPENING_WINDOW_MS = 120_000;

  public static volatile Vote pendingVote = Vote.NONE;
  public static volatile boolean pendingVoteIsMine = false;
  public static volatile boolean voteAgreed = false;
  public static volatile long voteSentAtMs = 0;

  public static volatile long soloSeedRequestedAtMs = 0;

  // so leaving the old world isn't treated as abandoning the run
  public static volatile boolean seedChanging = false;

  public static volatile String lastError = null;

  public static volatile boolean rejoined = false;

  public static volatile boolean worldReset = false;

  private RunState() {}

  public static boolean isRunning() {
    return mode != Mode.NONE;
  }

  public static boolean isRanked() {
    return mode == Mode.RANKED;
  }

  public static boolean isRoom() {
    return mode == Mode.ROOM;
  }

  public static boolean isVersus() {
    return mode == Mode.RANKED || mode == Mode.ROOM;
  }

  public static boolean isSolo() {
    return mode == Mode.SOLO;
  }

  public static boolean isQueued() {
    return queueState != QueueState.NONE;
  }

  public static boolean seedChangeAvailable() {
    if (mode == Mode.SOLO) return true;
    if (mode != Mode.RANKED) return false;
    if (pendingVote == Vote.SEED) return true;
    return inOpeningWindow();
  }

  public static boolean forfeitAvailable() {
    return mode == Mode.RANKED && !inOpeningWindow();
  }

  public static int secondsUntilForfeit() {
    if (mode != Mode.RANKED || !inOpeningWindow()) return 0;
    long left = OPENING_WINDOW_MS - (System.currentTimeMillis() - raceStartedAtMs);
    return (int) Math.max(1, (left + 999) / 1000);
  }

  // an unknown start (e.g. after reconnecting) counts as past it
  private static boolean inOpeningWindow() {
    return raceStartedAtMs > 0 && System.currentTimeMillis() - raceStartedAtMs < OPENING_WINDOW_MS;
  }

  public static void endRun() {
    mode = Mode.NONE;
    matchId = null;
    cheats = false;
    modifiers = Collections.emptyList();
    ppMultiplier = 1;
    pace = 1;
    opponentModifiers = Collections.emptyList();
    opponentPace = 1;
    awaitingDeadlineRtaMs = -1;
    awaitingIsMine = false;
    readySent = false;
    waitingForRaceStart = false;
    raceCountdown = -1;
    raceStartedAtMs = 0;
    opponentSplits.clear();
    opponentGoals.clear();
    opponentWorldProgress = -1;
    clearVote();
    soloSeedRequestedAtMs = 0;
    seedChanging = false;
    rejoined = false;
    worldReset = false;
    com.coatt.tascade.world.WorldSnapshot.discard();
    RunResume.forget();
  }

  public static void clearVote() {
    pendingVote = Vote.NONE;
    pendingVoteIsMine = false;
    voteAgreed = false;
    voteSentAtMs = 0;
  }
}
