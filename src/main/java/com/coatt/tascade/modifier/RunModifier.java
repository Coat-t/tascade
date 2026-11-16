package com.coatt.tascade.modifier;

import com.coatt.tascade.screen.QueueStatus;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

// the multiplier here is display-only, the server decides the real one
public final class RunModifier {
    public enum Type {
        INCREASE("Difficulty Increase", 0xE05A5A),
        DECREASE("Difficulty Decrease", 0x5AC468),
        ALTERNATIVE("Alternative", QueueStatus.COLOR_ACCENT);

        public final String heading;
        public final int color;

        Type(String heading, int color) {
            this.heading = heading;
            this.color = color;
        }
    }

    public final String id;
    public final String name;
    public final String description;
    public final Type type;
    public final double multiplier;
    public final boolean enabled;
    public final List<String> incompatible;
    private final Item icon;

    RunModifier(String id, String name, String description, Type type, double multiplier, Item icon,
                boolean enabled, String... incompatible) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.multiplier = multiplier;
        this.icon = icon;
        this.enabled = enabled;
        this.incompatible = Collections.unmodifiableList(Arrays.asList(incompatible));
    }

    public double pace() {
        if (type == Type.ALTERNATIVE) return Double.NaN;
        return RunModifiers.round4(Math.pow(multiplier, 1 / RunModifiers.CURVE_EXPONENT));
    }

    public ItemStack icon() {
        return new ItemStack(icon);
    }

    public static String format(double multiplier) {
        return String.format(Locale.ROOT, "x%.2f", multiplier);
    }
}
