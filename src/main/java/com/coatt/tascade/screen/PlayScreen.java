package com.coatt.tascade.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

@Environment(EnvType.CLIENT)
public class PlayScreen extends Screen {
    private final Screen parent;
    private ButtonWidget backButton;
    private final int bottomPadding = 10;

    public PlayScreen(Screen parent) {
        super(new LiteralText("Select Gamemode"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addButton(new ButtonWidget(
                14,
                this.height - 40 - bottomPadding - 10,
                80,
                20,
                new LiteralText("Multi"),
                buttonWidget -> this.client.openScreen(new RankedModeScreen(this))
        ));
        this.addButton(new ButtonWidget(
                14,
                this.height - 60 - bottomPadding - 20,
                80,
                20,
                new LiteralText("Solo"),
                buttonWidget -> this.client.openScreen(new SoloModeScreen(this))
        ));
        this.addButton(new ButtonWidget(
                14,
                this.height - 20 - bottomPadding,
                80,
                20,
                new LiteralText("Back"),
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