package com.coatt.tascade.screen;

import com.coatt.tascade.TascadeConfig;
import com.coatt.tascade.chat.MatchChatDock;
import com.coatt.tascade.chat.MatchChatOverlay;
import com.coatt.tascade.hud.HudScale;
import com.coatt.tascade.hud.RunLabels;
import com.coatt.tascade.modifier.RunModifier;
import com.coatt.tascade.modifier.RunModifiers;
import com.coatt.tascade.network.RoomState;
import com.coatt.tascade.network.TascadeClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.Rect2i;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringRenderable;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class RoomLobbyScreen extends TascadeScreen implements MatchChatDock {
    private static final int LEFT = 14;
    private static final int TOP = 30;
    private static final int GAP = 10;
    private static final int BUTTON_GAP = 4;
    private static final int BUTTON_PADDING = 12;
    private static final int ROW_HEIGHT = 12;
    private static final int FACE_SIZE = 8;
    private static final int MOD_ICON_SIZE = 10;
    private static final int MOD_ICON_GAP = 2;
    private static final int MIN_NAME_WIDTH = 50;
    private static final String[] STATUSES = {"Ready", "Unready", "Racing", "Finished", "Gave up", "Offline", "Waiting"};
    private static final int PANEL_PADDING = 4;
    private static final int PANEL_BACKGROUND = 0x90000000;
    private static final int HOVER_COLOR = 0x30FFFFFF;
    private static final int MIN_CHAT_HEIGHT = 48;
    private static final int MIN_INFO_WIDTH = 170;
    private static final int MAX_INFO_WIDTH = 230;
    private static final int MIN_MEMBERS_WIDTH = 160;
    private static final int RESULTS_SHOWN = 3;
    private static final long ERROR_SHOWN_MS = 8_000;
    private static final long COPIED_SHOWN_MS = 2_000;

    static final int COLOR_HOST = 0xFFAA00;
    static final int COLOR_GOOD = 0x55FF55;
    static final int COLOR_RACING = 0xFFFF55;
    static final int COLOR_BAD = 0xFF5555;

    private final Screen parent;

    private int contentBottom;
    private int infoX;
    private int infoWidth;
    private int membersX;
    private int membersWidth;
    private int chatColumnX;
    private Rect2i chatArea = new Rect2i(0, 0, 0, 0);

    private ButtonWidget copyButton;
    private ButtonWidget hideButton;
    private ButtonWidget readyButton;
    private ButtonWidget modifiersButton;
    private int buttonsTop;
    private ButtonWidget startButton;
    private boolean builtReadyCheck;
    private boolean builtPrivileged;
    private boolean builtInRoom;
    private int builtStripHeight;
    private long copiedAtMs = 0;
    private int memberScroll = 0;

    public RoomLobbyScreen(Screen parent) {
        super(new LiteralText("Room"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        RoomState.Room room = RoomState.room();
        builtInRoom = room != null;
        builtReadyCheck = room != null && room.settings.readyCheck;
        builtPrivileged = room != null && room.amPrivileged();
        if (room != null) syncModifiers(room);

        List<ButtonWidget> bottom = new ArrayList<>();
        bottom.add(button(new String[] {"Back"}, buttonWidget -> onClose()));
        bottom.add(button(new String[] {"Leave Room"}, buttonWidget -> leave()));
        // the modifiers screen reports changed picks by reopening this (init)
        modifiersButton = button(new String[] {"Run Modifiers"},
                buttonWidget -> this.client.openScreen(new RunModifiersScreen(this)));
        bottom.add(modifiersButton);
        if (room != null) {
            String rules = builtPrivileged ? "Room Settings" : "View Rules";
            bottom.add(button(new String[] {rules},
                    buttonWidget -> this.client.openScreen(new RoomSettingsScreen(this, builtPrivileged))));
        }
        readyButton = null;
        startButton = null;
        if (builtReadyCheck) {
            readyButton = button(new String[] {"Ready", "Not Ready"}, buttonWidget -> toggleReady());
            bottom.add(readyButton);
        }
        if (builtPrivileged) {
            startButton = button(new String[] {"Start Race", "Force Start"},
                    buttonWidget -> TascadeClient.getInstance().startRoomRace(), this::startTooltip);
            bottom.add(startButton);
        }
        int rows = layoutButtons(bottom);
        buttonsTop = this.height - 10 - rows * 20 - (rows - 1) * BUTTON_GAP;
        builtStripHeight = RunModifiersScreen.pickedStripHeight(room != null ? room.settings.modifiers : null);
        contentBottom = buttonsTop - 8 - builtStripHeight;

        int available = this.width - 2 * LEFT;
        int chatWidth = MatchChatOverlay.dockedWidth(this.client);
        if (available >= MIN_INFO_WIDTH + MIN_MEMBERS_WIDTH + chatWidth + 2 * GAP) {
            int rest = available - chatWidth - 2 * GAP;
            infoWidth = Math.min(MAX_INFO_WIDTH, rest / 2);
            membersWidth = rest - infoWidth;
            chatColumnX = this.width - LEFT - chatWidth;
        } else {
            infoWidth = Math.min(MAX_INFO_WIDTH, (available - GAP) / 2);
            membersWidth = available - GAP - infoWidth;
            chatColumnX = 0;
        }
        infoX = LEFT;
        membersX = infoX + infoWidth + GAP;
        if (chatColumnX > 0) chatArea = new Rect2i(chatColumnX, TOP, this.width - LEFT - chatColumnX, contentBottom - TOP);

        String code = room != null ? RoomState.shownCode(room.code) : "";
        int codeRight = infoX + 2 * this.textRenderer.getWidth(code) + 8;
        copyButton = this.addButton(new ButtonWidget(codeRight, TOP + 10 - 2,
                this.textRenderer.getWidth("Copy Code") + BUTTON_PADDING, 20, new LiteralText("Copy Code"),
                buttonWidget -> {
                    RoomState.Room current = RoomState.room();
                    if (current == null) return;
                    this.client.keyboard.setClipboard(current.code);
                    copiedAtMs = System.currentTimeMillis();
                }));
        copyButton.visible = room != null;
        String hideLabel = TascadeConfig.hideRoomCode() ? "Show" : "Hide";
        hideButton = this.addButton(new ButtonWidget(copyButton.x + copyButton.getWidth() + 4, TOP + 10 - 2,
                Math.max(this.textRenderer.getWidth("Show"), this.textRenderer.getWidth("Hide")) + BUTTON_PADDING, 20,
                new LiteralText(hideLabel), buttonWidget -> {
                    TascadeConfig.setHideRoomCode(!TascadeConfig.hideRoomCode());
                    this.init(this.client, this.width, this.height);
                }, (button, matrices, mouseX, mouseY) -> this.renderTooltip(matrices, new LiteralText(
                        TascadeConfig.hideRoomCode() ? "Show the room code" : "Hide the room code, e.g. while streaming"),
                        mouseX, mouseY)));
        hideButton.visible = room != null;
        updateButtons();
    }

    private ButtonWidget button(String[] labels, ButtonWidget.PressAction onPress) {
        return button(labels, onPress, ButtonWidget.EMPTY);
    }

    private ButtonWidget button(String[] labels, ButtonWidget.PressAction onPress, ButtonWidget.TooltipSupplier tooltip) {
        int width = 0;
        for (String label : labels) width = Math.max(width, this.textRenderer.getWidth(label));
        return new ButtonWidget(0, 0, Math.max(50, width + BUTTON_PADDING), 20, new LiteralText(labels[0]), onPress, tooltip);
    }

    private int layoutButtons(List<ButtonWidget> buttons) {
        List<List<ButtonWidget>> rows = new ArrayList<>();
        List<ButtonWidget> row = new ArrayList<>();
        int x = LEFT;
        for (ButtonWidget button : buttons) {
            if (!row.isEmpty() && x + button.getWidth() > this.width - LEFT) {
                rows.add(row);
                row = new ArrayList<>();
                x = LEFT;
            }
            row.add(button);
            x += button.getWidth() + BUTTON_GAP;
        }
        if (!row.isEmpty()) rows.add(row);
        for (int i = 0; i < rows.size(); i++) {
            int y = this.height - 10 - 20 - (rows.size() - 1 - i) * (20 + BUTTON_GAP);
            x = LEFT;
            for (ButtonWidget button : rows.get(i)) {
                button.x = x;
                button.y = y;
                x += button.getWidth() + BUTTON_GAP;
                this.addButton(button);
            }
        }
        return Math.max(1, rows.size());
    }

    private static void syncModifiers(RoomState.Room room) {
        RoomState.Member me = room.me();
        if (me == null) return;
        if (!new HashSet<>(RunModifiers.selectedIds()).equals(new HashSet<>(me.modifiers))) {
            TascadeClient.getInstance().sendRoomModifiers();
        }
    }

    @Override
    public void tick() {
        super.tick();
        RoomState.Room room = RoomState.room();
        if (room == null) {
            left();
            return;
        }
        if (!builtInRoom || room.settings.readyCheck != builtReadyCheck || room.amPrivileged() != builtPrivileged
                || RunModifiersScreen.pickedStripHeight(room.settings.modifiers) != builtStripHeight) {
            this.init(this.client, this.width, this.height);
            return;
        }
        updateButtons();
    }

    private void updateButtons() {
        RoomState.Room room = RoomState.room();
        if (room == null) return;
        boolean lobby = RoomState.PHASE_LOBBY.equals(room.phase);
        boolean online = TascadeClient.getInstance().isOnline();
        RoomState.Member me = room.me();
        if (readyButton != null) {
            boolean ready = me != null && me.ready;
            readyButton.setMessage(new LiteralText(ready ? "Not Ready" : "Ready"));
            readyButton.active = online && me != null && !room.racing();
        }
        if (startButton != null) {
            startButton.setMessage(new LiteralText(room.settings.readyCheck && !everyoneReady(room) ? "Force Start" : "Start Race"));
            startButton.active = online && lobby;
        }
    }

    private static boolean everyoneReady(RoomState.Room room) {
        return !room.members.isEmpty() && readyCount(room) == room.members.size();
    }

    private static int readyCount(RoomState.Room room) {
        int ready = 0;
        for (RoomState.Member member : room.members) {
            if (member.ready) ready++;
        }
        return ready;
    }

    private void toggleReady() {
        RoomState.Room room = RoomState.room();
        RoomState.Member me = room != null ? room.me() : null;
        if (me != null) TascadeClient.getInstance().setRoomReady(!me.ready);
    }

    private void startTooltip(ButtonWidget button, MatrixStack matrices, int mouseX, int mouseY) {
        RoomState.Room room = RoomState.room();
        if (button.active || room == null) return;
        String why = !TascadeClient.getInstance().isOnline() ? "Reconnecting to Tascade..."
                : room.racing() ? "A race is already running." : "The race is about to start.";
        this.renderTooltip(matrices, new LiteralText(why), mouseX, mouseY);
    }

    private void leave() {
        TascadeClient.getInstance().leaveRoom();
        this.client.openScreen(parent);
    }

    private void left() {
        this.client.openScreen(removedNotice(parent));
    }

    static Screen removedNotice(Screen next) {
        String reason = RoomState.removedReason;
        if (reason == null) return next;
        RoomState.removedReason = null;
        RunNoticeScreen notice = RunNoticeScreen.removedFromRoom(next, reason);
        return notice != null ? notice : next;
    }

    @Override
    public void onClose() {
        if (this.client != null) this.client.openScreen(parent);
    }

    // drawing

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        renderTopBar(matrices);
        RoomState.Room room = RoomState.room();
        if (room == null) {
            List<StringRenderable> pickedTooltip = renderPickedStrip(matrices, mouseX, mouseY);
            super.render(matrices, mouseX, mouseY, delta);
            if (pickedTooltip != null) this.renderTooltip(matrices, pickedTooltip, mouseX, mouseY);
            return;
        }
        int infoBottom = renderInfo(matrices, room);
        if (chatColumnX == 0) {
            int top = infoBottom + 4;
            chatArea = new Rect2i(infoX, top, infoWidth, Math.max(0, contentBottom - top));
        }
        List<StringRenderable> tooltip = renderMembers(matrices, room, mouseX, mouseY);
        List<StringRenderable> pickedTooltip = renderPickedStrip(matrices, mouseX, mouseY);
        if (pickedTooltip != null) tooltip = pickedTooltip;
        super.render(matrices, mouseX, mouseY, delta);
        if (tooltip != null) this.renderTooltip(matrices, tooltip, mouseX, mouseY);
    }

    private List<StringRenderable> renderPickedStrip(MatrixStack matrices, int mouseX, int mouseY) {
        RoomState.Room room = RoomState.room();
        return RunModifiersScreen.renderPickedStrip(matrices, this.client, this.width,
                modifiersButton.x + modifiersButton.getWidth() / 2, buttonsTop, mouseX, mouseY,
                room != null ? room.settings.modifiers : null);
    }

    private int renderInfo(MatrixStack matrices, RoomState.Room room) {
        int y = TOP;
        boolean copied = System.currentTimeMillis() - copiedAtMs < COPIED_SHOWN_MS;
        copyButton.setMessage(new LiteralText(copied ? "Copied!" : "Copy Code"));
        this.textRenderer.drawWithShadow(matrices, trim(room.settings.name, infoWidth), infoX, y - 1, QueueStatus.COLOR_ACCENT);
        y += 10;
        matrices.push();
        matrices.scale(2f, 2f, 1f);
        this.textRenderer.drawWithShadow(matrices, RoomState.shownCode(room.code), infoX / 2f, (y + 2) / 2f, QueueStatus.COLOR_TEXT);
        matrices.pop();
        y += 24;

        y = line(matrices, status(room), y, statusColor(room));
        String error = RoomState.lastError;
        if (error != null && System.currentTimeMillis() - RoomState.lastErrorAtMs < ERROR_SHOWN_MS) {
            y = line(matrices, error, y, QueueStatus.COLOR_ERROR);
        }

        int small = smallLineHeight();
        List<String> rules = rulesSummary(room.settings);
        List<RoomState.Standing> results = room.lastResults;
        int resultLines = results.isEmpty() ? 0 : 1 + Math.min(RESULTS_SHOWN, results.size());
        int detailsBottom = chatColumnX == 0 ? contentBottom - MIN_CHAT_HEIGHT - 4 : contentBottom;
        int smallWidth = (int) (infoWidth / HudScale.oneStepSmaller(this.client));
        if (resultLines > 0 && detailsBottom - y - 4 >= 2 * small) {
            int shown = Math.min(resultLines, (detailsBottom - y - 4) / small);
            y += 4;
            drawSmall(matrices, "Last race", infoX, y, QueueStatus.COLOR_ACCENT);
            y += small;
            for (int i = 0; i < shown - 1; i++) {
                RoomState.Standing standing = results.get(i);
                String place = standing.place != null ? standing.place + "." : "-";
                String time = standing.rtaMs != null ? RunLabels.time(standing.rtaMs) : "DNF";
                drawSmall(matrices, trim(place + " " + standing.username + "  " + time, smallWidth), infoX, y,
                        standing.place != null && standing.place == 1 ? COLOR_HOST : QueueStatus.COLOR_TEXT);
                y += small;
            }
        }
        int rulesShown = Math.max(0, Math.min(rules.size(), (detailsBottom - y - 4) / small));
        y += 4;
        for (int i = 0; i < rulesShown; i++) {
            drawSmall(matrices, trim(rules.get(i), smallWidth), infoX, y, QueueStatus.COLOR_MUTED);
            y += small;
        }
        return y;
    }

    private static String status(RoomState.Room room) {
        int countdown = RoomState.lobbyCountdown >= 0 ? RoomState.lobbyCountdown
                : RoomState.PHASE_COUNTDOWN.equals(room.phase) ? room.countdown : -1;
        if (countdown >= 0) return "Starting in " + countdown + "...";
        if (room.racing()) {
            int racing = 0;
            for (RoomState.Member member : room.members) {
                if (RoomState.STATUS_RACING.equals(member.status)) racing++;
            }
            return "Race in progress - " + racing + " racing";
        }
        if (room.settings.readyCheck) {
            return "Waiting for everyone to be ready (" + readyCount(room) + "/" + room.members.size() + ")";
        }
        return room.amPrivileged() ? "Start the race when everyone's here" : "Waiting for the host to start";
    }

    private static int statusColor(RoomState.Room room) {
        if (RoomState.lobbyCountdown >= 0 || RoomState.PHASE_COUNTDOWN.equals(room.phase)) return COLOR_GOOD;
        return room.racing() ? COLOR_RACING : QueueStatus.COLOR_TEXT;
    }

    static List<String> rulesSummary(RoomState.Settings settings) {
        List<String> lines = new ArrayList<>();
        lines.add(settings.seedLabel());
        lines.add(settings.goalLabel());
        lines.add("Ready check " + (settings.readyCheck ? "on" : "off") + " · Cheats " + (settings.cheats ? "on" : "off"));
        String seedVote = RoomState.VOTE_OFF.equals(settings.voteSeed) ? "off"
                : voteLabel(settings.voteSeed).toLowerCase(java.util.Locale.ROOT)
                + (settings.voteSeedWindowSec > 0 ? " (first " + RoomState.durationLabel(settings.voteSeedWindowSec) + ")" : "");
        lines.add("Seed vote: " + seedVote);
        lines.add("End vote: " + voteLabel(settings.voteEnd).toLowerCase(java.util.Locale.ROOT));
        lines.add(modifierRulesLabel(settings));
        lines.add("Up to " + settings.maxPlayers + (settings.maxPlayers == 1 ? " player" : " players"));
        return lines;
    }

    static String voteLabel(String rule) {
        if (RoomState.VOTE_MAJORITY.equals(rule)) return "Majority";
        if (RoomState.VOTE_UNANIMOUS.equals(rule)) return "Unanimous";
        return "Off";
    }

    static String modifierRulesLabel(RoomState.Settings settings) {
        List<String> forced = new ArrayList<>();
        int disallowed = 0;
        for (Map.Entry<String, String> rule : settings.modifiers.entrySet()) {
            if (RoomState.MOD_FORCE.equals(rule.getValue())) {
                RunModifier modifier = RunModifiers.byId(rule.getKey());
                forced.add(modifier != null ? modifier.name : rule.getKey());
            } else if (RoomState.MOD_DISALLOW.equals(rule.getValue())) {
                disallowed++;
            }
        }
        if (forced.isEmpty() && disallowed == 0) return "All modifiers allowed";
        StringBuilder label = new StringBuilder();
        if (!forced.isEmpty()) {
            label.append(forced.size() <= 2 ? "Forced: " + String.join(", ", forced) : forced.size() + " forced");
        }
        if (disallowed > 0) {
            if (label.length() > 0) label.append(" · ");
            label.append(disallowed).append(" disallowed");
        }
        return label.toString();
    }

    private List<StringRenderable> renderMembers(MatrixStack matrices, RoomState.Room room, int mouseX, int mouseY) {
        int left = membersX;
        int right = membersX + membersWidth;
        fill(matrices, left, TOP, right, contentBottom, PANEL_BACKGROUND);
        String heading = "Players (" + room.members.size() + "/" + room.settings.maxPlayers + ")";
        this.textRenderer.drawWithShadow(matrices, heading, left + PANEL_PADDING, TOP + PANEL_PADDING, QueueStatus.COLOR_ACCENT);

        int listTop = listTop();
        int shownRows = shownRows();
        List<RoomState.Member> members = room.members;
        memberScroll = Math.max(0, Math.min(memberScroll, members.size() - shownRows));
        boolean scrolls = members.size() > shownRows;
        int rowRight = right - PANEL_PADDING - (scrolls ? 4 : 0);
        if (scrolls) {
            int barX = right - PANEL_PADDING - 2;
            int track = shownRows * ROW_HEIGHT;
            int thumb = Math.max(8, track * shownRows / members.size());
            int thumbTop = listTop + (track - thumb) * memberScroll / (members.size() - shownRows);
            fill(matrices, barX, listTop, barX + 2, listTop + track, 0x30FFFFFF);
            fill(matrices, barX, thumbTop, barX + 2, thumbTop + thumb, 0x80FFFFFF);
        }

        boolean privileged = room.amPrivileged();
        String me = TascadeClient.getInstance().getPlayerUuid();
        List<StringRenderable> tooltip = null;
        float scale = HudScale.oneStepSmaller(this.client);
        int statusColumn = statusColumnWidth(room);
        for (int i = memberScroll; i < members.size() && i < memberScroll + shownRows; i++) {
            RoomState.Member member = members.get(i);
            int y = listTop + (i - memberScroll) * ROW_HEIGHT;
            boolean mine = member.uuid.equals(me);
            boolean hovered = mouseX >= left && mouseX < rowRight && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2;
            if (hovered && privileged && !mine) {
                fill(matrices, left + 2, y - 2, rowRight + 2, y + ROW_HEIGHT - 2, HOVER_COLOR);
                tooltip = lines("Click to manage " + member.username);
            }

            String status = memberStatus(room, member);
            if (status != null) {
                this.textRenderer.drawWithShadow(matrices, status, rowRight - this.textRenderer.getWidth(status), y,
                        memberStatusColor(member));
            }
            int x = statusColumn > 0 ? rowRight - statusColumn - 6 : rowRight;
            List<String> modifiers = RunModifiers.inRoom(member.modifiers, room.settings.modifiers);
            if (!modifiers.isEmpty()) {
                int iconRoom = Math.max(0, x - (left + PANEL_PADDING + FACE_SIZE + 4) - MIN_NAME_WIDTH);
                List<StringRenderable> iconTooltip = renderModifierIcons(matrices, modifiers, room.settings.modifiers,
                        x, y, iconRoom, mouseX, mouseY);
                if (iconTooltip != null) tooltip = iconTooltip;
                x -= modifierIconsWidth(modifiers, iconRoom) + 4;
            }

            int nameX = left + PANEL_PADDING + FACE_SIZE + 4;
            PlayerHeads.draw(matrices, member.uuid, left + PANEL_PADDING, y - 1, FACE_SIZE);
            String badge = member.owner ? "Host" : member.coHost ? "Co-host" : null;
            int badgeWidth = badge != null ? Math.round(this.textRenderer.getWidth(badge) * scale) + 4 : 0;
            String name = trim(member.username, Math.max(10, x - nameX - badgeWidth));
            int nameColor = !member.connected ? QueueStatus.COLOR_MUTED : mine ? QueueStatus.COLOR_ACCENT : QueueStatus.COLOR_TEXT;
            this.textRenderer.drawWithShadow(matrices, name, nameX, y, nameColor);
            if (badge != null) {
                drawSmall(matrices, badge, nameX + this.textRenderer.getWidth(name) + 4, y + 1,
                        member.owner ? COLOR_HOST : QueueStatus.COLOR_ACCENT);
            }
        }
        return tooltip;
    }

    private static String memberStatus(RoomState.Room room, RoomState.Member member) {
        if (RoomState.STATUS_FINISHED.equals(member.status)) return "Finished";
        if (RoomState.STATUS_DNF.equals(member.status)) return "Gave up";
        if (!member.connected) return "Offline";
        if (RoomState.STATUS_RACING.equals(member.status)) return "Racing";
        if (room.racing()) return "Waiting";
        if (room.settings.readyCheck) return member.ready ? "Ready" : "Unready";
        return null;
    }

    private static int memberStatusColor(RoomState.Member member) {
        if (RoomState.STATUS_FINISHED.equals(member.status)) return COLOR_GOOD;
        if (RoomState.STATUS_DNF.equals(member.status)) return COLOR_BAD;
        if (!member.connected) return QueueStatus.COLOR_MUTED;
        if (RoomState.STATUS_RACING.equals(member.status)) return COLOR_RACING;
        if (member.ready) return COLOR_GOOD;
        return QueueStatus.COLOR_MUTED;
    }

    private int statusColumnWidth(RoomState.Room room) {
        int width = 0;
        if (room.settings.readyCheck) {
            for (String status : STATUSES) width = Math.max(width, this.textRenderer.getWidth(status));
            return width;
        }
        for (RoomState.Member member : room.members) {
            String status = memberStatus(room, member);
            if (status != null) width = Math.max(width, this.textRenderer.getWidth(status));
        }
        return width;
    }

    private int iconsShown(List<String> ids, int room) {
        int count = ids.size();
        int all = count * (MOD_ICON_SIZE + MOD_ICON_GAP);
        if (all <= room) return count;
        int plus = this.textRenderer.getWidth("+" + count) + MOD_ICON_GAP;
        return Math.max(0, (room - plus) / (MOD_ICON_SIZE + MOD_ICON_GAP));
    }

    private int modifierIconsWidth(List<String> ids, int room) {
        int shown = iconsShown(ids, room);
        int width = shown * (MOD_ICON_SIZE + MOD_ICON_GAP);
        int hidden = ids.size() - shown;
        if (hidden > 0) width += this.textRenderer.getWidth("+" + hidden) + MOD_ICON_GAP;
        return Math.max(0, width - MOD_ICON_GAP);
    }

    private List<StringRenderable> renderModifierIcons(MatrixStack matrices, List<String> ids, Map<String, String> rules,
                                                       int right, int y, int room, int mouseX, int mouseY) {
        int shown = iconsShown(ids, room);
        int x = right - modifierIconsWidth(ids, room);
        int iconY = y + (8 - MOD_ICON_SIZE) / 2;
        List<StringRenderable> tooltip = null;
        float iconScale = MOD_ICON_SIZE / 16f;
        for (int i = 0; i < shown; i++) {
            RunModifier modifier = RunModifiers.byId(ids.get(i));
            if (modifier != null) {
                RenderSystem.pushMatrix();
                RenderSystem.translatef(x, iconY, 0);
                RenderSystem.scalef(iconScale, iconScale, 1f);
                this.itemRenderer.renderInGui(modifier.icon(), 0, 0);
                RenderSystem.popMatrix();
            } else {
                drawSmall(matrices, "?", x + 2, y + 1, QueueStatus.COLOR_MUTED);
            }
            if (mouseX >= x && mouseX < x + MOD_ICON_SIZE && mouseY >= iconY && mouseY < iconY + MOD_ICON_SIZE) {
                tooltip = modifierTooltip(modifier, ids.get(i));
                if (RunModifiersScreen.forcedIn(rules, ids.get(i))) tooltip.add(RunModifiersScreen.forcedNote());
            }
            x += MOD_ICON_SIZE + MOD_ICON_GAP;
        }
        int hidden = ids.size() - shown;
        if (hidden > 0) {
            String plus = "+" + hidden;
            this.textRenderer.drawWithShadow(matrices, plus, x, y, QueueStatus.COLOR_MUTED);
            if (mouseX >= x && mouseX < x + this.textRenderer.getWidth(plus) && mouseY >= y - 1 && mouseY < y + 9) {
                List<StringRenderable> lines = new ArrayList<>();
                for (String id : ids.subList(shown, ids.size())) {
                    RunModifier modifier = RunModifiers.byId(id);
                    MutableText line = new LiteralText(modifier != null ? modifier.name : id);
                    if (RunModifiersScreen.forcedIn(rules, id)) line.append(new LiteralText(" (forced)").formatted(Formatting.GOLD));
                    lines.add(line);
                }
                tooltip = lines;
            }
        }
        return tooltip;
    }

    private static List<StringRenderable> modifierTooltip(RunModifier modifier, String id) {
        List<StringRenderable> lines = new ArrayList<>();
        if (modifier == null) {
            lines.add(new LiteralText(id));
            return lines;
        }
        lines.add(new LiteralText(modifier.name));
        lines.add(new LiteralText(modifier.description).formatted(Formatting.GRAY));
        return lines;
    }

    private static List<StringRenderable> lines(String text) {
        List<StringRenderable> lines = new ArrayList<>();
        lines.add(new LiteralText(text));
        return lines;
    }

    private int listTop() {
        return TOP + PANEL_PADDING + 14;
    }

    private int shownRows() {
        return Math.max(1, (contentBottom - listTop()) / ROW_HEIGHT);
    }

    @Override
    public Rect2i matchChatArea() {
        return chatArea;
    }

    // input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        RoomState.Room room = RoomState.room();
        if (button != 0 || room == null || !room.amPrivileged()) return false;
        RoomState.Member member = memberAt(room, mouseX, mouseY);
        if (member == null || member.uuid.equals(TascadeClient.getInstance().getPlayerUuid())) return false;
        this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        this.client.openScreen(new RoomPlayerScreen(this, member.uuid));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX >= membersX && mouseX < membersX + membersWidth && mouseY >= TOP && mouseY < contentBottom) {
            memberScroll -= (int) Math.round(amount * 3);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private RoomState.Member memberAt(RoomState.Room room, double mouseX, double mouseY) {
        if (mouseX < membersX || mouseX >= membersX + membersWidth) return null;
        int offset = (int) Math.floor(mouseY) - listTop() + 2;
        if (offset < 0) return null;
        int row = offset / ROW_HEIGHT;
        if (row >= shownRows()) return null;
        int index = memberScroll + row;
        return index < room.members.size() ? room.members.get(index) : null;
    }

    // helpers

    private int line(MatrixStack matrices, String text, int y, int color) {
        this.textRenderer.drawWithShadow(matrices, trim(text, infoWidth), infoX, y, color);
        return y + ROW_HEIGHT;
    }

    private int smallLineHeight() {
        return (int) Math.ceil(9 * HudScale.oneStepSmaller(this.client)) + 1;
    }

    private String trim(String text, int width) {
        if (this.textRenderer.getWidth(text) <= width) return text;
        return this.textRenderer.trimToWidth(text, Math.max(0, width - this.textRenderer.getWidth("..."))) + "...";
    }

    private void drawSmall(MatrixStack matrices, String text, int x, int y, int color) {
        float scale = HudScale.oneStepSmaller(this.client);
        matrices.push();
        matrices.translate(HudScale.snapToScreenPixel(this.client, x), HudScale.snapToScreenPixel(this.client, y), 0);
        matrices.scale(scale, scale, 1f);
        this.textRenderer.drawWithShadow(matrices, text, 0, 0, color);
        matrices.pop();
    }
}
