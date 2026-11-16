package com.coatt.tascade.screen;

import com.coatt.tascade.hud.HudScale;
import com.coatt.tascade.modifier.RunModifier;
import com.coatt.tascade.modifier.RunModifiers;
import com.coatt.tascade.network.RoomState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringRenderable;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class RoomModifierRulesScreen extends TascadeScreen {
    private static final int LEFT = 14;
    private static final int TITLE_Y = 32;
    private static final int NOTE_Y = 44;
    private static final int LIST_TOP = 58;
    private static final int ROW_HEIGHT = 26;
    private static final int MAX_LIST_WIDTH = 380;
    private static final int RULE_WIDTH = 70;
    private static final int STRIP_WIDTH = 2;
    private static final int ICON_SIZE = 16;
    private static final int ROW_BACKGROUND = 0x90000000;
    private static final RunModifier.Type[] TYPE_ORDER = {
            RunModifier.Type.DECREASE, RunModifier.Type.INCREASE, RunModifier.Type.ALTERNATIVE};

    private final Screen parent;
    private final RoomState.Settings settings;
    private final boolean editable;
    private final List<RunModifier> modifiers = new ArrayList<>();
    private final List<RuleButton> ruleButtons = new ArrayList<>();
    private int listLeft;
    private int listWidth;
    private int listBottom;
    private int scroll = 0;
    private String refusal = null;

    public RoomModifierRulesScreen(Screen parent, RoomState.Settings settings, boolean editable) {
        super(new LiteralText("Modifier Rules"));
        this.parent = parent;
        this.settings = settings;
        this.editable = editable;
        for (RunModifier.Type type : TYPE_ORDER) {
            for (RunModifier modifier : RunModifiers.ofType(type)) {
                if (modifier.enabled) modifiers.add(modifier);
            }
        }
    }

    @Override
    protected void init() {
        listWidth = Math.min(MAX_LIST_WIDTH, this.width - 2 * LEFT - 8);
        listLeft = (this.width - listWidth) / 2;
        listBottom = this.height - 20 - 10 - 8;
        ruleButtons.clear();
        for (RunModifier modifier : modifiers) {
            RuleButton button = this.addChild(new RuleButton(ruleLabel(modifier),
                    buttonWidget -> step(modifier, buttonWidget)));
            button.active = editable;
            ruleButtons.add(button);
        }
        this.addButton(new ButtonWidget(this.width / 2 - 40, this.height - 20 - 10, 80, 20,
                new LiteralText(editable ? "Done" : "Back"), buttonWidget -> onClose()));
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        placeButtons();
    }

    private void step(RunModifier modifier, ButtonWidget button) {
        refusal = null;
        String rule = settings.modifierRule(modifier.id);
        String next;
        if (RoomState.MOD_ALLOW.equals(rule)) {
            next = RoomState.MOD_DISALLOW;
        } else if (RoomState.MOD_DISALLOW.equals(rule)) {
            RunModifier clash = forcedClash(modifier);
            if (clash != null) {
                refusal = modifier.name + " can't be forced with " + clash.name + ", which is forced.";
                next = RoomState.MOD_ALLOW;
            } else {
                next = RoomState.MOD_FORCE;
            }
        } else {
            next = RoomState.MOD_ALLOW;
        }
        if (RoomState.MOD_ALLOW.equals(next)) settings.modifiers.remove(modifier.id);
        else settings.modifiers.put(modifier.id, next);
        button.setMessage(ruleLabel(modifier));
    }

    private RunModifier forcedClash(RunModifier modifier) {
        for (Map.Entry<String, String> rule : settings.modifiers.entrySet()) {
            if (!RoomState.MOD_FORCE.equals(rule.getValue()) || rule.getKey().equals(modifier.id)) continue;
            RunModifier forced = RunModifiers.byId(rule.getKey());
            if (forced != null && RunModifiers.incompatible(modifier, forced)) return forced;
        }
        return null;
    }

    private MutableText ruleLabel(RunModifier modifier) {
        String rule = settings.modifierRule(modifier.id);
        if (RoomState.MOD_FORCE.equals(rule)) return new LiteralText("Forced").formatted(Formatting.GOLD);
        if (RoomState.MOD_DISALLOW.equals(rule)) return new LiteralText("Disallowed").formatted(Formatting.RED);
        return new LiteralText("Allowed");
    }

    private int maxScroll() {
        return Math.max(0, modifiers.size() * ROW_HEIGHT - (listBottom - LIST_TOP));
    }

    private void placeButtons() {
        for (int i = 0; i < ruleButtons.size(); i++) {
            ButtonWidget button = ruleButtons.get(i);
            int y = LIST_TOP + i * ROW_HEIGHT - scroll;
            button.x = listLeft + listWidth - RULE_WIDTH - 3;
            button.y = y + 2;
            button.visible = rowInView(y);
        }
    }

    private boolean rowInView(int y) {
        return y + ROW_HEIGHT - 2 > LIST_TOP && y < listBottom;
    }

    private boolean inList(double mouseY) {
        return mouseY >= LIST_TOP && mouseY < listBottom;
    }

    @Override
    public void tick() {
        super.tick();
        if (!RoomState.inRoom()) onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (maxScroll() > 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(amount * ROW_HEIGHT)));
            placeButtons();
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
        String note = refusal != null ? refusal : "Forced ones are played by everyone; disallowed ones can't be picked.";
        drawSmallCentered(matrices, note, this.width / 2, NOTE_Y, refusal != null ? QueueStatus.COLOR_ERROR : QueueStatus.COLOR_MUTED);

        RunModifier hovered = null;
        float scale = HudScale.oneStepSmaller(this.client);
        int textRight = listLeft + listWidth - RULE_WIDTH - 8;
        enableScissor(listLeft, LIST_TOP, listWidth, listBottom - LIST_TOP);
        for (int i = 0; i < modifiers.size(); i++) {
            int y = LIST_TOP + i * ROW_HEIGHT - scroll;
            if (!rowInView(y)) continue;
            RunModifier modifier = modifiers.get(i);
            fill(matrices, listLeft, y, listLeft + listWidth, y + ROW_HEIGHT - 2, ROW_BACKGROUND);
            fill(matrices, listLeft, y, listLeft + STRIP_WIDTH, y + ROW_HEIGHT - 2, 0xFF000000 | modifier.type.color);
            int iconX = listLeft + STRIP_WIDTH + 3;
            this.itemRenderer.renderInGui(modifier.icon(), iconX, y + (ROW_HEIGHT - 2 - ICON_SIZE) / 2);
            int textX = iconX + ICON_SIZE + 5;
            this.textRenderer.drawWithShadow(matrices, trim(modifier.name, textRight - textX, 1f), textX, y + 3,
                    QueueStatus.COLOR_TEXT);
            drawSmall(matrices, trim(modifier.description, textRight - textX, scale), textX, y + 14, QueueStatus.COLOR_MUTED);
            ruleButtons.get(i).render(matrices, mouseX, mouseY, delta);
            if (inList(mouseY) && mouseX >= listLeft && mouseX < textRight && mouseY >= y && mouseY < y + ROW_HEIGHT - 2) {
                hovered = modifier;
            }
        }
        disableScissor();
        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            int barX = listLeft + listWidth + 4;
            int track = listBottom - LIST_TOP;
            int thumb = Math.max(8, track * track / (track + maxScroll));
            int thumbTop = LIST_TOP + (track - thumb) * scroll / maxScroll;
            fill(matrices, barX, LIST_TOP, barX + 2, listBottom, 0x30FFFFFF);
            fill(matrices, barX, thumbTop, barX + 2, thumbTop + thumb, 0x80FFFFFF);
        }
        super.render(matrices, mouseX, mouseY, delta);
        if (hovered != null) this.renderTooltip(matrices, tooltip(hovered), mouseX, mouseY);
    }

    private class RuleButton extends ButtonWidget {
        RuleButton(MutableText label, PressAction onPress) {
            super(0, 0, RULE_WIDTH, 20, label, onPress);
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            super.render(matrices, mouseX, inList(mouseY) ? mouseY : Integer.MIN_VALUE, delta);
        }

        @Override
        protected boolean clicked(double mouseX, double mouseY) {
            return inList(mouseY) && super.clicked(mouseX, mouseY);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return inList(mouseY) && super.isMouseOver(mouseX, mouseY);
        }
    }

    private List<StringRenderable> tooltip(RunModifier modifier) {
        List<StringRenderable> lines = new ArrayList<>();
        lines.add(new LiteralText(modifier.name));
        lines.addAll(this.textRenderer.wrapLines(new LiteralText(modifier.description), 180));
        RunModifier clash = forcedClash(modifier);
        if (clash != null && !RoomState.MOD_FORCE.equals(settings.modifierRule(modifier.id))) {
            lines.add(new LiteralText("Can't be played with " + clash.name).formatted(Formatting.GRAY));
        }
        return lines;
    }

    private String trim(String text, int width, float scale) {
        int fits = (int) (width / scale);
        if (this.textRenderer.getWidth(text) <= fits) return text;
        return this.textRenderer.trimToWidth(text, Math.max(0, fits - this.textRenderer.getWidth("..."))) + "...";
    }

    private void drawSmall(MatrixStack matrices, String text, int x, int y, int color) {
        float scale = HudScale.oneStepSmaller(this.client);
        matrices.push();
        matrices.translate(HudScale.snapToScreenPixel(this.client, x), HudScale.snapToScreenPixel(this.client, y), 0);
        matrices.scale(scale, scale, 1f);
        this.textRenderer.drawWithShadow(matrices, text, 0, 0, color);
        matrices.pop();
    }

    private void drawSmallCentered(MatrixStack matrices, String text, int center, int y, int color) {
        float scale = HudScale.oneStepSmaller(this.client);
        int width = Math.min(this.width - 2 * LEFT, Math.round(this.textRenderer.getWidth(text) * scale));
        String fitted = trim(text, this.width - 2 * LEFT, scale);
        drawSmall(matrices, fitted, center - width / 2, y, color);
    }
}
