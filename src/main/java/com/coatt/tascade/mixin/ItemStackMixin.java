package com.coatt.tascade.mixin;

import com.coatt.tascade.modifier.ModifierEffects;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
  @ModifyVariable(method = "getTooltip", at = @At("HEAD"), argsOnly = true)
  private TooltipContext tascade$noAdvancedTooltips(TooltipContext context) {
    return context.isAdvanced() && ModifierEffects.noF3() ? TooltipContext.Default.NORMAL : context;
  }
}
