package com.coatt.tascade.integration;

import com.coatt.tascade.Main;
import com.coatt.tascade.modifier.ModifierEffects;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.network.TascadeClient;
import com.coatt.tascade.replay.record.ReplayRecorder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// reach() is called from both the client and server threads
public final class RunGoals {
    public static final String IRON = "iron";
    public static final String LAVA_BUCKET = "lava_bucket";
    public static final String GOLD_BLOCK = "gold_block";
    public static final String BARTER = "barter";
    public static final String OBSIDIAN = "obsidian";
    public static final String BLAZE_ROD = "blaze_rod";
    public static final String DRAGON_DYING = "dragon_dying";

    public static final String FINDING_STRONGHOLD = "finding_stronghold";

    private static final Set<String> GOALS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            IRON, LAVA_BUCKET, GOLD_BLOCK, BARTER, OBSIDIAN, BLAZE_ROD, DRAGON_DYING)));
    private static final int OBSIDIAN_NEEDED = 20;

    private static final Map<String, Long> reached = new HashMap<>();
    private static boolean enteredNether = false;

    private RunGoals() {}

    public static synchronized void reset() {
        reached.clear();
        enteredNether = false;
    }

    public static boolean isGoal(String name) {
        return GOALS.contains(name);
    }

    public static synchronized Map<String, Long> times() {
        return new HashMap<>(reached);
    }

    public static synchronized void restore(String goal, long rta) {
        reached.putIfAbsent(goal, rta);
    }

    public static void reach(String goal) {
        if (!racing()) return;
        long rta = Main.runRta();
        if (rta < 0) return;
        synchronized (RunGoals.class) {
            if (reached.putIfAbsent(goal, rta) != null) return;
        }
        ReplayRecorder.goal(goal, rta);
        TascadeClient.getInstance().sendGoal(goal, rta);
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null || !racing()) return;
        if (client.world.getRegistryKey() == World.NETHER) enteredNether = true;

        Map<Item, Integer> held = held(client.player.inventory);
        if (held.containsKey(Items.IRON_INGOT) || held.containsKey(Items.IRON_PICKAXE)) reach(IRON);
        if (held.containsKey(Items.LAVA_BUCKET)) reach(LAVA_BUCKET);
        if (held.getOrDefault(Items.OBSIDIAN, 0) >= OBSIDIAN_NEEDED) reach(OBSIDIAN);
        if (held.containsKey(Items.BLAZE_ROD)) reach(BLAZE_ROD);

        boolean canMakeEye = held.containsKey(Items.ENDER_PEARL)
                && (held.containsKey(Items.BLAZE_POWDER) || held.containsKey(Items.BLAZE_ROD));
        boolean inOverworld = client.world.getRegistryKey() == World.OVERWORLD;
        boolean beenInNether = enteredNether || RunRecorder.hasSplit("enter_nether");
        if (beenInNether && inOverworld && (held.containsKey(Items.ENDER_EYE) || canMakeEye)) {
            findingStronghold();
        }
    }

    private static void findingStronghold() {
        if (RunRecorder.hasSplit(FINDING_STRONGHOLD)) return;
        long rta = Main.runRta();
        if (rta < 0) return;
        long igt = Main.runIgt();
        if (RunRecorder.recordSplit(FINDING_STRONGHOLD, rta, igt)) {
            ModifierEffects.splitReached(FINDING_STRONGHOLD, rta);
            TascadeClient.getInstance().sendSplit(FINDING_STRONGHOLD, rta, igt);
        }
    }

    private static boolean racing() {
        return RunState.isRunning() && !RunState.waitingForRaceStart;
    }

    private static Map<Item, Integer> held(PlayerInventory inventory) {
        Map<Item, Integer> counts = new HashMap<>();
        for (ItemStack stack : inventory.main) count(counts, stack);
        for (ItemStack stack : inventory.offHand) count(counts, stack);
        count(counts, inventory.getCursorStack());
        return counts;
    }

    private static void count(Map<Item, Integer> counts, ItemStack stack) {
        if (!stack.isEmpty()) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
    }
}
