package com.coatt.tascade.screen;

import com.coatt.tascade.network.RoomState;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.network.TascadeClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

@Environment(EnvType.CLIENT)
public class JoinRoomScreen extends TascadeScreen {
    private static final int CODE_LENGTH = 6;
    private static final int FIELD_WIDTH = 100;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_GAP = 8;

    private final Screen parent;
    private long shownSinceMs = System.currentTimeMillis();
    private TextFieldWidget codeField;
    private ButtonWidget joinButton;
    private int fieldY;

    public JoinRoomScreen(Screen parent) {
        super(new LiteralText("Join a room"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.client.keyboard.enableRepeatEvents(true);
        String code = codeField != null ? codeField.getText() : "";
        fieldY = this.height / 2 - 20;
        codeField = new TextFieldWidget(this.textRenderer, this.width / 2 - FIELD_WIDTH / 2, fieldY, FIELD_WIDTH, 20,
                new LiteralText("Room code"));
        codeField.setMaxLength(CODE_LENGTH);
        codeField.setText(code);
        codeField.setRenderTextProvider((text, firstCharacter) -> text.toUpperCase(Locale.ROOT));
        this.children.add(codeField);
        this.setInitialFocus(codeField);

        int buttonY = fieldY + 20 + 28;
        int left = this.width / 2 - BUTTON_WIDTH - BUTTON_GAP / 2;
        joinButton = this.addButton(new ButtonWidget(left, buttonY, BUTTON_WIDTH, 20, new LiteralText("Join"),
                buttonWidget -> join(), this::joinTooltip));
        this.addButton(new ButtonWidget(left + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, 20,
                new LiteralText("Cancel"), buttonWidget -> onClose()));
        updateButtons();
    }

    @Override
    public void tick() {
        super.tick();
        codeField.tick();
        if (RoomState.inRoom()) {
            this.client.openScreen(new RoomLobbyScreen(parent));
            return;
        }
        updateButtons();
    }

    private void updateButtons() {
        joinButton.active = !codeField.getText().trim().isEmpty() && TascadeClient.getInstance().isOnline()
                && !TascadeClient.roomRequestPending();
        joinButton.setMessage(new LiteralText(TascadeClient.roomRequestPending() ? "Joining..." : "Join"));
    }

    private void join() {
        updateButtons();
        if (!joinButton.active) return;
        shownSinceMs = System.currentTimeMillis();
        TascadeClient.getInstance().joinRoom(codeField.getText());
    }

    private void joinTooltip(ButtonWidget button, MatrixStack matrices, int mouseX, int mouseY) {
        if (button.active || TascadeClient.getInstance().isOnline()) return;
        this.renderTooltip(matrices, new LiteralText(RunState.connected ? "Signing in to Tascade..." : "Reconnecting to Tascade..."),
                mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            join();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        this.client.keyboard.enableRepeatEvents(false);
    }

    @Override
    public void onClose() {
        if (this.client != null) this.client.openScreen(parent);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        renderTopBar(matrices);
        drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, fieldY - 30, QueueStatus.COLOR_ACCENT);
        drawCenteredString(matrices, this.textRenderer, "Enter the " + CODE_LENGTH + "-character room code",
                this.width / 2, fieldY - 16, QueueStatus.COLOR_MUTED);
        codeField.render(matrices, mouseX, mouseY, delta);

        String error = RoomState.lastError;
        if (error != null && RoomState.lastErrorAtMs >= shownSinceMs) {
            drawCenteredString(matrices, this.textRenderer, error, this.width / 2, fieldY + 20 + 10, QueueStatus.COLOR_ERROR);
        } else if (TascadeClient.roomRequestPending()) {
            drawCenteredString(matrices, this.textRenderer, "Joining...", this.width / 2, fieldY + 20 + 10,
                    QueueStatus.COLOR_MUTED);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }
}
