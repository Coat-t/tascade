package com.coatt.tascade.mixin.access;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraInvoker {
    @Invoker("setPos")
    void tascade$setPos(double x, double y, double z);

    @Invoker("setRotation")
    void tascade$setRotation(float yaw, float pitch);
}
