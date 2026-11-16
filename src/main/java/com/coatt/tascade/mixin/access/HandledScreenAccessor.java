package com.coatt.tascade.mixin.access;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int tascade$getX();

    @Accessor("x")
    void tascade$setX(int x);

    @Accessor("y")
    int tascade$getY();

    @Accessor("y")
    void tascade$setY(int y);

    @Accessor("backgroundWidth")
    int tascade$getBackgroundWidth();

    @Accessor("backgroundHeight")
    int tascade$getBackgroundHeight();
}
