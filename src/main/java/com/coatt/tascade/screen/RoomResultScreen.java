package com.coatt.tascade.screen;

import com.coatt.tascade.chat.MatchChatDock;
import com.coatt.tascade.chat.MatchChatOverlay;
import com.coatt.tascade.finish.RunFinish;
import com.coatt.tascade.finish.RunSummary;
import com.coatt.tascade.hud.RunLabels;
import com.coatt.tascade.network.RoomState;
import com.coatt.tascade.network.TascadeClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.Rect2i;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class RoomResultScreen extends TascadeScreen implements MatchChatDock {
    private static final long FADE_MS = 600;
    private static final int LEFT = 14;
    private static final int LINE = 12;
    private static final int COLUMN_GAP = 16;
    private static final int FACE_SIZE = 8;
    private static final int FACE_INDENT = FACE_SIZE + 3;
    private static final int TABLE_GAP = 12;
    private static final int COLOR_FIRST = 0xFFAA00;

    private final RunSummary summary;
    private final long openedAt = System.currentTimeMillis();
    private final TableScroll scroll = new TableScroll();
    private int contentRight = 0;

    public RoomResultScreen(RunSummary summary) {
        super(summary.title());
        this.summary = summary;
    }

    @Override
    protected void init() {
        this.addButton(new ButtonWidget(
                LEFT,
                this.height - 20 - 10,
                100,
                20,
                new LiteralText("Back to Lobby"),
                buttonWidget -> {
                    if (this.client.world != null) {
                        this.client.world.disconnect();
                        this.client.disconnect();
                    }
                    this.client.openScreen(new TitleScreen());
                }
        ));
        this.addButton(new ButtonWidget(
                LEFT + 110,
                this.height - 20 - 10,
                100,
                20,
                new LiteralText("Spectate"),
                buttonWidget -> this.onClose()
        ));
    }

    @Override
    public void onClose() {
        RunFinish.clearTitle(this.client);
        this.client.openScreen(null);
    }

    @Override
    protected float backgroundAlpha() {
        return fade();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        float alpha = fade();
        this.renderBackground(matrices);
        contentRight = LEFT + 2 * this.textRenderer.getWidth(summary.title());
        for (AbstractButtonWidget button : this.buttons) button.setAlpha(alpha);

        // whole-number scale keeps the pixel font crisp
        matrices.push();
        matrices.scale(2f, 2f, 1f);
        this.textRenderer.drawWithShadow(matrices, summary.title(), LEFT / 2f, LEFT / 2f,
                withAlpha(QueueStatus.COLOR_TEXT, alpha));
        matrices.pop();

        int y = LEFT + 24;
        y = line(matrices, summary.subtitle(), y, QueueStatus.COLOR_TEXT, alpha);
        y = line(matrices, new LiteralText(context()), y, QueueStatus.COLOR_MUTED, alpha);
        y = line(matrices, new LiteralText(summary.reason()), y, QueueStatus.COLOR_MUTED, alpha);
        y = line(matrices, new LiteralText(raceStatus()), y, QueueStatus.COLOR_TEXT, alpha);

        standings(matrices, y + 8, mouseX, mouseY, alpha);

        super.render(matrices, mouseX, mouseY, delta);
    }

    private String context() {
        RoomState.Room room = RoomState.room();
        StringBuilder context = new StringBuilder(room != null ? room.settings.name : "Custom room");
        if (summary.seedType != null && !summary.seedType.isEmpty()) {
            context.append(" · ").append(RunLabels.seedType(summary.seedType));
        }
        if (room != null) context.append(" · ").append(room.settings.goalLabel());
        return context.toString();
    }

    private String raceStatus() {
        if (RoomState.finalStandings != null) return endedLabel(RoomState.endReason);
        int racing = 0;
        for (RoomState.Standing standing : RoomState.standings()) {
            if (RoomState.STATUS_RACING.equals(standing.status)) racing++;
        }
        StringBuilder status = new StringBuilder("Race in progress · ").append(racing).append(" still racing");
        long endsAt = RoomState.raceEndsAtMs;
        if (endsAt > 0) {
            long left = Math.max(0, endsAt - System.currentTimeMillis());
            status.append(" · ").append(clock(left)).append(" left");
        }
        return status.toString();
    }

    private static String endedLabel(String reason) {
        if (reason == null) return "Race over";
        switch (reason) {
            case "goal": return "Race over: the goal was reached";
            case "time": return "Race over: time ran out";
            case "vote": return "Race over: ended by vote";
            case "host": return "Race over: ended by the host";
            case "all_done": return "Race over: everyone is done";
            default: return "Race over";
        }
    }

    private static String clock(long ms) {
        long seconds = (ms + 999) / 1000;
        long hours = seconds / 3600;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, seconds / 60 % 60, seconds % 60)
                : String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private void standings(MatrixStack matrices, int y, int mouseX, int mouseY, float alpha) {
        List<RoomState.Standing> standings = RoomState.standings();
        if (standings.isEmpty()) {
            line(matrices, new LiteralText("No standings yet"), y, QueueStatus.COLOR_MUTED, alpha);
            return;
        }
        String me = TascadeClient.getInstance().getPlayerUuid();
        TextTable table = new TextTable();
        table.column("#").alignRight();
        table.column("Player").indent(FACE_INDENT);
        table.column("Time");
        int[] widths = {this.textRenderer.getWidth("#"), this.textRenderer.getWidth("Player") + FACE_INDENT,
                this.textRenderer.getWidth("Time")};
        for (RoomState.Standing standing : standings) {
            boolean mine = standing.uuid.equals(me);
            String place = place(standing, standings);
            String name = standing.username;
            String time = standing.rtaMs != null ? RunLabels.time(standing.rtaMs) : status(standing);
            int placeColor = standing.place != null && standing.place == 1 ? COLOR_FIRST : QueueStatus.COLOR_MUTED;
            int timeColor = standing.rtaMs != null ? QueueStatus.COLOR_TEXT : QueueStatus.COLOR_MUTED;
            table.row(
                    TextTable.cell(place, withAlpha(placeColor, alpha)),
                    TextTable.cell(name, withAlpha(mine ? QueueStatus.COLOR_ACCENT : QueueStatus.COLOR_TEXT, alpha)),
                    TextTable.cell(time, withAlpha(timeColor, alpha)));
            widths[0] = Math.max(widths[0], this.textRenderer.getWidth(place));
            widths[1] = Math.max(widths[1], this.textRenderer.getWidth(name) + FACE_INDENT);
            widths[2] = Math.max(widths[2], this.textRenderer.getWidth(time));
        }

        int maxWidth = Math.max(120, this.width - 2 * LEFT - COLUMN_GAP * 2 - MatchChatOverlay.dockedWidth(this.client));
        int bottom = this.height - 20 - 10 - 6;
        table.render(matrices, this.textRenderer, LEFT, y, maxWidth, bottom, mouseX, mouseY, false, scroll);
        int tableWidth = widths[0] + widths[1] + widths[2] + 2 * TABLE_GAP
                + TableScroll.BAR_GAP + TableScroll.BAR_WIDTH;
        contentRight = Math.max(contentRight, LEFT + Math.min(tableWidth, maxWidth));

        if (alpha < 1f) return;
        // faces can't fade with the text
        for (int i = table.firstShownRow(); i < table.firstShownRow() + table.shownRows(); i++) {
            PlayerHeads.draw(matrices, standings.get(i).uuid, table.cellX(1), table.rowY(i) - 1, FACE_SIZE);
        }
    }

    private static String place(RoomState.Standing standing, List<RoomState.Standing> all) {
        if (standing.finished()) return String.valueOf(standing.place);
        if (RoomState.STATUS_DNF.equals(standing.status)) return "-";
        return String.valueOf(RoomState.rankOf(standing, all));
    }

    private static String status(RoomState.Standing standing) {
        if (RoomState.STATUS_RACING.equals(standing.status)) {
            String state = RoomState.finalStandings != null ? "Not finished" : "Racing";
            String latest = latestSplit(standing.splits);
            return latest != null ? state + " - " + RunLabels.split(latest).getString() : state;
        }
        if (RoomState.STATUS_FINISHED.equals(standing.status)) return "Finished";
        RoomState.Racer racer = RoomState.racer(standing.uuid);
        String reason = racer != null ? racer.dnfReason : null;
        if ("forfeit".equals(reason)) return "Gave up";
        if ("left".equals(reason)) return "Left";
        return "DNF";
    }

    static String latestSplit(Map<String, Long> splits) {
        String latest = null;
        long latestRta = Long.MIN_VALUE;
        for (Map.Entry<String, Long> split : splits.entrySet()) {
            if (split.getValue() != null && split.getValue() > latestRta) {
                latest = split.getKey();
                latestRta = split.getValue();
            }
        }
        return latest;
    }

    private int line(MatrixStack matrices, StringRenderable text, int y, int rgb, float alpha) {
        this.textRenderer.drawWithShadow(matrices, text, LEFT, y, withAlpha(rgb, alpha));
        contentRight = Math.max(contentRight, LEFT + this.textRenderer.getWidth(text));
        return y + LINE;
    }

    @Override
    public Rect2i matchChatArea() {
        int right = this.width - LEFT;
        int left = Math.max(contentRight + COLUMN_GAP * 2, right - MatchChatOverlay.dockedWidth(this.client));
        int top = LEFT + 24;
        return new Rect2i(left, top, right - left, this.height - 10 - top);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scroll.mouseScrolled(amount) || super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button) || scroll.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return scroll.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return scroll.mouseReleased() | super.mouseReleased(mouseX, mouseY, button);
    }

    private float fade() {
        return MathHelper.clamp((System.currentTimeMillis() - openedAt) / (float) FADE_MS, 0f, 1f);
    }

    // alpha below 4 renders fully opaque, so the fade starts from 5
    private static int withAlpha(int rgb, float alpha) {
        int a = Math.max(5, Math.round(alpha * 255));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
