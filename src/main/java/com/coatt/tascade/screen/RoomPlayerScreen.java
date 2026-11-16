package com.coatt.tascade.screen;

import com.coatt.tascade.network.RoomState;
import com.coatt.tascade.network.TascadeClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

@Environment(EnvType.CLIENT)
public class RoomPlayerScreen extends TascadeScreen {
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_GAP = 4;
    private static final int FACE_SIZE = 16;

    private final Screen parent;
    private final String uuid;
    private String username = "?";

    private ButtonWidget coHostButton;
    private ButtonWidget ownerButton;
    private ButtonWidget kickButton;
    private ButtonWidget banButton;
    private int top;

    public RoomPlayerScreen(Screen parent, String uuid) {
        super(new LiteralText("Manage player"));
        this.parent = parent;
        this.uuid = uuid;
        RoomState.Room room = RoomState.room();
        RoomState.Member member = room != null ? room.member(uuid) : null;
        if (member != null) username = member.username;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - BUTTON_WIDTH / 2;
        int buttons = 5;
        int height = 32 + buttons * (20 + BUTTON_GAP);
        top = Math.max(30, (this.height - height) / 2);
        int y = top + 32;
        coHostButton = this.addButton(new ButtonWidget(x, y, BUTTON_WIDTH, 20, new LiteralText("Make Co-host"),
                buttonWidget -> toggleCoHost(), this::ownerOnlyTooltip));
        y += 20 + BUTTON_GAP;
        ownerButton = this.addButton(new ButtonWidget(x, y, BUTTON_WIDTH, 20, new LiteralText("Make Host"),
                buttonWidget -> confirmTransfer(), this::ownerOnlyTooltip));
        y += 20 + BUTTON_GAP;
        kickButton = this.addButton(new ButtonWidget(x, y, BUTTON_WIDTH, 20, new LiteralText("Kick"),
                buttonWidget -> kick(false), this::kickTooltip));
        y += 20 + BUTTON_GAP;
        banButton = this.addButton(new ButtonWidget(x, y, BUTTON_WIDTH, 20, new LiteralText("Ban"),
                buttonWidget -> confirmBan(), this::kickTooltip));
        y += 20 + BUTTON_GAP;
        this.addButton(new ButtonWidget(x, y, BUTTON_WIDTH, 20, new LiteralText("Back"), buttonWidget -> onClose()));
        updateButtons();
    }

    @Override
    public void tick() {
        super.tick();
        RoomState.Room room = RoomState.room();
        if (room == null || room.member(uuid) == null || !room.amPrivileged()) {
            onClose();
            return;
        }
        updateButtons();
    }

    private void updateButtons() {
        RoomState.Room room = RoomState.room();
        RoomState.Member member = room != null ? room.member(uuid) : null;
        if (room == null || member == null) return;
        username = member.username;
        boolean owner = room.amOwner();
        coHostButton.setMessage(new LiteralText(member.coHost ? "Remove Co-host" : "Make Co-host"));
        coHostButton.active = owner;
        ownerButton.active = owner;
        boolean kickable = canKick(room, member);
        kickButton.active = kickable;
        banButton.active = kickable;
    }

    private static boolean canKick(RoomState.Room room, RoomState.Member member) {
        if (member.owner) return false;
        if (room.amOwner()) return true;
        return room.amPrivileged() && !member.coHost;
    }

    private void toggleCoHost() {
        RoomState.Room room = RoomState.room();
        RoomState.Member member = room != null ? room.member(uuid) : null;
        if (member != null) TascadeClient.getInstance().setRoomCoHost(uuid, !member.coHost);
    }

    private void confirmTransfer() {
        this.client.openScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) TascadeClient.getInstance().transferRoomOwner(uuid);
            this.client.openScreen(confirmed ? parent : this);
        }, new LiteralText("Make " + username + " the host?"),
                new LiteralText("They get the room, and you stay on as a co-host.")));
    }

    private void confirmBan() {
        this.client.openScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) kick(true);
            else this.client.openScreen(this);
        }, new LiteralText("Ban " + username + " from the room?"),
                new LiteralText("They're kicked and can't join again unless unbanned in the room's settings.")));
    }

    private void kick(boolean ban) {
        TascadeClient.getInstance().kickFromRoom(uuid, ban);
        this.client.openScreen(parent);
    }

    private void ownerOnlyTooltip(ButtonWidget button, MatrixStack matrices, int mouseX, int mouseY) {
        if (button.active) return;
        this.renderTooltip(matrices, new LiteralText("Only the host can do this."), mouseX, mouseY);
    }

    private void kickTooltip(ButtonWidget button, MatrixStack matrices, int mouseX, int mouseY) {
        if (button.active) return;
        RoomState.Room room = RoomState.room();
        RoomState.Member member = room != null ? room.member(uuid) : null;
        String why = member != null && member.owner ? "The host can't be kicked."
                : "Only the host can kick or ban a co-host.";
        this.renderTooltip(matrices, new LiteralText(why), mouseX, mouseY);
    }

    @Override
    public void onClose() {
        if (this.client != null) this.client.openScreen(parent);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        renderTopBar(matrices);
        int nameWidth = this.textRenderer.getWidth(username);
        int rowWidth = FACE_SIZE + 6 + nameWidth;
        int x = this.width / 2 - rowWidth / 2;
        PlayerHeads.draw(matrices, uuid, x, top, FACE_SIZE);
        this.textRenderer.drawWithShadow(matrices, username, x + FACE_SIZE + 6, top + 4, QueueStatus.COLOR_TEXT);

        RoomState.Room room = RoomState.room();
        RoomState.Member member = room != null ? room.member(uuid) : null;
        String role = member == null ? "Not in the room" : member.owner ? "Host" : member.coHost ? "Co-host" : "Player";
        int roleColor = member != null && member.owner ? RoomLobbyScreen.COLOR_HOST
                : member != null && member.coHost ? QueueStatus.COLOR_ACCENT : QueueStatus.COLOR_MUTED;
        drawCenteredString(matrices, this.textRenderer, role, this.width / 2, top + FACE_SIZE + 4, roleColor);
        super.render(matrices, mouseX, mouseY, delta);
    }
}
