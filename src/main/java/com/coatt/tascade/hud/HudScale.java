package com.coatt.tascade.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public final class HudScale {
    private HudScale() {}

    // one GUI-scale step down keeps font pixels on whole screen pixels, so text stays crisp
    public static float oneStepSmaller(MinecraftClient client) {
        double guiScale = client.getWindow().getScaleFactor();
        return guiScale <= 1 ? 1f : (float) ((guiScale - 1) / guiScale);
    }

    public static float snapToScreenPixel(MinecraftClient client, float gui) {
        double guiScale = client.getWindow().getScaleFactor();
        return (float) (Math.round(gui * guiScale) / guiScale);
    }
}
