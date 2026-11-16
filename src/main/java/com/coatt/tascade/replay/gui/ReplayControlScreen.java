package com.coatt.tascade.replay.gui;

import com.coatt.tascade.replay.play.ReplayPlayer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import org.json.JSONObject;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ReplayControlScreen extends Screen {
    private static final int TEXT = 0xFFFFFF;
    private static final int MUTED = 0xA0A0A0;
    private static final int ERROR = 0xFF5555;
    private static final int SPLIT_ROW = 10;

    private final ReplayPlayer player;
    private final boolean focusGoTo;
    // the G that focused the box also types a char, skip it
    private boolean skipChar;
    private TextFieldWidget goTo;
    private ButtonWidget playButton;
    private ButtonWidget viewButton;
    private ButtonWidget settingsButton;
    private String goToError;
    private boolean dragging;

    private int timelineX;
    private int timelineY;
    private int timelineWidth;
    private final List<ReplayPlayer.Marker> splits = new ArrayList<>();
    private int splitsX;
    private int splitsY;
    private int speedLabelX;

    public ReplayControlScreen(ReplayPlayer player, boolean focusGoTo) {
        super(new LiteralText("Replay"));
        this.player = player;
        this.focusGoTo = focusGoTo;
        this.skipChar = focusGoTo;
    }

    @Override
    protected void init() {
        timelineX = 52;
        timelineWidth = this.width - 104;
        timelineY = this.height - 16;

        int y = this.height - 42;
        int x = 8;
        x = add(x, y, 32, "Start", b -> player.seek(player.raceStart >= 0 ? player.raceStart : 0));
        x = add(x, y, 30, "-30s", b -> player.skip(-30_000));
        x = add(x, y, 26, "-5s", b -> player.skip(-5_000));
        playButton = new ButtonWidget(x, y, 40, 20, new LiteralText(""), b -> player.togglePlaying());
        this.addButton(playButton);
        x += 42;
        x = add(x, y, 26, "+5s", b -> player.skip(5_000));
        x = add(x, y, 30, "+30s", b -> player.skip(30_000));
        x = add(x, y, 28, "End", b -> player.seek(player.duration));
        x += 6;
        x = add(x, y, 16, "-", b -> player.slower());
        speedLabelX = x;
        x += 30;
        add(x, y, 16, "+", b -> player.faster());

        y = this.height - 66;
        x = 152;
        viewButton = new ButtonWidget(x, y, 80, 20, new LiteralText(""), b -> player.togglePov());
        this.addButton(viewButton);
        x += 82;
        settingsButton = new ButtonWidget(x, y, 96, 20, new LiteralText(""), b -> player.toggleRunnerSettings());
        this.addButton(settingsButton);

        this.addButton(new ButtonWidget(this.width - 68, 6, 62, 20, new LiteralText("Exit replay"), b -> player.exit()));

        goTo = new TextFieldWidget(this.textRenderer, 38, this.height - 63, 80, 14, new LiteralText("Go to"));
        goTo.setMaxLength(16);
        goTo.setSuggestion("m:ss or +/-s");
        goTo.setChangedListener(text -> {
            goTo.setSuggestion(text.isEmpty() ? "m:ss or +/-s" : null);
            goToError = null;
        });
        this.children.add(goTo);
        this.addButton(new ButtonWidget(122, this.height - 65, 24, 18, new LiteralText("Go"), b -> submitGoTo()));
        if (focusGoTo) {
            goTo.setSelected(true);
            this.setFocused(goTo);
        }

        splits.clear();
        for (ReplayPlayer.Marker marker : player.markers()) if (marker.major) splits.add(marker);
        splitsX = this.width - 150;
        splitsY = 34;
        updateLabels();
    }

    private int add(int x, int y, int width, String label, ButtonWidget.PressAction action) {
        this.addButton(new ButtonWidget(x, y, width, 20, new LiteralText(label), action));
        return x + width + 2;
    }

    private void updateLabels() {
        playButton.setMessage(new LiteralText(player.isPlaying() ? "Pause" : "Play"));
        viewButton.setMessage(new LiteralText(player.isPov() ? "Runner's view" : "Free camera"));
        settingsButton.setMessage(new LiteralText("Their settings: " + (player.usesRunnerSettings() ? "On" : "Off")));
        settingsButton.active = player.isPov();
    }

    @Override
    public void tick() {
        skipChar = false;
        goTo.tick();
        updateLabels();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        updateLabels();
        fill(matrices, 0, this.height - 72, this.width, this.height, 0xF0101010);
        if (player.overlayShown) ReplayHud.renderInfo(matrices, player);
        renderSplits(matrices, mouseX, mouseY);

        Timeline.draw(matrices, player, timelineX, timelineY, timelineWidth, 8);
        String now = player.formatRunTime(player.time());
        this.textRenderer.drawWithShadow(matrices, now, timelineX - 4 - this.textRenderer.getWidth(now), timelineY, TEXT);
        this.textRenderer.drawWithShadow(matrices, player.formatRunTime(player.duration), timelineX + timelineWidth + 4, timelineY, MUTED);

        String speed = player.speedLabel();
        this.textRenderer.drawWithShadow(matrices, speed, speedLabelX + 14 - this.textRenderer.getWidth(speed) / 2f, this.height - 36, TEXT);

        this.textRenderer.drawWithShadow(matrices, "Go to", 8, this.height - 60, MUTED);
        goTo.render(matrices, mouseX, mouseY, delta);
        if (goToError != null) this.textRenderer.drawWithShadow(matrices, goToError, 8, this.height - 82, ERROR);
        renderKeys(matrices);

        super.render(matrices, mouseX, mouseY, delta);

        if (onTimeline(mouseX, mouseY) || dragging) {
            double at = Timeline.timeAt(player, timelineX, timelineWidth, mouseX);
            List<StringRenderable> lines = new ArrayList<>();
            ReplayPlayer.Marker marker = Timeline.markerNear(player, timelineX, timelineWidth, mouseX);
            if (marker != null && !dragging) {
                lines.add(new LiteralText(marker.label));
                at = marker.time;
            }
            ReplayPlayer.Band band = Timeline.bandAt(player, at);
            lines.add(new LiteralText(player.formatRunTime(at) + (band != null ? "  ·  " + band.name : "")));
            this.renderTooltip(matrices, lines, mouseX, mouseY - 12);
        }
    }

    private void renderKeys(MatrixStack matrices) {
        String[] keys = {"P / K  play or pause", "Left / Right  5 s back / on (Shift: 30 s, Ctrl: 1 tick)",
                "J / L  10 s back / on", ", / .  one tick", "Up / Down  speed", "V  runner's view / free camera",
                "O  runner's settings", "H  hide overlay", "G  go to a time"};
        int y = 60;
        int bottom = this.height - 92;
        for (String key : keys) {
            if (y + 10 > bottom) break;
            this.textRenderer.drawWithShadow(matrices, this.textRenderer.trimToWidth(key, splitsX - 20), 8, y, MUTED);
            y += 10;
        }
    }

    private void renderSplits(MatrixStack matrices, int mouseX, int mouseY) {
        if (splits.isEmpty()) return;
        int bottom = this.height - 76;
        int shown = Math.min(splits.size(), Math.max(0, (bottom - splitsY - 4) / SPLIT_ROW));
        fill(matrices, splitsX - 4, splitsY - 4, this.width - 4, splitsY + shown * SPLIT_ROW + 2, 0x90000000);
        for (int i = 0; i < shown; i++) {
            ReplayPlayer.Marker marker = splits.get(i);
            int y = splitsY + i * SPLIT_ROW;
            boolean hovered = mouseX >= splitsX - 4 && mouseX < this.width - 4 && mouseY >= y - 1 && mouseY < y + SPLIT_ROW - 1;
            if (hovered) fill(matrices, splitsX - 4, y - 1, this.width - 4, y + SPLIT_ROW - 1, 0x30FFFFFF);
            String time = player.formatRunTime(marker.time);
            String label = this.textRenderer.trimToWidth(marker.label, this.width - 12 - splitsX - this.textRenderer.getWidth(time) - 6);
            this.textRenderer.drawWithShadow(matrices, label, splitsX, y, marker.color);
            this.textRenderer.drawWithShadow(matrices, time, this.width - 8 - this.textRenderer.getWidth(time), y, MUTED);
        }
    }

    private int splitAt(double mouseX, double mouseY) {
        if (mouseX < splitsX - 4 || mouseX >= this.width - 4 || mouseY < splitsY - 1) return -1;
        int row = (int) ((mouseY - splitsY + 1) / SPLIT_ROW);
        int shown = Math.min(splits.size(), Math.max(0, (this.height - 76 - splitsY - 4) / SPLIT_ROW));
        return row < shown ? row : -1;
    }

    private boolean onTimeline(double mouseX, double mouseY) {
        return mouseX >= timelineX - 2 && mouseX <= timelineX + timelineWidth + 2 && mouseY >= timelineY - 5 && mouseY <= timelineY + 13;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && onTimeline(mouseX, mouseY)) {
            dragging = true;
            player.beginScrub();
            player.seek(Timeline.timeAt(player, timelineX, timelineWidth, mouseX));
            return true;
        }
        int split = button == 0 ? splitAt(mouseX, mouseY) : -1;
        if (split >= 0) {
            player.seek(Math.max(0, splits.get(split).time - 1000));
            return true;
        }
        if (goTo.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(goTo);
            return true;
        }
        goTo.setSelected(false);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            player.seek(Timeline.timeAt(player, timelineX, timelineWidth, mouseX));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            player.endScrub();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (onTimeline(mouseX, mouseY)) {
            player.skip(amount > 0 ? 1000 : -1000);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        skipChar = false;
        if (goTo.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitGoTo();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                goTo.setSelected(false);
                this.setFocused(null);
                return true;
            }
            return goTo.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && keyCode != GLFW.GLFW_KEY_G
                && player.handleKey(keyCode, modifiers, false)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_G) {
            goTo.setSelected(true);
            this.setFocused(goTo);
            skipChar = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (skipChar) {
            skipChar = false;
            return true;
        }
        if (goTo.isFocused()) return goTo.charTyped(chr, modifiers);
        return super.charTyped(chr, modifiers);
    }

    private void submitGoTo() {
        Double target = parseTime(goTo.getText(), player);
        if (target == null) {
            goToError = "Try 5:30, 1:02:03.5, +10 or -2.5";
            return;
        }
        player.seek(target);
        goTo.setText("");
        goTo.setSelected(false);
        this.setFocused(null);
    }

    static Double parseTime(String text, ReplayPlayer player) {
        String s = text.trim().replace(',', '.');
        if (s.isEmpty()) return null;
        try {
            boolean signed = s.startsWith("+") || s.startsWith("-");
            if (signed && !s.contains(":")) {
                return player.time() + Double.parseDouble(s) * 1000;
            }
            boolean negative = s.startsWith("-");
            if (signed) s = s.substring(1);
            String[] parts = s.split(":");
            if (parts.length > 3) return null;
            double seconds = 0;
            for (String part : parts) {
                if (part.isEmpty()) return null;
                seconds = seconds * 60 + Double.parseDouble(part);
            }
            double ms = (negative ? -seconds : seconds) * 1000;
            return player.replayTime(ms);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String outcome(JSONObject meta) {
        String outcome = meta.optString("outcome", "");
        boolean forfeit = meta.optBoolean("forfeit", false);
        switch (outcome) {
            case "COMPLETED": return "Completed";
            case "FINISHED": return "Finished";
            case "VICTORY": return forfeit ? "Won (forfeit)" : "Won";
            case "DEFEAT": return forfeit ? "Forfeited" : "Lost";
            case "DRAW": return "Draw";
            case "ENDED": return "Ended";
            case "LEFT": return "Left the match";
            case "SEED_CHANGED": return "Seed changed";
            case "ABANDONED": return "Abandoned";
            default: return "Unfinished";
        }
    }
}
