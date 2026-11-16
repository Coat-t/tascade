package com.coatt.tascade.screen;

import com.coatt.tascade.hud.HudScale;
import com.coatt.tascade.hud.RunLabels;
import com.coatt.tascade.network.RoomState;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.network.TascadeClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import org.json.JSONArray;
import org.lwjgl.glfw.GLFW;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class PlayScreen extends TascadeScreen {
    private static final int LEFT = 14;
    private static final int TOP_BAR_BOTTOM = TOP_BAR_HEIGHT + 8;
    private static final int MAX_PANEL_WIDTH = 170;
    private static final int PANEL_HEIGHT = 120;
    private static final int PANEL_GAP = 12;
    private static final int PANELS = 3;
    private static final int PANEL_PADDING = 10;
    private static final int PANEL_BACKGROUND = 0x90000000;
    private static final int CONTENT_TOP = 32;

    private static final int RECENT_MATCHES = 5;
    private static final int RESULT_SIZE = 14;
    private static final int RESULT_GAP = 3;
    private static final int COLOR_WIN = 0xFF3C9A3C;
    private static final int COLOR_LOSS = 0xFFB33A3A;
    private static final int COLOR_DRAW = 0xFF6E6E6E;

    // delay so a misclick can still be cancelled
    private static final long SOLO_COUNTDOWN_MS = 3_000;
    private static final long SOLO_START_TIMEOUT_MS = 15_000;
    private static final long QUEUE_POLL_MS = 5_000;
    private static final long QUEUE_POLL_TIMEOUT_MS = 15_000;

    private final Screen parent;
    private final BackendReply profile = new BackendReply();
    private final BackendReply history = new BackendReply();
    private int queueSize = -1;
    private long queuePolledAtMs = 0;
    private boolean queuePollPending = false;

    private ButtonWidget soloButton;
    private long soloCountdownStartMs = -1;
    private long soloStartedAtMs = -1;
    private int buttonY;
    private ButtonWidget queueButton;
    private ButtonWidget createRoomButton;
    private ButtonWidget joinRoomButton;
    private ButtonWidget openRoomButton;
    private boolean roomRequested = false;
    private long roomRequestedAtMs = 0;
    private int panelTop;
    private int panelWidth;
    private int soloLeft;
    private int rankedLeft;
    private int roomLeft;

    public PlayScreen(Screen parent) {
        super(new LiteralText("Play"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(MAX_PANEL_WIDTH, (this.width - 2 * LEFT - (PANELS - 1) * PANEL_GAP) / PANELS);
        soloLeft = (this.width - PANELS * panelWidth - (PANELS - 1) * PANEL_GAP) / 2;
        rankedLeft = soloLeft + panelWidth + PANEL_GAP;
        roomLeft = rankedLeft + panelWidth + PANEL_GAP;
        int bottom = this.height - 20 - 10 - 6 - RunModifiersScreen.pickedStripHeight();
        panelTop = TOP_BAR_BOTTOM + Math.max(0, (bottom - TOP_BAR_BOTTOM - PANEL_HEIGHT) / 2);
        buttonY = panelTop + PANEL_HEIGHT - PANEL_PADDING - 20;
        int buttonWidth = panelWidth - 2 * PANEL_PADDING;

        this.addButton(new ButtonWidget(LEFT, this.height - 20 - 10, 80, 20, new LiteralText("Back"),
                buttonWidget -> onClose()));
        this.addButton(RunModifiersScreen.bottomCenterButton(this.width, this.height, "Run Modifiers",
                buttonWidget -> this.client.openScreen(new RunModifiersScreen(this))));
        soloButton = this.addButton(new ButtonWidget(soloLeft + PANEL_PADDING, buttonY, buttonWidth, 20,
                new LiteralText("New run"), buttonWidget -> soloCountdownStartMs =
                        soloCountdownStartMs < 0 ? System.currentTimeMillis() : -1,
                this::runTooltip));
        queueButton = this.addButton(new ButtonWidget(rankedLeft + PANEL_PADDING, buttonY, buttonWidth, 20,
                new LiteralText(queueButtonLabel()), buttonWidget -> {
                    TascadeClient.getInstance().joinQueue();
                    this.client.openScreen(new RankedQueueScreen(this));
                }, this::runTooltip));
        createRoomButton = this.addButton(new ButtonWidget(roomLeft + PANEL_PADDING, buttonY - 24, buttonWidth, 20,
                new LiteralText("Create room"), buttonWidget -> {
                    roomRequested = true;
                    roomRequestedAtMs = System.currentTimeMillis();
                    TascadeClient.getInstance().createRoom();
                }, this::offlineTooltip));
        joinRoomButton = this.addButton(new ButtonWidget(roomLeft + PANEL_PADDING, buttonY, buttonWidth, 20,
                new LiteralText(this.textRenderer.getWidth("Join with code") + 8 <= buttonWidth ? "Join with code" : "Join"),
                buttonWidget -> this.client.openScreen(new JoinRoomScreen(this)),
                this::offlineTooltip));
        openRoomButton = this.addButton(new ButtonWidget(roomLeft + PANEL_PADDING, buttonY, buttonWidth, 20,
                new LiteralText("Open room"), buttonWidget -> this.client.openScreen(new RoomLobbyScreen(this))));
        updateButtons();

        // init runs again on resize, only the first one fetches
        if (!profile.requested()) {
            TascadeClient tascade = TascadeClient.getInstance();
            tascade.requestProfile(null, 1, profile.request());
            // a few extra, in case some of the latest were cancelled
            tascade.requestHistory(null, "ranked", 1, RECENT_MATCHES * 2, history.request());
            pollQueue();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (RoomState.inRoom() && roomRequested) {
            roomRequested = false;
            this.client.openScreen(new RoomLobbyScreen(this));
            return;
        }
        if (!RoomState.inRoom() && RoomState.removedReason != null) {
            this.client.openScreen(RoomLobbyScreen.removedNotice(this));
            return;
        }
        if (roomRequested && !TascadeClient.roomRequestPending()) roomRequested = false;
        updateButtons();
        if (soloCountdownStartMs >= 0 && System.currentTimeMillis() - soloCountdownStartMs >= SOLO_COUNTDOWN_MS) {
            soloCountdownStartMs = -1;
            soloStartedAtMs = System.currentTimeMillis();
            TascadeClient.getInstance().startSoloRun();
            updateButtons();
            return;
        }
        long sincePoll = System.currentTimeMillis() - queuePolledAtMs;
        if (sincePoll >= QUEUE_POLL_MS && (!queuePollPending || sincePoll >= QUEUE_POLL_TIMEOUT_MS)) pollQueue();
    }

    private void pollQueue() {
        queuePolledAtMs = System.currentTimeMillis();
        queuePollPending = true;
        TascadeClient.getInstance().requestQueueInfo(reply -> {
            queuePollPending = false;
            if (!reply.has("error")) queueSize = reply.optInt("size", 0);
        });
    }

    private void updateButtons() {
        boolean online = TascadeClient.getInstance().isOnline();
        boolean inRoom = RoomState.inRoom();
        if (soloStartedAtMs >= 0 && (RunState.lastError != null
                || System.currentTimeMillis() - soloStartedAtMs >= SOLO_START_TIMEOUT_MS)) {
            soloStartedAtMs = -1;
        }
        boolean starting = soloStartedAtMs >= 0;
        soloButton.active = online && !inRoom && !starting;
        if (!soloButton.active) soloCountdownStartMs = -1;
        soloButton.setMessage(new LiteralText(starting ? "Starting..."
                : soloCountdownStartMs >= 0 ? "Cancel" : "New run"));
        queueButton.active = (online && !inRoom) || RunState.isQueued();
        queueButton.setMessage(new LiteralText(queueButtonLabel()));
        createRoomButton.visible = !inRoom;
        joinRoomButton.visible = !inRoom;
        openRoomButton.visible = inRoom;
        boolean pending = TascadeClient.roomRequestPending();
        createRoomButton.active = online && !pending;
        createRoomButton.setMessage(new LiteralText(pending && roomRequested ? "Creating..." : "Create room"));
        joinRoomButton.active = online && !pending;
    }

    private void runTooltip(ButtonWidget button, MatrixStack matrices, int mouseX, int mouseY) {
        if (button.active) {
            if (button == soloButton && RunState.isQueued() && soloCountdownStartMs < 0) {
                this.renderTooltip(matrices, this.textRenderer.wrapLines(new LiteralText(
                        "You stay in the ranked queue. When a match starts, this run is abandoned."), 200),
                        mouseX, mouseY);
            }
            return;
        }
        if (button == soloButton && soloStartedAtMs >= 0) return;
        if (RoomState.inRoom()) {
            this.renderTooltip(matrices, new LiteralText("Leave your room first."), mouseX, mouseY);
            return;
        }
        offlineTooltip(button, matrices, mouseX, mouseY);
    }

    private void offlineTooltip(ButtonWidget button, MatrixStack matrices, int mouseX, int mouseY) {
        if (button.active) return;
        this.renderTooltip(matrices, new LiteralText(RunState.connected ? "Signing in to Tascade..." : "Reconnecting to Tascade..."),
                mouseX, mouseY);
    }

    private static String queueButtonLabel() {
        return RunState.isQueued() ? "View queue" : "Start queuing";
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && soloCountdownStartMs >= 0) {
            soloCountdownStartMs = -1;
            updateButtons();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        soloCountdownStartMs = -1;
        super.removed();
    }

    @Override
    public void onClose() {
        if (this.client != null) {
            this.client.openScreen(this.parent);
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        renderTopBar(matrices);
        renderSoloPanel(matrices);
        renderRankedPanel(matrices);
        renderRoomPanel(matrices);
        List<StringRenderable> pickedTooltip = RunModifiersScreen.renderPickedStrip(matrices, this.client, this.width,
                this.width / 2, this.height - 20 - 10, mouseX, mouseY);
        super.render(matrices, mouseX, mouseY, delta);
        if (pickedTooltip != null) this.renderTooltip(matrices, pickedTooltip, mouseX, mouseY);
    }

    private void renderSoloPanel(MatrixStack matrices) {
        int center = panel(matrices, soloLeft, "Solo mode");
        int y = panelTop + CONTENT_TOP;
        drawSmallCentered(matrices, "Personal Best", center, y, QueueStatus.COLOR_MUTED);
        y += 12;

        JSONObject data = profile.data();
        JSONObject stats = data != null ? data.optJSONObject("stats") : null;
        if (stats != null && !stats.isNull("bestRtaMs")) {
            drawCentered(matrices, RunLabels.time(stats.optLong("bestRtaMs")), center, y, QueueStatus.COLOR_TEXT);
        } else if (data == null && profile.failure() == null) {
            drawCentered(matrices, "Loading...", center, y, QueueStatus.COLOR_MUTED);
        } else {
            drawCentered(matrices, "-", center, y, QueueStatus.COLOR_MUTED);
        }
        if (soloCountdownStartMs >= 0) {
            long left = SOLO_COUNTDOWN_MS - (System.currentTimeMillis() - soloCountdownStartMs);
            long seconds = Math.max(1, (left + 999) / 1000);
            drawCentered(matrices, "Starting in " + seconds + "...", center, buttonY - 14, QueueStatus.COLOR_TEXT);
        }
    }

    private void renderRankedPanel(MatrixStack matrices) {
        int center = panel(matrices, rankedLeft, "Ranked mode");
        int y = panelTop + CONTENT_TOP;
        String queue = queueSize < 0 ? "..." : queueSize + (queueSize == 1 ? " player" : " players") + " in queue";
        drawCentered(matrices, queue, center, y, QueueStatus.COLOR_TEXT);
        y += 18;
        drawSmallCentered(matrices, "Match history", center, y, QueueStatus.COLOR_MUTED);
        y += 11;

        JSONObject data = history.data();
        if (data == null) {
            String note = history.failure() == null ? "Loading..." : "-";
            drawCentered(matrices, note, center, y + 3, QueueStatus.COLOR_MUTED);
            return;
        }
        List<JSONObject> recent = recentResults(data.optJSONArray("entries"));
        if (recent.isEmpty()) {
            drawCentered(matrices, "No matches yet", center, y + 3, QueueStatus.COLOR_MUTED);
            return;
        }
        int rowWidth = recent.size() * RESULT_SIZE + (recent.size() - 1) * RESULT_GAP;
        int x = center - rowWidth / 2;
        for (JSONObject match : recent) {
            drawResult(matrices, match, x, y);
            x += RESULT_SIZE + RESULT_GAP;
        }
    }

    private void renderRoomPanel(MatrixStack matrices) {
        int center = panel(matrices, roomLeft, "Custom room");
        int y = panelTop + CONTENT_TOP;
        RoomState.Room room = RoomState.room();
        if (room != null) {
            drawCentered(matrices, trimTo(room.settings.name, panelWidth - 2 * PANEL_PADDING), center, y, QueueStatus.COLOR_TEXT);
            y += 12;
            int players = room.members.size();
            drawSmallCentered(matrices, RoomState.shownCode(room.code) + " · " + players + (players == 1 ? " player" : " players"),
                    center, y, QueueStatus.COLOR_MUTED);
            return;
        }
        String error = RoomState.lastError;
        if (error != null && roomRequestedAtMs > 0 && RoomState.lastErrorAtMs >= roomRequestedAtMs) {
            for (StringRenderable line : this.textRenderer.wrapLines(new LiteralText(error), panelWidth - 2 * PANEL_PADDING)) {
                if (y > panelTop + PANEL_HEIGHT - PANEL_PADDING - 44 - 9) break;
                this.textRenderer.drawWithShadow(matrices, line, center - this.textRenderer.getWidth(line) / 2f, y,
                        QueueStatus.COLOR_ERROR);
                y += 10;
            }
            return;
        }
        drawSmallCentered(matrices, "Private races", center, y, QueueStatus.COLOR_MUTED);
        drawSmallCentered(matrices, "with friends", center, y + 10, QueueStatus.COLOR_MUTED);
    }

    private static List<JSONObject> recentResults(JSONArray entries) {
        List<JSONObject> recent = new ArrayList<>();
        if (entries == null) return recent;
        for (int i = 0; i < entries.length() && recent.size() < RECENT_MATCHES; i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry != null && !"CANCELED".equals(entry.optString("result"))) recent.add(entry);
        }
        return recent;
    }

    private void drawResult(MatrixStack matrices, JSONObject match, int x, int y) {
        String letter;
        int color;
        if ("DRAW".equals(match.optString("result"))) {
            letter = "D";
            color = COLOR_DRAW;
        } else if (match.optBoolean("isWinner")) {
            letter = "W";
            color = COLOR_WIN;
        } else {
            letter = "L";
            color = COLOR_LOSS;
        }
        fill(matrices, x, y, x + RESULT_SIZE, y + RESULT_SIZE, color);
        int letterX = x + (RESULT_SIZE - this.textRenderer.getWidth(letter) + 1) / 2;
        this.textRenderer.drawWithShadow(matrices, letter, letterX, y + 3, QueueStatus.COLOR_TEXT);
    }

    private int panel(MatrixStack matrices, int left, String title) {
        fill(matrices, left, panelTop, left + panelWidth, panelTop + PANEL_HEIGHT, PANEL_BACKGROUND);
        int center = left + panelWidth / 2;
        drawCentered(matrices, title, center, panelTop + PANEL_PADDING, QueueStatus.COLOR_ACCENT);
        return center;
    }

    private String trimTo(String text, int width) {
        if (this.textRenderer.getWidth(text) <= width) return text;
        return this.textRenderer.trimToWidth(text, Math.max(0, width - this.textRenderer.getWidth("..."))) + "...";
    }

    private void drawCentered(MatrixStack matrices, String text, int center, int y, int color) {
        this.textRenderer.drawWithShadow(matrices, text, center - this.textRenderer.getWidth(text) / 2f, y, color);
    }

    private void drawSmallCentered(MatrixStack matrices, String text, int center, int y, int color) {
        float scale = HudScale.oneStepSmaller(this.client);
        float width = this.textRenderer.getWidth(text) * scale;
        matrices.push();
        matrices.translate(HudScale.snapToScreenPixel(this.client, center - width / 2),
                HudScale.snapToScreenPixel(this.client, y), 0);
        matrices.scale(scale, scale, 1f);
        this.textRenderer.drawWithShadow(matrices, text, 0, 0, color);
        matrices.pop();
    }
}
