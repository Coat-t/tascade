package com.coatt.tascade.network;

import com.coatt.tascade.hud.RaceProgress;
import com.coatt.tascade.integration.RunGoals;
import com.coatt.tascade.integration.RunRecorder;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// written from socket threads: the room is an immutable snapshot swapped whole
public final class RoomState {
  private RoomState() {}

  /** seed modes, as the backend names them */
  public static final String SEED_RANDOM = "random";
  public static final String SEED_TYPE = "type";
  public static final String SEED_CUSTOM = "custom";
  public static final String SEED_ID = "id";
  /** the seed types a room can ask for, as filtered_seeds names them */
  public static final List<String> SEED_TYPES = Collections.unmodifiableList(java.util.Arrays.asList(
          "village", "buried_treasure", "shipwreck", "desert_temple", "ruined_portal"));

  /** finish goals */
  public static final String GOAL_COMPLETIONS = "completions";
  public static final String GOAL_TIME = "time";
  public static final String GOAL_FIRST_PLUS = "first_plus";

  /** vote rules */
  public static final String VOTE_OFF = "off";
  public static final String VOTE_MAJORITY = "majority";
  public static final String VOTE_UNANIMOUS = "unanimous";

  /** modifier rules */
  public static final String MOD_ALLOW = "allow";
  public static final String MOD_DISALLOW = "disallow";
  public static final String MOD_FORCE = "force";

  /** phases */
  public static final String PHASE_LOBBY = "lobby";
  public static final String PHASE_COUNTDOWN = "countdown";
  public static final String PHASE_RACE = "race";

  /** member and standing statuses */
  public static final String STATUS_LOBBY = "lobby";
  public static final String STATUS_RACING = "racing";
  public static final String STATUS_FINISHED = "finished";
  public static final String STATUS_DNF = "dnf";

  // the room

  private static volatile Room room = null;
  public static volatile String lastError = null;
  public static volatile long lastErrorAtMs = 0;
  public static volatile String removedReason = null;
  public static volatile int lobbyCountdown = -1;
  public static volatile long requestSentAtMs = 0;

  public static Room room() {
    return room;
  }

  public static boolean inRoom() {
    return room != null;
  }

  static void setRoom(Room updated) {
    room = updated;
    requestSentAtMs = 0;
  }

  static void clear() {
    room = null;
    lobbyCountdown = -1;
    requestSentAtMs = 0;
    clearRace();
  }

  public static void error(String message) {
    lastError = message;
    lastErrorAtMs = System.currentTimeMillis();
    requestSentAtMs = 0;
  }

  public static final class Member {
    public final String uuid;
    public final String username;
    public final boolean ready;
    public final boolean connected;
    public final List<String> modifiers;
    public final String status;
    public final boolean owner;
    public final boolean coHost;

    Member(JSONObject json, String ownerUuid, Set<String> coHosts) {
      this.uuid = json.optString("uuid", "");
      this.username = json.optString("username", "?");
      this.ready = json.optBoolean("ready", false);
      this.connected = json.optBoolean("connected", true);
      this.modifiers = strings(json.optJSONArray("modifiers"));
      this.status = json.optString("status", STATUS_LOBBY);
      this.owner = uuid.equals(ownerUuid);
      this.coHost = coHosts.contains(uuid);
    }

    public boolean privileged() {
      return owner || coHost;
    }
  }

  public static final class Banned {
    public final String uuid;
    public final String username;

    Banned(String uuid, String username) {
      this.uuid = uuid;
      this.username = username;
    }
  }

  public static final int NAME_MAX = 32;

  public static String shownCode(String code) {
    if (code == null) return "";
    if (!com.coatt.tascade.TascadeConfig.hideRoomCode()) return code;
    StringBuilder hidden = new StringBuilder();
    for (int i = 0; i < code.length(); i++) hidden.append('*');
    return hidden.toString();
  }

  public static final class Settings {
    public String name = "Room";
    public int maxPlayers = 8;
    public String seedMode = SEED_RANDOM;
    public String seedType = null;
    public String seedCustom = null;
    public Integer seedId = null;
    public String goalType = GOAL_COMPLETIONS;
    public int goalCount = 1;
    public int goalMinutes = 30;
    public int goalSeconds = 120;
    public boolean readyCheck = false;
    public boolean cheats = false;
    public String voteSeed = VOTE_MAJORITY;
    public int voteSeedWindowSec = 120;
    public String voteEnd = VOTE_MAJORITY;
    public final Map<String, String> modifiers = new LinkedHashMap<>();

    public static Settings fromJson(JSONObject json) {
      Settings s = new Settings();
      if (json == null) return s;
      s.name = json.optString("name", s.name);
      s.maxPlayers = json.optInt("maxPlayers", s.maxPlayers);
      JSONObject seed = json.optJSONObject("seed");
      if (seed != null) {
        s.seedMode = seed.optString("mode", s.seedMode);
        s.seedType = optString(seed, "type");
        s.seedCustom = optString(seed, "custom");
        s.seedId = seed.has("id") && !seed.isNull("id") ? seed.optInt("id") : null;
      }
      JSONObject goal = json.optJSONObject("goal");
      if (goal != null) {
        s.goalType = goal.optString("type", s.goalType);
        s.goalCount = goal.optInt("count", s.goalCount);
        s.goalMinutes = goal.optInt("minutes", s.goalMinutes);
        s.goalSeconds = goal.optInt("seconds", s.goalSeconds);
      }
      s.readyCheck = json.optBoolean("readyCheck", s.readyCheck);
      s.cheats = json.optBoolean("cheats", s.cheats);
      JSONObject voting = json.optJSONObject("voting");
      if (voting != null) {
        s.voteSeed = voting.optString("seed", s.voteSeed);
        s.voteSeedWindowSec = voting.optInt("seedWindowSec", s.voteSeedWindowSec);
        s.voteEnd = voting.optString("end", s.voteEnd);
      }
      JSONObject modifiers = json.optJSONObject("modifiers");
      if (modifiers != null) {
        for (String id : modifiers.keySet()) {
          String rule = modifiers.optString(id, MOD_ALLOW);
          if (!MOD_ALLOW.equals(rule)) s.modifiers.put(id, rule);
        }
      }
      return s;
    }

    public JSONObject toJson(Iterable<String> knownModifierIds) {
      JSONObject json = new JSONObject();
      json.put("name", name);
      json.put("maxPlayers", maxPlayers);
      JSONObject seed = new JSONObject();
      seed.put("mode", seedMode);
      seed.put("type", seedType == null ? JSONObject.NULL : seedType);
      seed.put("custom", seedCustom == null ? JSONObject.NULL : seedCustom);
      seed.put("id", seedId == null ? JSONObject.NULL : seedId);
      json.put("seed", seed);
      JSONObject goal = new JSONObject();
      goal.put("type", goalType);
      goal.put("count", goalCount);
      goal.put("minutes", goalMinutes);
      goal.put("seconds", goalSeconds);
      json.put("goal", goal);
      json.put("readyCheck", readyCheck);
      json.put("cheats", cheats);
      JSONObject voting = new JSONObject();
      voting.put("seed", voteSeed);
      voting.put("seedWindowSec", voteSeedWindowSec);
      voting.put("end", voteEnd);
      json.put("voting", voting);
      JSONObject rules = new JSONObject();
      for (String id : knownModifierIds) rules.put(id, modifierRule(id));
      for (Map.Entry<String, String> rule : modifiers.entrySet()) rules.put(rule.getKey(), rule.getValue());
      json.put("modifiers", rules);
      return json;
    }

    public Settings copy() {
      Settings s = new Settings();
      s.name = name;
      s.maxPlayers = maxPlayers;
      s.seedMode = seedMode;
      s.seedType = seedType;
      s.seedCustom = seedCustom;
      s.seedId = seedId;
      s.goalType = goalType;
      s.goalCount = goalCount;
      s.goalMinutes = goalMinutes;
      s.goalSeconds = goalSeconds;
      s.readyCheck = readyCheck;
      s.cheats = cheats;
      s.voteSeed = voteSeed;
      s.voteSeedWindowSec = voteSeedWindowSec;
      s.voteEnd = voteEnd;
      s.modifiers.putAll(modifiers);
      return s;
    }

    public String modifierRule(String id) {
      return modifiers.getOrDefault(id, MOD_ALLOW);
    }

    public String goalLabel() {
      switch (goalType) {
        case GOAL_TIME:
          return goalMinutes + " min time limit";
        case GOAL_FIRST_PLUS:
          return "First finish + " + durationLabel(goalSeconds);
        default:
          return goalCount <= 1 ? "First to finish" : "Until " + goalCount + " finish";
      }
    }

    public String seedLabel() {
      switch (seedMode) {
        case SEED_TYPE:
          return "Random " + (seedType != null ? com.coatt.tascade.hud.RunLabels.seedType(seedType) : "?") + " seed";
        case SEED_CUSTOM:
          return "Seed " + (seedCustom != null ? seedCustom : "?");
        case SEED_ID:
          return "Filtered seed #" + (seedId != null ? seedId : "?");
        default:
          return "Random filtered seed";
      }
    }

    private static String optString(JSONObject json, String key) {
      return json.has(key) && !json.isNull(key) ? json.optString(key, null) : null;
    }
  }

  public static String durationLabel(int seconds) {
    int minutes = seconds / 60;
    int rest = seconds % 60;
    if (minutes == 0) return rest + "s";
    return rest == 0 ? minutes + "m" : minutes + "m " + rest + "s";
  }

  public static final class Vote {
    public final String type;
    public final String initiator;
    public final String username;
    public final Set<String> yes;
    public final int needed;
    public final long expiresAtMs;

    Vote(String type, String initiator, String username, Set<String> yes, int needed, long expiresAtMs) {
      this.type = type;
      this.initiator = initiator;
      this.username = username;
      this.yes = yes;
      this.needed = needed;
      this.expiresAtMs = expiresAtMs;
    }

    static Vote fromJson(JSONObject json, long expiresInMs) {
      if (json == null) return null;
      Set<String> yes = new LinkedHashSet<>(strings(json.optJSONArray("yes")));
      return new Vote(json.optString("type", "seed"), json.optString("initiator", null),
              json.optString("username", "?"), Collections.unmodifiableSet(yes), json.optInt("needed", 1),
              System.currentTimeMillis() + expiresInMs);
    }

    public int secondsLeft() {
      return (int) Math.max(0, (expiresAtMs - System.currentTimeMillis() + 999) / 1000);
    }
  }

  public static final class Standing {
    public final String uuid;
    public final String username;
    public final Integer place;
    public final String status;
    public final Long rtaMs;
    public final Map<String, Long> splits;
    public final Map<String, Long> goals;
    final RaceProgress.Standing progress;

    Standing(String uuid, String username, Integer place, String status, Long rtaMs,
             Map<String, Long> splits, Map<String, Long> goals) {
      this.uuid = uuid;
      this.username = username;
      this.place = place;
      this.status = status;
      this.rtaMs = rtaMs;
      this.splits = Collections.unmodifiableMap(splits);
      this.goals = Collections.unmodifiableMap(goals);
      this.progress = RaceProgress.of(splits, goals);
    }

    static Standing fromJson(JSONObject json) {
      Map<String, Long> splits = new HashMap<>();
      JSONArray splitArray = json.optJSONArray("splits");
      for (int i = 0; splitArray != null && i < splitArray.length(); i++) {
        JSONObject split = splitArray.optJSONObject(i);
        if (split != null && !split.isNull("rta")) splits.put(split.optString("name"), split.optLong("rta"));
      }
      Map<String, Long> goals = new HashMap<>();
      JSONArray goalArray = json.optJSONArray("goals");
      for (int i = 0; goalArray != null && i < goalArray.length(); i++) {
        JSONObject goal = goalArray.optJSONObject(i);
        if (goal != null && !goal.isNull("rta")) goals.put(goal.optString("goal"), goal.optLong("rta"));
      }
      Integer place = json.has("place") && !json.isNull("place") ? json.optInt("place") : null;
      Long rta = json.has("rtaMs") && !json.isNull("rtaMs") ? json.optLong("rtaMs") : null;
      return new Standing(json.optString("uuid", ""), json.optString("username", "?"), place,
              json.optString("status", STATUS_DNF), rta, splits, goals);
    }

    public boolean finished() {
      return place != null;
    }
  }

  public static final class Room {
    public final String code;
    public final String ownerUuid;
    public final Set<String> coHosts;
    public final List<Member> members;
    public final List<Banned> banned;
    public final Settings settings;
    public final String phase;
    public final int countdown;
    public final String raceId;
    public final Set<String> racers;
    public final List<Standing> lastResults;

    Room(JSONObject json) {
      this.code = json.optString("code", "");
      this.ownerUuid = json.optString("ownerUuid", "");
      Set<String> coHosts = new LinkedHashSet<>(strings(json.optJSONArray("coHosts")));
      this.coHosts = Collections.unmodifiableSet(coHosts);
      List<Member> members = new ArrayList<>();
      JSONArray memberArray = json.optJSONArray("members");
      for (int i = 0; memberArray != null && i < memberArray.length(); i++) {
        JSONObject member = memberArray.optJSONObject(i);
        if (member != null) members.add(new Member(member, ownerUuid, coHosts));
      }
      this.members = Collections.unmodifiableList(members);
      List<Banned> banned = new ArrayList<>();
      JSONArray bannedArray = json.optJSONArray("banned");
      for (int i = 0; bannedArray != null && i < bannedArray.length(); i++) {
        Object entry = bannedArray.opt(i);
        if (entry instanceof JSONObject) {
          banned.add(new Banned(((JSONObject) entry).optString("uuid", ""), ((JSONObject) entry).optString("username", "?")));
        } else if (entry instanceof String) {
          banned.add(new Banned((String) entry, (String) entry));
        }
      }
      this.banned = Collections.unmodifiableList(banned);
      this.settings = Settings.fromJson(json.optJSONObject("settings"));
      this.phase = json.optString("phase", PHASE_LOBBY);
      this.countdown = json.has("countdown") && !json.isNull("countdown") ? json.optInt("countdown", -1) : -1;
      JSONObject race = json.optJSONObject("race");
      this.raceId = race != null ? race.optString("raceId", null) : null;
      this.racers = race != null
              ? Collections.unmodifiableSet(new LinkedHashSet<>(strings(race.optJSONArray("racers"))))
              : Collections.emptySet();
      List<Standing> results = new ArrayList<>();
      JSONArray resultArray = json.optJSONArray("lastResults");
      for (int i = 0; resultArray != null && i < resultArray.length(); i++) {
        JSONObject standing = resultArray.optJSONObject(i);
        if (standing != null) results.add(Standing.fromJson(standing));
      }
      this.lastResults = Collections.unmodifiableList(results);
    }

    public Member member(String uuid) {
      for (Member member : members) {
        if (member.uuid.equals(uuid)) return member;
      }
      return null;
    }

    public Member me() {
      return member(TascadeClient.getInstance().getPlayerUuid());
    }

    public boolean amOwner() {
      String me = TascadeClient.getInstance().getPlayerUuid();
      return me != null && me.equals(ownerUuid);
    }

    public boolean amPrivileged() {
      Member me = me();
      return me != null && me.privileged();
    }

    public boolean racing() {
      return PHASE_RACE.equals(phase);
    }
  }

  // the race

  public static final class Racer {
    public final String uuid;
    public final String username;
    public final List<String> modifiers;
    public final Map<String, Long> splits = new ConcurrentHashMap<>();
    public final Map<String, Long> goals = new ConcurrentHashMap<>();
    public volatile int worldProgress = -1;
    public volatile long finishedRtaMs = -1;
    public volatile int place = 0;
    public volatile String dnfReason = null;

    Racer(String uuid, String username, List<String> modifiers) {
      this.uuid = uuid;
      this.username = username;
      this.modifiers = modifiers;
    }

    public boolean finished() {
      return finishedRtaMs >= 0;
    }

    public boolean out() {
      return dnfReason != null;
    }
  }

  private static final Map<String, Racer> racers = Collections.synchronizedMap(new LinkedHashMap<>());
  public static volatile String raceId = null;
  public static volatile long raceEndsAtMs = 0;
  public static volatile Vote vote = null;
  public static volatile boolean voted = false;
  public static volatile List<Standing> finalStandings = null;
  public static volatile String endReason = null;

  public static List<Racer> racers() {
    synchronized (racers) {
      return new ArrayList<>(racers.values());
    }
  }

  public static Racer racer(String uuid) {
    return uuid == null ? null : racers.get(uuid);
  }

  public static List<Racer> others() {
    String me = TascadeClient.getInstance().getPlayerUuid();
    List<Racer> list = new ArrayList<>();
    for (Racer racer : racers()) {
      if (!racer.uuid.equals(me)) list.add(racer);
    }
    return list;
  }

  static void startRace(String id, JSONArray players) {
    synchronized (racers) {
      racers.clear();
      for (int i = 0; players != null && i < players.length(); i++) {
        JSONObject player = players.optJSONObject(i);
        if (player == null) continue;
        String uuid = player.optString("uuid", null);
        if (uuid == null) continue;
        racers.put(uuid, new Racer(uuid, player.optString("username", "?"), strings(player.optJSONArray("modifiers"))));
      }
    }
    raceId = id;
    raceEndsAtMs = 0;
    vote = null;
    voted = false;
    finalStandings = null;
    endReason = null;
  }

  static void restartRace() {
    for (Racer racer : racers()) {
      racer.splits.clear();
      racer.goals.clear();
      racer.worldProgress = -1;
      racer.finishedRtaMs = -1;
      racer.place = 0;
      racer.dnfReason = null;
    }
    raceEndsAtMs = 0;
    vote = null;
    voted = false;
  }

  static void clearRace() {
    synchronized (racers) {
      racers.clear();
    }
    raceId = null;
    raceEndsAtMs = 0;
    vote = null;
    voted = false;
    finalStandings = null;
    endReason = null;
  }

  public static List<Standing> standings() {
    List<Standing> list = new ArrayList<>();
    List<Standing> done = finalStandings;
    String me = TascadeClient.getInstance().getPlayerUuid();
    if (done != null) {
      list.addAll(done);
    } else {
      for (Racer racer : racers()) {
        boolean self = racer.uuid.equals(me);
        Map<String, Long> splits = self ? RunRecorder.splitTimes() : new HashMap<>(racer.splits);
        Map<String, Long> goals = self ? RunGoals.times() : new HashMap<>(racer.goals);
        String status = racer.finished() ? STATUS_FINISHED : racer.out() ? STATUS_DNF : STATUS_RACING;
        list.add(new Standing(racer.uuid, racer.username, racer.finished() ? racer.place : null, status,
                racer.finished() ? racer.finishedRtaMs : null, splits, goals));
      }
    }
    list.sort(STANDING_ORDER);
    return list;
  }

  private static final Comparator<Standing> STANDING_ORDER = (a, b) -> {
    if (a.finished() != b.finished()) return a.finished() ? -1 : 1;
    if (a.finished()) return Integer.compare(a.place, b.place);
    boolean aOut = STATUS_DNF.equals(a.status);
    boolean bOut = STATUS_DNF.equals(b.status);
    if (aOut != bOut) return aOut ? 1 : -1;
    return RaceProgress.compare(a.progress, b.progress);
  };

  public static int rankOf(Standing standing, List<Standing> all) {
    if (standing.finished()) return standing.place;
    int rank = 1;
    for (Standing other : all) {
      if (other != standing && STANDING_ORDER.compare(other, standing) < 0) rank++;
    }
    return rank;
  }

  static List<String> strings(JSONArray array) {
    if (array == null) return Collections.emptyList();
    List<String> list = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) {
      String value = array.optString(i, null);
      if (value != null) list.add(value);
    }
    return Collections.unmodifiableList(list);
  }
}
