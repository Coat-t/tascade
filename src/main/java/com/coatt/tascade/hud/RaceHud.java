package com.coatt.tascade.hud;

import com.coatt.tascade.TascadeConfig;
import com.coatt.tascade.integration.RunGoals;
import com.coatt.tascade.finish.RunFinish;
import com.coatt.tascade.integration.RunRecorder;
import com.coatt.tascade.mixin.access.InventoryScreenAccessor;
import com.coatt.tascade.modifier.RunModifier;
import com.coatt.tascade.modifier.RunModifiers;
import com.coatt.tascade.network.RoomState;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.network.TascadeClient;
import com.coatt.tascade.screen.QueueStatus;
import com.coatt.tascade.screen.RunModifiersScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringRenderable;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class RaceHud {
    private static final String SELF = "self";
    private static final String HIDDEN_SPLIT = "kill_ender_dragon";

    private static final int MARGIN_RIGHT = 4;
    private static final float TOP_FRACTION = 0.25f;
    private static final int PADDING = 4;
    private static final int FACE_SIZE = 16;
    private static final int FACE_GAP = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int BOX_HEIGHT = PADDING * 2 + LINE_HEIGHT * 2;
    private static final int BOX_GAP = 2;
    private static final int MIN_BOX_WIDTH = 110;
    private static final int BACKGROUND = 0x90000000;
    private static final int TINT_SELF = 0x407FFFD4;
    private static final int TINT_OPPONENT = 0x40FF3030;
    private static final int COLOR_DEADLINE = 0xFF5555;
    private static final int ICONS_ABOVE = 3;
    private static final float SLIDE_SPEED = 20f;

    private static final List<String> order = new ArrayList<>();
    private static final Map<String, Float> boxY = new HashMap<>();
    private static long lastFrameNanos = 0;

    private RaceHud() {}

    private static final int MAX_ROOM_BOXES = 8;
    private static final int MORE_HEIGHT = LINE_HEIGHT + 2;
    private static int hiddenRacers = 0;

    private static final class Racer {
        final String name;
        final Identifier skin;
        final boolean self;
        final MutableText label;
        final RaceProgress.Standing standing;
        int rank = 0;

        Racer(String name, Identifier skin, boolean self, Map<String, Long> splits, Map<String, Long> goals) {
            this(name, skin, self, stageLabel(splits), RaceProgress.of(splits, goals));
        }

        Racer(String name, Identifier skin, boolean self, MutableText label, RaceProgress.Standing standing) {
            this.name = name;
            this.skin = skin;
            this.self = self;
            this.label = label;
            this.standing = standing;
        }
    }

    public static boolean drawnOver(Screen screen) {
        if (screen instanceof GameMenuScreen) return true;
        return screen instanceof InventoryScreen
                && !((InventoryScreenAccessor) screen).tascade$getRecipeBook().isOpen();
    }

    public static void render(MinecraftClient client, MatrixStack matrices) {
        render(client, matrices, Double.NaN, Double.NaN);
    }

    public static List<StringRenderable> render(MinecraftClient client, MatrixStack matrices, double mouseX, double mouseY) {
        // F3's right column uses the same spot. SIMPLIFIED draws the full HUD for now
        if (!RunState.isRunning() || client.player == null
                || client.options.hudHidden || client.options.debugEnabled
                || TascadeConfig.raceHud() == RaceHudMode.OFF) {
            reset();
            return null;
        }

        Map<String, Racer> racers = new LinkedHashMap<>();
        hiddenRacers = 0;
        if (RunState.isRoom()) {
            roomRacers(client, racers);
            order.clear();
            order.addAll(racers.keySet());
        } else {
            racers.put(SELF, new Racer(client.player.getGameProfile().getName(),
                    client.player.getSkinTexture(), true, RunRecorder.splitTimes(), RunGoals.times()));
            String opponentUuid = RunState.opponentUuid;
            PlayerListEntry opponent = OpponentEntry.get();
            if (opponent != null && opponentUuid != null) {
                racers.put(opponentUuid, new Racer(RunState.opponentName, opponent.getSkinTexture(), false,
                        new HashMap<>(RunState.opponentSplits), new HashMap<>(RunState.opponentGoals)));
            }

            order.retainAll(racers.keySet());
            for (String key : racers.keySet()) {
                if (!order.contains(key)) order.add(key);
            }
            order.sort((a, b) -> RaceProgress.compare(racers.get(a).standing, racers.get(b).standing));
        }

        TextRenderer textRenderer = client.textRenderer;
        boolean showRank = racers.size() > 1;
        int width = MIN_BOX_WIDTH;
        for (Racer racer : racers.values()) {
            int nameWidth = textRenderer.getWidth(racer.name) + (showRank ? textRenderer.getWidth(" #0") : 0);
            int textWidth = Math.max(nameWidth, textRenderer.getWidth(racer.label));
            width = Math.max(width, PADDING + FACE_SIZE + FACE_GAP + textWidth + PADDING);
        }
        float scale = HudScale.oneStepSmaller(client);
        int anchorX = client.getWindow().getScaledWidth() - MARGIN_RIGHT;
        int anchorY = Math.round(client.getWindow().getScaledHeight() * TOP_FRACTION);
        matrices.push();
        matrices.translate(anchorX, anchorY, 0);
        matrices.scale(scale, scale, 1f);
        int right = 0;
        int left = right - width;
        int top = 0;

        RenderSystem.disableDepthTest();

        String mode = RunState.isRanked() ? "Ranked" : RunState.isRoom() ? roomName(textRenderer, width) : "Solo";
        if (RunState.cheats) mode += " · Cheats";
        else if (RunState.isSolo() && RunState.ppMultiplier != 1) mode += " · " + RunModifier.format(RunState.ppMultiplier) + " pp";
        textRenderer.drawWithShadow(matrices, mode, right - textRenderer.getWidth(mode), top, QueueStatus.COLOR_ACCENT);
        int lineY = top + LINE_HEIGHT;
        if (RunState.isRanked() && (RunState.pace != 1 || RunState.opponentPace != 1)) {
            String pace = "Pace " + RunModifier.format(RunState.pace) + " · them " + RunModifier.format(RunState.opponentPace);
            textRenderer.drawWithShadow(matrices, pace, right - textRenderer.getWidth(pace), lineY, QueueStatus.COLOR_MUTED);
            lineY += LINE_HEIGHT;
        }
        String deadline = RunState.isRoom() ? roomTimeLabel() : deadlineLabel();
        if (deadline != null) {
            int color = RunState.awaitingIsMine ? QueueStatus.COLOR_TEXT : COLOR_DEADLINE;
            textRenderer.drawWithShadow(matrices, deadline, right - textRenderer.getWidth(deadline), lineY, color);
            lineY += LINE_HEIGHT;
        }
        String vote = RunState.isRoom() ? roomVoteLabel() : null;
        if (vote != null) {
            textRenderer.drawWithShadow(matrices, vote, right - textRenderer.getWidth(vote), lineY, QueueStatus.COLOR_ACCENT);
            lineY += LINE_HEIGHT;
        }
        int boxesTop = lineY + 2;

        float slide = slideStep();
        for (int i = 0; i < order.size(); i++) {
            String key = order.get(i);
            float target = boxesTop + i * (BOX_HEIGHT + BOX_GAP);
            Float current = boxY.get(key);
            float y = current == null ? target : current + (target - current) * slide;
            if (Math.abs(target - y) < 0.5f) y = target;
            boxY.put(key, y);

            Racer racer = racers.get(key);
            drawBox(client, matrices, racer, racer.rank > 0 ? racer.rank : rankOf(racer, racers), showRank, left, right, Math.round(y));
        }

        if (hiddenRacers > 0) {
            int barTop = boxesTop + order.size() * (BOX_HEIGHT + BOX_GAP);
            DrawableHelper.fill(matrices, left, barTop, right, barTop + MORE_HEIGHT, BACKGROUND);
            String more = "+" + hiddenRacers + (hiddenRacers == 1 ? " player" : " players");
            textRenderer.drawWithShadow(matrices, more, left + (width - textRenderer.getWidth(more)) / 2f,
                    barTop + (MORE_HEIGHT - 8) / 2f, QueueStatus.COLOR_MUTED);
        }

        RenderSystem.enableDepthTest();
        // after the depth test is back on: items drawn without it show back faces
        RunModifier hovered = renderModifierIcons(client, width, right, top, anchorX, anchorY, scale,
                (mouseX - anchorX) / scale, (mouseY - anchorY) / scale);
        matrices.pop();
        return hovered == null ? null : RunModifiersScreen.details(textRenderer, hovered);
    }

    private static RunModifier renderModifierIcons(MinecraftClient client, int width, int right,
                                                   int top, int anchorX, int anchorY, float scale,
                                                   double mouseX, double mouseY) {
        List<RunModifier> modifiers = new ArrayList<>();
        for (String id : RunState.modifiers) {
            RunModifier modifier = RunModifiers.byId(id);
            if (modifier != null) modifiers.add(modifier);
        }
        if (modifiers.isEmpty()) return null;
        int step = RunModifiersScreen.ICON_ROW_FRAME + RunModifiersScreen.ICON_ROW_GAP;
        int perRow = Math.max(1, (width + RunModifiersScreen.ICON_ROW_GAP) / step);
        int rows = (modifiers.size() + perRow - 1) / perRow;
        int y = top - ICONS_ABOVE - rows * step + RunModifiersScreen.ICON_ROW_GAP;

        // items use RenderSystem's matrix, not the MatrixStack
        RenderSystem.pushMatrix();
        RenderSystem.translatef(anchorX, anchorY, 0);
        RenderSystem.scalef(scale, scale, 1f);
        MatrixStack unscaled = new MatrixStack();
        RunModifier hovered = null;
        for (int start = 0; start < modifiers.size(); start += perRow) {
            List<RunModifier> row = modifiers.subList(start, Math.min(modifiers.size(), start + perRow));
            int x = right - RunModifiersScreen.iconRowWidth(row.size());
            RunModifier inRow = RunModifiersScreen.renderIconRow(unscaled, client, row, x, y, mouseX, mouseY);
            if (inRow != null) hovered = inRow;
            y += step;
        }
        RenderSystem.popMatrix();
        return hovered;
    }

    private static void drawBox(MinecraftClient client, MatrixStack matrices, Racer racer, int rank,
                                boolean showRank, int left, int right, int y) {
        DrawableHelper.fill(matrices, left, y, right, y + BOX_HEIGHT, BACKGROUND);
        DrawableHelper.fill(matrices, left, y, right, y + BOX_HEIGHT, racer.self ? TINT_SELF : TINT_OPPONENT);

        int faceX = left + PADDING;
        int faceY = y + (BOX_HEIGHT - FACE_SIZE) / 2;
        client.getTextureManager().bindTexture(racer.skin);
        RenderSystem.color4f(1f, 1f, 1f, 1f);
        DrawableHelper.drawTexture(matrices, faceX, faceY, FACE_SIZE, FACE_SIZE, 8f, 8f, 8, 8, 64, 64);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        DrawableHelper.drawTexture(matrices, faceX, faceY, FACE_SIZE, FACE_SIZE, 40f, 8f, 8, 8, 64, 64);
        RenderSystem.disableBlend();

        TextRenderer textRenderer = client.textRenderer;
        int textX = faceX + FACE_SIZE + FACE_GAP;
        int textY = y + PADDING + 1;
        textRenderer.drawWithShadow(matrices, racer.name, textX, textY, QueueStatus.COLOR_TEXT);
        if (showRank) {
            textRenderer.drawWithShadow(matrices, "#" + rank,
                    textX + textRenderer.getWidth(racer.name + " "), textY, QueueStatus.COLOR_MUTED);
        }
        textRenderer.drawWithShadow(matrices, racer.label, textX, textY + LINE_HEIGHT,
                QueueStatus.COLOR_MUTED);
    }

    private static String deadlineLabel() {
        long deadline = RunState.awaitingDeadlineRtaMs;
        if (!RunState.isRanked() || deadline < 0) return null;
        long left = Math.max(0, deadline - RunFinish.elapsedSinceRaceStart());
        String remaining = RunLabels.time(left);
        remaining = remaining.substring(0, remaining.length() - 2);
        return RunState.awaitingIsMine
                ? "Opponent has " + remaining + " left"
                : "Finish within " + remaining + " to win";
    }

    private static void roomRacers(MinecraftClient client, Map<String, Racer> racers) {
        List<RoomState.Standing> standings = RoomState.standings();
        String me = TascadeClient.getInstance().getPlayerUuid();
        int selfIndex = -1;
        for (int i = 0; i < standings.size(); i++) {
            if (standings.get(i).uuid.equals(me)) selfIndex = i;
        }
        boolean selfBelow = selfIndex >= MAX_ROOM_BOXES;
        for (int i = 0; i < standings.size(); i++) {
            boolean self = i == selfIndex;
            if (!self && racers.size() >= MAX_ROOM_BOXES - (selfBelow ? 1 : 0)) continue;
            RoomState.Standing standing = standings.get(i);
            Identifier skin;
            if (self) {
                skin = client.player.getSkinTexture();
            } else {
                PlayerListEntry entry = OpponentEntry.of(standing.uuid, standing.username);
                skin = entry != null ? entry.getSkinTexture() : client.player.getSkinTexture();
            }
            Racer racer = new Racer(standing.username, skin, self, roomLabel(standing),
                    RaceProgress.of(standing.splits, standing.goals));
            racer.rank = RoomState.rankOf(standing, standings);
            racers.put(standing.uuid, racer);
        }
        hiddenRacers = standings.size() - racers.size();
    }

    private static String roomName(TextRenderer textRenderer, int width) {
        RoomState.Room room = RoomState.room();
        String name = room != null ? room.settings.name : "Room";
        if (textRenderer.getWidth(name) <= width) return name;
        return textRenderer.trimToWidth(name, Math.max(0, width - textRenderer.getWidth("..."))) + "...";
    }

    private static MutableText roomLabel(RoomState.Standing standing) {
        if (standing.finished() && standing.rtaMs != null) return new LiteralText("Finished · " + RunLabels.time(standing.rtaMs));
        if (RoomState.STATUS_DNF.equals(standing.status)) {
            RoomState.Racer racer = RoomState.racer(standing.uuid);
            String reason = racer != null ? racer.dnfReason : null;
            return new LiteralText("forfeit".equals(reason) ? "Gave up" : "Out of the race");
        }
        return stageLabel(standing.splits);
    }

    private static String roomTimeLabel() {
        long endsAt = RoomState.raceEndsAtMs;
        if (endsAt <= 0) return null;
        String remaining = RunLabels.time(Math.max(0, endsAt - System.currentTimeMillis()));
        remaining = remaining.substring(0, remaining.length() - 4);
        return "Race ends in " + remaining;
    }

    private static String roomVoteLabel() {
        RoomState.Vote vote = RoomState.vote;
        if (vote == null) return null;
        String what = "seed".equals(vote.type) ? "new seed" : "end race";
        return "Vote: " + what + " " + vote.yes.size() + "/" + vote.needed + " · " + vote.secondsLeft() + "s";
    }

    private static int rankOf(Racer racer, Map<String, Racer> racers) {
        int rank = 1;
        for (Racer other : racers.values()) {
            if (other != racer && RaceProgress.compare(other.standing, racer.standing) < 0) rank++;
        }
        return rank;
    }

    private static MutableText stageLabel(Map<String, Long> splits) {
        String latest = null;
        long latestAt = Long.MIN_VALUE;
        for (Map.Entry<String, Long> split : splits.entrySet()) {
            if (HIDDEN_SPLIT.equals(split.getKey())) continue;
            if (split.getValue() >= latestAt) {
                latest = split.getKey();
                latestAt = split.getValue();
            }
        }
        return latest != null ? RunLabels.split(latest) : new LiteralText("Overworld");
    }

    private static float slideStep() {
        long now = System.nanoTime();
        float seconds = lastFrameNanos == 0 ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        return 1f - (float) Math.exp(-SLIDE_SPEED * seconds);
    }

    private static void reset() {
        order.clear();
        boxY.clear();
        lastFrameNanos = 0;
    }
}
