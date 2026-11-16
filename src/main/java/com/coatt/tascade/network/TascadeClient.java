package com.coatt.tascade.network;

import com.coatt.tascade.Main;
import com.coatt.tascade.TascadeConfig;
import com.coatt.tascade.chat.MatchChat;
import com.coatt.tascade.finish.RunFinish;
import com.coatt.tascade.finish.RunSummary;
import com.coatt.tascade.integration.RunGoals;
import com.coatt.tascade.integration.RunRecorder;
import com.coatt.tascade.integration.StandardSettingsCompat;
import com.coatt.tascade.mixin.access.ServerPlayerEntityAccessor;
import com.coatt.tascade.modifier.ModifierEffects;
import com.coatt.tascade.modifier.RunModifiers;
import com.coatt.tascade.replay.record.ReplayRecorder;
import com.coatt.tascade.world.RunWorldOpening;
import com.coatt.tascade.world.WorldSnapshot;
import com.coatt.tascade.screen.BackendCache;
import com.coatt.tascade.screen.MainScreen;
import com.coatt.tascade.screen.RaceWaitScreen;
import com.coatt.tascade.screen.RejoinScreen;
import com.coatt.tascade.screen.RunResultScreen;
import com.coatt.tascade.screen.RunNoticeScreen;
import com.coatt.tascade.screen.SeedChangeScreen;
import com.coatt.tascade.screen.TascadeScreen;
import com.coatt.tascade.sound.MatchSounds;
import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.SaveLevelScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public class TascadeClient {
  private Socket socket;
  private static final String SERVER_URL = "https://tascadebackend-production.up.railway.app";
  private static final int API_TIMEOUT_MS = 8_000;
  private static final String CONFIG_DIR = "config";
  private static final String CONFIG_FILE = "tascade.json";

  private static final int READY_COUNTDOWN_SECONDS = 5;
  private static final long SOLO_COUNTDOWN_MS = READY_COUNTDOWN_SECONDS * 1000L;

  private static final long PING_INTERVAL_MS = 5_000;
  private static final long PING_TIMEOUT_MS = 10_000;
  private static final long WORLD_PROGRESS_INTERVAL_MS = 250;
  private static final long VOTE_ANSWER_TIMEOUT_MS = 5_000;
  private static final long SOLO_SEED_TIMEOUT_MS = 15_000;
  // matches the backend's FORFEIT_GRACE_MS / SOLO_GRACE_MS
  private static final long RECONNECT_GRACE_MS = 120_000;

  private static final String SERVER_NOTICE_UUID = "00000000-0000-0000-0000-000000000000";
  private static final int LEADERBOARD_PAGE_LIMIT = 50;

  private static TascadeClient INSTANCE;

  private volatile boolean authenticated = false;
  private String reconnectKey = null;
  private volatile String playerUuid = null;

  private volatile boolean needsRequeue = false;
  private volatile long disconnectedAtMs = 0;
  // cleared by STATE_RESTORE or SOLO_RESTORED, see confirmRunHeld
  private volatile boolean awaitingRunRestore = false;
  private volatile boolean soloFinishUnanswered = false;
  private int lostWarningSeconds = -1;
  private volatile RejoinOffer rejoinOffer = null;
  private volatile Function<Screen, Screen> pendingNotice = null;
  private volatile boolean pendingNoticeInTascade = false;
  private boolean rejoinArriving = false;
  private volatile long pingSentAtMs = 0;
  // when the countdown hits GO by this client's clock, 0 until started
  private volatile long countdownEndsAtMs = 0;
  private volatile long lastFinishRtaMs = -1;
  // MATCH_DECIDED and MATCH_RESULT (and reconnects) must not end the match twice
  private volatile String announcedMatchId = null;
  private volatile String chatLeftMatchId = null;
  private volatile MatchSnapshot leftAwaiting = null;
  private boolean clearChatOnJoin = false;
  private int worldProgressSent = -1;
  private long worldProgressSentAtMs = 0;
  private ScheduledExecutorService pinger;
  private final AtomicReference<Runnable> pendingWorldStart = new AtomicReference<>();
  private JSONObject currentSeed = null;

  // drops repeated sends from double clicks or held keys
  private static final long ACTION_DEBOUNCE_MS = 500;
  private final Map<String, Long> lastActionAtMs = new java.util.concurrent.ConcurrentHashMap<>();

  private boolean debounce(String action) {
    long now = System.currentTimeMillis();
    Long last = lastActionAtMs.get(action);
    if (last != null && now - last < ACTION_DEBOUNCE_MS) return false;
    lastActionAtMs.put(action, now);
    return true;
  }

  public static TascadeClient getInstance() {
    if (INSTANCE == null) INSTANCE = new TascadeClient();
    return INSTANCE;
  }


  public void connect() {
    if (socket != null && socket.connected()) return;

    try {
      loadReconnectKey();
      if (socket == null) {
        IO.Options opts = new IO.Options();
        opts.transports = new String[]{"websocket"};
        opts.reconnection = true;
        opts.reconnectionAttempts = Integer.MAX_VALUE;
        opts.reconnectionDelay = 1000;
        opts.reconnectionDelayMax = 5000;
        opts.timeout = 20000;
        socket = IO.socket(URI.create(SERVER_URL), opts);
        registerCoreListeners();
      }
      if (pinger == null) {
        pinger = Executors.newSingleThreadScheduledExecutor(runnable -> {
          Thread thread = new Thread(runnable, "Tascade-Latency");
          thread.setDaemon(true);
          return thread;
        });
        pinger.scheduleAtFixedRate(this::pingIfActive, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
      }
      if (!socket.connected()) {
        socket.connect();
      }
    } catch (Exception e) {
      Main.profileStatus = Main.ProfileState.FAILED;
      e.printStackTrace();
    }
  }

  // reconnect keys
  private void loadReconnectKey() {
    try {
      Path path = Paths.get(MinecraftHelper.getGameDir(), CONFIG_DIR, CONFIG_FILE);
      if (Files.exists(path)) {
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        JSONObject obj = new JSONObject(content);
        reconnectKey = obj.optString("reconnectKey", null);
      }
    } catch (Exception e) {
      reconnectKey = null;
    }
  }

  private void saveReconnectKey(String key) {
    reconnectKey = key;
    try {
      Path dir = Paths.get(MinecraftHelper.getGameDir(), CONFIG_DIR);
      Files.createDirectories(dir);
      Path path = dir.resolve(CONFIG_FILE);
      JSONObject obj = new JSONObject();
      obj.put("reconnectKey", key);
      Files.write(path, obj.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // core listeners
  private void registerCoreListeners() {
    socket.on(Events.AUTH_CHALLENGE, args -> {
      Main.profileStatus = Main.ProfileState.LOADING;
      try {
        System.out.println("Running auth Challenge...");
        if (reconnectKey != null && !reconnectKey.isEmpty()) {
          JSONObject restore = new JSONObject();
          restore.put("reconnectKey", reconnectKey);
          socket.emit(Events.AUTH_RESTORE, restore);
        } else {
          JSONObject data = (JSONObject) args[0];
          String serverId = data.getString("serverId");
          new Thread(() -> AuthHandler.doMojangAuth(socket, serverId)).start();
        }
      } catch (Exception e) {
        Main.profileStatus = Main.ProfileState.FAILED;
        e.printStackTrace();
      }
    });
    socket.on(Events.AUTH_SUCCESS, args -> {
      try {
        JSONObject data = (JSONObject) args[0];
        authenticated = true;
        playerUuid = data.optString("uuid", null);
        String username = data.getString("username");
        System.out.println("Authenticated as " + username + " (" + playerUuid + ")");
        Main.nickname = username;
        Main.pp = (int) Math.round(data.getDouble("performancePoints"));
        Main.elo = data.getInt("elo");
        Main.profileStatus = Main.ProfileState.SUCCESS;
        clearMenuCache();

        String newKey = data.optString("reconnectKey", null);
        if (newKey != null && !newKey.isEmpty()) saveReconnectKey(newKey);

        boolean reconnectedMidRun = disconnectedAtMs > 0 && RunState.isRunning();
        disconnectedAtMs = 0;
        flushPendingActions();
        if (reconnectedMidRun) confirmRunHeld();
        else if (!RunState.isRunning() && RunResume.saved() != null) checkLeftRun();
        if (RoomState.inRoom()) confirmRoomHeld();

        if (needsRequeue) {
          needsRequeue = false;
          rejoinQueue();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    socket.on(Events.AUTH_FAILURE, args -> {
      Main.profileStatus = Main.ProfileState.FAILED;
      authenticated = false;
      String reason = (args.length > 0 && args[0] instanceof JSONObject)
              ? ((JSONObject) args[0]).optString("reason", "Authentication failed")
              : (args.length > 0 ? String.valueOf(args[0]) : "Authentication failed");
      System.err.println("Auth failed: " + reason);

      if (reconnectKey != null) {
        reconnectKey = null;
        try {
          Files.deleteIfExists(Paths.get(MinecraftHelper.getGameDir(), CONFIG_DIR, CONFIG_FILE));
        } catch (Exception ignored) {}
      }
    });

    socket.on(Events.ERROR, args -> {
      String message = (args.length > 0 && args[0] instanceof JSONObject)
              ? ((JSONObject) args[0]).optString("message", "Unknown error")
              : "Unknown error";
      RunState.lastError = message;
      RunState.soloSeedRequestedAtMs = 0;
      System.err.println("Server error: " + message);
      chat(new LiteralText("" + message).formatted(Formatting.RED));
    });

    // reconnect: the server re-attaches us to whatever we were doing
    socket.on(Events.STATE_RESTORE, args -> {
      try {
        JSONObject data = (JSONObject) args[0];
        String mode = data.optString("mode", "");
        if ("match".equals(mode)) {
          if (!RunState.isRanked()) {
            RunResume.Saved left = RunResume.saved();
            if (left != null && left.ranked && Objects.equals(left.matchId, data.optString("matchId", null))) {
              offerRejoin(left, data);
              return;
            }
            System.out.println("Server still held a match this client left; forfeiting it.");
            queueOrEmit(Events.MATCH_FORFEIT);
            return;
          }
          awaitingRunRestore = false;
          applyRestoredProgress(data);
          RunState.matchId = data.optString("matchId", null);
          RunState.opponentUuid = data.optString("opponentUuid", RunState.opponentUuid);
          applyModifierInfo(data);
          onAwaiting(data.optJSONObject("awaiting"));
          RunState.queueState = RunState.QueueState.NONE;
          chat(new LiteralText("Reconnected to your match.").formatted(Formatting.GREEN));
          MinecraftClient.getInstance().execute(this::showReconnected);
        } else if ("countdown".equals(mode)) {
          RunState.queueState = RunState.QueueState.COUNTDOWN;
          RunState.countdownSeconds = data.optInt("seconds", READY_COUNTDOWN_SECONDS);
        } else {
          RunState.queueState = RunState.QueueState.QUEUE;
        }
        System.out.println("State restored: " + mode);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    // ranked matchmaking

    socket.on(Events.QUEUE_STATUS, args -> {
      try {
        JSONObject data = (JSONObject) args[0];
        RunState.queueSize = data.optInt("size", 0);
        // matched players leave the queue, so a status now means the match fell through
        if (!RunState.isRunning()) {
          RunState.queueState = RunState.QueueState.QUEUE;
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    socket.on(Events.MATCH_FOUND, args -> {
      try {
        JSONObject opponent = ((JSONObject) args[0]).getJSONObject("opponent");
        String name = opponent.optString("username", "Opponent");
        RunState.opponentName = name;
        RunState.opponentElo = opponent.optInt("elo", 0);
        RunState.opponentUuid = opponent.optString("uuid", null);
        RunState.queueState = RunState.QueueState.COUNTDOWN;
        MatchSounds.matchFound();
        if (RunState.isSolo()) {
          chat(new LiteralText("Match found against " + name
                  + ". Your solo run will be abandoned when the match starts.").formatted(Formatting.YELLOW));
        }
        chatLeftMatchId = null;
        leftAwaiting = null;
        MatchChat.post(() -> MatchChat.open(name));
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    socket.on(Events.COUNTDOWN, args -> {
      try {
        RunState.countdownSeconds = ((JSONObject) args[0]).optInt("seconds", 0);
        RunState.queueState = RunState.QueueState.COUNTDOWN;
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    socket.on(Events.GAME_START, args -> {
      try {
        JSONObject data = (JSONObject) args[0];
        boolean abandonSolo = RunState.isSolo();
        RunState.matchId = data.optString("matchId", null);
        RunState.mode = RunState.Mode.RANKED;
        RunState.queueState = RunState.QueueState.NONE;
        startWorldFromSeed(data, true, false, abandonSolo);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    socket.on(Events.READY_COUNTDOWN, args -> {
      JSONObject data = (JSONObject) args[0];
      if (!data.has("endsInMs")) {
        RunState.raceCountdown = data.optInt("seconds", 0);
        return;
      }
      // ignore countdowns for a world from before a seed change
      if (!RunState.readySent || !RunState.waitingForRaceStart) return;
      // counted locally so late packets don't stretch seconds, later packets can only bring GO forward
      long endsAt = System.currentTimeMillis() + Math.max(0, data.optLong("endsInMs", 0));
      long current = countdownEndsAtMs;
      if (current == 0 || endsAt < current) countdownEndsAtMs = endsAt;
    });

    socket.on(Events.WORLD_PROGRESS_BROADCAST, args -> {
      JSONObject data = (JSONObject) args[0];
      if (isMe(data.optString("uuid", null))) return;
      int progress = Math.max(0, Math.min(100, data.optInt("progress", 0)));
      RoomState.Racer racer = RoomState.racer(data.optString("uuid", null));
      if (racer != null) racer.worldProgress = progress;
      RunState.opponentWorldProgress = progress;
    });

    // ranked votes (seed change / draw)

    socket.on(Events.SEED_CHANGE_VOTING, args -> voteOpened(RunState.Vote.SEED, (JSONObject) args[0]));

    socket.on(Events.SEED_CHANGE_PASSED, args -> {
      JSONObject data = args.length > 0 && args[0] instanceof JSONObject ? (JSONObject) args[0] : new JSONObject();
      boolean heard = RunState.voteAgreed;
      RunState.pendingVote = RunState.Vote.SEED;
      RunState.voteAgreed = true;
      RunState.voteSentAtMs = System.currentTimeMillis() + data.optInt("switchIn", 3) * 1000L;
      if (!heard) MatchSounds.voteAgreed();
    });

    socket.on(Events.SEED_CHANGE_ACCEPTED, args -> {
      try {
        RunState.clearVote();
        MatchSounds.seedChanged();
        startWorldFromSeed((JSONObject) args[0], true, true);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    socket.on(Events.SEED_CHANGE_EXPIRED, args -> {
      RunState.clearVote();
      MatchSounds.voteExpired();
    });

    socket.on(Events.DRAW_VOTING, args -> voteOpened(RunState.Vote.DRAW, (JSONObject) args[0]));

    socket.on(Events.DRAW_ACCEPTED, args -> {
      boolean heard = RunState.voteAgreed;
      RunState.clearVote();
      if (!heard) MatchSounds.voteAgreed();
      MinecraftClient.getInstance().execute(() -> concludeProvisionally(RunSummary.Outcome.DRAW, false));
    });

    socket.on(Events.DRAW_EXPIRED, args -> {
      RunState.clearVote();
      MatchSounds.voteExpired();
    });

    socket.on(Events.MATCH_AWAITING, args -> {
      try {
        onAwaiting((JSONObject) args[0]);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    socket.on(Events.MATCH_DECIDED, args -> {
      try {
        announceMatchResult((JSONObject) args[0], false);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    socket.on(Events.MATCH_RESULT, args -> {
      try {
        clearMenuCache();
        JSONObject data = (JSONObject) args[0];
        String matchId = data.optString("matchId", null);
        if (matchId != null && matchId.equals(announcedMatchId)) {
          applyMatchRatings(data);
        } else {
          announceMatchResult(data, true);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    // solo runs

    socket.on(Events.SOLO_STARTED, args -> {
      try {
        RunState.mode = RunState.Mode.SOLO;
        startWorldFromSeed((JSONObject) args[0], false, false);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    socket.on(Events.SOLO_SEED_CHANGED, args -> {
      try {
        startWorldFromSeed((JSONObject) args[0], false, true);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    socket.on(Events.SOLO_RESTORED, args -> {
      if (RunState.isSolo()) {
        awaitingRunRestore = false;
        applyModifierInfo((JSONObject) args[0]);
        chat(new LiteralText("Reconnected to your run.").formatted(Formatting.GREEN));
        MinecraftClient.getInstance().execute(this::showReconnected);
      } else if (!soloFinishUnanswered) {
        RunResume.Saved left = RunResume.saved();
        if (left != null && !left.ranked && Objects.equals(left.seedId, seedId(((JSONObject) args[0]).optJSONObject("seed")))) {
          offerRejoin(left, (JSONObject) args[0]);
          return;
        }
        System.out.println("Server still held a solo run this client left, ending it.");
        JSONObject payload = new JSONObject();
        payload.put("reason", "disconnected");
        queueOrEmit(Events.SOLO_ABANDON, payload);
      }
    });

    socket.on(Events.SOLO_FINISHED, args -> {
      soloFinishUnanswered = false;
      try {
        JSONObject data = (JSONObject) args[0];
        double ppAwarded = data.optDouble("ppAwarded", 0);
        double newTotalPP = data.optDouble("newTotalPP", Double.NaN);
        if (!Double.isNaN(newTotalPP)) Main.pp = (int) Math.round(newTotalPP);
        RunState.endRun();
        clearMenuCache();
        MinecraftClient.getInstance().execute(() -> RunFinish.soloRecorded(ppAwarded, newTotalPP));
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    socket.on(Events.SOLO_ABANDONED, args -> {
      String result = ((JSONObject) args[0]).optString("result", "FORFEIT");
      if (!RunState.isVersus()) RunState.endRun();
      clearMenuCache();
      System.out.println("Solo run abandoned (" + result + ").");
    });

    // in-game relay (chat + advancements)

    socket.on(Events.CHAT_BROADCAST, args -> {
      JSONObject data = (JSONObject) args[0];
      String text = data.optString("text", "");
      Text line;
      if (SERVER_NOTICE_UUID.equals(data.optString("uuid", null))) {
        line = new LiteralText(text).formatted(Formatting.YELLOW);
      } else {
        if (!TascadeConfig.opponentChat()) return;
        line = new LiteralText("<" + data.optString("username", "?") + "> " + text);
      }
      MatchChat.post(() -> MatchChat.receive(line));
    });

    socket.on(Events.CHAT_CLOSED, args -> {
      JSONObject data = (args.length > 0 && args[0] instanceof JSONObject) ? (JSONObject) args[0] : new JSONObject();
      String reason = data.optString("reason", "ended");
      String username = data.optString("username", null);
      MatchChat.post(() -> {
        // room chats outlive whatever the server says about ranked ones
        if (MatchChat.isRoomChat()) return;
        MatchChat.closedByServer(reason, username);
      });
    });

    socket.on(Events.SPLIT_BROADCAST, args -> {
      JSONObject split = ((JSONObject) args[0]).optJSONObject("split");
      if (split == null || split.isNull("rta")) return;
      String name = split.optString("name", "");
      if (!RunRecorder.isTrackedSplit(name)) return;
      long rta = split.optLong("rta");
      if (RunState.isRoom()) {
        RoomState.Racer racer = RoomState.racer(((JSONObject) args[0]).optString("uuid", null));
        if (racer != null) racer.splits.putIfAbsent(name, rta);
        return;
      }
      if (RunState.opponentSplits.putIfAbsent(name, rta) == null) ReplayRecorder.opponentSplit(name, rta);
    });

    socket.on(Events.GOAL_BROADCAST, args -> {
      JSONObject data = (JSONObject) args[0];
      String goal = data.optString("goal", "");
      if (!RunGoals.isGoal(goal) || data.isNull("rta")) return;
      long rta = data.optLong("rta");
      if (RunState.isRoom()) {
        RoomState.Racer racer = RoomState.racer(data.optString("uuid", null));
        if (racer != null) racer.goals.putIfAbsent(goal, rta);
        return;
      }
      if (RunState.opponentGoals.putIfAbsent(goal, rta) == null) ReplayRecorder.opponentGoal(goal, rta);
    });

    socket.on(Events.ADVANCEMENT_BROADCAST, args -> {
      JSONObject data = (JSONObject) args[0];
      String username = data.optString("username", "?");
      String id = data.optString("advancement", "");
      if (id.isEmpty() || RunRecorder.isRootAdvancement(id)) return;
      if (!TascadeConfig.opponentAdvancements()) return;

      MinecraftClient mc = MinecraftClient.getInstance();
      MatchChat.post(() -> {
        Text announcement = advancementAnnouncement(mc, username, id);
        if (announcement != null) MatchChat.receive(announcement);
      });
    });

    // socket connection statuses
    socket.on(Socket.EVENT_CONNECT, args -> {
      RunState.connected = true;
      RunState.reconnectAttempt = 0;
    });

    socket.on(Socket.EVENT_DISCONNECT, args -> {
      // "ping timeout"/"transport close" is the network, "io server disconnect" is the server
      System.out.println("Disconnected from the backend: " + (args.length > 0 ? args[0] : "unknown reason"));
      Main.profileStatus = Main.ProfileState.FAILED;
      authenticated = false;
      if (disconnectedAtMs == 0) disconnectedAtMs = System.currentTimeMillis();
      RunState.connected = false;
      RunState.latencyMs = -1;
      pingSentAtMs = 0;
      if (RunState.isQueued()) needsRequeue = true;
    });

    socket.io().on(Manager.EVENT_RECONNECT_ATTEMPT, args -> {
      if (args.length > 0 && args[0] instanceof Integer) {
        RunState.reconnectAttempt = (Integer) args[0];
      }
    });

    socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
      Object error = args.length > 0 ? args[0] : null;
      System.out.println("Couldn't connect to the backend: "
              + (error instanceof Throwable ? error.toString() : String.valueOf(error)));
      Main.profileStatus = Main.ProfileState.FAILED;
    });

    registerRoomListeners();
  }

  // custom room listeners

  private void registerRoomListeners() {
    socket.on(Events.ROOM_JOINED, args -> {
      try {
        JSONObject data = (JSONObject) args[0];
        JSONObject state = data.optJSONObject("state");
        RoomState.Room room = new RoomState.Room(state != null ? state : new JSONObject().put("code", data.optString("code")));
        RoomState.setRoom(room);
        RoomState.lastError = null;
        if (RunState.isQueued()) leaveQueue();
        MatchChat.post(() -> MatchChat.openRoom(room.code, room.settings.name));
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    socket.on(Events.ROOM_STATE, args -> {
      try {
        if (!RoomState.inRoom()) return;
        RoomState.Room room = new RoomState.Room((JSONObject) args[0]);
        if (!room.code.equals(RoomState.room().code)) return;
        RoomState.setRoom(room);
        if (!RoomState.PHASE_COUNTDOWN.equals(room.phase)) RoomState.lobbyCountdown = -1;
        applyRaceInfo(((JSONObject) args[0]).optJSONObject("race"));
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    socket.on(Events.ROOM_RESTORED, args -> {
      try {
        onRoomRestored((JSONObject) args[0]);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    socket.on(Events.ROOM_LEFT, args -> {
      JSONObject data = args.length > 0 && args[0] instanceof JSONObject ? (JSONObject) args[0] : new JSONObject();
      String reason = data.optString("reason", "left");
      roomRestorePending = false;
      if (!RoomState.inRoom()) return;
      RoomState.clear();
      if (!"left".equals(reason)) RoomState.removedReason = reason;
      String line = "kicked".equals(reason) ? "You were kicked from the room."
              : "banned".equals(reason) ? "You were banned from the room."
              : "closed".equals(reason) ? "The room was closed." : null;
      MatchChat.post(() -> MatchChat.close(line != null ? new LiteralText(line).formatted(Formatting.YELLOW) : null));
      if (RunState.isRoom()) MinecraftClient.getInstance().execute(this::leaveRoomRaceWorld);
    });

    socket.on(Events.ROOM_ERROR, args -> {
      String message = (args.length > 0 && args[0] instanceof JSONObject)
              ? ((JSONObject) args[0]).optString("message", "Something went wrong.")
              : "Something went wrong.";
      System.err.println("Room error: " + message);
      RoomState.error(message);
      RoomState.voted = RoomState.vote != null && RoomState.vote.yes.contains(playerUuid);
      if (MinecraftClient.getInstance().world != null) chat(new LiteralText("" + message).formatted(Formatting.RED));
    });

    socket.on(Events.PLAYER_JOINED, args -> roomNotice((JSONObject) args[0], " joined the room."));
    socket.on(Events.PLAYER_LEFT, args -> {
      JSONObject data = (JSONObject) args[0];
      String reason = data.optString("reason", "left");
      roomNotice(data, "kicked".equals(reason) ? " was kicked from the room."
              : "banned".equals(reason) ? " was banned from the room."
              : "disconnected".equals(reason) ? " lost connection and left the room." : " left the room.");
    });

    socket.on(Events.ROOM_COUNTDOWN, args -> {
      int seconds = ((JSONObject) args[0]).optInt("seconds", 0);
      RoomState.lobbyCountdown = seconds;
    });

    socket.on(Events.ROOM_COUNTDOWN_CANCELLED, args -> {
      RoomState.lobbyCountdown = -1;
    });

    socket.on(Events.ROOM_RACE_START, args -> {
      try {
        JSONObject data = (JSONObject) args[0];
        if (RunState.isRunning() && !RunState.isRoom()) {
          queueOrEmit(Events.ROOM_FORFEIT);
          return;
        }
        if (RunState.isQueued()) leaveQueue();
        RoomState.lobbyCountdown = -1;
        RoomState.startRace(data.optString("raceId", null), data.optJSONArray("players"));
        roomRaceDone = false;
        RunState.matchId = data.optString("raceId", null);
        RunState.mode = RunState.Mode.ROOM;
        RunState.queueState = RunState.QueueState.NONE;
        MatchSounds.matchFound();
        startWorldFromSeed(data, false, false);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    socket.on(Events.PLAYER_FINISHED_BROADCAST, args -> {
      JSONObject data = (JSONObject) args[0];
      String uuid = data.optString("uuid", null);
      long rta = data.optLong("rtaMs", data.optLong("rta", -1));
      int place = data.optInt("place", 0);
      RoomState.Racer racer = RoomState.racer(uuid);
      if (racer != null) {
        racer.finishedRtaMs = rta;
        racer.place = place;
      }
      if (isMe(uuid)) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> RunFinish.roomPlaced(mc, place));
        return;
      }
      if (!RunState.isRoom() && !RoomState.inRoom()) return;
      String name = data.optString("username", "?");
      String time = rta >= 0 ? " in " + com.coatt.tascade.hud.RunLabels.time(rta) : "";
      MatchChat.post(() -> MatchChat.receive(new LiteralText(name + " finished " + RunSummary.ordinal(Math.max(1, place)) + time + ".")
              .formatted(Formatting.YELLOW)));
      if (RunState.isRoom()) MatchSounds.deadlineSet();
    });

    socket.on(Events.ROOM_PLAYER_DNF, args -> {
      JSONObject data = (JSONObject) args[0];
      String uuid = data.optString("uuid", null);
      String reason = data.optString("reason", "forfeit");
      RoomState.Racer racer = RoomState.racer(uuid);
      if (racer != null) racer.dnfReason = reason;
      if (isMe(uuid) || !RunState.isRoom()) return;
      String name = data.optString("username", "?");
      String what = "forfeit".equals(reason) ? " gave up the race." : "kicked".equals(reason) ? " was taken out of the race."
              : "disconnected".equals(reason) ? " lost connection and is out of the race." : " left the race.";
      MatchChat.post(() -> MatchChat.receive(new LiteralText(name + what).formatted(Formatting.YELLOW)));
    });

    socket.on(Events.ROOM_VOTE_OPENED, args -> {
      JSONObject data = (JSONObject) args[0];
      RoomState.Vote previous = RoomState.vote;
      RoomState.Vote vote = RoomState.Vote.fromJson(data, data.optLong("expiresIn", 30) * 1000L);
      RoomState.vote = vote;
      boolean mine = vote.yes.contains(playerUuid);
      if (!mine && (previous == null || !previous.type.equals(vote.type))) MatchSounds.voteStarted();
      RoomState.voted = mine;
    });

    socket.on(Events.ROOM_VOTE_PASSED, args -> {
      RoomState.vote = null;
      RoomState.voted = false;
      MatchSounds.voteAgreed();
    });

    socket.on(Events.ROOM_VOTE_FAILED, args -> {
      RoomState.vote = null;
      RoomState.voted = false;
      MatchSounds.voteExpired();
    });

    socket.on(Events.ROOM_SEED_CHANGED, args -> {
      try {
        JSONObject data = (JSONObject) args[0];
        if (!RunState.isRoom() && RunState.isRunning()) return;
        RoomState.restartRace();
        roomRaceDone = false;
        RunState.mode = RunState.Mode.ROOM;
        RunState.matchId = data.optString("raceId", RunState.matchId);
        RoomState.raceId = RunState.matchId;
        MatchSounds.seedChanged();
        startWorldFromSeed(data, false, true);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    socket.on(Events.ROOM_RACE_ENDED, args -> {
      try {
        onRoomRaceEnded((JSONObject) args[0]);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

  private void applyRaceInfo(JSONObject race) {
    if (race == null || RoomState.raceId == null || !RoomState.raceId.equals(race.optString("raceId", null))) return;
    if (race.has("endsInMs") && !race.isNull("endsInMs")) {
      RoomState.raceEndsAtMs = System.currentTimeMillis() + Math.max(0, race.optLong("endsInMs"));
    }
    JSONObject vote = race.optJSONObject("vote");
    if (vote == null) {
      RoomState.vote = null;
      RoomState.voted = false;
    } else {
      RoomState.Vote parsed = RoomState.Vote.fromJson(vote, vote.optLong("expiresInMs", 30_000));
      RoomState.vote = parsed;
      RoomState.voted = parsed.yes.contains(playerUuid);
    }
    JSONArray finished = race.optJSONArray("finished");
    for (int i = 0; finished != null && i < finished.length(); i++) {
      JSONObject finish = finished.optJSONObject(i);
      RoomState.Racer racer = finish != null ? RoomState.racer(finish.optString("uuid", null)) : null;
      if (racer == null || racer.finished()) continue;
      racer.finishedRtaMs = finish.optLong("rtaMs", 0);
      racer.place = finish.optInt("place", 0);
    }
  }

  private void roomNotice(JSONObject data, String what) {
    if (!RoomState.inRoom() || isMe(data.optString("uuid", null))) return;
    String name = data.optString("username", "?");
    MatchChat.post(() -> MatchChat.receive(new LiteralText(name + what).formatted(Formatting.YELLOW)));
  }

  private void onRoomRaceEnded(JSONObject data) {
    String id = data.optString("raceId", null);
    if (id != null && RoomState.raceId != null && !id.equals(RoomState.raceId)) return;
    List<RoomState.Standing> standings = new ArrayList<>();
    JSONArray array = data.optJSONArray("standings");
    for (int i = 0; array != null && i < array.length(); i++) {
      JSONObject standing = array.optJSONObject(i);
      if (standing != null) standings.add(RoomState.Standing.fromJson(standing));
    }
    RoomState.endReason = data.optString("reason", "goal");
    RoomState.finalStandings = Collections.unmodifiableList(standings);
    RoomState.vote = null;
    RoomState.voted = false;
    RoomState.raceEndsAtMs = 0;
    if (!RunState.isRoom()) return;
    boolean wasDone = roomRaceDone;
    roomRaceDone = true;

    String reason = RoomState.endReason;
    String line = "time".equals(reason) ? "Time's up: the race is over."
            : "all_done".equals(reason) ? "Everyone is done: the race is over."
            : "goal".equals(reason) ? "The race is over." : null;
    if (line != null) MatchChat.post(() -> MatchChat.receive(new LiteralText(line).formatted(Formatting.YELLOW)));

    Map<String, Long> splits = RunRecorder.splitTimes();
    String seedType = RunState.seedType;
    String bastionType = RunState.bastionType;
    long elapsed = RunFinish.elapsedSinceRaceStart();
    boolean waiting = RunState.waitingForRaceStart;
    RunState.endRun();
    MinecraftClient mc = MinecraftClient.getInstance();
    mc.execute(() -> {
      if (!wasDone && !RunFinish.isActive()) {
        if (waiting || mc.world == null) {
          leaveRoomRaceWorld();
          return;
        }
        RunFinish.room(mc, RunSummary.Outcome.ENDED, false, elapsed, 0, seedType, bastionType, splits);
      }
    });
  }

  // room:restored arrives before the probe's answer, if not, the room dropped us
  private volatile boolean roomRestorePending = false;

  private void confirmRoomHeld() {
    roomRestorePending = true;
    socket.emit(Events.LATENCY_PING, new Object[0], ack -> {
      if (!roomRestorePending) return;
      roomRestorePending = false;
      if (!RoomState.inRoom()) return;
      System.out.println("The room let this player go while they were away.");
      RoomState.clear();
      RoomState.removedReason = "dropped";
      MatchChat.post(() -> MatchChat.close(new LiteralText("You were away too long and left the room.").formatted(Formatting.YELLOW)));
    });
  }

  private void onRoomRestored(JSONObject data) {
    roomRestorePending = false;
    JSONObject state = data.optJSONObject("state");
    if (state == null) return;
    RoomState.Room room = new RoomState.Room(state);
    boolean wasIn = RoomState.inRoom() && RoomState.room().code.equals(room.code);
    RoomState.setRoom(room);
    if (!wasIn) MatchChat.post(() -> MatchChat.openRoom(room.code, room.settings.name));
    applyRaceInfo(state.optJSONObject("race"));
    JSONObject race = data.optJSONObject("race");
    if (race == null) return;
    String raceId = race.optString("raceId", null);
    if (!RunState.isRoom() || !Objects.equals(raceId, RoomState.raceId)) {
      if (!race.optBoolean("finished", false) && !roomRaceDone) queueOrEmit(Events.ROOM_FORFEIT);
      return;
    }
    awaitingRunRestore = false;
    JSONObject others = race.optJSONObject("others");
    if (others != null) {
      for (String uuid : others.keySet()) {
        RoomState.Racer racer = RoomState.racer(uuid);
        JSONObject progress = others.optJSONObject(uuid);
        if (racer == null || progress == null) continue;
        JSONArray splits = progress.optJSONArray("splits");
        for (int i = 0; splits != null && i < splits.length(); i++) {
          JSONObject split = splits.optJSONObject(i);
          if (split != null && !split.isNull("rta") && RunRecorder.isTrackedSplit(split.optString("name"))) {
            racer.splits.putIfAbsent(split.optString("name"), split.optLong("rta"));
          }
        }
        JSONArray goals = progress.optJSONArray("goals");
        for (int i = 0; goals != null && i < goals.length(); i++) {
          JSONObject goal = goals.optJSONObject(i);
          if (goal != null && !goal.isNull("rta") && RunGoals.isGoal(goal.optString("goal"))) {
            racer.goals.putIfAbsent(goal.optString("goal"), goal.optLong("rta"));
          }
        }
      }
    }
    chat(new LiteralText("Reconnected to your room's race.").formatted(Formatting.GREEN));
    MinecraftClient.getInstance().execute(this::showReconnected);
  }

  public void leaveRoomRace() {
    if (!debounce(Events.ROOM_FORFEIT + ":leave")) return;
    if (!RunState.isRoom()) return;
    if (!roomRaceDone) queueOrEmit(Events.ROOM_FORFEIT);
    roomRaceDone = true;
    leaveRoomRaceWorld();
  }

  private void leaveRoomRaceWorld() {
    RunState.endRun();
    MinecraftClient mc = MinecraftClient.getInstance();
    if (mc.world != null) {
      mc.world.disconnect();
      mc.disconnect(new SaveLevelScreen(new TranslatableText("menu.savingLevel")));
      mc.openScreen(new TitleScreen());
    }
  }

  // ranked mode

  public void joinQueue() {
    if (!debounce(Events.JOIN_QUEUE)) return;
    if (RunState.isQueued()) return;
    if (leftAwaiting != null) {
      RunState.lastError = "Your last match is still waiting on its result.";
      return;
    }
    String refusal = RunModifiers.rankedRefusal();
    if (refusal != null) {
      RunState.lastError = refusal;
      return;
    }
    RunState.queueState = RunState.QueueState.QUEUE;
    RunState.queueStartedAtMs = System.currentTimeMillis();
    RunState.queueSize = 0;
    RunState.opponentName = null;
    RunState.lastError = null;
    queueOrEmit(Events.JOIN_QUEUE, queueOptions());
    ping();
  }

  public void leaveQueue() {
    if (!debounce(Events.LEAVE_QUEUE)) return;
    RunState.queueState = RunState.QueueState.NONE;
    needsRequeue = false;
    queueOrEmit(Events.LEAVE_QUEUE);
  }

  // keeps the original start time so elapsed time carries on
  private void rejoinQueue() {
    if (!RunState.isQueued()) return;
    // the server cancels a countdown when a player drops
    RunState.queueState = RunState.QueueState.QUEUE;
    RunState.opponentName = null;
    queueOrEmit(Events.JOIN_QUEUE, queueOptions());
    ping();
  }

  public void onSettingsChanged() {
    if (RunState.queueState != RunState.QueueState.QUEUE) return;
    queueOrEmit(Events.JOIN_QUEUE, queueOptions());
  }

  private static JSONObject queueOptions() {
    JSONObject options = new JSONObject();
    options.put("allowBot", TascadeConfig.rankedBot());
    options.put("cheats", TascadeConfig.cheats());
    options.put("modifiers", new JSONArray(RunModifiers.selectedIds()));
    return options;
  }

  private static JSONObject soloOptions() {
    JSONObject options = new JSONObject();
    options.put("cheats", TascadeConfig.cheats());
    List<String> modifiers = RunState.isSolo() ? RunState.modifiers : RunModifiers.selectedIds();
    options.put("modifiers", new JSONArray(modifiers));
    return options;
  }

  private static void applyModifierInfo(JSONObject data) {
    if (data.has("modifiers")) RunState.modifiers = stringList(data.optJSONArray("modifiers"));
    if (data.has("ppMultiplier")) RunState.ppMultiplier = data.optDouble("ppMultiplier", 1);
    if (data.has("pace")) RunState.pace = data.optDouble("pace", 1);
    if (data.has("opponentModifiers")) RunState.opponentModifiers = stringList(data.optJSONArray("opponentModifiers"));
    if (data.has("opponentPace")) RunState.opponentPace = data.optDouble("opponentPace", 1);
  }

  private static List<String> stringList(JSONArray array) {
    if (array == null) return Collections.emptyList();
    List<String> list = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) {
      String value = array.optString(i, null);
      if (value != null) list.add(value);
    }
    return Collections.unmodifiableList(list);
  }

  private void onAwaiting(JSONObject awaiting) {
    if (awaiting == null || !RunState.isRanked()) return;
    String matchId = awaiting.optString("matchId", null);
    if (matchId != null && !matchId.equals(RunState.matchId)) return;
    long deadline = awaiting.optLong("deadlineRtaMs", -1);
    if (deadline < 0) return;
    boolean mine = isMe(awaiting.optString("finishedUuid", null));
    boolean known = RunState.awaitingDeadlineRtaMs == deadline && RunState.awaitingIsMine == mine;
    RunState.awaitingDeadlineRtaMs = deadline;
    RunState.awaitingIsMine = mine;
    if (known) return;
    if (RunState.pendingVote == RunState.Vote.DRAW) RunState.clearVote();
    String opponent = RunState.opponentName;
    MinecraftClient mc = MinecraftClient.getInstance();
    mc.execute(() -> RunFinish.awaiting(mc, mine, deadline, opponent));
  }

  private void pingIfActive() {
    try {
      if (RunState.isQueued() || RunState.isRunning() || MatchChat.isOpen()) ping();
    } catch (Exception e) {
      // an uncaught exception would cancel every future run of this task
      e.printStackTrace();
    }
  }

  private void ping() {
    if (socket == null || !socket.connected()) return;
    long sentAt = System.currentTimeMillis();
    if (pingSentAtMs > 0 && sentAt - pingSentAtMs < PING_TIMEOUT_MS) return;
    pingSentAtMs = sentAt;
    socket.emit(Events.LATENCY_PING, new Object[0], ackArgs -> {
      if (pingSentAtMs != sentAt) return;
      RunState.latencyMs = System.currentTimeMillis() - sentAt;
      pingSentAtMs = 0;
    });
  }

  private void reportReady() {
    if (!RunState.isVersus() || RunState.matchId == null || RunState.readySent) return;
    RunState.readySent = true;
    // the loading screen can close before drawing 100%
    queueOrEmit(Events.WORLD_PROGRESS, worldProgressPayload(100));
    JSONObject payload = new JSONObject();
    payload.put("matchId", RunState.matchId);
    queueOrEmit(Events.PLAYER_READY, payload);
  }

  public void reportWorldProgress(int progress) {
    if (!RunState.isVersus() || RunState.matchId == null || RunState.readySent) return;
    if (progress == worldProgressSent || socket == null || !socket.connected() || !authenticated) return;
    long now = System.currentTimeMillis();
    if (now - worldProgressSentAtMs < WORLD_PROGRESS_INTERVAL_MS) return;
    worldProgressSent = progress;
    worldProgressSentAtMs = now;
    socket.emit(Events.WORLD_PROGRESS, worldProgressPayload(progress));
  }

  private static JSONObject worldProgressPayload(int progress) {
    JSONObject payload = new JSONObject();
    payload.put("matchId", RunState.matchId);
    payload.put("progress", progress);
    return payload;
  }

  private void reportRankedFinished(long rta) {
    if (!RunState.isRanked()) return;
    JSONObject payload = new JSONObject();
    payload.put("rta", rta);
    queueOrEmit(Events.PLAYER_FINISHED, payload);
  }

  public void forfeit() {
    if (!debounce(Events.MATCH_FORFEIT)) return;
    if (!RunState.isRanked() || RunFinish.isActive()) return;
    queueOrEmit(Events.MATCH_FORFEIT);
    MinecraftClient.getInstance().execute(() -> concludeProvisionally(RunSummary.Outcome.DEFEAT, true));
  }

  public void requestSeedChange() {
    if (!debounce("seed_change")) return;
    if (RunState.isRanked()) {
      voteLocally(RunState.Vote.SEED);
      queueOrEmit(Events.MATCH_SEED_VOTE);
    } else if (RunState.isSolo()) {
      if (RunState.soloSeedRequestedAtMs > 0) return;
      RunState.soloSeedRequestedAtMs = System.currentTimeMillis();
      MatchSounds.voteAgreed();
      queueOrEmit(Events.SOLO_SEED_CHANGE, soloOptions());
    }
  }

  public void offerDraw() {
    if (!debounce(Events.MATCH_DRAW_VOTE)) return;
    if (!RunState.isRanked() || RunState.awaitingDeadlineRtaMs >= 0) return;
    voteLocally(RunState.Vote.DRAW);
    queueOrEmit(Events.MATCH_DRAW_VOTE);
  }

  // shown immediately, confirmed by the server or rolled back in tickRequests
  private void voteLocally(RunState.Vote type) {
    if (RunState.pendingVote == RunState.Vote.NONE) {
      RunState.pendingVote = type;
      RunState.pendingVoteIsMine = true;
      MatchSounds.voteStarted();
    } else if (RunState.pendingVote == type && !RunState.pendingVoteIsMine && !RunState.voteAgreed) {
      RunState.voteAgreed = true;
      MatchSounds.voteAgreed();
    } else {
      return;
    }
    RunState.voteSentAtMs = System.currentTimeMillis();
  }

  private void voteOpened(RunState.Vote type, JSONObject data) {
    boolean mine = isMe(data.optString("initiator", null));
    boolean heard = mine && RunState.voteSentAtMs > 0 && RunState.pendingVote == type && RunState.pendingVoteIsMine;
    RunState.pendingVote = type;
    RunState.pendingVoteIsMine = mine;
    RunState.voteAgreed = false;
    RunState.voteSentAtMs = 0;
    if (!heard) MatchSounds.voteStarted();
  }

  public void tickRequests() {
    long now = System.currentTimeMillis();
    long voteSent = RunState.voteSentAtMs;
    if (voteSent > 0 && now - voteSent > VOTE_ANSWER_TIMEOUT_MS && RunState.voteSentAtMs == voteSent) {
      RunState.clearVote();
    }
    long seedRequested = RunState.soloSeedRequestedAtMs;
    if (seedRequested > 0 && now - seedRequested > SOLO_SEED_TIMEOUT_MS) RunState.soloSeedRequestedAtMs = 0;
  }

  public void sendChatMessage(String text) {
    if (!MatchChat.isOpen() || text == null || text.trim().isEmpty()) return;
    JSONObject payload = new JSONObject();
    payload.put("text", text);
    queueOrEmit(Events.CHAT_MESSAGE, payload);
  }

  public void sendSplit(String name, long rta, long igt) {
    if (!RunState.isVersus()) return;
    JSONObject split = new JSONObject();
    split.put("name", name);
    split.put("rta", rta);
    split.put("igt", igt);
    JSONObject payload = new JSONObject();
    payload.put("split", split);
    queueOrEmit(Events.SPLIT_UPDATE, payload);
  }

  public void sendGoal(String goal, long rta) {
    if (!RunState.isVersus()) return;
    JSONObject payload = new JSONObject();
    payload.put("goal", goal);
    payload.put("rta", rta);
    queueOrEmit(Events.GOAL_UPDATE, payload);
  }

  public void sendAdvancement(String advancement, String category) {
    if (!RunState.isVersus()) return;
    JSONObject payload = new JSONObject();
    payload.put("advancement", advancement);
    payload.put("category", category);
    queueOrEmit(Events.ADVANCEMENT, payload);
  }

  public void sendDeath(boolean reset, long rta) {
    JSONObject payload = new JSONObject();
    payload.put("reset", reset);
    payload.put("rta", rta);
    queueOrEmit(Events.DEATH, payload);
  }

  private void announceMatchResult(JSONObject data, boolean withRatings) {
    MatchSnapshot away = leftAwaiting;
    if (!RunState.isRanked() && away != null && away.matchId != null
            && away.matchId.equals(data.optString("matchId", null))) {
      leftAwaiting = null;
      announcedMatchId = away.matchId;
      Decided decided = decide(data, away);
      boolean unrated = away.cheats || data.optBoolean("unrated", false);
      ReplayRecorder.matchResult(replayOutcome(decided.outcome), decided.forfeit, away.matchId);
      MinecraftClient mc = MinecraftClient.getInstance();
      mc.execute(() -> {
        RunSummary summary = RunFinish.rankedAway(decided.outcome, decided.forfeit, decided.time,
                decided.timeHolder, decided.playerName, away.opponent, away.seedType, away.bastionType,
                away.playerSplits, away.opponentSplits, unrated);
        pendingNoticeInTascade = false;
        pendingNotice = parent -> {
          MatchSounds.result(decided.outcome);
          return new RunResultScreen(summary, parent);
        };
      });
    }
    if (!RunState.isRanked()) {
      RunResume.Saved left = RunResume.saved();
      if (left != null && left.ranked && Objects.equals(left.matchId, data.optString("matchId", null))) {
        RunResume.forget();
        RejoinOffer offer = rejoinOffer;
        boolean offerShown = offer != null && offer.shown;
        rejoinOffer = null;
        String reason = endedWhileAwayReason(data, left);
        pendingNoticeInTascade = !offerShown;
        pendingNotice = parent -> RunNoticeScreen.endedWhileAway(parent, true, reason);
      }
      String decidedId = data.optString("matchId", null);
      if (decidedId != null && decidedId.equals(chatLeftMatchId)) {
        chatLeftMatchId = null;
        MatchChat.post(() -> {
          if (!MatchChat.isOpen()) return;
          queueOrEmit(Events.CHAT_LEAVE);
          MatchChat.close(null);
        });
      }
      if (withRatings) applyMatchRatings(data);
      return;
    }
    // snapshot before endRun clears it
    MatchSnapshot run = new MatchSnapshot(lastFinishRtaMs);
    boolean unrated = run.cheats || data.optBoolean("unrated", false);
    announcedMatchId = data.optString("matchId", run.matchId);
    RunState.endRun();
    RunState.queueState = RunState.QueueState.NONE;

    Decided decided = decide(data, run);
    ReplayRecorder.matchResult(replayOutcome(decided.outcome), decided.forfeit, run.matchId);

    MinecraftClient mc = MinecraftClient.getInstance();
    mc.execute(() -> {
      MatchChat.markMatchOver();
      RunFinish.ranked(mc, decided.outcome, decided.forfeit, decided.time, decided.timeHolder, decided.playerName,
              run.opponent, run.seedType, run.bastionType, run.playerSplits, run.opponentSplits,
              0, null, 0, Double.NaN, unrated, false, false);
    });
    // queued after the outcome so they fill in its summary
    if (withRatings) applyMatchRatings(data);
  }

  private static final class MatchSnapshot {
    final String matchId = RunState.matchId;
    final String opponent = RunState.opponentName;
    final String seedType = RunState.seedType;
    final String bastionType = RunState.bastionType;
    final boolean cheats = RunState.cheats;
    final Map<String, Long> playerSplits = RunRecorder.splitTimes();
    final Map<String, Long> opponentSplits = new HashMap<>(RunState.opponentSplits);
    final long raceStartedAtMs = RunState.raceStartedAtMs;
    final long finishRtaMs;

    MatchSnapshot(long finishRtaMs) {
      this.finishRtaMs = finishRtaMs;
    }

    long elapsed() {
      return raceStartedAtMs > 0 ? Math.max(0, System.currentTimeMillis() - raceStartedAtMs) : 0;
    }
  }

  private static final class Decided {
    RunSummary.Outcome outcome;
    boolean forfeit;
    long time;
    String timeHolder;
    String playerName;
  }

  private Decided decide(JSONObject data, MatchSnapshot run) {
    Decided decided = new Decided();
    String winnerUuid = data.optString("winnerUuid", null);
    String result = data.optString("result", "");
    boolean won = isMe(winnerUuid);
    decided.forfeit = "FORFEIT".equals(result);
    if ("DRAW".equals(result)) decided.outcome = RunSummary.Outcome.DRAW;
    else if (winnerUuid == null || winnerUuid.isEmpty()) decided.outcome = RunSummary.Outcome.ENDED;
    else decided.outcome = won ? RunSummary.Outcome.VICTORY : RunSummary.Outcome.DEFEAT;

    // the local times are only a fallback for older backends
    decided.playerName = MinecraftClient.getInstance().getSession().getUsername();
    long localElapsed = run.elapsed();
    if ("COMPLETE".equals(result) && winnerUuid != null) {
      decided.time = optMillis(data, "winnerRtaMs", won && run.finishRtaMs >= 0 ? run.finishRtaMs : localElapsed);
      decided.timeHolder = won ? decided.playerName : run.opponent;
    } else {
      decided.time = optMillis(data, "elapsedMs", localElapsed);
      decided.timeHolder = null;
    }
    return decided;
  }

  public void leavingWhileAwaitingOutcome() {
    if (RunState.isRanked() && RunState.matchId != null) leftAwaiting = new MatchSnapshot(lastFinishRtaMs);
  }

  private void applyMatchRatings(JSONObject data) {
    JSONObject players = data.optJSONObject("players");
    JSONObject mine = (players != null && playerUuid != null) ? players.optJSONObject(playerUuid) : null;

    Integer newElo = mine != null && mine.has("newElo") && !mine.isNull("newElo") ? mine.getInt("newElo") : null;
    double newTotalPP = mine != null ? mine.optDouble("newTotalPP", Double.NaN) : Double.NaN;
    int eloDelta = mine != null ? mine.optInt("eloDelta", 0) : 0;
    double ppAwarded = isMe(data.optString("winnerUuid", null)) ? data.optDouble("ppDelta", 0) : 0;
    if (newElo != null) Main.elo = newElo;
    if (!Double.isNaN(newTotalPP)) Main.pp = (int) Math.round(newTotalPP);
    MinecraftClient.getInstance().execute(() -> RunFinish.rankedRated(eloDelta, newElo, ppAwarded, newTotalPP));
  }

  private void concludeProvisionally(RunSummary.Outcome outcome, boolean forfeit) {
    if (!RunState.isRanked() || RunFinish.isActive()) return;
    MinecraftClient mc = MinecraftClient.getInstance();
    RunState.clearVote();
    MatchChat.markMatchOver();
    RunFinish.ranked(mc, outcome, forfeit, RunFinish.elapsedSinceRaceStart(), null,
            mc.getSession().getUsername(), RunState.opponentName, RunState.seedType, RunState.bastionType,
            RunRecorder.splitTimes(), new HashMap<>(RunState.opponentSplits), 0, null, 0, Double.NaN,
            RunState.cheats, false, true);
  }

  private static ReplayRecorder.Outcome replayOutcome(RunSummary.Outcome outcome) {
    switch (outcome) {
      case VICTORY: return ReplayRecorder.Outcome.VICTORY;
      case DEFEAT: return ReplayRecorder.Outcome.DEFEAT;
      case DRAW: return ReplayRecorder.Outcome.DRAW;
      default: return ReplayRecorder.Outcome.ENDED;
    }
  }

  private static long optMillis(JSONObject data, String key, long fallback) {
    return data.has(key) && !data.isNull(key) ? data.optLong(key, fallback) : fallback;
  }

  public void onRunCompleted(long rta) {
    MinecraftClient mc = MinecraftClient.getInstance();
    if (RunState.isRunning()) ReplayRecorder.runCompleted(rta);
    if (RunState.isRanked()) {
      lastFinishRtaMs = rta;
      mc.execute(() -> RunFinish.awaitOutcome(mc));
      reportRankedFinished(rta);
    } else if (RunState.isRoom()) {
      if (roomRaceDone) return;
      roomRaceDone = true;
      lastFinishRtaMs = rta;
      RoomState.Racer me = RoomState.racer(playerUuid);
      int place = me != null && me.finished() ? me.place : 0;
      Map<String, Long> splits = RunRecorder.splitTimes();
      String seedType = RunState.seedType;
      String bastionType = RunState.bastionType;
      mc.execute(() -> RunFinish.room(mc, RunSummary.Outcome.COMPLETED, false, rta, place, seedType, bastionType, splits));
      JSONObject payload = new JSONObject();
      payload.put("rta", rta);
      queueOrEmit(Events.PLAYER_FINISHED, payload);
    } else if (RunState.isSolo()) {
      String playerName = mc.getSession().getUsername();
      Map<String, Long> splits = RunRecorder.splitTimes();
      String seedType = RunState.seedType;
      String bastionType = RunState.bastionType;
      boolean unrated = RunState.cheats;
      RunState.lastError = null;
      mc.execute(() -> RunFinish.solo(mc, rta, playerName, seedType, bastionType, splits, unrated));
      reportSoloFinished(rta);
    }
  }

  // custom rooms

  private volatile boolean roomRaceDone = false;

  private static final long ROOM_REQUEST_TIMEOUT_MS = 10_000;

  public static boolean roomRequestPending() {
    long sent = RoomState.requestSentAtMs;
    return sent > 0 && System.currentTimeMillis() - sent < ROOM_REQUEST_TIMEOUT_MS;
  }

  public void createRoom() {
    if (!debounce(Events.CREATE_ROOM)) return;
    if (RoomState.inRoom() || roomRequestPending()) return;
    beforeRoomRequest();
    JSONObject payload = new JSONObject();
    payload.put("modifiers", new JSONArray(RunModifiers.selectedIds()));
    queueOrEmit(Events.CREATE_ROOM, payload);
  }

  public void joinRoom(String code) {
    if (!debounce(Events.JOIN_ROOM)) return;
    if (RoomState.inRoom() || roomRequestPending() || code == null || code.trim().isEmpty()) return;
    beforeRoomRequest();
    JSONObject payload = new JSONObject();
    payload.put("code", code.trim().toUpperCase(java.util.Locale.ROOT));
    payload.put("modifiers", new JSONArray(RunModifiers.selectedIds()));
    queueOrEmit(Events.JOIN_ROOM, payload);
  }

  private void beforeRoomRequest() {
    if (RunState.isQueued()) leaveQueue();
    RoomState.lastError = null;
    RoomState.removedReason = null;
    RoomState.requestSentAtMs = System.currentTimeMillis();
  }

  public void leaveRoom() {
    if (!debounce(Events.LEAVE_ROOM)) return;
    if (!RoomState.inRoom()) return;
    queueOrEmit(Events.LEAVE_ROOM);
    RoomState.clear();
    MatchChat.close(null);
  }

  public void updateRoomSettings(RoomState.Settings settings) {
    if (!debounce(Events.ROOM_SETTINGS_UPDATE)) return;
    List<String> ids = new ArrayList<>();
    for (com.coatt.tascade.modifier.RunModifier.Type type : com.coatt.tascade.modifier.RunModifier.Type.values()) {
      for (com.coatt.tascade.modifier.RunModifier modifier : RunModifiers.ofType(type)) ids.add(modifier.id);
    }
    queueOrEmit(Events.ROOM_SETTINGS_UPDATE, settings.toJson(ids));
  }

  public void startRoomRace() {
    if (!debounce(Events.ROOM_START)) return;
    queueOrEmit(Events.ROOM_START);
  }

  public void setRoomReady(boolean ready) {
    if (!debounce(Events.ROOM_READY)) return;
    JSONObject payload = new JSONObject();
    payload.put("ready", ready);
    queueOrEmit(Events.ROOM_READY, payload);
  }

  public void sendRoomModifiers() {
    if (!debounce(Events.ROOM_SET_MODIFIERS)) return;
    if (!RoomState.inRoom()) return;
    JSONObject payload = new JSONObject();
    payload.put("modifiers", new JSONArray(RunModifiers.selectedIds()));
    queueOrEmit(Events.ROOM_SET_MODIFIERS, payload);
  }

  public void kickFromRoom(String uuid, boolean ban) {
    if (!debounce(Events.ROOM_KICK + ":" + uuid)) return;
    JSONObject payload = new JSONObject();
    payload.put("uuid", uuid);
    payload.put("ban", ban);
    queueOrEmit(Events.ROOM_KICK, payload);
  }

  public void unbanFromRoom(String uuid) {
    if (!debounce(Events.ROOM_UNBAN + ":" + uuid)) return;
    JSONObject payload = new JSONObject();
    payload.put("uuid", uuid);
    queueOrEmit(Events.ROOM_UNBAN, payload);
  }

  public void setRoomCoHost(String uuid, boolean coHost) {
    if (!debounce("co_host:" + uuid)) return;
    JSONObject payload = new JSONObject();
    payload.put("uuid", uuid);
    queueOrEmit(coHost ? Events.ROOM_ADD_CO_OWNER : Events.ROOM_REMOVE_CO_OWNER, payload);
  }

  public void transferRoomOwner(String uuid) {
    if (!debounce(Events.ROOM_TRANSFER_OWNER)) return;
    JSONObject payload = new JSONObject();
    payload.put("uuid", uuid);
    queueOrEmit(Events.ROOM_TRANSFER_OWNER, payload);
  }

  public void roomVote(String type) {
    if (!debounce(Events.ROOM_VOTE + ":" + type)) return;
    if (!RunState.isRoom()) return;
    RoomState.Vote vote = RoomState.vote;
    if (vote != null && (!vote.type.equals(type) || RoomState.voted)) return;
    RoomState.voted = true;
    if (vote == null) MatchSounds.voteStarted();
    else MatchSounds.voteAgreed();
    JSONObject payload = new JSONObject();
    payload.put("type", type);
    queueOrEmit(Events.ROOM_VOTE, payload);
  }

  public void endRoomRace() {
    if (!debounce(Events.ROOM_END_RACE)) return;
    queueOrEmit(Events.ROOM_END_RACE);
  }

  public void forfeitRoomRace() {
    if (!debounce(Events.ROOM_FORFEIT)) return;
    if (!RunState.isRoom() || RunFinish.isActive() || roomRaceDone) return;
    roomRaceDone = true;
    queueOrEmit(Events.ROOM_FORFEIT);
    MinecraftClient mc = MinecraftClient.getInstance();
    mc.execute(() -> RunFinish.room(mc, RunSummary.Outcome.DEFEAT, true, RunFinish.elapsedSinceRaceStart(),
            0, RunState.seedType, RunState.bastionType, RunRecorder.splitTimes()));
  }

  // solo mode

  public void startSoloRun() {
    if (!debounce(Events.SOLO_START)) return;
    RunState.lastError = null;
    queueOrEmit(Events.SOLO_START, soloOptions());
  }

  private void reportSoloFinished(long rta) {
    if (!RunState.isSolo()) return;
    RunState.mode = RunState.Mode.NONE;
    soloFinishUnanswered = true;
    JSONObject payload = RunRecorder.toPayload();
    payload.put("rta", rta);
    queueOrEmit(Events.SOLO_FINISH, payload);
  }

  public void onPlayerLeftWorld() {
    if (!MinecraftClient.getInstance().isRunning()) {
      onGameClosing();
      return;
    }
    ReplayRecorder.leftWorld(RunState.seedChanging);
    // a seed change tears the world down on purpose
    if (RunState.seedChanging) return;
    if (RunState.isSolo()) {
      queueOrEmit(Events.SOLO_ABANDON, RunRecorder.toPayload());
    }
    if (RunState.isRoom()) {
      if (!roomRaceDone) queueOrEmit(Events.ROOM_FORFEIT);
      roomRaceDone = true;
      RunState.endRun();
      return;
    }
    if (MatchChat.isOpen() && MatchChat.isMatchOver()) {
      queueOrEmit(Events.CHAT_LEAVE);
      MatchChat.close(null);
    } else if (MatchChat.isOpen() && RunState.isRanked()) {
      chatLeftMatchId = RunState.matchId;
    }
    RunState.endRun();
  }

  public void onGameClosing() {
    ReplayRecorder.leftWorld(RunState.seedChanging);
    // the seal needs the world's last files for a rejoin to match
    if (RunState.isRunning()) WorldSeal.flush(2_000);
  }

  public void onJoinedWorld() {
    if (!clearChatOnJoin) return;
    clearChatOnJoin = false;
    MinecraftClient.getInstance().inGameHud.getChatHud().clear(false);
  }

  // holds the player on RaceWaitScreen (pausing the server) until the countdown ends
  public void tickRaceGate() {
    if (!RunState.waitingForRaceStart || RunFinish.isActive()) return;

    MinecraftClient mc = MinecraftClient.getInstance();
    if (mc.world == null || mc.player == null) return;
    if (mc.currentScreen instanceof DownloadingTerrainScreen) return;
    WorldSnapshot.takeOnce(mc.getServer(), RunRecorder.getWorldName());

    if (RunState.isVersus()) {
      // reported here, not on joinWorld, which fires while terrain is still loading
      reportReady();
    } else if (countdownEndsAtMs == 0) {
      countdownEndsAtMs = System.currentTimeMillis() + SOLO_COUNTDOWN_MS;
    }
    tickCountdown();

    int countdown = RunState.raceCountdown;
    if (countdown == 0) {
      RunState.waitingForRaceStart = false;
      RunState.raceStartedAtMs = System.currentTimeMillis();
      RunResume.raceStarted(RunState.raceStartedAtMs);
      if (RunState.isSolo()) queueOrEmit(Events.SOLO_RACE_STARTED);
      if (mc.currentScreen instanceof RaceWaitScreen) mc.openScreen(null);
      // start RTA at GO so the countdown isn't charged to the run
      Main.onRaceStart();
      return;
    }

    if (!(mc.currentScreen instanceof RaceWaitScreen)) {
      mc.openScreen(new RaceWaitScreen());
    }
  }

  public Screen raceGateScreen() {
    MinecraftClient mc = MinecraftClient.getInstance();
    if (!RunState.waitingForRaceStart || RunFinish.isActive() || mc.world == null || mc.player == null) return null;
    if (RunState.isSolo()) {
      if (countdownEndsAtMs == 0) countdownEndsAtMs = System.currentTimeMillis() + SOLO_COUNTDOWN_MS;
      tickCountdown();
    }
    return new RaceWaitScreen();
  }

  private void tickCountdown() {
    long endsAt = countdownEndsAtMs;
    if (endsAt == 0) return;
    long remainingMs = endsAt - System.currentTimeMillis();
    RunState.raceCountdown = (int) Math.max(0, (remainingMs + 999) / 1000);
  }

  // connection loss

  public boolean isOnline() {
    return socket != null && socket.connected() && authenticated;
  }

  public void tickConnection() {
    tickLeftRun();
    tickRejoinArrival();
    if ((RunState.rejoined || RunState.worldReset) && RunState.isRunning() && RunState.raceStartedAtMs > 0
            && MinecraftClient.getInstance().player != null) {
      Main.pinRunClock(RunState.raceStartedAtMs);
    }
    long droppedAt = disconnectedAtMs;
    if (droppedAt == 0 || !RunState.isRunning() || RunFinish.isActive()) {
      lostWarningSeconds = -1;
      return;
    }
    long left = RECONNECT_GRACE_MS - (System.currentTimeMillis() - droppedAt);
    if (left <= 0) {
      loseRun(false);
      return;
    }
    int seconds = (int) ((left + 999) / 1000);
    if (seconds == lostWarningSeconds) return;
    if (lostWarningSeconds == -1) MatchSounds.disconnected();
    lostWarningSeconds = seconds;
    // the action bar lasts three seconds, refreshing every second keeps it up
    MinecraftClient.getInstance().inGameHud.setOverlayMessage(
            new LiteralText("Reconnecting... " + seconds + "s").formatted(Formatting.RED), false);
  }

  private void showReconnected() {
    lostWarningSeconds = -1;
    MinecraftClient mc = MinecraftClient.getInstance();
    if (mc.world != null) mc.inGameHud.setOverlayMessage(new LiteralText("Reconnected").formatted(Formatting.GREEN), false);
  }

  // anything the server restores arrives before the probe's answer, if not, the run is gone
  private void confirmRunHeld() {
    awaitingRunRestore = true;
    socket.emit(Events.LATENCY_PING, new Object[0], ack -> {
      if (!awaitingRunRestore) return;
      awaitingRunRestore = false;
      MinecraftClient.getInstance().execute(() -> loseRun(true));
    });
  }

  private void loseRun(boolean endedByServer) {
    if (!RunState.isRunning() || RunFinish.isActive()) return;
    boolean ranked = RunState.isRanked();
    boolean room = RunState.isRoom();
    System.err.println("Lost the run to the connection ("
            + (endedByServer ? "the server no longer had it" : "offline too long") + ").");
    RunState.endRun();
    lostWarningSeconds = -1;
    pendingWorldStart.set(null);
    synchronized (pendingActionsLock) {
      pendingActions.clear();
    }
    if (!RoomState.inRoom()) MatchChat.close(null);

    MinecraftClient mc = MinecraftClient.getInstance();
    if (mc.world != null) {
      mc.world.disconnect();
      mc.disconnect(new SaveLevelScreen(new TranslatableText("menu.savingLevel")));
      mc.openScreen(new TitleScreen());
    }
    Screen menu = mc.currentScreen instanceof TascadeScreen ? mc.currentScreen : new MainScreen(new TitleScreen());
    mc.openScreen(room ? RunNoticeScreen.lostRoomRace(menu, endedByServer) : RunNoticeScreen.lostConnection(menu, ranked, endedByServer));
  }

  // rejoining a run left by a closed game

  public static final class RejoinOffer {
    public final RunResume.Saved left;
    private final JSONObject restored;
    public final long raceStartedAtMs;
    private volatile boolean verified = false;
    private volatile long sealSeq = 0;
    private boolean shown = false;

    private RejoinOffer(RunResume.Saved left, JSONObject restored) {
      this.left = left;
      this.restored = restored;
      if (restored.has("raceElapsedMs")) {
        this.raceStartedAtMs = restored.isNull("raceElapsedMs") ? 0 : System.currentTimeMillis() - restored.getLong("raceElapsedMs");
      } else {
        this.raceStartedAtMs = left.raceStartedAtMs;
      }
    }
  }

  private static String seedId(JSONObject seed) {
    if (seed == null) return null;
    return seed.has("id") ? String.valueOf(seed.get("id")) : seed.optString("overworld_seed", null);
  }

  private void checkLeftRun() {
    socket.emit(Events.LATENCY_PING, new Object[0], ack -> {
      RunResume.Saved left = RunResume.saved();
      if (left == null || rejoinOffer != null || RunState.isRunning()) return;
      RunResume.forget();
      WorldSnapshot.discard();
      if (!left.ranked) return;
      String reason = "It was over by the time you came back. Its result is in your match history.";
      pendingNoticeInTascade = true;
      pendingNotice = parent -> RunNoticeScreen.endedWhileAway(parent, true, reason);
    });
  }

  private void offerRejoin(RunResume.Saved left, JSONObject restored) {
    RejoinOffer offer = new RejoinOffer(left, restored);
    if (offer.raceStartedAtMs <= 0 || !RunResume.worldExists(left)) {
      System.out.println("Can't rejoin the run left behind ("
              + (offer.raceStartedAtMs <= 0 ? "its race hadn't started" : "its world is gone") + "); ending it.");
      endLeftRun(left, true);
      return;
    }
    rejoinOffer = offer;
    JSONObject seal = restored.optJSONObject("seal");
    if (seal == null) {
      offer.verified = true;
      return;
    }
    // reading every chunk takes a while, so off the socket thread
    Thread check = new Thread(() -> {
      boolean matches;
      String found;
      try {
        Path world = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("saves").resolve(left.worldName);
        WorldSeal.Check result = WorldSeal.check(world, seal);
        matches = left.worldName.equals(seal.optString("world")) && result.mismatched.isEmpty();
        offer.sealSeq = result.lastSeq;
        found = result.files + " files on disk, " + result.mismatched.size() + " not matching"
                + (result.mismatched.isEmpty() ? "" : ": " + result.mismatched.subList(0, Math.min(10, result.mismatched.size())));
      } catch (Exception e) {
        found = e.toString();
        matches = false;
      }
      System.out.println("World seal " + (matches ? "matches" : "DOESN'T match") + " for " + left.worldName
              + " (" + found + ")");
      boolean ok = matches;
      MinecraftClient.getInstance().execute(() -> {
        if (rejoinOffer != offer) return;
        if (ok) {
          offer.verified = true;
        } else {
          refuseRejoin(offer);
        }
      });
    }, "Tascade-SealCheck");
    check.setDaemon(true);
    check.start();
  }

  private void refuseRejoin(RejoinOffer offer) {
    JSONObject report = new JSONObject();
    report.put("world", offer.left.worldName);
    queueOrEmit(Events.SEAL_MISMATCH, report);
    endLeftRun(offer.left, true);
    boolean ranked = offer.left.ranked;
    pendingNoticeInTascade = false;
    pendingNotice = parent -> RunNoticeScreen.worldChanged(parent, ranked);
  }

  void sendSeal(String world, String key, JSONArray entries, Runnable onAck) {
    JSONObject payload = new JSONObject();
    payload.put("world", world);
    payload.put("key", key);
    payload.put("entries", entries);
    queueOrEmit(Events.SEAL_UPDATE, payload, ackArgs -> onAck.run());
  }

  private void endLeftRun(RunResume.Saved left, boolean cutOff) {
    rejoinOffer = null;
    RunResume.forget();
    WorldSnapshot.discard();
    if (left.ranked) {
      queueOrEmit(Events.MATCH_FORFEIT);
    } else {
      JSONObject payload = new JSONObject();
      if (cutOff) payload.put("reason", "disconnected");
      queueOrEmit(Events.SOLO_ABANDON, payload);
    }
  }

  private void tickLeftRun() {
    MinecraftClient mc = MinecraftClient.getInstance();
    RejoinOffer offer = rejoinOffer;
    Function<Screen, Screen> notice = pendingNotice;
    if (offer == null && notice == null) return;
    if (offer != null && !offer.shown && mc.world != null && !RunState.isRunning()) {
      endLeftRun(offer.left, true);
      return;
    }
    boolean tascade = mc.currentScreen instanceof TascadeScreen;
    boolean menu = tascade || mc.currentScreen instanceof TitleScreen && (notice == null || !pendingNoticeInTascade);
    if (mc.getOverlay() != null || mc.world != null || !menu) return;
    if (notice != null) {
      pendingNotice = null;
      Screen parent = mc.currentScreen instanceof RejoinScreen ? ((RejoinScreen) mc.currentScreen).parent() : mc.currentScreen;
      mc.openScreen(notice.apply(parent));
    } else if (!offer.shown && offer.verified) {
      offer.shown = true;
      mc.openScreen(new RejoinScreen(mc.currentScreen, offer));
    }
  }

  public void rejoin(RejoinOffer offer) {
    if (rejoinOffer != offer || !offer.verified) return;
    rejoinOffer = null;
    RunResume.Saved left = offer.left;
    System.out.println("Rejoining " + (left.ranked ? "match " + left.matchId : "solo run")
            + " in " + left.worldName + ".");

    RunState.mode = left.ranked ? RunState.Mode.RANKED : RunState.Mode.SOLO;
    RunState.matchId = left.matchId;
    RunState.opponentName = left.opponentName;
    RunState.opponentUuid = left.opponentUuid;
    RunState.opponentElo = left.opponentElo;
    RunState.opponentWorldProgress = -1;
    RunState.opponentSplits.clear();
    RunState.opponentGoals.clear();
    RunState.seedType = left.seedType;
    RunState.bastionType = left.bastionType;
    RunState.cheats = left.cheats;
    RunState.queueState = RunState.QueueState.NONE;
    RunState.clearVote();
    RunState.soloSeedRequestedAtMs = 0;
    RunState.raceStartedAtMs = offer.raceStartedAtMs;
    RunState.waitingForRaceStart = false;
    RunState.readySent = true;
    RunState.raceCountdown = -1;
    countdownEndsAtMs = 0;
    RunState.rejoined = true;
    RunState.worldReset = false;
    currentSeed = left.seed;
    WorldSnapshot.resume(left.snapshot);
    lastFinishRtaMs = -1;
    soloFinishUnanswered = false;
    RunFinish.reset();
    RunRecorder.reset(left.worldName);
    RunGoals.reset();
    applyRestoredProgress(offer.restored);
    applyModifierInfo(offer.restored);
    RunState.awaitingDeadlineRtaMs = -1;
    RunState.awaitingIsMine = false;
    if (left.ranked) onAwaiting(offer.restored.optJSONObject("awaiting"));
    if (left.ranked && left.opponentName != null) MatchChat.rejoin(left.opponentName);

    JSONObject seal = offer.restored.optJSONObject("seal");
    if (seal != null) WorldSeal.resume(left.worldName, seal, offer.sealSeq);
    rejoinArriving = true;
    MinecraftClient mc = MinecraftClient.getInstance();
    mc.method_29970(new SaveLevelScreen(new LiteralText(left.ranked ? "Rejoining your match..." : "Rejoining your run...")));
    RunWorldOpening.run(() -> mc.startIntegratedServer(left.worldName));
  }

  // removing vanilla's player immunity on join and the game pauses once the player can see
  private void tickRejoinArrival() {
    if (!rejoinArriving) return;
    MinecraftClient mc = MinecraftClient.getInstance();
    if (!RunState.rejoined) {
      rejoinArriving = false;
      return;
    }
    if (mc.world == null || mc.player == null) return;
    if (mc.player.isDead()) {
      rejoinArriving = false;
      return;
    }
    if (mc.currentScreen != null) return;
    rejoinArriving = false;
    mc.openPauseMenu(false);
    IntegratedServer server = mc.getServer();
    if (server == null) return;
    UUID id = mc.player.getUuid();
    server.execute(() -> {
      ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
      if (player != null) ((ServerPlayerEntityAccessor) player).tascade$setJoinInvulnerabilityTicks(0);
    });
  }

  public void declineRejoin(RejoinOffer offer) {
    if (rejoinOffer != offer) return;
    System.out.println("Player gave up the run left behind.");
    endLeftRun(offer.left, false);
  }

  private static void applyRestoredProgress(JSONObject restored) {
    JSONArray splits = restored.optJSONArray("splits");
    for (int i = 0; splits != null && i < splits.length(); i++) {
      JSONObject split = splits.optJSONObject(i);
      if (split == null || split.isNull("rta")) continue;
      RunRecorder.recordSplit(split.optString("name"), split.optLong("rta"), split.optLong("igt", -1));
    }
    JSONArray goals = restored.optJSONArray("goals");
    for (int i = 0; goals != null && i < goals.length(); i++) {
      JSONObject goal = goals.optJSONObject(i);
      if (goal != null && RunGoals.isGoal(goal.optString("goal")) && !goal.isNull("rta")) {
        RunGoals.restore(goal.optString("goal"), goal.optLong("rta"));
      }
    }
    JSONArray opponentSplits = restored.optJSONArray("opponentSplits");
    for (int i = 0; opponentSplits != null && i < opponentSplits.length(); i++) {
      JSONObject split = opponentSplits.optJSONObject(i);
      if (split == null || split.isNull("rta")) continue;
      String name = split.optString("name");
      if (RunRecorder.isTrackedSplit(name)) RunState.opponentSplits.putIfAbsent(name, split.optLong("rta"));
    }
    JSONArray opponentGoals = restored.optJSONArray("opponentGoals");
    for (int i = 0; opponentGoals != null && i < opponentGoals.length(); i++) {
      JSONObject goal = opponentGoals.optJSONObject(i);
      if (goal != null && RunGoals.isGoal(goal.optString("goal")) && !goal.isNull("rta")) {
        RunState.opponentGoals.putIfAbsent(goal.optString("goal"), goal.optLong("rta"));
      }
    }
  }

  private String endedWhileAwayReason(JSONObject data, RunResume.Saved left) {
    String result = data.optString("result", "");
    String winner = data.optString("winnerUuid", null);
    String opponent = left.opponentName != null ? left.opponentName : "Your opponent";
    boolean won = isMe(winner);
    if ("DRAW".equals(result)) return "It ended in a draw.";
    if ("FORFEIT".equals(result)) {
      return won ? opponent + " forfeited, so you won." : "You were away for over two minutes, so you forfeited.";
    }
    if ("COMPLETE".equals(result)) return won ? "You won." : opponent + " finished first.";
    return "It was cancelled.";
  }

  // menus

  public void requestLeaderboard(String sortBy, int page, int limit, Consumer<JSONObject> onReply) {
    JSONObject payload = new JSONObject();
    payload.put("sortBy", sortBy);
    payload.put("page", page);
    payload.put("limit", limit);
    request(Events.LEADERBOARD_GET, payload, onReply);
  }

  public void requestLeaderboardTop(String sortBy, int count, Consumer<JSONObject> onReply) {
    requestLeaderboardFrom(sortBy, 1, count, new JSONArray(), onReply);
  }

  private void requestLeaderboardFrom(String sortBy, int page, int count, JSONArray entries,
                                      Consumer<JSONObject> onReply) {
    requestLeaderboard(sortBy, page, LEADERBOARD_PAGE_LIMIT, reply -> {
      if (reply.has("error")) {
        onReply.accept(reply);
        return;
      }
      JSONArray pageEntries = reply.optJSONArray("entries");
      for (int i = 0; pageEntries != null && i < pageEntries.length() && entries.length() < count; i++) {
        entries.put(pageEntries.get(i));
      }
      if (entries.length() < count && page < reply.optInt("totalPages", 1)) {
        requestLeaderboardFrom(sortBy, page + 1, count, entries, onReply);
      } else {
        reply.put("entries", entries);
        onReply.accept(reply);
      }
    });
  }

  public void requestProfile(String uuid, int playsLimit, Consumer<JSONObject> onReply) {
    JSONObject payload = new JSONObject();
    putPlayer(payload, uuid);
    payload.put("playsLimit", playsLimit);
    request(Events.PROFILE_GET, payload, onReply);
  }

  public void requestHistory(String uuid, String mode, int page, int limit, Consumer<JSONObject> onReply) {
    JSONObject payload = new JSONObject();
    putPlayer(payload, uuid);
    payload.put("mode", mode);
    payload.put("page", page);
    payload.put("limit", limit);
    request(Events.HISTORY_GET, payload, onReply);
  }

  public void requestRunDetails(boolean ranked, String id, Consumer<JSONObject> onReply) {
    requestApi((ranked ? "/api/match/" : "/api/run/") + id, onReply);
  }

  private static void requestApi(String path, Consumer<JSONObject> onReply) {
    MinecraftClient mc = MinecraftClient.getInstance();
    Thread request = new Thread(() -> {
      JSONObject reply = fetchApi(path);
      mc.execute(() -> onReply.accept(reply));
    }, "Tascade-Api");
    request.setDaemon(true);
    request.start();
  }

  private static JSONObject fetchApi(String path) {
    try {
      HttpURLConnection connection = (HttpURLConnection) new URL(SERVER_URL + path).openConnection();
      connection.setConnectTimeout(API_TIMEOUT_MS);
      connection.setReadTimeout(API_TIMEOUT_MS);
      connection.setRequestProperty("Accept", "application/json");
      int status = connection.getResponseCode();
      InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
      ByteArrayOutputStream body = new ByteArrayOutputStream();
      if (in != null) {
        try (InputStream stream = in) {
          byte[] buffer = new byte[8192];
          for (int n; (n = stream.read(buffer)) > 0; ) body.write(buffer, 0, n);
        }
      }
      JSONObject json = body.size() > 0 ? new JSONObject(new String(body.toByteArray(), StandardCharsets.UTF_8)) : new JSONObject();
      if (status >= 400 && !json.has("error")) json.put("error", "Tascade answered with an error (" + status + ").");
      return json;
    } catch (Exception e) {
      System.err.println("GET " + path + " failed: " + e);
      return new JSONObject().put("error", "Couldn't reach Tascade.");
    }
  }

  public void requestQueueInfo(Consumer<JSONObject> onReply) {
    request(Events.QUEUE_INFO_GET, new JSONObject(), onReply);
  }

  public String getPlayerUuid() {
    return playerUuid;
  }

  // right after a reconnect the server may not have restored the session yet
  private void putPlayer(JSONObject payload, String uuid) {
    String player = uuid != null ? uuid : playerUuid;
    if (player != null) payload.put("uuid", player);
  }

  private static void clearMenuCache() {
    MinecraftClient.getInstance().execute(BackendCache::clear);
  }

  private void request(String event, JSONObject payload, Consumer<JSONObject> onReply) {
    MinecraftClient mc = MinecraftClient.getInstance();
    queueOrEmit(event, payload, args -> {
      if (args.length > 0 && args[0] instanceof JSONObject) {
        JSONObject reply = (JSONObject) args[0];
        mc.execute(() -> onReply.accept(reply));
      }
    });
  }

  // helpers

  // resolved through the integrated server, which knows every advancement
  private static Text advancementAnnouncement(MinecraftClient mc, String username, String id) {
    Identifier identifier = Identifier.tryParse(id);
    Advancement advancement = (identifier != null && mc.getServer() != null)
            ? mc.getServer().getAdvancementLoader().get(identifier)
            : null;

    if (advancement != null && advancement.getDisplay() != null) {
      AdvancementDisplay display = advancement.getDisplay();
      if (!display.shouldAnnounceToChat()) return null;
      // copy the top node so the hover tooltip survives without mutating the original
      Text title = advancement.toHoverableText().shallowCopy().styled(style -> style.withColor(Formatting.GRAY));
      return opponentAdvancementLine(new TranslatableText("chat.type.advancement." + display.getFrame().getId(),
              new LiteralText(username), title));
    }

    String titleKey = "advancements." + id.substring(id.indexOf(':') + 1).replace('/', '.') + ".title";
    Text title = new LiteralText("[").append(new TranslatableText(titleKey)).append("]");
    return opponentAdvancementLine(new TranslatableText("chat.type.advancement.task", new LiteralText(username), title));
  }

  private static Text opponentAdvancementLine(TranslatableText line) {
    return line.formatted(Formatting.GRAY, Formatting.ITALIC);
  }

  private boolean isMe(String uuid) {
    return uuid != null && uuid.equals(playerUuid);
  }

  // runs on a tick, not via execute(): a queued task would block input (and the match chat) during generation
  private void startWorldFromSeed(JSONObject start, boolean ranked, boolean seedChange) {
    startWorldFromSeed(start, ranked, seedChange, false);
  }

  private void startWorldFromSeed(JSONObject start, boolean ranked, boolean seedChange, boolean abandonSolo) {
    JSONObject seed = start.getJSONObject("seed");
    boolean cheats = start.optBoolean("cheats", false);
    String overworldSeed = seed.optString("overworld_seed", null);
    String netherSeed = seed.optString("nether_seed", null);
    JSONObject overworldMeta = seed.optJSONObject("overworld_meta");
    JSONObject netherMeta = seed.optJSONObject("nether_meta");
    RunState.seedType = seed.optString("overworld_type", null);
    RunState.bastionType = seed.optString("bastion_type", null);
    // on the socket thread: the opponent's first progress can beat our next tick
    RunState.opponentWorldProgress = -1;
    MinecraftClient mc = MinecraftClient.getInstance();
    pendingWorldStart.set(() -> {
      try {
        if (abandonSolo) {
          // before the teardown, which would end the recording as a seed change
          ReplayRecorder.leftWorld(false);
          queueOrEmit(Events.SOLO_ABANDON, RunRecorder.toPayload());
        }
        if (mc.world != null) {
          RunState.seedChanging = true;
          mc.world.disconnect();
          mc.disconnect(seedChange ? SeedChangeScreen.closingWorld() : new ProgressScreen());
          RunState.seedChanging = false;
        }
        RunState.clearVote();
        RunState.soloSeedRequestedAtMs = 0;
        soloFinishUnanswered = false;
        // armed only once the old world is gone, or a tick could report ready for it
        RunState.readySent = false;
        RunState.raceCountdown = -1;
        countdownEndsAtMs = 0;
        worldProgressSent = -1;
        worldProgressSentAtMs = 0;
        RunState.opponentSplits.clear();
        RunState.opponentGoals.clear();
        lastFinishRtaMs = -1;
        RunFinish.reset();
        RunState.cheats = cheats;
        RunState.worldReset = false;
        currentSeed = seed;
        WorldSnapshot.discard();
        applyModifierInfo(start);
        RunState.awaitingDeadlineRtaMs = -1;
        RunState.awaitingIsMine = false;
        RunState.waitingForRaceStart = true;
        clearChatOnJoin = seedChange;
        Main.startTascadeRun(overworldSeed, netherSeed, overworldMeta, netherMeta, ranked);
        if (RunState.isRoom()) RunResume.forget();
        else RunResume.save(seedId(seed), seed);
      } catch (Exception e) {
        RunState.seedChanging = false;
        clearChatOnJoin = false;
        e.printStackTrace();
      }
    });
  }

  public String resetWorldRefusal() {
    if (!RunState.isRunning() || RunFinish.isActive()) return "There's no run going.";
    if (RunState.waitingForRaceStart || RunState.raceStartedAtMs <= 0) return "The race hasn't started yet.";
    if (currentSeed == null) return "This run's seed isn't known here, so its world can't be made again.";
    if (RunState.seedChanging || RunState.soloSeedRequestedAtMs > 0 || pendingWorldStart.get() != null
            || (RunState.pendingVote == RunState.Vote.SEED && RunState.voteAgreed)) {
      return "A new world is already on its way.";
    }
    return null;
  }

  public void resetWorld() {
    if (resetWorldRefusal() != null) return;
    JSONObject seed = currentSeed;
    boolean ranked = RunState.isRanked();
    System.out.println("Resetting the run's world.");
    pendingWorldStart.set(() -> {
      try {
        MinecraftClient mc = MinecraftClient.getInstance();
        ModifierEffects.carryChargesOver();
        if (mc.world != null) {
          RunState.seedChanging = true;
          mc.world.disconnect();
          mc.disconnect(new SaveLevelScreen(new LiteralText("Resetting world...")));
          RunState.seedChanging = false;
        }
        RunState.worldReset = true;
        String restored = WorldSnapshot.available() ? WorldSnapshot.restore() : null;
        if (restored != null) {
          openRestoredWorld(restored);
          return;
        }
        System.out.println("No snapshot of the run's world to reset from; generating it again.");
        Main.startTascadeRun(seed.optString("overworld_seed", null), seed.optString("nether_seed", null),
                seed.optJSONObject("overworld_meta"), seed.optJSONObject("nether_meta"), ranked, true);
        if (RunState.isRoom()) {
          RunResume.forget();
        } else {
          RunResume.save(seedId(seed), seed);
          RunResume.raceStarted(RunState.raceStartedAtMs);
        }
      } catch (Exception e) {
        RunState.seedChanging = false;
        e.printStackTrace();
      }
    });
  }

  private void openRestoredWorld(String worldName) {
    MinecraftClient mc = MinecraftClient.getInstance();
    System.out.println("Resetting the run's world from its snapshot, as " + worldName + ".");
    ModifierEffects.newWorld();
    RunRecorder.moveToWorld(worldName);
    ReplayRecorder.arm(worldName);
    try {
      WorldSeal.sealCopied(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("saves").resolve(worldName));
    } catch (Exception e) {
      System.err.println("Couldn't seal the reset world: " + e);
    }
    if (RunState.isRoom()) RunResume.forget();
    else RunResume.movedToWorld(worldName);
    StandardSettingsCompat.beforeNewWorld();
    mc.method_29970(new SaveLevelScreen(new LiteralText("Resetting world...")));
    RunWorldOpening.run(() -> mc.startIntegratedServer(worldName));
    StandardSettingsCompat.afterNewWorld(worldName);
  }

  public void startPendingWorld() {
    Runnable start = pendingWorldStart.getAndSet(null);
    if (start != null) start.run();
  }

  private static void chat(Text message) {
    MinecraftClient mc = MinecraftClient.getInstance();
    mc.execute(() -> {
      if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(message);
    });
  }

  private static class MinecraftHelper {
    static String getGameDir() {
      try {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc != null && mc.runDirectory != null) {
          return mc.runDirectory.getAbsolutePath();
        }
      } catch (Throwable ignored) {}
      return ".";
    }
  }

  // Railway kills idle websockets (30s), so actions are queued and flushed on reconnect
  private final List<Runnable> pendingActions = new ArrayList<>();
  private final Object pendingActionsLock = new Object();

  private void queueOrEmit(String event) {
    queueOrEmit(event, null);
  }

  private void queueOrEmit(String event, JSONObject data) {
    queueOrEmit(event, data, null);
  }

  private void queueOrEmit(String event, JSONObject data, Ack ack) {
    Runnable emit = () -> {
      if (ack != null) socket.emit(event, new Object[]{data}, ack);
      else if (data == null) socket.emit(event);
      else socket.emit(event, data);
    };
    if (socket != null && socket.connected() && authenticated) {
      emit.run();
      return;
    }
    synchronized (pendingActionsLock) {
      pendingActions.add(emit);
    }
    System.out.println("Queueing action '" + event + "' until reconnection/auth completes.");
  }
  private void flushPendingActions() {
    List<Runnable> toRun;
    synchronized (pendingActionsLock) {
      toRun = new ArrayList<>(pendingActions);
      pendingActions.clear();
    }
    for (Runnable r : toRun) {
      try {
        r.run();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
