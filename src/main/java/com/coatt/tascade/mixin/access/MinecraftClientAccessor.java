package com.coatt.tascade.mixin.access;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.profiler.ProfileResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("tickProfilerResult")
    ProfileResult tascade$getTickProfilerResult();

    // parts joined by '\u001e'
    @Accessor("openProfilerSection")
    String tascade$getOpenProfilerSection();

    @Invoker("drawProfilerResults")
    void tascade$drawProfilerResults(MatrixStack matrices, ProfileResult result);
}
