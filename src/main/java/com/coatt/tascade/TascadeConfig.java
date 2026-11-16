package com.coatt.tascade;

import com.coatt.tascade.hud.RaceHudMode;
import com.coatt.tascade.network.TascadeClient;
import me.contaria.speedrunapi.config.SpeedrunConfigContainer;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.annotations.Config;
import net.minecraft.client.gui.screen.Screen;

import java.io.IOException;

public final class TascadeConfig implements SpeedrunConfig {
    private static TascadeConfig instance;
    private static final TascadeConfig DEFAULTS = new TascadeConfig(true);
    @Config.Ignored
    private SpeedrunConfigContainer<?> container;

    public boolean rankedBot = true;

    public boolean matchSounds = true;

    public boolean cheats = false;

    public boolean discordPresence = true;

    public RaceHudMode raceHud = RaceHudMode.ON;

    public boolean opponentChat = true;

    public boolean opponentAdvancements = true;

    public boolean hideRoomCode = false;

    public boolean rememberModifiers = false;

    @Config.Numbers.Whole.Bounds(min = 0, max = 10)
    public int pauseMenuCooldown = 1;

    @Config.Hide
    public String savedModifiers = "";

    @Config.Category("replays")
    public boolean replaySoloCompleted = true;

    @Config.Category("replays")
    public boolean replaySoloAbandoned = false;

    @Config.Category("replays")
    public boolean replaySoloSeedChanged = false;

    @Config.Category("replays")
    public boolean replayRankedWon = true;

    @Config.Category("replays")
    public boolean replayRankedLost = true;

    @Config.Category("replays")
    public boolean replayRankedDrawn = true;

    @Config.Category("replays")
    public boolean replayRankedForfeited = true;

    @Config.Category("replays")
    public boolean replayRankedSeedChanged = true;

    @Config.Category("replays")
    @Config.Numbers.Whole.Bounds(min = 1, max = 16)
    public int replaySoundMaxSpeed = 4;

    private TascadeConfig() {
        instance = this;
    }

    private TascadeConfig(boolean defaults) {
    }

    @Override
    public String modID() {
        return "tascade";
    }

    @Override
    public void finishInitialization(SpeedrunConfigContainer<?> container) {
        this.container = container;
    }

    @Override
    public void finishLoading() {
        // not remembered: don't later save this session's picks as if they were remembered
        if (!rememberModifiers) savedModifiers = "";
    }

    @Override
    public void finishSaving() {
        TascadeClient.getInstance().onSettingsChanged();
    }

    public static boolean loaded() {
        return instance != null;
    }

    public static Screen screen(Screen parent) {
        return instance.createConfigScreen(parent);
    }

    public static boolean rankedBot() {
        return instance == null || instance.rankedBot;
    }

    public static boolean matchSounds() {
        return instance == null || instance.matchSounds;
    }

    public static boolean cheats() {
        return instance != null && instance.cheats;
    }

    public static boolean discordPresence() {
        return instance == null || instance.discordPresence;
    }

    public static RaceHudMode raceHud() {
        return instance != null && instance.raceHud != null ? instance.raceHud : RaceHudMode.ON;
    }

    public static boolean opponentChat() {
        return instance == null || instance.opponentChat;
    }

    public static boolean opponentAdvancements() {
        return instance == null || instance.opponentAdvancements;
    }

    public static long pauseMenuCooldownMs() {
        return (instance != null ? instance.pauseMenuCooldown : DEFAULTS.pauseMenuCooldown) * 1000L;
    }

    public static boolean hideRoomCode() {
        return instance != null ? instance.hideRoomCode : DEFAULTS.hideRoomCode;
    }

    public static void setHideRoomCode(boolean hide) {
        if (instance == null) return;
        instance.hideRoomCode = hide;
        if (instance.container == null) return;
        try {
            instance.container.save();
        } catch (IOException e) {
            Main.LOGGER.warn("Couldn't save Hide Room Codes", e);
        }
    }

    public static String rememberedModifiers() {
        return instance != null && instance.rememberModifiers && instance.savedModifiers != null
                ? instance.savedModifiers : "";
    }

    public static void saveModifiers(String ids) {
        if (instance == null) return;
        instance.savedModifiers = ids;
        if (!instance.rememberModifiers || instance.container == null) return;
        try {
            instance.container.save();
        } catch (IOException e) {
            Main.LOGGER.warn("Couldn't save the picked run modifiers", e);
        }
    }

    public static TascadeConfig replays() {
        return instance != null ? instance : DEFAULTS;
    }
}
