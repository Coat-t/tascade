package com.coatt.tascade.mixin.access;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityInvoker {
    @Invoker("setFlag")
    void tascade$setFlag(int index, boolean value);
}
