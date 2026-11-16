package com.coatt.tascade.mixin;

import com.coatt.tascade.modifier.ModifierEffects;
import com.coatt.tascade.mixin.access.SlotAccessor;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
  @Unique private static final int LOCKED_SLOT = 0xFF555555;
  @Unique private static final int LOCKED_SLOT_EDGE = 0xFF464646;

  @Inject(at = @At("HEAD"), method = "drawSlot")
  private void tascade$shadeLockedSlot(MatrixStack matrices, Slot slot, CallbackInfo ci) {
    if (!ModifierEffects.pocketLocked(slot.inventory, ((SlotAccessor) slot).tascade$getIndex())) return;
    int x = slot.x;
    int y = slot.y;
    DrawableHelper.fill(matrices, x, y, x + 16, y + 16, LOCKED_SLOT_EDGE);
    DrawableHelper.fill(matrices, x + 1, y + 1, x + 15, y + 15, LOCKED_SLOT);
  }
}
