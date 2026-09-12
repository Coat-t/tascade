package com.coatt.tascade.network;

import com.coatt.tascade.Main;
import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import org.json.JSONObject;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TascadeClient {
  private Socket socket;
  private static final String SERVER_URL = "https://tascadebackend-production.up.railway.app";
  private static final String CONFIG_DIR = "config";
  private static final String CONFIG_FILE = "tascade.json";

  private static TascadeClient INSTANCE;

  private boolean authenticated = false;
  private String reconnectKey = null;
  private String playerUuid = null;

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
        opts.timeout = 5000;
        socket = IO.socket(URI.create(SERVER_URL), opts);
        registerCoreListeners();
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

  // core listeners --
  private void registerCoreListeners() {
    // server sends this immediately on connect
    socket.on(Events.AUTH_CHALLENGE, args -> {
      Main.profileStatus = Main.ProfileState.LOADING;
      try {
        // use reconnectKey instead of Mojang auth if available
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
        System.out.println("[Tascade] Authenticated as " + username + " (" + playerUuid + ")");
        Main.nickname = username;
        Main.pp = (int) Math.round(data.getDouble("performancePoints"));
        Main.elo = data.getInt("elo");
        Main.profileStatus = Main.ProfileState.SUCCESS;
        String newKey = data.optString("reconnectKey", null);
        if (newKey != null && !newKey.isEmpty()) saveReconnectKey(newKey);
        flushPendingActions();
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
      System.err.println("[Tascade] Auth failed: " + reason);
      // On restore failure, clear the stored key
      if (reconnectKey != null) {
        reconnectKey = null;
        try {
          Files.deleteIfExists(Paths.get(MinecraftHelper.getGameDir(), CONFIG_DIR, CONFIG_FILE));
        } catch (Exception ignored) {}
      }
    });
    // socket connection statuses --
    socket.on(Socket.EVENT_CONNECT, args -> {
      // connected
    });

    socket.on(Socket.EVENT_DISCONNECT, args -> {
      Main.profileStatus = Main.ProfileState.FAILED;
    });

    socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
      // `args[0]` connection error
      Main.profileStatus = Main.ProfileState.FAILED;
    });

    socket.io().on(Manager.EVENT_RECONNECT_ATTEMPT, args -> {
      // no. `args[0]` attempt
    });

    socket.io().on(Manager.EVENT_RECONNECT_FAILED, args -> {
      // failed after max attempts
      Main.profileStatus = Main.ProfileState.FAILED;
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

  // queue and flush actions in case they are fired when Railway kills idle websockets (30s)
  private final List<Runnable> pendingActions = new ArrayList<>();
  private final Object pendingActionsLock = new Object();

  private void queueOrEmit(String event) {
    if (socket != null && socket.connected() && authenticated) {
      socket.emit(event);
      return;
    }
    synchronized (pendingActionsLock) {
      pendingActions.add(() -> socket.emit(event));
    }
    System.out.println("[Tascade] Queueing action '" + event + "' until reconnection/auth completes.");
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
