package com.coatt.tascade.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class IconButton extends ButtonWidget {
    private static final int OUTLINE = 0x000000;
    private static final int OUTLINE_HOVERED = 0xFFFFFF;

    private final Identifier icon;
    private final int iconTextureSize;

    public IconButton(int x, int y, int size, Identifier icon, int iconTextureSize, Text message, PressAction onPress) {
        super(x, y, size, size, message, onPress);
        this.icon = icon;
        this.iconTextureSize = iconTextureSize;
    }

    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        // the title screen fades its buttons in
        int alphaBits = Math.round(this.alpha * 255) << 24;
        int outline = isHovered() ? OUTLINE_HOVERED : OUTLINE;
        fill(matrices, this.x, this.y, this.x + this.width, this.y + this.height, alphaBits | outline);

        MinecraftClient.getInstance().getTextureManager().bindTexture(this.icon);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.color4f(1f, 1f, 1f, this.alpha);
        int inner = this.width - 2;
        drawTexture(matrices, this.x + 1, this.y + 1, inner, inner, 0f, 0f,
                iconTextureSize, iconTextureSize, iconTextureSize, iconTextureSize);
        RenderSystem.color4f(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
