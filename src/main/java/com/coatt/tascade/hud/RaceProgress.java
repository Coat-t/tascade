package com.coatt.tascade.hud;

import com.coatt.tascade.integration.RunGoals;

import java.util.HashMap;
import java.util.Map;

// orders the race HUD: later milestone wins, at the same milestone, more points toward the next one, then whoever got there first
public final class RaceProgress {
    private static final int OVERWORLD = 0;
    private static final int NETHER = 1;

    private static final Map<String, Integer> MILESTONES = new HashMap<>();
    private static final Map<String, Step> STEPS = new HashMap<>();

    static {
        MILESTONES.put("enter_nether", NETHER);
        MILESTONES.put(RunGoals.FINDING_STRONGHOLD, 2);
        MILESTONES.put("enter_stronghold", 3);
        MILESTONES.put("enter_end", 4);
        MILESTONES.put("kill_ender_dragon", 5);
        MILESTONES.put(RunGoals.DRAGON_DYING, 6);

        STEPS.put(RunGoals.IRON, new Step(OVERWORLD, 1));
        STEPS.put(RunGoals.LAVA_BUCKET, new Step(OVERWORLD, 1));
        STEPS.put("enter_bastion", new Step(NETHER, 1));
        STEPS.put(RunGoals.GOLD_BLOCK, new Step(NETHER, 1));
        STEPS.put(RunGoals.BARTER, new Step(NETHER, 1));
        STEPS.put(RunGoals.OBSIDIAN, new Step(NETHER, 1));
        STEPS.put("enter_fortress", new Step(NETHER, 2));
        STEPS.put(RunGoals.BLAZE_ROD, new Step(NETHER, 2));
    }

    private RaceProgress() {}

    private static final class Step {
        final int milestone;
        final int points;

        Step(int milestone, int points) {
            this.milestone = milestone;
            this.points = points;
        }
    }

    public static final class Standing {
        final int milestone;
        final int points;
        final long reachedAtMs;

        Standing(int milestone, int points, long reachedAtMs) {
            this.milestone = milestone;
            this.points = points;
            this.reachedAtMs = reachedAtMs;
        }
    }

    public static Standing of(Map<String, Long> splits, Map<String, Long> goals) {
        Map<String, Long> reached = new HashMap<>(goals);
        reached.putAll(splits);

        int milestone = OVERWORLD;
        long reachedAt = 0;
        for (Map.Entry<String, Long> entry : reached.entrySet()) {
            Integer place = MILESTONES.get(entry.getKey());
            if (place != null && place > milestone) {
                milestone = place;
                reachedAt = entry.getValue();
            }
        }

        int points = 0;
        for (Map.Entry<String, Long> entry : reached.entrySet()) {
            Step step = STEPS.get(entry.getKey());
            if (step != null && step.milestone == milestone) {
                points += step.points;
                reachedAt = Math.max(reachedAt, entry.getValue());
            }
        }
        return new Standing(milestone, points, reachedAt);
    }

    public static int compare(Standing a, Standing b) {
        if (a.milestone != b.milestone) return Integer.compare(b.milestone, a.milestone);
        if (a.points != b.points) return Integer.compare(b.points, a.points);
        if (a.milestone == OVERWORLD && a.points == 0) return 0;
        return Long.compare(a.reachedAtMs, b.reachedAtMs);
    }
}
