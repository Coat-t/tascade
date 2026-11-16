package com.coatt.tascade.replay;

import com.coatt.tascade.screen.RaceWaitScreen;
import com.coatt.tascade.screen.RunResultScreen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.CreditsScreen;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import net.minecraft.client.gui.screen.StatsScreen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HorseScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.gui.screen.options.GameOptionsScreen;
import net.minecraft.client.gui.screen.options.OptionsScreen;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

// class names are obfuscated outside dev, so screens get stable names
public final class ReplayScreens {
    public static final String INVENTORY = "inventory";
    public static final String CREATIVE = "creative";
    public static final String HORSE = "horse";
    public static final String UNKNOWN = "unknown";

    private ReplayScreens() {}

    public static String typeOf(HandledScreen<?> screen) {
        if (screen instanceof CreativeInventoryScreen) return CREATIVE;
        if (screen instanceof InventoryScreen) return INVENTORY;
        if (screen instanceof HorseScreen) return HORSE;
        try {
            Identifier id = Registry.SCREEN_HANDLER.getId(screen.getScreenHandler().getType());
            return id != null ? id.toString() : UNKNOWN;
        } catch (UnsupportedOperationException e) {
            return UNKNOWN;
        }
    }

    public static String nameOf(Screen screen) {
        if (screen instanceof DeathScreen) return "death";
        if (screen instanceof GameMenuScreen) return "pause";
        if (screen instanceof SleepingChatScreen) return "sleeping";
        if (screen instanceof ChatScreen) return "chat";
        if (screen instanceof AdvancementsScreen) return "advancements";
        if (screen instanceof StatsScreen) return "statistics";
        if (screen instanceof DownloadingTerrainScreen) return "loading";
        if (screen instanceof CreditsScreen) return "credits";
        if (screen instanceof BookScreen) return "book";
        if (screen instanceof SignEditScreen) return "sign";
        if (screen instanceof OptionsScreen || screen instanceof GameOptionsScreen) return "options";
        if (screen instanceof RaceWaitScreen) return "race_wait";
        if (screen instanceof RunResultScreen) return "results";
        return "other";
    }

    public static String label(String name) {
        switch (name) {
            case "death": return "Died";
            case "pause": return "Paused";
            case "sleeping": return "In bed";
            case "chat": return "Using chat";
            case "advancements": return "Advancements";
            case "statistics": return "Statistics";
            case "loading": return "Loading terrain";
            case "credits": return "Credits";
            case "book": return "Reading a book";
            case "sign": return "Editing a sign";
            case "options": return "Options";
            case "race_wait": return "Race countdown";
            case "results": return "Results";
            default: return "Menu open";
        }
    }
}
