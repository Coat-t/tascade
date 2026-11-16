package com.coatt.tascade.mixin;

import com.coatt.tascade.modifier.ModifierEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {
  // Small Pockets: all automatic inserts find their slot through these two
  @Redirect(method = {"getEmptySlot", "getOccupiedSlotWithRoomForStack"}, require = 2,
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/collection/DefaultedList;size()I"))
  private int tascade$smallPockets(DefaultedList<?> main) {
    return ModifierEffects.pocketSize(main.size());
  }
}
