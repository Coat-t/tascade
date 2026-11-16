package com.coatt.tascade.mixin.access;

import net.minecraft.client.render.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DebugRenderer.class)
public interface DebugRendererAccessor {
    @Accessor("showChunkBorder")
    boolean tascade$showsChunkBorder();
}
