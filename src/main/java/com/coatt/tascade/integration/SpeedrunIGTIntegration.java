package com.coatt.tascade.integration;

import com.coatt.tascade.modifier.ModifierEffects;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.network.TascadeClient;
import com.redlimerl.speedrunigt.timer.InGameTimer;
import com.redlimerl.speedrunigt.timer.TimerAdvancementTracker;
import com.redlimerl.speedrunigt.timer.TimerStatus;
import com.redlimerl.speedrunigt.timer.category.RunCategories;
import com.redlimerl.speedrunigt.timer.logs.TimerTimeline;
import com.redlimerl.speedrunigt.timer.running.RunType;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// only touch after FabricLoader#isModLoaded("speedrunigt"), or loading throws NoClassDefFoundError
public final class SpeedrunIGTIntegration {
  private static final long POLL_INTERVAL_MS = 1000;

  private static volatile String fallbackTimerWorld = null;
  private static String watchedWorld = null;
  private static final Set<String> watchedSplits = new HashSet<>();

  private SpeedrunIGTIntegration() {}

  public static void init() {
    InGameTimer.onComplete(SpeedrunIGTIntegration::onRunCompleted);

    Thread recorder = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          InGameTimer timer = activeTimer();
          if (timer != null) record(timer);
          Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
          return;
        } catch (Throwable t) {
          t.printStackTrace();
        }
      }
    }, "Tascade-RunRecorder");
    recorder.setDaemon(true);
    recorder.start();

    System.out.println("SpeedRunIGT integration initialized.");
  }

  public static void onRaceStart() {
    InGameTimer timer = InGameTimer.getInstance();
    if (timer == null) return;
    timer.setStartTime(System.currentTimeMillis());
    timer.setRTAMode(true);
    System.out.println("Race started — RTA rebased to now, timer in RTA mode.");
    saveTimer();
  }

  // SpeedRunIGT only saves at its own pauses, so a crash would lose the timer
  private static void saveTimer() {
    try {
      Method save = InGameTimer.class.getDeclaredMethod("save");
      save.setAccessible(true);
      save.invoke(null);
    } catch (ReflectiveOperationException | RuntimeException e) {
      System.err.println("Couldn't have SpeedRunIGT save its timer: " + e);
    }
  }

  // SpeedRunIGT leaves closed time out of RTA on reopen, shift the start back to compensate
  public static void pinRta(long raceStartedAtMs) {
    InGameTimer timer = activeTimer();
    String world = RunRecorder.getWorldName();
    if (timer == null && world != null && !world.equals(fallbackTimerWorld)) {
      fallbackTimerWorld = world;
      InGameTimer.start(world, RunType.RANDOM_SEED);
      timer = InGameTimer.getInstance();
      if (timer != null) {
        timer.updateFirstInput();
        timer.updateFirstRendered();
      }
      timer = activeTimer();
      System.out.println("No saved timer in the rejoined world; started a new one.");
    }
    if (timer == null || timer.isCompleted()) return;
    if (!timer.isRTAMode()) timer.setRTAMode(true);
    long drift = (System.currentTimeMillis() - raceStartedAtMs) - timer.getRealTimeAttack();
    if (Math.abs(drift) > 50) timer.setStartTime(timer.getStartTime() - drift);
  }

  public static long rta() {
    InGameTimer timer = activeTimer();
    return timer != null ? timer.getRealTimeAttack() : -1;
  }

  public static long igt() {
    InGameTimer timer = activeTimer();
    return timer != null ? timer.getInGameTime() : -1;
  }

  private static InGameTimer activeTimer() {
    InGameTimer timer = InGameTimer.getInstance();
    return timer != null && timer.isStarted() && belongsToActiveRun(timer) ? timer : null;
  }

  private static boolean belongsToActiveRun(InGameTimer timer) {
    if (!RunState.isRunning()) return false;
    String runWorld = RunRecorder.getWorldName();
    return runWorld != null && runWorld.equals(timer.getWorldName());
  }

  private static void record(InGameTimer timer) {
    String world = timer.getWorldName();
    boolean firstLook = !Objects.equals(world, watchedWorld);
    if (firstLook) {
      watchedWorld = world;
      watchedSplits.clear();
    }
    for (TimerTimeline timeline : timer.getTimelines()) {
      if (timeline == null || timeline.getName() == null || timeline.getName().isEmpty()) continue;
      boolean newHere = watchedSplits.add(timeline.getName()) && !firstLook;
      if (RunRecorder.hasSplit(timeline.getName())) {
        if (newHere) ModifierEffects.splitReached(timeline.getName(), timeline.getRTA());
        continue;
      }
      if (RunRecorder.recordSplit(timeline.getName(), timeline.getRTA(), timeline.getIGT())) {
        ModifierEffects.splitReached(timeline.getName(), timeline.getRTA());
        TascadeClient.getInstance().sendSplit(timeline.getName(), timeline.getRTA(), timeline.getIGT());
        saveTimer();
      }
    }

    for (Map.Entry<String, TimerAdvancementTracker.AdvancementTrack> entry
            : timer.getAdvancementsTracker().getAdvancements().entrySet()) {
      TimerAdvancementTracker.AdvancementTrack track = entry.getValue();
      // recipe unlocks are tracked too, only keep real, completed advancements
      if (track == null || !track.isAdvancement() || !track.isComplete()) continue;
      String id = entry.getKey();
      String category = categoryOf(id);
      // the receiver renders it in their own language
      if (RunRecorder.recordAdvancement(id, category, track.getRTA())) {
        TascadeClient.getInstance().sendAdvancement(id, category);
      }
    }
  }

  private static String categoryOf(String advancementId) {
    String path = advancementId.substring(advancementId.indexOf(':') + 1);
    int slash = path.indexOf('/');
    return slash >= 0 ? path.substring(0, slash) : "";
  }
  private static void onRunCompleted(InGameTimer timer) {
    if (timer == null || !belongsToActiveRun(timer)) return;

    record(timer);
    TascadeClient.getInstance().onRunCompleted(timer.getRealTimeAttack());
  }

  // mirrors SpeedRunIGT's CreditsScreenMixin, since Tascade skips the credits
  public static void completeOnGameWon() {
    InGameTimer timer = InGameTimer.getInstance();
    if (timer == null || timer.getStatus() == TimerStatus.NONE) return;
    if (timer.getCategory() == RunCategories.ANY) InGameTimer.complete();
  }
}
