package com.coatt.tascade.sound;

import com.coatt.tascade.TascadeConfig;
import com.coatt.tascade.finish.RunSummary;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

// MASTER category, so replays neither record nor silence them
public final class MatchSounds {
    private static volatile boolean seedChangePending = false;

    private MatchSounds() {}

    public static void matchFound() {
        play(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
    }

    public static void voteStarted() {
        play(SoundEvents.BLOCK_NOTE_BLOCK_HARP, 0.5F, 1.0F);
    }

    public static void voteAgreed() {
        play(SoundEvents.BLOCK_NOTE_BLOCK_HARP, 1.0F, 1.0F);
    }

    public static void voteExpired() {
        play(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.7F, 1.0F);
    }

    // leaving the old world stops every sound, so the cue waits for the loading screen
    public static void seedChanged() {
        seedChangePending = true;
    }

    public static void onLoadingScreen() {
        if (!seedChangePending) return;
        seedChangePending = false;
        play(SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0F, 0.6F);
    }

    public static void deadlineSet() {
        play(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 1.0F, 1.0F);
    }

    public static void disconnected() {
        play(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
    }

    public static void result(RunSummary.Outcome outcome) {
        switch (outcome) {
            case VICTORY:
                play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                break;
            case DEFEAT:
                play(SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.8F);
                break;
            default:
                play(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 1.0F, 0.8F);
                break;
        }
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        if (!TascadeConfig.matchSounds()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume)));
    }
}
