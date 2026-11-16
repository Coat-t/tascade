package com.coatt.tascade.replay.gui;

import com.coatt.tascade.hud.RunLabels;
import com.coatt.tascade.network.RoomState;
import com.coatt.tascade.replay.ReplayStorage;
import com.coatt.tascade.replay.play.ReplayPlayer;
import com.coatt.tascade.screen.QueueStatus;
import com.coatt.tascade.screen.TableScroll;
import com.coatt.tascade.screen.TascadeScreen;
import com.coatt.tascade.screen.TextTable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Util;
import org.json.JSONObject;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

import static com.coatt.tascade.screen.TextTable.cell;

public final class ReplayListScreen extends TascadeScreen {
    private static final int LEFT = 14;
    private static final int TOP = 36;
    private static final int BUTTON_WIDTH = 80;
    private static final int GAP = 6;
    private static final int SELECTED_COLOR = 0x40FFFFFF;

    private final Screen parent;
    private final TableScroll scroll = new TableScroll();
    private List<ReplayStorage.Entry> entries;
    private TextTable table;
    private int selected = -1;
    private int lastClickedRow = -1;
    private long lastClickMs;
    private boolean confirmingDelete;
    private ButtonWidget watchButton;
    private ButtonWidget deleteButton;

    public ReplayListScreen(Screen parent) {
        super(new LiteralText("Replays"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        entries = ReplayStorage.list();
        if (selected >= entries.size()) selected = -1;
        int y = this.height - 30;
        this.addButton(new ButtonWidget(LEFT, y, BUTTON_WIDTH, 20, new LiteralText("Back"), b -> onClose()));
        this.addButton(new ButtonWidget(LEFT + BUTTON_WIDTH + GAP, y, BUTTON_WIDTH, 20, new LiteralText("Open folder"), b -> {
            try {
                Files.createDirectories(ReplayStorage.directory());
            } catch (Exception ignored) {
            }
            Util.getOperatingSystem().open(ReplayStorage.directory().toFile());
        }));
        int right = this.width - LEFT - BUTTON_WIDTH;
        watchButton = this.addButton(new ButtonWidget(right, y, BUTTON_WIDTH, 20, new LiteralText("Watch"), b -> watch()));
        deleteButton = this.addButton(new ButtonWidget(right - BUTTON_WIDTH - GAP, y, BUTTON_WIDTH, 20,
                new LiteralText("Delete"), b -> delete()));
        updateButtons();
    }

    private void updateButtons() {
        boolean any = selected >= 0 && selected < entries.size();
        watchButton.active = any && watchBlocked() == null;
        deleteButton.active = any;
        deleteButton.setMessage(new LiteralText(confirmingDelete ? "Really delete?" : "Delete"));
    }

    private String watchBlocked() {
        if (this.client.world != null) return "Leave the world to watch a replay.";
        if (RoomState.inRoom()) return "Leave the custom room to watch a replay.";
        return null;
    }

    private void watch() {
        if (selected < 0 || selected >= entries.size() || watchBlocked() != null) return;
        ReplayPlayer.open(this.client, entries.get(selected).path, this);
    }

    private void delete() {
        if (selected < 0 || selected >= entries.size()) return;
        if (!confirmingDelete) {
            confirmingDelete = true;
            updateButtons();
            return;
        }
        confirmingDelete = false;
        ReplayStorage.delete(entries.get(selected).path);
        selected = -1;
        init(this.client, this.width, this.height);
    }

    @Override
    public void onClose() {
        this.client.openScreen(parent);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        this.drawStringWithShadow(matrices, this.textRenderer, "Replays", LEFT, 14, QueueStatus.COLOR_ACCENT);

        if (entries.isEmpty()) {
            this.drawStringWithShadow(matrices, this.textRenderer,
                    "No replays yet. Tascade records your runs as you play them.", LEFT, TOP, QueueStatus.COLOR_MUTED);
        } else {
            table = new TextTable();
            table.column("When");
            table.column("Match");
            table.column("Result");
            table.column("Seed").optional();
            table.column("Time").alignRight();
            table.column("Length").alignRight().optional();
            table.column("Size").alignRight().optional();
            for (ReplayStorage.Entry entry : entries) table.row(row(entry));
            table.render(matrices, this.textRenderer, LEFT, TOP, this.width - 2 * LEFT, this.height - 38, mouseX, mouseY, true, scroll);
            if (selected >= table.firstShownRow() && selected < table.firstShownRow() + table.shownRows()) {
                int y = table.rowY(selected);
                fill(matrices, LEFT - 2, y - 2, this.width - LEFT + 2, y + TextTable.ROW_HEIGHT - 2, SELECTED_COLOR);
            }
        }
        String note = watchBlocked();
        watchButton.active = selected >= 0 && selected < entries.size() && note == null;
        if (note != null) {
            this.drawStringWithShadow(matrices, this.textRenderer, note,
                    this.width - LEFT - this.textRenderer.getWidth(note), 14, QueueStatus.COLOR_MUTED);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    private TextTable.Cell[] row(ReplayStorage.Entry entry) {
        JSONObject meta = entry.meta;
        long started = meta.optLong("startedAt", entry.modifiedMs);
        String when = RunLabels.ago(Instant.ofEpochMilli(started).toString());
        String mode = !meta.has("mode") ? "?" : "RANKED".equals(meta.optString("mode")) ? "Ranked" : "Solo";
        if (meta.has("opponentName")) mode = meta.optString("playerName", "Player") + " vs " + meta.optString("opponentName");
        String result = entry.complete ? ReplayControlScreen.outcome(meta) : "Cut off";
        int resultColor = resultColor(meta.optString("outcome", ""), entry.complete);
        String seed = meta.has("seedType") ? RunLabels.seedType(meta.optString("seedType")) : "";
        String time = meta.has("finalRtaMs") ? RunLabels.time(meta.optLong("finalRtaMs")) : "";
        String length = meta.has("durationMs") ? RunLabels.time(meta.optLong("durationMs")) : "";
        String size = String.format("%.1f MB", entry.sizeBytes / (1024.0 * 1024.0));
        return new TextTable.Cell[] {
                cell(when, QueueStatus.COLOR_MUTED),
                cell(mode, QueueStatus.COLOR_TEXT),
                cell(result, resultColor),
                cell(seed, QueueStatus.COLOR_MUTED),
                cell(time, QueueStatus.COLOR_TEXT),
                cell(length, QueueStatus.COLOR_MUTED),
                cell(size, QueueStatus.COLOR_MUTED)};
    }

    private static int resultColor(String outcome, boolean complete) {
        if (!complete) return QueueStatus.COLOR_ERROR;
        switch (outcome) {
            case "COMPLETED":
            case "VICTORY":
            case "FINISHED":
                return 0x7CE36B;
            case "DEFEAT":
                return QueueStatus.COLOR_ERROR;
            default:
                return QueueStatus.COLOR_MUTED;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (scroll.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || table == null) return false;
        int row = table.rowAt(mouseX, mouseY);
        if (row < 0 || row >= entries.size()) return false;
        long now = Util.getMeasuringTimeMs();
        boolean doubleClick = row == lastClickedRow && now - lastClickMs < 350;
        lastClickedRow = row;
        lastClickMs = now;
        if (row != selected) confirmingDelete = false;
        selected = row;
        updateButtons();
        this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        if (doubleClick) watch();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scroll.mouseScrolled(amount) || super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return scroll.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return scroll.mouseReleased() | super.mouseReleased(mouseX, mouseY, button);
    }
}
