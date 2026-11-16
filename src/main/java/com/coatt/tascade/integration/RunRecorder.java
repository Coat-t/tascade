package com.coatt.tascade.integration;

import com.coatt.tascade.replay.record.ReplayRecorder;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// kept free of SpeedRunIGT types so it works without it installed
public final class RunRecorder {
  private static String worldName = null;
  private static JSONArray splits = new JSONArray();
  private static JSONArray advancements = new JSONArray();
  private static final Set<String> seenSplits = new HashSet<>();
  private static final Set<String> seenAdvancements = new HashSet<>();
  private static final Map<String, Long> splitTimes = new HashMap<>();

  // SpeedRunIGT also records other categories' splits, those are ignored
  private static final List<String> TRACKED_SPLITS = Collections.unmodifiableList(Arrays.asList(
          "enter_nether",
          "enter_bastion",
          "enter_fortress",
          RunGoals.FINDING_STRONGHOLD,
          "enter_stronghold",
          "enter_end",
          "kill_ender_dragon"));

  private RunRecorder() {}

  public static synchronized void reset(String newWorldName) {
    worldName = newWorldName;
    splits = new JSONArray();
    advancements = new JSONArray();
    seenSplits.clear();
    seenAdvancements.clear();
    splitTimes.clear();
  }

  public static synchronized void moveToWorld(String newWorldName) {
    worldName = newWorldName;
  }

  public static synchronized boolean hasSplit(String name) {
    return splitTimes.containsKey(name);
  }

  public static synchronized Map<String, Long> splitTimes() {
    return new HashMap<>(splitTimes);
  }

  public static boolean isTrackedSplit(String name) {
    return TRACKED_SPLITS.contains(name);
  }

  public static List<String> trackedSplits() {
    return TRACKED_SPLITS;
  }

  public static synchronized String getWorldName() {
    return worldName;
  }

  public static synchronized boolean recordSplit(String name, long rta, long igt) {
    if (!TRACKED_SPLITS.contains(name) || !seenSplits.add(name + "|" + rta)) return false;
    JSONObject split = new JSONObject();
    split.put("name", name);
    split.put("rta", rta);
    split.put("igt", igt);
    splits.put(split);
    splitTimes.putIfAbsent(name, rta);
    ReplayRecorder.split(name, rta);
    return true;
  }

  public static boolean isRootAdvancement(String id) {
    String path = id.substring(id.indexOf(':') + 1);
    return path.equals("root") || path.endsWith("/root");
  }

  public static synchronized boolean recordAdvancement(String id, String category, long rta) {
    if (isRootAdvancement(id) || !seenAdvancements.add(id)) return false;
    JSONObject advancement = new JSONObject();
    advancement.put("id", id);
    advancement.put("category", category);
    advancement.put("rta", rta);
    advancements.put(advancement);
    return true;
  }

  // backend shape: { splits: [{name, rta, igt}], advancements: [{id, category, rta}] }
  public static synchronized JSONObject toPayload() {
    JSONObject payload = new JSONObject();
    payload.put("splits", new JSONArray(splits.toString()));
    payload.put("advancements", new JSONArray(advancements.toString()));
    return payload;
  }
}
