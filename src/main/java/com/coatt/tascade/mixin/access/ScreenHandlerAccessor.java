package com.coatt.tascade.mixin.access;

import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ScreenHandler.class)
public interface ScreenHandlerAccessor {
    @Accessor("properties")
    List<Property> tascade$getProperties();
}
