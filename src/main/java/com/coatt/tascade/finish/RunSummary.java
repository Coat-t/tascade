package com.coatt.tascade.finish;

import com.coatt.tascade.hud.RunLabels;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;

import java.util.Collections;
import java.util.Map;

public final class RunSummary {
    public enum Outcome { COMPLETED, VICTORY, DEFEAT, DRAW, ENDED }

    public final Outcome outcome;
    public final boolean ranked;
    public final boolean forfeit;
    public volatile long timeMs;
    public final String timeHolder;
    public final String playerName;
    public final String opponentName;
    public final String seedType;
    public final String bastionType;
    public final Map<String, Long> playerSplits;
    public final Map<String, Long> opponentSplits;
    public final boolean unrated;

    public volatile boolean provisional;

    public volatile boolean room;
    public volatile int place;

    public volatile boolean ratingKnown;
    public volatile int eloDelta;
    public volatile Integer newElo;
    public volatile double ppAwarded = Double.NaN;
    public volatile double newTotalPP = Double.NaN;

    RunSummary(Outcome outcome, boolean ranked, boolean forfeit, long timeMs, String timeHolder,
               String playerName, String opponentName, String seedType, String bastionType,
               Map<String, Long> playerSplits, Map<String, Long> opponentSplits, boolean unrated) {
        this.outcome = outcome;
        this.ranked = ranked;
        this.forfeit = forfeit;
        this.timeMs = timeMs;
        this.timeHolder = timeHolder;
        this.playerName = playerName;
        this.opponentName = opponentName;
        this.seedType = seedType;
        this.bastionType = bastionType;
        this.playerSplits = Collections.unmodifiableMap(playerSplits);
        this.opponentSplits = Collections.unmodifiableMap(opponentSplits);
        this.unrated = unrated;
    }

    public MutableText title() {
        if (room) {
            switch (outcome) {
                case COMPLETED:
                    if (place <= 0) return new LiteralText("Finished!").formatted(Formatting.GREEN);
                    return new LiteralText(ordinal(place) + " place").formatted(place == 1 ? Formatting.GOLD : Formatting.GREEN);
                case DEFEAT: return new LiteralText("Gave Up").formatted(Formatting.RED);
                default: return new LiteralText("Race Over").formatted(Formatting.YELLOW);
            }
        }
        switch (outcome) {
            case VICTORY: return new LiteralText("Victory").formatted(Formatting.GREEN);
            case DEFEAT: return new LiteralText("Defeat").formatted(Formatting.RED);
            case DRAW: return new LiteralText("Match Drawn").formatted(Formatting.YELLOW);
            case COMPLETED: return new LiteralText("Completed").formatted(Formatting.GREEN);
            default: return new LiteralText("Match Ended").formatted(Formatting.YELLOW);
        }
    }

    public MutableText subtitle() {
        String time = RunLabels.time(timeMs);
        return new LiteralText(timeHolder != null ? time + " · " + timeHolder : time);
    }

    public static String ordinal(int n) {
        int lastTwo = n % 100;
        if (lastTwo >= 11 && lastTwo <= 13) return n + "th";
        switch (n % 10) {
            case 1: return n + "st";
            case 2: return n + "nd";
            case 3: return n + "rd";
            default: return n + "th";
        }
    }

    public String reason() {
        if (room) {
            switch (outcome) {
                case COMPLETED: return "You finished the run.";
                case DEFEAT: return "You gave up the race.";
                default: return "The race ended before you finished.";
            }
        }
        String opponent = opponentName != null ? opponentName : "Your opponent";
        switch (outcome) {
            case VICTORY: return forfeit ? opponent + " forfeited." : "You finished first.";
            case DEFEAT: return forfeit ? "You forfeited." : opponent + " finished first.";
            case DRAW: return "Both players agreed to a draw.";
            case COMPLETED: return "Run complete.";
            default: return "The match ended without a result.";
        }
    }
}
