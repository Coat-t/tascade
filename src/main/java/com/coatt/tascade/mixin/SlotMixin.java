package com.coatt.tascade.mixin;

import com.coatt.tascade.modifier.ModifierEffects;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {
  @Shadow @Final private int index;
  @Shadow @Final public Inventory inventory;

  @Inject(at = @At("HEAD"), method = "canInsert", cancellable = true)
  private void tascade$smallPockets(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
    if (ModifierEffects.pocketLocked(this.inventory, this.index)) cir.setReturnValue(false);
  }
}
