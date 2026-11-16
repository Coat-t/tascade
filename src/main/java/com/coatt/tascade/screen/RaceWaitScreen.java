package com.coatt.tascade.screen;

import com.coatt.tascade.Main;
import com.coatt.tascade.chat.MatchChatDock;
import com.coatt.tascade.chat.MatchChatOverlay;
import com.coatt.tascade.hud.RunLabels;
import com.coatt.tascade.network.RoomState;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.network.TascadeClient;
import com.google.common.collect.ImmutableSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.Rect2i;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class RaceWaitScreen extends Screen implements MatchChatDock {
    private static final long FORFEIT_DELAY_MS = 15_000;
    private static final Set<String> SEED_PICTURES = ImmutableSet.of("buried_treasure", "desert_temple", "ruined_portal", "shipwreck", "village");
    private static final int SEED_PICTURE_WIDTH = 1600;
    private static final int SEED_PICTURE_HEIGHT = 900;

    private final long openedAt = System.currentTimeMillis();
    private ButtonWidget forfeitButton;

    public RaceWaitScreen() {
        super(new LiteralText("Race starting"));
    }

    // load-bearing: this keeps the integrated server paused while we wait
    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        if (RunState.isRoom()) {
            forfeitButton = this.addButton(new ButtonWidget(14, this.height - 20 - 10, 100, 20,
                    new LiteralText("Leave Race"), buttonWidget -> TascadeClient.getInstance().leaveRoomRace()));
            forfeitButton.active = forfeitAllowed();
            return;
        }
        if (!RunState.isRanked()) return;
        forfeitButton = this.addButton(new ButtonWidget(
                14,
                this.height - 20 - 10,
                80,
                20,
                new LiteralText("Forfeit"),
                buttonWidget -> TascadeClient.getInstance().forfeit()
        ));
        forfeitButton.active = forfeitAllowed();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        int seconds = RunState.raceCountdown;
        if (forfeitButton != null) forfeitButton.active = forfeitAllowed();

        int opponentProgress = RunState.opponentWorldProgress;
        String waiting = opponentProgress >= 0 && opponentProgress < 100
                ? "Waiting for opponent to load... " + opponentProgress + "%"
                : "Waiting for opponent to load...";
        if (RunState.isRoom()) waiting = roomWaiting();
        String heading = seconds < 0
                ? waiting
                : (seconds == 0 ? "GO!" : "Starting in " + seconds);
        renderHeld(this, matrices, seconds, heading);

        super.render(matrices, mouseX, mouseY, delta);
    }

    public static boolean worldGenerated() {
        if (!RunState.waitingForRaceStart) return false;
        IntegratedServer server = MinecraftClient.getInstance().getServer();
        // isLoading is true once the server has finished loading, despite its name
        return server != null && server.isLoading();
    }

    public static void renderHeld(Screen screen, MatrixStack matrices, int seconds, String heading) {
        Identifier picture = seedPicture();
        if (picture != null) {
            TascadeScreen.drawCover(matrices, picture, SEED_PICTURE_WIDTH, SEED_PICTURE_HEIGHT, screen.width, screen.height);
            fill(matrices, 0, 0, screen.width, screen.height, 0x60000000);
        } else if (seconds < 0) {
            // opaque while waiting so whoever loads first can't study the spawn
            screen.renderBackgroundTexture(0);
        } else {
            screen.renderBackground(matrices);
        }

        drawCentered(screen, matrices, heading, screen.height / 2 - 20, 16777215);

        if (RunState.seedType != null && !RunState.seedType.isEmpty()) {
            drawCentered(screen, matrices, "Seed: " + RunLabels.seedType(RunState.seedType), screen.height / 2, QueueStatus.COLOR_ACCENT);
        }

        // opponentName outlives a ranked match, so check the mode
        if (RunState.isRoom()) {
            RoomState.Room room = RoomState.room();
            int players = RoomState.racers().size();
            String line = (room != null ? room.settings.name : "Custom room") + " · " + players + (players == 1 ? " player" : " players");
            if (room != null) line += " · " + room.settings.goalLabel();
            drawCentered(screen, matrices, line, screen.height / 2 + 16, 10066329);
        }
        if (RunState.isRanked() && RunState.opponentName != null) {
            drawCentered(screen, matrices, "vs " + RunState.opponentName + " (" + RunState.opponentElo + " elo)",
                    screen.height / 2 + 16, 10066329);
        }
    }

    private static String roomWaiting() {
        String me = TascadeClient.getInstance().getPlayerUuid();
        int total = 0;
        int loaded = 0;
        for (RoomState.Racer racer : RoomState.racers()) {
            if (racer.out()) continue;
            total++;
            if (racer.uuid.equals(me) ? RunState.readySent : racer.worldProgress >= 100) loaded++;
        }
        return "Waiting for players to load... " + loaded + "/" + total;
    }

    @Override
    public Rect2i matchChatArea() {
        int top = this.height / 2 + 32;
        int bottom = this.height - 20 - 10 - 6;
        int width = Math.min(MatchChatOverlay.dockedWidth(this.client), this.width - 28);
        return new Rect2i(14, top, width, bottom - top);
    }

    private static Identifier seedPicture() {
        String type = RunState.seedType != null ? RunState.seedType.toLowerCase(Locale.ROOT) : "";
        return SEED_PICTURES.contains(type) ? Main.id("textures/gui/seed/" + type + ".png") : null;
    }

    private boolean forfeitAllowed() {
        return System.currentTimeMillis() - openedAt >= FORFEIT_DELAY_MS;
    }

    private static void drawCentered(Screen screen, MatrixStack matrices, String text, int y, int color) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int x = (screen.width - textRenderer.getWidth(text)) / 2;
        screen.drawTextWithShadow(matrices, textRenderer, new LiteralText(text), x, y, color);
    }
}
