package com.coatt.tascade.mixin;

import com.coatt.tascade.modifier.ModifierEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.recipe.InputSlotFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InputSlotFiller.class)
public abstract class InputSlotFillerMixin {
  @Shadow protected PlayerInventory inventory;

  // Small Pockets: only hotbar slots count as free, so the grid never clears into locked slots
  @Inject(at = @At("HEAD"), method = "getFreeInventorySlots", cancellable = true)
  private void tascade$smallPockets(CallbackInfoReturnable<Integer> cir) {
    int size = ModifierEffects.pocketSize(this.inventory.main.size());
    if (size == this.inventory.main.size()) return;
    int free = 0;
    for (int i = 0; i < size; i++) {
      if (this.inventory.main.get(i).isEmpty()) free++;
    }
    cir.setReturnValue(free);
  }
}
