package com.coatt.tascade.replay.play;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.GameOptions;
import net.minecraft.util.math.MathHelper;

final class FreeCamera {
    private static final double TICK_MS = 50;
    private static final double DRAG = 0.91;
    private static final double VERTICAL_DRAG = 0.6;
    private static final double DEFAULT_FLY_SPEED = 0.05;

    private double flySpeed = DEFAULT_FLY_SPEED;
    private double x;
    private double y;
    private double z;
    private double prevX;
    private double prevY;
    private double prevZ;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private boolean sprinting;
    private double sinceTick;
    private double placedX = Double.NaN;
    private double placedY;
    private double placedZ;

    void move(MinecraftClient client, CameraEntity camera, double realMs) {
        if (camera.getX() != placedX || camera.getY() != placedY || camera.getZ() != placedZ) {
            x = prevX = camera.getX();
            y = prevY = camera.getY();
            z = prevZ = camera.getZ();
            velocityX = velocityY = velocityZ = 0;
            sprinting = false;
            sinceTick = 0;
        }
        sinceTick += realMs;
        while (sinceTick >= TICK_MS) {
            sinceTick -= TICK_MS;
            tick(client, camera.yaw);
        }
        double delta = sinceTick / TICK_MS;
        placedX = MathHelper.lerp(delta, prevX, x);
        placedY = MathHelper.lerp(delta, prevY, y);
        placedZ = MathHelper.lerp(delta, prevZ, z);
        camera.place(placedX, placedY, placedZ, camera.yaw, camera.pitch);
    }

    // one tick of ClientPlayerEntity.tickMovement + PlayerEntity.travel, flying with noclip
    private void tick(MinecraftClient client, float yaw) {
        GameOptions o = client.options;
        boolean keys = client.currentScreen == null;
        int forward = keys ? axis(o.keyForward.isPressed(), o.keyBack.isPressed()) : 0;
        int sideways = keys ? axis(o.keyLeft.isPressed(), o.keyRight.isPressed()) : 0;
        int vertical = keys ? axis(o.keyJump.isPressed(), o.keySneak.isPressed()) : 0;

        if (!sprinting && forward > 0 && keys && o.keySprint.isPressed()) sprinting = true;
        if (sprinting && forward <= 0) sprinting = false;

        velocityY += vertical * flySpeed * 3;
        if (Math.abs(velocityX) < 0.003) velocityX = 0;
        if (Math.abs(velocityY) < 0.003) velocityY = 0;
        if (Math.abs(velocityZ) < 0.003) velocityZ = 0;

        double inSideways = sideways * 0.98;
        double inForward = forward * 0.98;
        double length = inSideways * inSideways + inForward * inForward;
        if (length >= 1.0E-7) {
            double speed = flySpeed * (sprinting ? 2 : 1) / (length > 1 ? Math.sqrt(length) : 1);
            double sin = Math.sin(Math.toRadians(yaw));
            double cos = Math.cos(Math.toRadians(yaw));
            velocityX += (inSideways * cos - inForward * sin) * speed;
            velocityZ += (inForward * cos + inSideways * sin) * speed;
        }

        prevX = x;
        prevY = y;
        prevZ = z;
        x += velocityX;
        y += velocityY;
        z += velocityZ;
        velocityX *= DRAG;
        velocityY *= VERTICAL_DRAG;
        velocityZ *= DRAG;
    }

    private static int axis(boolean positive, boolean negative) {
        return positive == negative ? 0 : positive ? 1 : -1;
    }

    void adjustSpeed(double notches) {
        flySpeed = MathHelper.clamp(flySpeed * Math.pow(1.25, notches), 0.0025, 1);
    }

    double topSpeed() {
        return flySpeed * 0.98 / (1 - DRAG) * (1000 / TICK_MS);
    }
}
