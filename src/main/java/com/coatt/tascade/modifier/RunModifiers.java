package com.coatt.tascade.modifier;

import com.coatt.tascade.TascadeConfig;
import com.coatt.tascade.modifier.RunModifier.Type;
import com.coatt.tascade.network.RoomState;
import net.minecraft.item.Items;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// mirrors the backend's src/modifiers.js, keep the two in step
public final class RunModifiers {
    private RunModifiers() {}

    // PP goes with time^-2 (backend pp.js), so pace is multiplier^(1/2)
    public static final double CURVE_EXPONENT = 2;
    public static final int MAX_PER_RUN = 16;

    private static final List<RunModifier> ALL = Collections.unmodifiableList(Arrays.asList(
            // difficulty Increase
            new RunModifier("flammable", "Flammable", "Fire Resistance potions' duration is quartered.",
                    Type.INCREASE, 1.11, Items.MAGMA_CREAM, true),
            new RunModifier("no_f3", "NoF3", "Debug features are disabled.",
                    Type.INCREASE, 1.19, Items.COMPASS, true),
            new RunModifier("no_jump", "No Jump", "The jump key does nothing.",
                    Type.INCREASE, 1.30, Items.RABBIT_FOOT, true),
            new RunModifier("one_legged", "One Legged", "Walking is disabled; move by jumping in a direction.",
                    Type.INCREASE, 1.22, Items.SLIME_BALL, true, "no_jump"),
            new RunModifier("fragile", "Fragile", "Max health is limited to 10.",
                    Type.INCREASE, 1.12, Items.GLASS, true),
            new RunModifier("small_pockets", "Small Pockets", "Inventory space is limited to hotbar.",
                    Type.INCREASE, 1.27, Items.LEATHER, true),
            new RunModifier("delusional", "Delusional", "Health and hunger bars are hidden.",
                    Type.INCREASE, 1.08, Items.SUSPICIOUS_STEW, true),

            // difficulty Decrease
            new RunModifier("easy_mode", "Easy Mode", "Respawn at the start position of the latest split once.",
                    Type.DECREASE, 0.82, Items.RESPAWN_ANCHOR, true),
            new RunModifier("keep_inventory", "Keep Inventory", "Items are preserved after death once.",
                    Type.DECREASE, 0.68, Items.CHEST, true),

            // alternative
            new RunModifier("cursed", "Cursed", "Get a random status effect every 15s for 10s.",
                    Type.ALTERNATIVE, 0, Items.FERMENTED_SPIDER_EYE, true),
            new RunModifier("bubble", "Bubble", "Get Levitation I for 8s every 16s.",
                    Type.ALTERNATIVE, 0, Items.SHULKER_SHELL, true)
    ));

    private static final Set<String> selected = new LinkedHashSet<>();
    private static boolean restored = false;

    // lazy: settings load before this class (with its items) can be loaded
    private static Set<String> selected() {
        if (!restored) {
            restored = true;
            for (String id : TascadeConfig.rememberedModifiers().split(",")) {
                RunModifier modifier = byId(id.trim());
                if (modifier != null && modifier.enabled && blocker(modifier) == null
                        && selected.size() < MAX_PER_RUN) selected.add(modifier.id);
            }
        }
        return selected;
    }

    public static List<RunModifier> ofType(Type type) {
        List<RunModifier> list = new ArrayList<>();
        for (RunModifier modifier : ALL) {
            if (modifier.type == type && modifier.enabled) list.add(modifier);
        }
        for (RunModifier modifier : ALL) {
            if (modifier.type == type && !modifier.enabled) list.add(modifier);
        }
        return list;
    }

    public static RunModifier byId(String id) {
        for (RunModifier modifier : ALL) {
            if (modifier.id.equals(id)) return modifier;
        }
        return null;
    }

    public static boolean isSelected(RunModifier modifier) {
        return selected().contains(modifier.id);
    }

    public static List<RunModifier> fromJson(JSONArray modifiers) {
        List<RunModifier> list = new ArrayList<>();
        for (int i = 0; modifiers != null && i < modifiers.length(); i++) {
            Object entry = modifiers.opt(i);
            String id = entry instanceof JSONObject ? ((JSONObject) entry).optString("id", null)
                    : entry instanceof String ? (String) entry : null;
            RunModifier modifier = id != null ? byId(id) : null;
            if (modifier != null && !list.contains(modifier)) list.add(modifier);
        }
        return list;
    }

    public static boolean incompatible(RunModifier a, RunModifier b) {
        return a.incompatible.contains(b.id) || b.incompatible.contains(a.id);
    }

    public static RunModifier blocker(RunModifier modifier) {
        for (String id : selected) {
            RunModifier picked = byId(id);
            if (picked != null && picked != modifier && incompatible(picked, modifier)) return picked;
        }
        return null;
    }

    public static void toggle(RunModifier modifier) {
        Set<String> picks = selected();
        if (!picks.remove(modifier.id)) {
            if (!modifier.enabled || blocker(modifier) != null || picks.size() >= MAX_PER_RUN) return;
            picks.add(modifier.id);
        }
        TascadeConfig.saveModifiers(String.join(",", picks));
    }

    public static void clear() {
        selected().clear();
        TascadeConfig.saveModifiers("");
    }

    public static List<String> selectedIds() {
        List<String> ids = new ArrayList<>();
        for (RunModifier modifier : picked()) ids.add(modifier.id);
        return ids;
    }

    public static double multiplier() {
        double product = 1;
        for (RunModifier modifier : picked()) product *= modifier.multiplier;
        return round4(product);
    }

    public static double pace() {
        double product = 1;
        for (RunModifier modifier : picked()) {
            double pace = modifier.pace();
            if (Double.isNaN(pace)) return Double.NaN;
            product *= pace;
        }
        return round4(product);
    }

    // same rules as the backend's rankedRefusal
    public static String rankedRefusal() {
        for (RunModifier modifier : picked()) {
            if (modifier.type == Type.ALTERNATIVE) return modifier.name + " can't be played in ranked.";
        }
        return null;
    }

    public static List<RunModifier> picked() {
        List<RunModifier> list = new ArrayList<>();
        for (String id : selected()) {
            RunModifier modifier = byId(id);
            if (modifier != null && modifier.enabled) list.add(modifier);
        }
        return list;
    }

    // mirrors the backend's rooms.js effectiveModifiers
    public static List<String> inRoom(List<String> picks, Map<String, String> rules) {
        List<RunModifier> forced = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (Map.Entry<String, String> rule : rules.entrySet()) {
            if (RoomState.MOD_FORCE.equals(rule.getValue()) && byId(rule.getKey()) == null) unknown.add(rule.getKey());
        }
        for (RunModifier modifier : ALL) {
            if (modifier.enabled && RoomState.MOD_FORCE.equals(rules.get(modifier.id))) forced.add(modifier);
        }
        List<RunModifier> kept = new ArrayList<>();
        for (String id : picks) {
            if (RoomState.MOD_DISALLOW.equals(rules.get(id))) continue;
            RunModifier modifier = byId(id);
            if (modifier == null) {
                if (!unknown.contains(id)) unknown.add(id);
                continue;
            }
            if (!modifier.enabled || forced.contains(modifier) || kept.contains(modifier)) continue;
            if (clashes(modifier, forced) || clashes(modifier, kept)) continue;
            kept.add(modifier);
        }
        List<RunModifier> chosen = new ArrayList<>(forced);
        chosen.addAll(kept);
        if (chosen.size() > MAX_PER_RUN) chosen = chosen.subList(0, MAX_PER_RUN);
        List<String> ids = new ArrayList<>();
        for (RunModifier modifier : ALL) {
            if (chosen.contains(modifier)) ids.add(modifier.id);
        }
        for (String id : unknown) {
            if (ids.size() < MAX_PER_RUN) ids.add(id);
        }
        return ids;
    }

    private static boolean clashes(RunModifier modifier, List<RunModifier> others) {
        for (RunModifier other : others) {
            if (incompatible(modifier, other)) return true;
        }
        return false;
    }

    static double round4(double x) {
        return Math.round(x * 10_000) / 10_000.0;
    }
}
