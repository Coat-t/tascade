package com.coatt.tascade.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

@Environment(EnvType.CLIENT)
public class RankedModeScreen extends Screen {
    private final Screen parent;
    private ButtonWidget backButton;
    private final int bottomPadding = 10;

    public RankedModeScreen(Screen parent) {
        super(new LiteralText("Ranked mode"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addButton(new ButtonWidget(
                14,
                this.height - 20 - bottomPadding,
                80,
                20,
                new LiteralText("Back"),
                buttonWidget -> this.client.openScreen(parent)
        ));
        this.addButton(new ButtonWidget(
                14,
                this.height - 40 - bottomPadding - 10,
                80,
                20,
                new LiteralText("Join Queue"),
                buttonWidget -> this.client.openScreen(parent)
        ));
    }
    @Override
    public void onClose() {
        if (this.client != null) {
            this.client.openScreen(this.parent);
        }
    }
    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        this.drawTextWithShadow(matrices, this.textRenderer, this.title, 14, 14, 10911999);
        super.render(matrices, mouseX, mouseY, delta);
    }
}