package com.coatt.tascade.screen;

import com.coatt.tascade.hud.RunLabels;
import com.coatt.tascade.modifier.RunModifier;
import com.coatt.tascade.modifier.RunModifiers;
import com.coatt.tascade.network.RoomState;
import com.coatt.tascade.network.TascadeClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntConsumer;

@Environment(EnvType.CLIENT)
public class RoomSettingsScreen extends TascadeScreen {
    private static final int LEFT = 14;
    private static final int TITLE_Y = 32;
    private static final int LIST_TOP = 48;
    private static final int ROW_HEIGHT = 24;
    private static final int COLUMN_GAP = 20;
    private static final int LABEL_GAP = 8;
    private static final int MIN_CONTROL_WIDTH = 120;
    private static final int MAX_CONTROL_WIDTH = 150;
    private static final int NUMBER_WIDTH = 44;
    private static final int MAX_PLAYERS = 32;
    private static final int BUTTON_WIDTH = 80;

    private static final List<String> SEED_MODES = Arrays.asList(
            RoomState.SEED_RANDOM, RoomState.SEED_TYPE, RoomState.SEED_CUSTOM, RoomState.SEED_ID);
    private static final List<String> GOALS = Arrays.asList(
            RoomState.GOAL_COMPLETIONS, RoomState.GOAL_TIME, RoomState.GOAL_FIRST_PLUS);
    private static final List<String> VOTES = Arrays.asList(
            RoomState.VOTE_OFF, RoomState.VOTE_MAJORITY, RoomState.VOTE_UNANIMOUS);

    private final Screen parent;
    private final boolean editable;
    private final RoomState.Settings settings;

    private String maxPlayersText;
    private String nameText;
    private String seedCustomText;
    private IntSlider finishersSlider;
    private String seedIdText;
    private String goalCountText;
    private String goalMinutesText;
    private String goalSecondsText;
    private String windowText;

    private final List<Row> rows = new ArrayList<>();
    private int columns;
    private int columnWidth;
    private int labelWidth;
    private int controlWidth;
    private int columnsLeft;
    private int listBottom;
    private int scroll = 0;
    private boolean discardArmed = false;
    private String error = null;

    private static final class Row {
        final String label;
        final AbstractButtonWidget control;
        final String note;
        final boolean narrow;
        int x;
        int y;

        Row(String label, AbstractButtonWidget control, String note, boolean narrow) {
            this.label = label;
            this.control = control;
            this.note = note;
            this.narrow = narrow;
        }
    }

    public RoomSettingsScreen(Screen parent, boolean editable) {
        super(new LiteralText(editable ? "Room Settings" : "Room Rules"));
        this.parent = parent;
        this.editable = editable;
        RoomState.Room room = RoomState.room();
        this.settings = room != null ? room.settings.copy() : new RoomState.Settings();
        maxPlayersText = String.valueOf(settings.maxPlayers);
        nameText = settings.name;
        seedCustomText = settings.seedCustom != null ? settings.seedCustom : "";
        seedIdText = settings.seedId != null ? String.valueOf(settings.seedId) : "";
        goalCountText = String.valueOf(settings.goalCount);
        goalMinutesText = String.valueOf(settings.goalMinutes);
        goalSecondsText = String.valueOf(settings.goalSeconds);
        windowText = String.valueOf(settings.voteSeedWindowSec);
    }

    @Override
    protected void init() {
        this.client.keyboard.enableRepeatEvents(true);
        rows.clear();
        finishersSlider = null;
        text("Room name", nameText, RoomState.NAME_MAX, text -> nameText = text);
        // the server refuses fewer than the players already in the room
        RoomState.Room current = RoomState.room();
        int fewestPlayers = Math.max(1, current != null ? current.members.size() : 1);
        slider("Max players", fewestPlayers, MAX_PLAYERS, parseOr(maxPlayersText, settings.maxPlayers),
                count -> count + (count == 1 ? " player" : " players"), count -> {
                    maxPlayersText = String.valueOf(count);
                    if (finishersSlider != null) finishersSlider.setMax(count);
                });
        cycle("Seed", seedModeLabel(settings.seedMode), () -> {
            settings.seedMode = next(SEED_MODES, settings.seedMode);
            if (RoomState.SEED_TYPE.equals(settings.seedMode) && settings.seedType == null) {
                settings.seedType = RoomState.SEED_TYPES.get(0);
            }
        });
        switch (settings.seedMode) {
            case RoomState.SEED_TYPE:
                cycle("Seed type", settings.seedType != null ? RunLabels.seedType(settings.seedType) : "?",
                        () -> settings.seedType = next(RoomState.SEED_TYPES, settings.seedType));
                break;
            case RoomState.SEED_CUSTOM:
                text("Custom seed", seedCustomText, 32, text -> seedCustomText = text);
                break;
            case RoomState.SEED_ID:
                number("Seed id", seedIdText, 9, text -> seedIdText = text, null);
                break;
            default:
                break;
        }
        cycle("Finish goal", goalLabel(settings.goalType), () -> settings.goalType = next(GOALS, settings.goalType));
        switch (settings.goalType) {
            case RoomState.GOAL_TIME:
                number("Minutes", goalMinutesText, 3, text -> goalMinutesText = text, "1-180");
                break;
            case RoomState.GOAL_FIRST_PLUS:
                number("Extra time", goalSecondsText, 4, text -> goalSecondsText = text, "s, 10-3600");
                break;
            default:
                finishersSlider = slider("Finishers", 1, parseOr(maxPlayersText, settings.maxPlayers),
                        parseOr(goalCountText, settings.goalCount),
                        count -> count == 1 ? "First to finish" : count + " finishers",
                        count -> goalCountText = String.valueOf(count));
                goalCountText = String.valueOf(finishersSlider.current());
                break;
        }
        cycle("Ready system", onOff(settings.readyCheck), () -> settings.readyCheck = !settings.readyCheck);
        cycle("Cheats", onOff(settings.cheats), () -> settings.cheats = !settings.cheats);
        cycle("Seed vote", RoomLobbyScreen.voteLabel(settings.voteSeed), () -> settings.voteSeed = next(VOTES, settings.voteSeed));
        number("Seed vote window", windowText, 4, text -> windowText = text, "s, 0 = any");
        cycle("End-race vote", RoomLobbyScreen.voteLabel(settings.voteEnd), () -> settings.voteEnd = next(VOTES, settings.voteEnd));
        row("Modifier rules", new ButtonWidget(0, 0, 0, 20, new LiteralText("Modifier Rules..."),
                buttonWidget -> this.client.openScreen(new RoomModifierRulesScreen(this, settings, editable))), null, false);
        if (editable) {
            RoomState.Room room = RoomState.room();
            int banned = room != null ? room.banned.size() : 0;
            row("Banned players", new ButtonWidget(0, 0, 0, 20, new LiteralText("Banned Players (" + banned + ")..."),
                    buttonWidget -> this.client.openScreen(new BannedPlayersScreen(this))), null, false);
        }

        layout();

        int y = this.height - 20 - 10;
        this.addButton(new ButtonWidget(this.width / 2 - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, 20,
                new LiteralText("Close"), buttonWidget -> onClose(), this::closeTooltip));
    }

    // rows

    private void cycle(String label, String value, Runnable step) {
        ButtonWidget button = new ButtonWidget(0, 0, 0, 20, new LiteralText(value), buttonWidget -> {
            step.run();
            error = null;
            this.init(this.client, this.width, this.height);
        });
        button.active = editable;
        row(label, button, null, false);
    }

    private IntSlider slider(String label, int min, int max, int value, IntFunction<String> format, IntConsumer onChange) {
        IntSlider slider = new IntSlider(min, max, value, format, count -> {
            onChange.accept(count);
            error = null;
        });
        slider.editable = editable;
        slider.active = editable && max > min;
        row(label, slider, null, false);
        return slider;
    }

    private static int parseOr(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static final class IntSlider extends SliderWidget {
        private final int min;
        private int max;
        private boolean editable = true;
        private final IntFunction<String> format;
        private final IntConsumer onChange;

        IntSlider(int min, int max, int value, IntFunction<String> format, IntConsumer onChange) {
            super(0, 0, 0, 20, LiteralText.EMPTY, max > min ? (MathHelper.clamp(value, min, max) - min) / (double) (max - min) : 0);
            this.min = min;
            this.max = max;
            this.format = format;
            this.onChange = onChange;
            updateMessage();
        }

        private int current() {
            return max > min ? min + (int) Math.round(this.value * (max - min)) : min;
        }

        void setMax(int newMax) {
            int count = Math.min(current(), Math.max(min, newMax));
            max = Math.max(min, newMax);
            this.value = max > min ? (count - min) / (double) (max - min) : 0;
            this.active = editable && max > min;
            updateMessage();
            applyValue();
        }

        @Override
        protected void updateMessage() {
            setMessage(new LiteralText(format.apply(current())));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }
    }

    private void number(String label, String value, int maxLength, Consumer<String> onChange, String note) {
        TextFieldWidget field = field(label, value, maxLength, onChange);
        field.setTextPredicate(text -> text.matches("\\d*"));
        row(label, field, note, true);
    }

    private void text(String label, String value, int maxLength, Consumer<String> onChange) {
        row(label, field(label, value, maxLength, onChange), null, false);
    }

    private TextFieldWidget field(String label, String value, int maxLength, Consumer<String> onChange) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, 0, 0, 0, 20, new LiteralText(label));
        field.setMaxLength(maxLength);
        field.setText(value);
        field.setChangedListener(text -> {
            onChange.accept(text);
            error = null;
        });
        field.setEditable(editable);
        return field;
    }

    private void row(String label, AbstractButtonWidget control, String note, boolean narrow) {
        rows.add(new Row(label, control, note, narrow));
        if (!(control instanceof TextFieldWidget)) this.addButton(control);
        else this.children.add(control);
    }

    private void layout() {
        labelWidth = 0;
        for (Row row : rows) labelWidth = Math.max(labelWidth, this.textRenderer.getWidth(row.label));
        labelWidth += LABEL_GAP;
        int available = this.width - 2 * LEFT;
        int twoColumns = (available - COLUMN_GAP) / 2;
        columns = twoColumns >= labelWidth + MIN_CONTROL_WIDTH ? 2 : 1;
        columnWidth = Math.min(labelWidth + MAX_CONTROL_WIDTH, columns == 2 ? twoColumns : available);
        controlWidth = columnWidth - labelWidth;
        int totalWidth = columns * columnWidth + (columns - 1) * COLUMN_GAP;
        columnsLeft = (this.width - totalWidth) / 2;
        listBottom = this.height - 20 - 10 - 8 - 12;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        placeRows();
    }

    private int perColumn() {
        return (rows.size() + columns - 1) / columns;
    }

    private int maxScroll() {
        return Math.max(0, perColumn() * ROW_HEIGHT - 4 - (listBottom - LIST_TOP));
    }

    private void placeRows() {
        int perColumn = perColumn();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int column = i / perColumn;
            row.x = columnsLeft + column * (columnWidth + COLUMN_GAP);
            row.y = LIST_TOP + (i % perColumn) * ROW_HEIGHT - scroll;
            AbstractButtonWidget control = row.control;
            boolean inView = row.y >= LIST_TOP && row.y + 20 <= listBottom;
            boolean field = control instanceof TextFieldWidget;
            control.x = row.x + labelWidth + (field ? 1 : 0);
            control.y = row.y + (field ? 1 : 0);
            control.setWidth((row.narrow ? Math.min(NUMBER_WIDTH, controlWidth) : controlWidth) - (field ? 2 : 0));
            if (field) {
                TextFieldWidget textField = (TextFieldWidget) control;
                textField.setVisible(inView);
                // made before it had a width, the field scrolled its text out of sight
                if (!textField.isFocused()) textField.setCursorToStart();
            } else {
                control.visible = inView;
            }
        }
    }

    // saving

    @Override
    public void tick() {
        super.tick();
        if (!RoomState.inRoom()) {
            leave();
            return;
        }
        for (Row row : rows) {
            if (row.control instanceof TextFieldWidget) ((TextFieldWidget) row.control).tick();
        }
    }

    private void closeTooltip(ButtonWidget button, MatrixStack matrices, int mouseX, int mouseY) {
        if (!editable) return;
        this.renderTooltip(matrices, new LiteralText(canSaveNow()
                ? "Saves your changes"
                : "The rules can only change in the lobby, between races"), mouseX, mouseY);
    }

    private static boolean canSaveNow() {
        RoomState.Room room = RoomState.room();
        return room != null && RoomState.PHASE_LOBBY.equals(room.phase);
    }

    private void close() {
        if (!editable) {
            leave();
            return;
        }
        if (!validate()) return;
        RoomState.Room room = RoomState.room();
        if (room == null || !changedFrom(room.settings)) {
            leave();
            return;
        }
        if (!canSaveNow()) {
            if (discardArmed) {
                leave();
                return;
            }
            discardArmed = true;
            error = "The rules can't change during a race. Close again to discard your changes.";
            return;
        }
        TascadeClient.getInstance().updateRoomSettings(settings);
        leave();
    }

    private boolean changedFrom(RoomState.Settings current) {
        List<String> ids = new ArrayList<>();
        for (RunModifier.Type type : RunModifier.Type.values()) {
            for (RunModifier modifier : RunModifiers.ofType(type)) ids.add(modifier.id);
        }
        return !settings.toJson(ids).similar(current.toJson(ids));
    }

    private void leave() {
        if (this.client != null) this.client.openScreen(parent);
    }

    private boolean validate() {
        RoomState.Room room = RoomState.room();
        int members = room != null ? room.members.size() : 1;
        String name = nameText.trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > RoomState.NAME_MAX || name.indexOf('\u00a7') >= 0) {
            error = "Give the room a name (up to " + RoomState.NAME_MAX + " characters, no formatting codes).";
            return false;
        }
        Integer maxPlayers = parse(maxPlayersText);
        if (maxPlayers == null || maxPlayers < 1 || maxPlayers > 32) {
            error = "Max players must be from 1 to 32.";
            return false;
        }
        if (maxPlayers < members) {
            error = "Max players can't be below the " + members + " players in the room.";
            return false;
        }
        String custom = seedCustomText.trim();
        Integer seedId = parse(seedIdText);
        switch (settings.seedMode) {
            case RoomState.SEED_TYPE:
                if (settings.seedType == null) {
                    error = "Pick a seed type.";
                    return false;
                }
                break;
            case RoomState.SEED_CUSTOM:
                if (custom.isEmpty() || custom.length() > 32) {
                    error = "Enter a seed (up to 32 characters).";
                    return false;
                }
                break;
            case RoomState.SEED_ID:
                if (seedId == null) {
                    error = "Enter the filtered seed's id.";
                    return false;
                }
                break;
            default:
                break;
        }
        Integer goal;
        switch (settings.goalType) {
            case RoomState.GOAL_TIME:
                goal = parse(goalMinutesText);
                if (goal == null || goal < 1 || goal > 180) {
                    error = "The time limit must be from 1 to 180 minutes.";
                    return false;
                }
                settings.goalMinutes = goal;
                break;
            case RoomState.GOAL_FIRST_PLUS:
                goal = parse(goalSecondsText);
                if (goal == null || goal < 10 || goal > 3600) {
                    error = "The extra time must be from 10 to 3600 seconds.";
                    return false;
                }
                settings.goalSeconds = goal;
                break;
            default:
                goal = parse(goalCountText);
                if (goal == null || goal < 1 || goal > maxPlayers) {
                    error = "Finishers must be from 1 to the room's " + maxPlayers + " max players.";
                    return false;
                }
                settings.goalCount = goal;
                break;
        }
        Integer window = parse(windowText);
        if (window == null || window < 0 || window > 3600) {
            error = "The seed vote window must be from 0 to 3600 seconds.";
            return false;
        }
        settings.name = name;
        settings.maxPlayers = maxPlayers;
        settings.voteSeedWindowSec = window;
        if (!custom.isEmpty()) settings.seedCustom = custom;
        if (seedId != null) settings.seedId = seedId;
        return true;
    }

    private static Integer parse(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // labels

    static String seedModeLabel(String mode) {
        switch (mode) {
            case RoomState.SEED_TYPE: return "Random seed of a type";
            case RoomState.SEED_CUSTOM: return "Custom seed";
            case RoomState.SEED_ID: return "Filtered seed by id";
            default: return "Random filtered seed";
        }
    }

    static String goalLabel(String goal) {
        switch (goal) {
            case RoomState.GOAL_TIME: return "Time limit";
            case RoomState.GOAL_FIRST_PLUS: return "First finish + time";
            default: return "First N finishers";
        }
    }

    private static String onOff(boolean on) {
        return on ? "On" : "Off";
    }

    private static String next(List<String> choices, String current) {
        int index = choices.indexOf(current);
        return choices.get((index + 1) % choices.size());
    }

    // screen

    @Override
    public void removed() {
        this.client.keyboard.enableRepeatEvents(false);
    }

    // Esc does what Close does: it saves
    @Override
    public void onClose() {
        close();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (maxScroll() > 0 && mouseY >= LIST_TOP && mouseY < listBottom) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(amount * ROW_HEIGHT)));
            placeRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        renderTopBar(matrices);
        drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, TITLE_Y, QueueStatus.COLOR_ACCENT);

        for (Row row : rows) {
            if (row.y < LIST_TOP || row.y + 20 > listBottom) continue;
            this.textRenderer.drawWithShadow(matrices, row.label, row.x, row.y + 6,
                    editable ? QueueStatus.COLOR_TEXT : QueueStatus.COLOR_MUTED);
            if (row.control instanceof TextFieldWidget) row.control.render(matrices, mouseX, mouseY, delta);
            if (row.note != null) {
                int noteX = row.control.x + row.control.getWidth() + 6;
                int room = row.x + columnWidth - noteX;
                if (this.textRenderer.getWidth(row.note) <= room) {
                    this.textRenderer.drawWithShadow(matrices, row.note, noteX, row.y + 6, QueueStatus.COLOR_MUTED);
                }
            }
        }
        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            int barX = this.width - LEFT + 4;
            int track = listBottom - LIST_TOP;
            int thumb = Math.max(8, track * track / (track + maxScroll));
            int thumbTop = LIST_TOP + (track - thumb) * scroll / maxScroll;
            fill(matrices, barX, LIST_TOP, barX + 2, listBottom, 0x30FFFFFF);
            fill(matrices, barX, thumbTop, barX + 2, thumbTop + thumb, 0x80FFFFFF);
        }

        if (error != null) {
            drawCenteredString(matrices, this.textRenderer, error, this.width / 2, listBottom + 4, QueueStatus.COLOR_ERROR);
        } else if (!editable) {
            drawCenteredString(matrices, this.textRenderer, "Only the host and co-hosts can change these.",
                    this.width / 2, listBottom + 4, QueueStatus.COLOR_MUTED);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Environment(EnvType.CLIENT)
    static final class BannedPlayersScreen extends TascadeScreen {
        private static final int ROW = 24;
        private static final int FACE_SIZE = 8;
        private static final int UNBAN_WIDTH = 60;
        private static final int LIST_WIDTH = 240;

        private final Screen parent;
        private List<RoomState.Banned> shown = new ArrayList<>();
        private int first = 0;
        private int listLeft;
        private int listWidth;

        BannedPlayersScreen(Screen parent) {
            super(new LiteralText("Banned Players"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            RoomState.Room room = RoomState.room();
            shown = room != null ? room.banned : new ArrayList<>();
            listWidth = Math.min(LIST_WIDTH, this.width - 2 * LEFT);
            listLeft = (this.width - listWidth) / 2;
            int fits = fits();
            first = Math.max(0, Math.min(first, shown.size() - fits));
            for (int i = first; i < shown.size() && i < first + fits; i++) {
                String uuid = shown.get(i).uuid;
                int y = LIST_TOP + (i - first) * ROW;
                this.addButton(new ButtonWidget(listLeft + listWidth - UNBAN_WIDTH, y, UNBAN_WIDTH, 20,
                        new LiteralText("Unban"), buttonWidget -> {
                            TascadeClient.getInstance().unbanFromRoom(uuid);
                            buttonWidget.active = false;
                        }));
            }
            this.addButton(new ButtonWidget(this.width / 2 - BUTTON_WIDTH / 2, this.height - 20 - 10, BUTTON_WIDTH, 20,
                    new LiteralText("Back"), buttonWidget -> onClose()));
        }

        private int fits() {
            return Math.max(1, (this.height - 20 - 10 - 8 - LIST_TOP) / ROW);
        }

        @Override
        public void tick() {
            super.tick();
            RoomState.Room room = RoomState.room();
            if (room == null || !room.amPrivileged()) {
                onClose();
                return;
            }
            if (room.banned != shown) this.init(this.client, this.width, this.height);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            if (shown.size() > fits()) {
                first -= (int) Math.round(amount);
                this.init(this.client, this.width, this.height);
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, amount);
        }

        @Override
        public void onClose() {
            if (this.client != null) this.client.openScreen(parent);
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            this.renderBackground(matrices);
            renderTopBar(matrices);
            drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, TITLE_Y, QueueStatus.COLOR_ACCENT);
            if (shown.isEmpty()) {
                drawCenteredString(matrices, this.textRenderer, "Nobody is banned.", this.width / 2, LIST_TOP + 6,
                        QueueStatus.COLOR_MUTED);
            }
            int fits = fits();
            for (int i = first; i < shown.size() && i < first + fits; i++) {
                RoomState.Banned banned = shown.get(i);
                int y = LIST_TOP + (i - first) * ROW;
                PlayerHeads.draw(matrices, banned.uuid, listLeft, y + 6, FACE_SIZE);
                int nameWidth = listWidth - UNBAN_WIDTH - FACE_SIZE - 12;
                String name = this.textRenderer.getWidth(banned.username) <= nameWidth ? banned.username
                        : this.textRenderer.trimToWidth(banned.username, nameWidth - this.textRenderer.getWidth("...")) + "...";
                this.textRenderer.drawWithShadow(matrices, name, listLeft + FACE_SIZE + 4, y + 6, QueueStatus.COLOR_TEXT);
            }
            if (shown.size() > first + fits) {
                drawCenteredString(matrices, this.textRenderer, "+" + (shown.size() - first - fits) + " more (scroll)",
                        this.width / 2, LIST_TOP + fits * ROW, QueueStatus.COLOR_MUTED);
            }
            super.render(matrices, mouseX, mouseY, delta);
        }
    }
}
