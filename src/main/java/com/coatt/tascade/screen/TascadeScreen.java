package com.coatt.tascade.screen;

import com.coatt.tascade.Main;
import com.coatt.tascade.hud.RunLabels;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.network.TascadeClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

@Environment(EnvType.CLIENT)
public abstract class TascadeScreen extends Screen {
    protected static final Identifier BACKGROUND = Main.id("textures/gui/background.png");
    protected static final Identifier BACKGROUND_BLUR = Main.id("textures/gui/background_blur.png");
    private static final int BACKGROUND_WIDTH = 1600;
    private static final int BACKGROUND_HEIGHT = 900;
    private static final int FACE_SIZE = 8;
    private static final int FACE_GAP = 4;
    public static final int TOP_BAR_HEIGHT = 20;
    private static final int TOP_BAR_TEXT_Y = 6;
    private static final int TOP_BAR_BACKGROUND = 0x90000000;

    private boolean topBarShown = false;

    protected TascadeScreen(Text title) {
        super(title);
    }

    @Override
    public void renderBackground(MatrixStack matrices, int vOffset) {
        float alpha = backgroundAlpha();
        if (alpha < 1f) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.color4f(1f, 1f, 1f, alpha);
        drawCover(matrices, backgroundImage(), BACKGROUND_WIDTH, BACKGROUND_HEIGHT, this.width, this.height);
        RenderSystem.color4f(1f, 1f, 1f, 1f);
        if (alpha < 1f) RenderSystem.disableBlend();
    }

    static void drawCover(MatrixStack matrices, Identifier image, int imageWidth, int imageHeight, int width, int height) {
        float scale = Math.max((float) width / imageWidth, (float) height / imageHeight);
        int regionWidth = Math.round(width / scale);
        int regionHeight = Math.round(height / scale);
        MinecraftClient.getInstance().getTextureManager().bindTexture(image);
        drawTexture(matrices, 0, 0, width, height, (imageWidth - regionWidth) / 2f, (imageHeight - regionHeight) / 2f,
                regionWidth, regionHeight, imageWidth, imageHeight);
    }

    protected void enableScissor(int x, int y, int width, int height) {
        Window window = this.client.getWindow();
        double scale = window.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) Math.round(x * scale), (int) Math.round(window.getFramebufferHeight() - (y + height) * scale),
                (int) Math.round(width * scale), (int) Math.round(height * scale));
    }

    protected static void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static int overlayTop(Screen screen) {
        return screen instanceof TascadeScreen && ((TascadeScreen) screen).topBarShown ? TOP_BAR_HEIGHT : 0;
    }

    protected void renderTopBar(MatrixStack matrices) {
        topBarShown = true;
        fill(matrices, 0, 0, this.width, TOP_BAR_HEIGHT, TOP_BAR_BACKGROUND);
        int y = TOP_BAR_TEXT_Y;
        this.drawStringWithShadow(matrices, this.textRenderer, "Tascade (Beta)", 14, y, QueueStatus.COLOR_TEXT);

        if (Main.profileStatus == Main.ProfileState.SUCCESS && Main.nickname != null && Main.pp != null && Main.elo != null) {
            int x = this.width - 14 - this.textRenderer.getWidth(Main.nickname);
            this.drawStringWithShadow(matrices, this.textRenderer, Main.nickname, x, y, QueueStatus.COLOR_TEXT);
            String uuid = TascadeClient.getInstance().getPlayerUuid();
            if (uuid != null) {
                x -= FACE_GAP + FACE_SIZE;
                PlayerHeads.draw(matrices, uuid, x, y - 1, FACE_SIZE);
            }
            String pp = RunLabels.pp(Main.pp);
            x -= 10 + this.textRenderer.getWidth(pp);
            this.drawStringWithShadow(matrices, this.textRenderer, pp, x, y, QueueStatus.COLOR_MUTED);
            String elo = Main.elo + "elo";
            x -= 10 + this.textRenderer.getWidth(elo);
            this.drawStringWithShadow(matrices, this.textRenderer, elo, x, y, QueueStatus.COLOR_MUTED);
        } else if (Main.profileStatus == Main.ProfileState.LOADING) {
            String loading = "Loading profile...";
            this.drawStringWithShadow(matrices, this.textRenderer, loading,
                    this.width - 14 - this.textRenderer.getWidth(loading), y, QueueStatus.COLOR_MUTED);
        } else if (!RunState.connected) {
            String reconnecting = QueueStatus.connection();
            this.drawStringWithShadow(matrices, this.textRenderer, reconnecting,
                    this.width - 14 - this.textRenderer.getWidth(reconnecting), y, QueueStatus.connectionColor());
        } else {
            String failed = "Failed to load";
            this.drawStringWithShadow(matrices, this.textRenderer, failed,
                    this.width - 14 - this.textRenderer.getWidth(failed), y, 0x994242);
        }
    }

    protected Identifier backgroundImage() {
        return BACKGROUND_BLUR;
    }

    protected float backgroundAlpha() {
        return 1f;
    }
}
