package com.coatt.tascade.network;

import com.coatt.tascade.integration.RunRecorder;
import net.fabricmc.loader.api.FabricLoader;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RunResume {
  private static final String CONFIG_DIR = "config";
  private static final String FILE = "tascade-run.json";

  public static final class Saved {
    public final boolean ranked;
    public final String worldName;
    public final String matchId;
    public final String seedId;
    public final String seedType;
    public final String bastionType;
    public final boolean cheats;
    public final String opponentName;
    public final String opponentUuid;
    public final int opponentElo;
    public final long raceStartedAtMs;
    public final JSONObject seed;
    public final String snapshot;

    private Saved(JSONObject json) {
      ranked = json.optBoolean("ranked");
      worldName = json.optString("worldName", null);
      matchId = json.optString("matchId", null);
      seedId = json.optString("seedId", null);
      seedType = json.optString("seedType", null);
      bastionType = json.optString("bastionType", null);
      cheats = json.optBoolean("cheats");
      opponentName = json.optString("opponentName", null);
      opponentUuid = json.optString("opponentUuid", null);
      opponentElo = json.optInt("opponentElo");
      raceStartedAtMs = json.optLong("raceStartedAtMs");
      seed = json.optJSONObject("seed");
      snapshot = json.optString("snapshot", null);
    }
  }

  private static JSONObject current;
  private static boolean read = false;

  private RunResume() {}

  public static synchronized void save(String seedId, JSONObject seed) {
    JSONObject json = new JSONObject();
    json.put("ranked", RunState.isRanked());
    json.put("worldName", RunRecorder.getWorldName());
    if (RunState.matchId != null) json.put("matchId", RunState.matchId);
    if (seedId != null) json.put("seedId", seedId);
    if (RunState.seedType != null) json.put("seedType", RunState.seedType);
    if (RunState.bastionType != null) json.put("bastionType", RunState.bastionType);
    json.put("cheats", RunState.cheats);
    if (RunState.opponentName != null) json.put("opponentName", RunState.opponentName);
    if (RunState.opponentUuid != null) json.put("opponentUuid", RunState.opponentUuid);
    json.put("opponentElo", RunState.opponentElo);
    json.put("raceStartedAtMs", 0L);
    if (seed != null) json.put("seed", seed);
    current = json;
    read = true;
    write();
  }

  public static synchronized void snapshotTaken(String name) {
    if (current == null) return;
    current.put("snapshot", name);
    write();
  }

  public static synchronized void movedToWorld(String worldName) {
    if (current == null) return;
    current.put("worldName", worldName);
    write();
  }

  public static synchronized void raceStarted(long atMs) {
    if (current == null) return;
    current.put("raceStartedAtMs", atMs);
    write();
  }

  public static synchronized Saved saved() {
    if (!read) {
      read = true;
      try {
        Path file = file();
        if (Files.exists(file)) current = new JSONObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
      } catch (Exception e) {
        System.err.println("Couldn't read the saved run: " + e);
        current = null;
      }
    }
    return current != null ? new Saved(current) : null;
  }

  public static synchronized void forget() {
    current = null;
    read = true;
    try {
      Files.deleteIfExists(file());
    } catch (Exception e) {
      System.err.println("Couldn't delete the saved run: " + e);
    }
  }

  public static boolean worldExists(Saved saved) {
    return saved.worldName != null
            && Files.isDirectory(FabricLoader.getInstance().getGameDir().resolve("saves").resolve(saved.worldName));
  }

  private static void write() {
    try {
      Path file = file();
      Files.createDirectories(file.getParent());
      Files.write(file, current.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      System.err.println("Couldn't save the run for rejoining: " + e);
    }
  }

  private static Path file() {
    return FabricLoader.getInstance().getGameDir().resolve(CONFIG_DIR).resolve(FILE);
  }
}
