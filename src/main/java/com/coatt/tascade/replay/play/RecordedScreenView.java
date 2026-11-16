package com.coatt.tascade.replay.play;

import com.coatt.tascade.mixin.access.HandledScreenAccessor;
import com.coatt.tascade.mixin.access.ScreenHandlerAccessor;
import com.coatt.tascade.replay.RecordCodec;
import com.coatt.tascade.replay.ReplayScreens;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.recipe.book.RecipeBook;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

final class RecordedScreenView {
    private static boolean capturing;
    private static Screen captured;

    private final MinecraftClient client;
    private Screen screen;
    private int serial = -1;
    private Tracks.ScreenState filled;

    RecordedScreenView(MinecraftClient client) {
        this.client = client;
    }

    static boolean capture(Screen screen) {
        if (!capturing) return false;
        captured = screen;
        return true;
    }

    void reset() {
        screen = null;
        serial = -1;
        filled = null;
    }

    void render(MatrixStack matrices, Tracks tracks, double time, float delta) {
        Tracks.ScreenState state = tracks.screens.at((int) time);
        if (state == null || state.info.kind == RecordCodec.SCREEN_NONE) {
            reset();
            return;
        }
        if (state.serial != serial) {
            serial = state.serial;
            filled = null;
            screen = build(state.info);
        }

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int recordedWidth = state.info.scaledWidth > 0 ? state.info.scaledWidth : width;
        int recordedHeight = state.info.scaledHeight > 0 ? state.info.scaledHeight : height;
        float scale = Math.min((float) width / recordedWidth, (float) height / recordedHeight);
        float offsetX = (width - recordedWidth * scale) / 2f;
        float offsetY = (height - recordedHeight * scale) / 2f;

        float[] cursor = tracks.cursor.at((int) time);
        int cursorIndex = tracks.cursor.indexAt((int) time);
        boolean cursorKnown = cursor != null && tracks.cursor.time(cursorIndex) >= state.openedAt - 100;
        int mouseX = cursorKnown ? Math.round(cursor[0]) : recordedWidth / 2;
        int mouseY = cursorKnown ? Math.round(cursor[1]) : recordedHeight / 2;

        RenderSystem.pushMatrix();
        RenderSystem.translatef(offsetX, offsetY, 0);
        RenderSystem.scalef(scale, scale, 1);
        try {
            if (screen instanceof HandledScreen) {
                HandledScreen<?> handled = (HandledScreen<?>) screen;
                fill(handled.getScreenHandler(), state);
                if (state != filled) {
                    filled = state;
                    if (screen instanceof RecipeBookProvider) ((RecipeBookProvider) screen).refreshRecipeBook();
                }
                screen.render(matrices, mouseX, mouseY, delta);
                drawClicks(matrices, handled, tracks, time, state.openedAt);
            } else if (screen != null) {
                screen.render(matrices, mouseX, mouseY, delta);
            } else {
                drawLabel(matrices, state.info, recordedWidth, recordedHeight);
            }
            if (cursorKnown) drawCursor(matrices, mouseX, mouseY);
        } catch (RuntimeException e) {
            screen = null;
        } finally {
            RenderSystem.popMatrix();
        }
    }

    private Screen build(RecordCodec.ScreenInfo info) {
        if (!(client.player instanceof CameraEntity)) return null;
        CameraEntity camera = (CameraEntity) client.player;
        Screen built = null;
        try {
            if (info.kind == RecordCodec.SCREEN_HANDLED) {
                applyRecipeBook(camera.getRecipeBook(), info.recipeBook);
                if (ReplayScreens.INVENTORY.equals(info.type)) {
                    built = new InventoryScreen(camera);
                } else if (info.type.contains(":")) {
                    ScreenHandlerType<?> type = Registry.SCREEN_HANDLER.get(new Identifier(info.type));
                    if (type != null) built = openCaptured(type, info.title != null ? info.title : LiteralText.EMPTY);
                }
                if (built != null) {
                    built.init(client, info.scaledWidth, info.scaledHeight);
                    // moving a recipe-book screen afterwards would leave the book's button behind
                    if (!(built instanceof RecipeBookProvider)) {
                        ((HandledScreenAccessor) built).tascade$setX(info.x);
                        ((HandledScreenAccessor) built).tascade$setY(info.y);
                    }
                }
            } else if ("death".equals(info.type)) {
                built = new DeathScreen(info.detail, false);
                built.init(client, info.scaledWidth, info.scaledHeight);
            }
        } catch (RuntimeException e) {
            built = null;
        } finally {
            camera.currentScreenHandler = camera.playerScreenHandler;
        }
        return built;
    }

    private static void applyRecipeBook(RecipeBook book, int state) {
        book.setGuiOpen((state & RecordCodec.RECIPE_BOOK_OPEN) != 0);
        book.setFilteringCraftable((state & RecordCodec.RECIPE_BOOK_CRAFTABLE) != 0);
        book.setFurnaceGuiOpen((state & RecordCodec.RECIPE_BOOK_FURNACE_OPEN) != 0);
        book.setFurnaceFilteringCraftable((state & RecordCodec.RECIPE_BOOK_FURNACE_CRAFTABLE) != 0);
        book.setBlastFurnaceGuiOpen((state & RecordCodec.RECIPE_BOOK_BLAST_FURNACE_OPEN) != 0);
        book.setBlastFurnaceFilteringCraftable((state & RecordCodec.RECIPE_BOOK_BLAST_FURNACE_CRAFTABLE) != 0);
        book.setSmokerGuiOpen((state & RecordCodec.RECIPE_BOOK_SMOKER_OPEN) != 0);
        book.setSmokerFilteringCraftable((state & RecordCodec.RECIPE_BOOK_SMOKER_CRAFTABLE) != 0);
    }

    private Screen openCaptured(ScreenHandlerType<?> type, Text title) {
        capturing = true;
        captured = null;
        try {
            HandledScreens.open(type, client, 0, title);
        } finally {
            capturing = false;
        }
        Screen result = captured;
        captured = null;
        return result;
    }

    private void fill(ScreenHandler handler, Tracks.ScreenState state) {
        int slots = Math.min(handler.slots.size(), state.slots.length);
        PlayerInventory own = client.player != null ? client.player.inventory : null;
        for (int i = 0; i < slots; i++) {
            Slot slot = handler.slots.get(i);
            // re-setting own slots would swap the held item and replay its equip animation
            if (slot.inventory != own) slot.setStack(state.slots[i]);
        }
        int properties = Math.min(((ScreenHandlerAccessor) handler).tascade$getProperties().size(), state.properties.length);
        for (int i = 0; i < properties; i++) handler.setProperty(i, state.properties[i]);
        if (client.player != null) client.player.inventory.setCursorStack(state.cursorStack);
    }

    private void drawClicks(MatrixStack matrices, HandledScreen<?> screen, Tracks tracks, double time, int openedAt) {
        ScreenHandler handler = screen.getScreenHandler();
        int left = ((HandledScreenAccessor) screen).tascade$getX();
        int top = ((HandledScreenAccessor) screen).tascade$getY();
        for (int i = tracks.clicks.indexAt((int) time); i >= 0; i--) {
            int age = (int) time - tracks.clicks.time(i);
            if (age > 250 || tracks.clicks.time(i) < openedAt) break;
            int slotId = tracks.clicks.get(i)[1];
            if (slotId < 0 || slotId >= handler.slots.size()) continue;
            Slot slot = handler.slots.get(slotId);
            int alpha = (int) (255 * (1 - age / 250f));
            int color = alpha << 24 | 0xFFD24A;
            int x = left + slot.x;
            int y = top + slot.y;
            DrawableHelper.fill(matrices, x - 1, y - 1, x + 17, y, color);
            DrawableHelper.fill(matrices, x - 1, y + 16, x + 17, y + 17, color);
            DrawableHelper.fill(matrices, x - 1, y, x, y + 16, color);
            DrawableHelper.fill(matrices, x + 16, y, x + 17, y + 16, color);
        }
    }

    private void drawLabel(MatrixStack matrices, RecordCodec.ScreenInfo info, int width, int height) {
        DrawableHelper.fill(matrices, 0, 0, width, height, 0x60101010);
        String label = ReplayScreens.label(info.type);
        int y = height / 2 - 10;
        client.textRenderer.drawWithShadow(matrices, label, width / 2f - client.textRenderer.getWidth(label) / 2f, y, 0xFFFFFF);
        if (info.title != null && !info.title.getString().isEmpty() && !label.equals(info.title.getString())) {
            client.textRenderer.drawWithShadow(matrices, info.title,
                    width / 2f - client.textRenderer.getWidth(info.title) / 2f, y + 12, 0xA0A0A0);
        }
    }

    private static void drawCursor(MatrixStack matrices, int x, int y) {
        int[] widths = {1, 2, 3, 4, 5, 6, 7, 4, 2, 1};
        int[] offsets = {0, 0, 0, 0, 0, 0, 0, 2, 3, 4};
        for (int r = 0; r < widths.length; r++) {
            DrawableHelper.fill(matrices, x + offsets[r] - 1, y + r - 1, x + offsets[r] + widths[r] + 1, y + r + 2, 0xC0000000);
        }
        for (int r = 0; r < widths.length; r++) {
            DrawableHelper.fill(matrices, x + offsets[r], y + r, x + offsets[r] + widths[r], y + r + 1, 0xFFFFFFFF);
        }
    }
}
