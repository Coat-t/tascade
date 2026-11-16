package com.coatt.tascade.screen;

import com.coatt.tascade.hud.HudScale;
import com.coatt.tascade.modifier.ModifierEffects;
import com.coatt.tascade.modifier.RunModifier;
import com.coatt.tascade.modifier.RunModifier.Type;
import com.coatt.tascade.modifier.RunModifiers;
import com.coatt.tascade.network.RoomState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class RunModifiersScreen extends TascadeScreen {
    static final int BUTTON_WIDTH = 100;

    private static final int TINT = 0xC8000000;
    private static final int MARGIN = 14;
    private static final int MULTIPLIER_TOP = 12;
    private static final int MULTIPLIER_SCALE = 3;
    private static final int COLUMN_GAP = 10;
    private static final int MAX_COLUMN_WIDTH = 190;
    private static final int HEADING_HEIGHT = 12;
    private static final int ITEM_HEIGHT = 30;
    private static final int ITEM_GAP = 4;
    private static final int ITEM_PADDING = 4;
    private static final int ICON_FRAME = 22;
    private static final int ITEM_BACKGROUND = 0xE0101010;
    private static final int ITEM_HOVERED = 0xE0282828;
    private static final int ITEM_PICKED_ALPHA = 0x48;
    private static final int ICON_BACKGROUND = 0xFF141414;
    private static final int UNAVAILABLE_SHADE = 0xA0101010;
    private static final int SCROLL_STEP = 20;
    private static final int BAR_WIDTH = 2;
    private static final int BAR_GAP = 3;
    private static final int TOOLTIP_WIDTH = 180;
    private static final int PICKED_FRAME = 18;
    private static final int PICKED_GAP = 2;
    private static final int PICKED_ABOVE = 3;
    private static final int BUTTON_GAP = 6;
    private static final Identifier READY_ICON = new Identifier("realms", "textures/gui/realms/trial_icon.png");
    private static final int READY_SIZE = 8;
    private static final int READY_OVERHANG = 2;

    private final Screen parent;
    private final List<Column> columns = new ArrayList<>();
    private ButtonWidget deselectAll;
    private int columnWidth;
    private int headingY;
    private int listTop;
    private int listBottom;

    private static final class Column {
        final Type type;
        final List<RunModifier> modifiers;
        int x;
        int scroll = 0;

        Column(Type type) {
            this.type = type;
            this.modifiers = RunModifiers.ofType(type);
        }

        int contentHeight() {
            return Math.max(0, modifiers.size() * (ITEM_HEIGHT + ITEM_GAP) - ITEM_GAP);
        }
    }

    public RunModifiersScreen(Screen parent) {
        super(new LiteralText("Run Modifiers"));
        this.parent = parent;
        for (Type type : new Type[] {Type.DECREASE, Type.INCREASE, Type.ALTERNATIVE}) columns.add(new Column(type));
    }

    static ButtonWidget bottomCenterButton(int width, int height, String label, ButtonWidget.PressAction onPress) {
        return new ButtonWidget(width / 2 - BUTTON_WIDTH / 2, height - 20 - 10, BUTTON_WIDTH, 20,
                new LiteralText(label), onPress);
    }

    static int pickedStripHeight() {
        return pickedStripHeight(null);
    }

    static int pickedStripHeight(Map<String, String> rules) {
        return stripModifiers(rules).isEmpty() ? 0 : PICKED_FRAME + PICKED_ABOVE;
    }

    private static List<RunModifier> stripModifiers(Map<String, String> rules) {
        if (rules == null) return RunModifiers.picked();
        List<RunModifier> modifiers = new ArrayList<>();
        for (String id : RunModifiers.inRoom(RunModifiers.selectedIds(), rules)) {
            RunModifier modifier = RunModifiers.byId(id);
            if (modifier != null) modifiers.add(modifier);
        }
        return modifiers;
    }

    static List<StringRenderable> renderPickedStrip(MatrixStack matrices, MinecraftClient client, int screenWidth,
                                                    int centerX, int buttonTop, int mouseX, int mouseY) {
        return renderPickedStrip(matrices, client, screenWidth, centerX, buttonTop, mouseX, mouseY, null);
    }

    static List<StringRenderable> renderPickedStrip(MatrixStack matrices, MinecraftClient client, int screenWidth,
                                                    int centerX, int buttonTop, int mouseX, int mouseY,
                                                    Map<String, String> rules) {
        List<RunModifier> picked = stripModifiers(rules);
        if (picked.isEmpty()) return null;
        int rowWidth = iconRowWidth(picked.size());
        int x = Math.max(MARGIN, Math.min(centerX - rowWidth / 2, screenWidth - MARGIN - rowWidth));
        int y = buttonTop - PICKED_ABOVE - PICKED_FRAME;
        RunModifier hovered = renderIconRow(matrices, client, picked, x, y, mouseX, mouseY);
        if (hovered == null) return null;
        List<StringRenderable> tooltip = details(client.textRenderer, hovered);
        if (forcedIn(rules, hovered.id)) tooltip.add(forcedNote());
        return tooltip;
    }

    static boolean forcedIn(Map<String, String> rules, String id) {
        return rules != null && RoomState.MOD_FORCE.equals(rules.get(id));
    }

    static StringRenderable forcedNote() {
        return new LiteralText("Forced by the host").formatted(Formatting.GOLD);
    }

    public static final int ICON_ROW_FRAME = PICKED_FRAME;
    public static final int ICON_ROW_GAP = PICKED_GAP;

    public static int iconRowWidth(int count) {
        return count <= 0 ? 0 : count * PICKED_FRAME + (count - 1) * PICKED_GAP;
    }

    public static RunModifier renderIconRow(MatrixStack matrices, MinecraftClient client, List<RunModifier> modifiers,
                                            int x, int y, double mouseX, double mouseY) {
        RunModifier hovered = null;
        for (RunModifier modifier : modifiers) {
            boolean hover = mouseX >= x && mouseX < x + PICKED_FRAME && mouseY >= y && mouseY < y + PICKED_FRAME;
            if (hover) hovered = modifier;
            fill(matrices, x, y, x + PICKED_FRAME, y + PICKED_FRAME, hover ? ITEM_HOVERED : ICON_BACKGROUND);
            outline(matrices, x, y, PICKED_FRAME, PICKED_FRAME, 0xFF000000 | modifier.type.color);
            client.getItemRenderer().renderInGui(modifier.icon(), x + (PICKED_FRAME - 16) / 2, y + (PICKED_FRAME - 16) / 2);
            if (Boolean.TRUE.equals(ModifierEffects.charge(modifier.id))) {
                renderReady(matrices, client, x + PICKED_FRAME - READY_SIZE + READY_OVERHANG, y - READY_OVERHANG);
            }
            x += PICKED_FRAME + PICKED_GAP;
        }
        return hovered;
    }

    private static void renderReady(MatrixStack matrices, MinecraftClient client, int x, int y) {
        matrices.push();
        // in front of the item, which is drawn well off the GUI plane
        matrices.translate(0, 0, 300);
        client.getTextureManager().bindTexture(READY_ICON);
        RenderSystem.color4f(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int frame = (Util.getMeasuringTimeMs() / 800L & 1L) == 1L ? READY_SIZE : 0;
        DrawableHelper.drawTexture(matrices, x, y, 0f, frame, READY_SIZE, READY_SIZE, READY_SIZE, 2 * READY_SIZE);
        matrices.pop();
    }

    @Override
    protected void init() {
        parent.init(this.client, this.width, this.height);
        ButtonWidget close = this.addButton(bottomCenterButton(this.width, this.height, "Close", buttonWidget -> onClose()));
        deselectAll = this.addButton(new ButtonWidget(close.x + BUTTON_WIDTH + BUTTON_GAP, close.y, BUTTON_WIDTH, 20,
                new LiteralText("Deselect All"), buttonWidget -> RunModifiers.clear()));

        int noteY = MULTIPLIER_TOP + 8 * MULTIPLIER_SCALE + 4;
        headingY = noteY + 16;
        listTop = headingY + HEADING_HEIGHT;
        listBottom = this.height - 20 - 10 - 8;
        columnWidth = Math.min(MAX_COLUMN_WIDTH, (this.width - 2 * MARGIN - 2 * COLUMN_GAP) / 3);
        int x = (this.width - (3 * columnWidth + 2 * COLUMN_GAP)) / 2;
        for (Column column : columns) {
            column.x = x;
            x += columnWidth + COLUMN_GAP;
            clampScroll(column);
        }
    }

    @Override
    public void tick() {
        parent.tick();
    }

    @Override
    public void onClose() {
        if (this.client != null) this.client.openScreen(parent);
    }

    // drawing

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        // off-screen mouse, so nothing behind the tint looks hovered
        parent.render(matrices, -1, -1, delta);
        // its text sits slightly above the GUI plane, so it would show through
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        fill(matrices, 0, 0, this.width, this.height, TINT);
        deselectAll.active = !RunModifiers.selectedIds().isEmpty();

        renderMultiplier(matrices);
        RunModifier hovered = null;
        for (Column column : columns) {
            RunModifier inColumn = renderColumn(matrices, column, mouseX, mouseY);
            if (inColumn != null) hovered = inColumn;
        }
        super.render(matrices, mouseX, mouseY, delta);
        if (hovered != null) this.renderTooltip(matrices, tooltip(hovered), mouseX, mouseY);
    }

    private void renderMultiplier(MatrixStack matrices) {
        double multiplier = RunModifiers.multiplier();
        String label = RunModifier.format(multiplier);
        int color = multiplier == 0 ? Type.ALTERNATIVE.color
                : multiplier > 1 ? Type.INCREASE.color
                : multiplier < 1 ? Type.DECREASE.color
                : QueueStatus.COLOR_TEXT;
        matrices.push();
        matrices.translate(this.width / 2f - this.textRenderer.getWidth(label) * MULTIPLIER_SCALE / 2f, MULTIPLIER_TOP, 0);
        matrices.scale(MULTIPLIER_SCALE, MULTIPLIER_SCALE, 1f);
        this.textRenderer.drawWithShadow(matrices, label, 0, 0, color);
        matrices.pop();

        String refusal = RunModifiers.rankedRefusal();
        double pace = RunModifiers.pace();
        String note = refusal != null ? refusal
                : "Scales the pp a run earns. In ranked, your time is divided by your pace ("
                        + RunModifier.format(pace) + ").";
        drawSmall(matrices, note, this.width / 2, MULTIPLIER_TOP + 8 * MULTIPLIER_SCALE + 4,
                refusal != null ? QueueStatus.COLOR_ERROR : QueueStatus.COLOR_MUTED, true);
    }

    private RunModifier renderColumn(MatrixStack matrices, Column column, int mouseX, int mouseY) {
        drawSmall(matrices, column.type.heading, column.x + columnWidth / 2, headingY, column.type.color, true);

        boolean scrolls = scrolls(column);
        int itemWidth = itemWidth(column);
        RunModifier hovered = null;
        boolean mouseInList = mouseY >= listTop && mouseY < listBottom;

        enableScissor(column.x, listTop, columnWidth, listBottom - listTop);
        int y = listTop - column.scroll;
        for (RunModifier modifier : column.modifiers) {
            if (y + ITEM_HEIGHT > listTop && y < listBottom) {
                boolean hover = mouseInList && mouseX >= column.x && mouseX < column.x + itemWidth
                        && mouseY >= y && mouseY < y + ITEM_HEIGHT;
                renderModifier(matrices, modifier, column.x, y, itemWidth, hover);
                if (hover) hovered = modifier;
            }
            y += ITEM_HEIGHT + ITEM_GAP;
        }
        disableScissor();

        if (scrolls) {
            int barX = column.x + columnWidth - BAR_WIDTH;
            int track = listBottom - listTop;
            int thumb = Math.max(8, track * track / column.contentHeight());
            int thumbTop = listTop + (track - thumb) * column.scroll / maxScroll(column);
            fill(matrices, barX, listTop, barX + BAR_WIDTH, listBottom, 0x30FFFFFF);
            fill(matrices, barX, thumbTop, barX + BAR_WIDTH, thumbTop + thumb, 0x80FFFFFF);
        }
        return hovered;
    }

    private void renderModifier(MatrixStack matrices, RunModifier modifier, int x, int y, int width, boolean hover) {
        int color = modifier.type.color;
        boolean picked = RunModifiers.isSelected(modifier);
        boolean available = picked || pickable(modifier);
        hover = hover && available;
        fill(matrices, x, y, x + width, y + ITEM_HEIGHT, hover && !picked ? ITEM_HOVERED : ITEM_BACKGROUND);
        if (picked) fill(matrices, x, y, x + width, y + ITEM_HEIGHT, ITEM_PICKED_ALPHA << 24 | color);
        if (picked) outline(matrices, x, y, width, ITEM_HEIGHT, 0xFF000000 | color);
        else if (hover) outline(matrices, x, y, width, ITEM_HEIGHT, 0x80FFFFFF);

        int iconX = x + ITEM_PADDING;
        int iconY = y + (ITEM_HEIGHT - ICON_FRAME) / 2;
        fill(matrices, iconX, iconY, iconX + ICON_FRAME, iconY + ICON_FRAME, ICON_BACKGROUND);
        outline(matrices, iconX, iconY, ICON_FRAME, ICON_FRAME, 0xFF000000 | color);
        this.itemRenderer.renderInGui(modifier.icon(), iconX + (ICON_FRAME - 16) / 2, iconY + (ICON_FRAME - 16) / 2);

        int textX = iconX + ICON_FRAME + 5;
        int right = x + width - ITEM_PADDING;
        this.textRenderer.drawWithShadow(matrices, trim(modifier.name, right - textX, 1f), textX, y + 5,
                QueueStatus.COLOR_TEXT);

        String multiplier = RunModifier.format(modifier.multiplier);
        int multiplierWidth = this.textRenderer.getWidth(multiplier);
        this.textRenderer.drawWithShadow(matrices, multiplier, right - multiplierWidth, y + 17, color);
        float scale = HudScale.oneStepSmaller(this.client);
        String description = trim(modifier.description, right - multiplierWidth - 4 - textX, scale);
        drawSmall(matrices, description, textX, y + 18, QueueStatus.COLOR_MUTED, false);
        if (!available) fill(matrices, x, y, x + width, y + ITEM_HEIGHT, UNAVAILABLE_SHADE);
    }

    private static boolean pickable(RunModifier modifier) {
        return modifier.enabled && RunModifiers.blocker(modifier) == null;
    }

    private List<StringRenderable> tooltip(RunModifier modifier) {
        List<StringRenderable> lines = details(this.textRenderer, modifier);
        RunModifier blocker = RunModifiers.blocker(modifier);
        String action = !modifier.enabled ? "Not available yet"
                : RunModifiers.isSelected(modifier) ? "Click to remove"
                : blocker != null ? "Can't be played with " + blocker.name : "Click to add";
        lines.add(new LiteralText(action)
                .styled(style -> style.withColor(TextColor.fromRgb(QueueStatus.COLOR_MUTED))));
        return lines;
    }

    public static List<StringRenderable> details(TextRenderer textRenderer, RunModifier modifier) {
        List<StringRenderable> lines = new ArrayList<>();
        lines.add(new LiteralText(modifier.name).styled(style -> style.withColor(TextColor.fromRgb(modifier.type.color))));
        for (StringRenderable line : textRenderer.wrapLines(new LiteralText(modifier.description), TOOLTIP_WIDTH)) {
            lines.add(new LiteralText(line.getString()).styled(style -> style.withColor(TextColor.fromRgb(QueueStatus.COLOR_MUTED))));
        }
        String effect = modifier.type == Type.ALTERNATIVE
                ? "No pp (" + RunModifier.format(0) + "), not in ranked"
                : RunModifier.format(modifier.multiplier) + " pp, pace " + RunModifier.format(modifier.pace());
        lines.add(new LiteralText(effect + "  -  " + modifier.type.heading)
                .styled(style -> style.withColor(TextColor.fromRgb(modifier.type.color))));
        Boolean ready = ModifierEffects.charge(modifier.id);
        if (ready != null) {
            lines.add(ready ? new LiteralText("Ready: not used yet this run").formatted(Formatting.GREEN)
                    : new LiteralText("Used this run").formatted(Formatting.GRAY));
        }
        return lines;
    }

    // input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        RunModifier modifier = modifierAt(mouseX, mouseY);
        if (modifier == null) return false;
        if (!RunModifiers.isSelected(modifier) && !pickable(modifier)) return true;
        RunModifiers.toggle(modifier);
        this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        Column column = columnAt(mouseX);
        if (column == null || !scrolls(column)) return false;
        column.scroll -= (int) Math.round(amount * SCROLL_STEP);
        clampScroll(column);
        return true;
    }

    private Column columnAt(double mouseX) {
        for (Column column : columns) {
            if (mouseX >= column.x && mouseX < column.x + columnWidth) return column;
        }
        return null;
    }

    private RunModifier modifierAt(double mouseX, double mouseY) {
        if (mouseY < listTop || mouseY >= listBottom) return null;
        Column column = columnAt(mouseX);
        if (column == null || mouseX >= column.x + itemWidth(column)) return null;
        int offset = (int) mouseY - listTop + column.scroll;
        int index = offset / (ITEM_HEIGHT + ITEM_GAP);
        boolean inGap = offset % (ITEM_HEIGHT + ITEM_GAP) >= ITEM_HEIGHT;
        if (inGap || index < 0 || index >= column.modifiers.size()) return null;
        return column.modifiers.get(index);
    }

    // layout helpers

    private boolean scrolls(Column column) {
        return column.contentHeight() > listBottom - listTop;
    }

    private int itemWidth(Column column) {
        return scrolls(column) ? columnWidth - BAR_WIDTH - BAR_GAP : columnWidth;
    }

    private int maxScroll(Column column) {
        return Math.max(0, column.contentHeight() - (listBottom - listTop));
    }

    private void clampScroll(Column column) {
        column.scroll = Math.max(0, Math.min(column.scroll, maxScroll(column)));
    }

    private static void outline(MatrixStack matrices, int x, int y, int width, int height, int color) {
        fill(matrices, x, y, x + width, y + 1, color);
        fill(matrices, x, y + height - 1, x + width, y + height, color);
        fill(matrices, x, y + 1, x + 1, y + height - 1, color);
        fill(matrices, x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private String trim(String text, int width, float scale) {
        int fits = (int) (width / scale);
        if (this.textRenderer.getWidth(text) <= fits) return text;
        return this.textRenderer.trimToWidth(text, fits - this.textRenderer.getWidth("...")) + "...";
    }

    private void drawSmall(MatrixStack matrices, String text, int x, int y, int color, boolean centered) {
        float scale = HudScale.oneStepSmaller(this.client);
        float left = centered ? x - this.textRenderer.getWidth(text) * scale / 2 : x;
        matrices.push();
        matrices.translate(HudScale.snapToScreenPixel(this.client, left), HudScale.snapToScreenPixel(this.client, y), 0);
        matrices.scale(scale, scale, 1f);
        this.textRenderer.drawWithShadow(matrices, text, 0, 0, color);
        matrices.pop();
    }
}
