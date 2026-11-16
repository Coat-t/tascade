package com.coatt.tascade.hud;

import com.coatt.tascade.modifier.ModifierEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class DelusionalHud {
    private DelusionalHud() {}

    private static final int HEALTH = 0;
    private static final int FOOD = 1;

    private static final int CONTAINER_U = 16;
    private static final int HEALTH_V = 0;
    private static final int FOOD_V = 27;
    // the pale damage-flash heart takes a tint well
    private static final int HEALTH_PHANTOM_U = 70;
    private static final int FOOD_PHANTOM_U = 52;
    private static final int HALF_OFFSET = 9;
    private static final int SPRITE = 9;

    private static final float FADE_MS = 400f;
    private static final float SWAY = 0.8f;
    private static final float SWAY_SPEED = 1 / 520f;
    private static final long PHANTOM_MIN_MS = 2300;
    private static final long PHANTOM_SPREAD_MS = 1700;
    private static final float PHANTOM_ALPHA = 0.6f;
    private static final long TEAR_EVERY_MS = 4300;
    private static final long TEAR_FOR_MS = 130;
    private static final long TEAR_BAR_OFFSET_MS = 1900;

    private static boolean active;
    private static float healthCover = 1;
    private static float foodCover = 1;
    private static long lastFrameMs;
    private static String section = "";
    // not drawn while riding a mount with health
    private static boolean foodDrawn;

    public static void begin(PlayerEntity player) {
        long now = Util.getMeasuringTimeMs();
        float step = Math.min(1f, Math.max(0, now - lastFrameMs) / FADE_MS);
        lastFrameMs = now;
        section = "";
        foodDrawn = false;
        active = player != null && ModifierEffects.delusional();
        if (!active) {
            healthCover = 1;
            foodCover = 1;
            return;
        }
        boolean healthHidden = MathHelper.ceil(player.getHealth()) > ModifierEffects.DELUSIONAL_HEALTH;
        boolean foodHidden = player.getHungerManager().getFoodLevel() > ModifierEffects.DELUSIONAL_FOOD;
        healthCover = approach(healthCover, healthHidden ? 1 : 0, step);
        foodCover = approach(foodCover, foodHidden ? 1 : 0, step);
    }

    public static void section(String name) {
        section = name;
        if ("food".equals(name)) foodDrawn = true;
    }

    public static boolean hides() {
        if (!active) return false;
        return "health".equals(section) && healthCover >= 1 || "food".equals(section) && foodCover >= 1;
    }

    public static void render(InGameHud hud, MatrixStack matrices, PlayerEntity player, int scaledWidth, int scaledHeight) {
        if (!active || player == null) return;
        MinecraftClient.getInstance().getTextureManager().bindTexture(DrawableHelper.GUI_ICONS_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        long now = Util.getMeasuringTimeMs();
        int left = scaledWidth / 2 - 91;
        int right = scaledWidth / 2 + 91;
        int bottom = scaledHeight - 39;

        if (healthCover > 0) {
            float maxHealth = (float) player.getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH);
            int absorption = MathHelper.ceil(player.getAbsorptionAmount());
            int rows = MathHelper.ceil((maxHealth + absorption) / 2f / 10f);
            int rowHeight = Math.max(10 - (rows - 2), 3);
            int hearts = MathHelper.ceil((maxHealth + absorption) / 2f);
            int[] xs = new int[hearts];
            int[] ys = new int[hearts];
            for (int i = 0; i < hearts; i++) {
                xs[i] = left + i % 10 * 8;
                ys[i] = bottom - (MathHelper.ceil((i + 1) / 10f) - 1) * rowHeight;
            }
            renderCover(hud, matrices, HEALTH, xs, ys, healthCover, now);
        }
        if (foodDrawn && foodCover > 0) {
            int[] xs = new int[10];
            int[] ys = new int[10];
            for (int i = 0; i < 10; i++) {
                xs[i] = right - i * 8 - 9;
                ys[i] = bottom;
            }
            renderCover(hud, matrices, FOOD, xs, ys, foodCover, now);
        }
        RenderSystem.color4f(1f, 1f, 1f, 1f);
    }

    private static void renderCover(InGameHud hud, MatrixStack matrices, int bar, int[] xs, int[] ys, float alpha, long now) {
        int v = bar == HEALTH ? HEALTH_V : FOOD_V;
        int phantomU = bar == HEALTH ? HEALTH_PHANTOM_U : FOOD_PHANTOM_U;
        boolean tear = (now + bar * TEAR_BAR_OFFSET_MS) % TEAR_EVERY_MS < TEAR_FOR_MS;
        int split = (mix(now / 40 + bar) & 1) == 0 ? 1 : 2;

        for (int i = 0; i < xs.length; i++) {
            int x = xs[i];
            int y = ys[i];
            matrices.push();
            matrices.translate(0, MathHelper.sin(now * SWAY_SPEED + i * 0.6f + bar * 2.1f) * SWAY, 0);
            if (tear) {
                RenderSystem.color4f(0.3f, 1f, 1f, 0.55f * alpha);
                hud.drawTexture(matrices, x - split, y, CONTAINER_U, v, SPRITE, SPRITE);
                RenderSystem.color4f(1f, 0.3f, 1f, 0.55f * alpha);
                hud.drawTexture(matrices, x + split, y, CONTAINER_U, v, SPRITE, SPRITE);
            }
            RenderSystem.color4f(1f, 1f, 1f, alpha);
            hud.drawTexture(matrices, x, y, CONTAINER_U, v, SPRITE, SPRITE);

            long period = PHANTOM_MIN_MS + Math.floorMod(mix(bar * 31L + i), PHANTOM_SPREAD_MS);
            long t = now + Math.floorMod(mix(bar * 131L + i + 7), period);
            long cycle = t / period;
            long roll = mix(cycle * 977L + bar * 31L + i);
            if (Math.floorMod(roll, 3) != 0) {
                float glow = MathHelper.sin((t % period) / (float) period * (float) Math.PI);
                RenderSystem.color4f(0.75f, 0.45f, 1f, glow * glow * PHANTOM_ALPHA * alpha);
                int u = (roll >>> 8 & 1) == 0 ? phantomU : phantomU + HALF_OFFSET;
                hud.drawTexture(matrices, x, y, u, v, SPRITE, SPRITE);
            }
            matrices.pop();
        }
    }

    private static float approach(float from, float to, float step) {
        return from < to ? Math.min(to, from + step) : Math.max(to, from - step);
    }

    // SplitMix64 finalizer
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
