package com.coatt.tascade.hud;

import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Language;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class RunLabels {
    private RunLabels() {}

    public static String seedType(String seedType) {
        return titleCase(seedType);
    }

    public static String bastion(String bastionType) {
        String type = bastionType.toLowerCase(Locale.ROOT);
        switch (type) {
            case "units":
            case "housing":
                return "Housing Bastion";
            case "hoglin_stable":
            case "hoglin_stables":
            case "stable":
            case "stables":
                return "Stables Bastion";
            default:
                return titleCase(type) + " Bastion";
        }
    }

    public static MutableText split(String name) {
        String key = "tascade.split." + name;
        return Language.getInstance().hasTranslation(key)
                ? new TranslatableText(key)
                : new LiteralText(titleCase(name));
    }

    public static String pp(double pp) {
        return Math.round(pp) + "pp";
    }

    public static String time(long ms) {
        long total = Math.max(0, ms);
        long hours = total / 3_600_000;
        long minutes = total / 60_000 % 60;
        long seconds = total / 1000 % 60;
        long millis = total % 1000;
        return hours > 0
                ? String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, millis)
                : String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }

    public static String ago(String timestamp) {
        ZonedDateTime then;
        try {
            then = OffsetDateTime.parse(timestamp).atZoneSameInstant(ZoneId.systemDefault());
        } catch (DateTimeParseException | NullPointerException e) {
            return "";
        }
        ZonedDateTime now = ZonedDateTime.now();
        long seconds = Math.max(0, now.toEpochSecond() - then.toEpochSecond());
        if (seconds < 60) return "just now";
        if (seconds < 3600) return seconds / 60 + "m ago";
        if (seconds < 86_400) return seconds / 3600 + "h ago";
        if (seconds < 7 * 86_400) return seconds / 86_400 + "d ago";
        String pattern = then.getYear() == now.getYear() ? "MMM d" : "MMM d, yyyy";
        return then.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
    }

    private static String titleCase(String snakeCase) {
        StringBuilder out = new StringBuilder();
        for (String word : snakeCase.split("_")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
