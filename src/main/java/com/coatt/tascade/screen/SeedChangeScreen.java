package com.coatt.tascade.screen;

import com.coatt.tascade.network.RunState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

@Environment(EnvType.CLIENT)
public class SeedChangeScreen extends Screen {
    private final boolean awaitingSeed;

    private SeedChangeScreen(boolean awaitingSeed) {
        super(new LiteralText("Changing seed..."));
        this.awaitingSeed = awaitingSeed;
    }

    public static SeedChangeScreen awaitingSeed() {
        return new SeedChangeScreen(true);
    }

    public static SeedChangeScreen closingWorld() {
        return new SeedChangeScreen(false);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void tick() {
        if (awaitingSeed && RunState.soloSeedRequestedAtMs == 0) this.client.openScreen(null);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        this.drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 70, 16777215);
        super.render(matrices, mouseX, mouseY, delta);
    }
}
