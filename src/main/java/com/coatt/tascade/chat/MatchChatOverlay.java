package com.coatt.tascade.chat;

import com.coatt.tascade.hud.HudScale;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.options.ControlsOptionsScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.Rect2i;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class MatchChatOverlay {
    private static final int MARGIN = 4;
    private static final int GAP = 2;
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 9;
    private static final int INPUT_HEIGHT = 14;
    private static final int FLOATING_WIDTH = 220;
    private static final int DOCKED_WIDTH = 280;
    private static final int FLOATING_LINES = 6;
    private static final int DOCKED_MAX_LINES = 12;
    private static final int MIN_DOCKED_WIDTH = 100;
    private static final int SCROLL_LINES = 3;
    private static final int BACKGROUND = 0x90000000;
    private static final int INPUT_BACKGROUND = 0x60000000;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_MUTED = 0x999999;
    private static final int MAX_LENGTH = 256;

    private enum Surface { NONE, VANILLA, DOCKED, FLOATING }

    private static TextFieldWidget input;
    private static boolean focused = false;
    // applied next frame, once the key's own char has gone by
    private static boolean focusRequested = false;
    private static float originX = 0;
    private static float originY = 0;
    private static float drawnScale = 1f;
    private static int panelWidth = 0;
    private static int panelHeight = 0;
    private static int scroll = 0;
    // text field cursor blinks on ticks, which stop during worldgen, so it's ticked by the clock
    private static long lastCursorTickMs = 0;

    private static List<StringRenderable> wrapped = new ArrayList<>();
    private static int wrappedRevision = -1;
    private static int wrappedWidth = -1;

    private MatchChatOverlay() {}

    public static void render(MinecraftClient client, int belowQueueOverlay, int top) {
        Surface surface = surface(client);
        if (surface == Surface.NONE || surface == Surface.VANILLA) {
            hide();
            return;
        }
        Rect2i dock = surface == Surface.DOCKED ? ((MatchChatDock) client.currentScreen).matchChatArea() : null;

        TextRenderer textRenderer = client.textRenderer;
        boolean canType = MatchChat.isOpen();
        if (!canType) focused = false;

        float scale = HudScale.oneStepSmaller(client);
        int width;
        int maxLines;
        if (dock != null) {
            width = Math.round(dock.getWidth() / scale);
            maxLines = Math.min(DOCKED_MAX_LINES,
                    ((int) (dock.getHeight() / scale) - PADDING * 2 - INPUT_HEIGHT) / LINE_HEIGHT);
        } else {
            width = Math.min(FLOATING_WIDTH, (int) ((client.getWindow().getScaledWidth() - MARGIN * 2) / scale));
            maxLines = FLOATING_LINES;
        }

        List<StringRenderable> lines = wrap(textRenderer, width - PADDING * 2);
        int shown = Math.min(maxLines, lines.size());
        scroll = Math.max(0, Math.min(scroll, lines.size() - shown));
        int height = PADDING * 2 + shown * LINE_HEIGHT + INPUT_HEIGHT;
        int inputTop = height - INPUT_HEIGHT;

        float x = dock != null ? dock.getX() : MARGIN;
        float y = dock != null
                ? dock.getY() + dock.getHeight() - height * scale
                : (belowQueueOverlay > 0 ? belowQueueOverlay + GAP : top + MARGIN);
        originX = HudScale.snapToScreenPixel(client, x);
        originY = HudScale.snapToScreenPixel(client, y);
        drawnScale = scale;
        panelWidth = width;
        panelHeight = height;

        // GL matrix: the text field draws its selection straight to the buffer, ignoring MatrixStack
        RenderSystem.pushMatrix();
        RenderSystem.translatef(originX, originY, 0f);
        RenderSystem.scalef(scale, scale, 1f);
        MatrixStack matrices = new MatrixStack();
        RenderSystem.disableDepthTest();
        DrawableHelper.fill(matrices, 0, 0, width, height, BACKGROUND);
        DrawableHelper.fill(matrices, 0, inputTop, width, height, INPUT_BACKGROUND);

        int first = lines.size() - shown - scroll;
        for (int i = 0; i < shown; i++) {
            textRenderer.drawWithShadow(matrices, lines.get(first + i), PADDING, PADDING + i * LINE_HEIGHT, COLOR_TEXT);
        }

        int textY = inputTop + (INPUT_HEIGHT - 8) / 2;
        if (!canType) {
            textRenderer.drawWithShadow(matrices, "Chat closed", PADDING, textY, COLOR_MUTED);
        } else {
            TextFieldWidget field = input(client);
            if (focusRequested) {
                focusRequested = false;
                focused = true;
            }
            field.setSelected(focused);
            long now = System.currentTimeMillis();
            if (focused && now - lastCursorTickMs >= 50) {
                field.tick();
                lastCursorTickMs = now;
            }
            field.x = PADDING;
            field.y = textY;
            field.setWidth(width - PADDING * 2);
            if (!focused && field.getText().isEmpty()) {
                if (canFocus(client)) {
                    Text key = client.options.keyChat.getBoundKeyLocalizedText();
                    textRenderer.drawWithShadow(matrices, new LiteralText("Press ").append(key).append(" to chat"),
                            field.x, textY, COLOR_MUTED);
                }
            } else {
                field.render(matrices, (int) localX(mouseX(client)), (int) localY(mouseY(client)), 0f);
            }
        }
        RenderSystem.enableDepthTest();
        RenderSystem.popMatrix();
    }

    public static int dockedWidth(MinecraftClient client) {
        return Math.round(DOCKED_WIDTH * HudScale.oneStepSmaller(client));
    }

    public static boolean hidesVanillaChat(MinecraftClient client) {
        return surface(client) == Surface.DOCKED;
    }

    public static boolean onKey(MinecraftClient client, int key, int scancode, int action, int modifiers) {
        if (action == GLFW.GLFW_RELEASE || !isShown() || !MatchChat.isOpen()) return false;
        if (focused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                focused = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                submit(client);
                return true;
            }
            // function keys keep working while typing
            if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) return false;
            input(client).keyPressed(key, scancode, modifiers);
            return true;
        }
        if (action == GLFW.GLFW_PRESS && canFocus(client) && client.options.keyChat.matchesKey(key, scancode)
                && !screenIsTyping(client.currentScreen)) {
            focusRequested = true;
            return true;
        }
        return false;
    }

    public static boolean onChar(MinecraftClient client, int codePoint, int modifiers) {
        if (!focused || !isShown()) return false;
        if (Character.charCount(codePoint) == 1) input(client).charTyped((char) codePoint, modifiers);
        return true;
    }

    public static boolean onMouseClick(MinecraftClient client, double x, double y, int button) {
        if (!isShown() || !canFocus(client)) return false;
        double localX = localX(x);
        double localY = localY(y);
        boolean overPanel = localX >= 0 && localX < panelWidth && localY >= 0 && localY < panelHeight;
        if (MatchChat.isOpen() && overPanel && localY >= panelHeight - INPUT_HEIGHT) {
            focused = true;
            TextFieldWidget field = input(client);
            field.setSelected(true);
            // the field drops focus on clicks outside it, and the strip is wider than the field
            double fieldX = Math.max(field.x, Math.min(localX, field.x + field.getWidth() - 1));
            field.mouseClicked(fieldX, field.y, button);
            return true;
        }
        if (overPanel) return true;
        focused = false;
        return false;
    }

    public static boolean onMouseScroll(MinecraftClient client, double x, double y, double amount) {
        if (!isShown() || !canFocus(client)) return false;
        double localX = localX(x);
        double localY = localY(y);
        if (localX < 0 || localX >= panelWidth || localY < 0 || localY >= panelHeight) return false;
        scroll += (int) Math.signum(amount) * SCROLL_LINES;
        return true;
    }

    private static Surface surface(MinecraftClient client) {
        if (!MatchChat.isShowing() || client.getOverlay() != null) return Surface.NONE;
        Screen screen = client.currentScreen;
        if (screen instanceof MatchChatDock) {
            Rect2i area = ((MatchChatDock) screen).matchChatArea();
            float scale = HudScale.oneStepSmaller(client);
            boolean fits = area != null && area.getWidth() / scale >= MIN_DOCKED_WIDTH
                    && area.getHeight() / scale >= PADDING * 2 + LINE_HEIGHT + INPUT_HEIGHT;
            return fits ? Surface.DOCKED : Surface.FLOATING;
        }
        if (client.world != null && client.isInSingleplayer() && !(screen instanceof DownloadingTerrainScreen)) {
            return Surface.VANILLA;
        }
        return Surface.FLOATING;
    }

    private static boolean canFocus(MinecraftClient client) {
        return client.currentScreen != null;
    }

    private static boolean screenIsTyping(Screen screen) {
        if (screen == null) return false;
        if (screen instanceof ControlsOptionsScreen) return true;
        Element element = screen.getFocused();
        return element instanceof TextFieldWidget && ((TextFieldWidget) element).isFocused();
    }

    private static void submit(MinecraftClient client) {
        TextFieldWidget field = input(client);
        String text = field.getText().trim();
        field.setText("");
        focused = false;
        if (text.isEmpty()) return;
        client.inGameHud.getChatHud().addToMessageHistory(text);
        MatchChat.send(text);
    }

    private static void hide() {
        panelWidth = 0;
        focused = false;
        focusRequested = false;
    }

    private static boolean isShown() {
        return panelWidth > 0;
    }

    private static double localX(double x) {
        return (x - originX) / drawnScale;
    }

    private static double localY(double y) {
        return (y - originY) / drawnScale;
    }

    private static List<StringRenderable> wrap(TextRenderer textRenderer, int width) {
        if (wrappedRevision != MatchChat.revision() || wrappedWidth != width) {
            List<StringRenderable> lines = new ArrayList<>();
            for (Text line : MatchChat.history()) lines.addAll(textRenderer.wrapLines(line, width));
            if (wrappedRevision != MatchChat.revision()) scroll = 0;
            wrapped = lines;
            wrappedRevision = MatchChat.revision();
            wrappedWidth = width;
        }
        return wrapped;
    }

    private static TextFieldWidget input(MinecraftClient client) {
        if (input == null) {
            input = new TextFieldWidget(client.textRenderer, 0, 0, 0, LINE_HEIGHT, new LiteralText(""));
            input.setMaxLength(MAX_LENGTH);
            input.setHasBorder(false);
            input.setEditableColor(COLOR_TEXT);
        }
        return input;
    }

    private static double mouseX(MinecraftClient client) {
        return client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
    }

    private static double mouseY(MinecraftClient client) {
        return client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
    }
}
