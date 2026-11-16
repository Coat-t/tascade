package com.coatt.tascade.mixin;

import com.coatt.tascade.integration.RunGoals;
import com.coatt.tascade.world.TascadeBarters;
import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.entity.mob.PiglinBrain;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PiglinBrain.class)
public abstract class PiglinBrainMixin {
  @Inject(at = @At("HEAD"), method = "getBarteredItem", cancellable = true)
  private static void tascade$seededBarter(PiglinEntity piglin, CallbackInfoReturnable<List<ItemStack>> cir) {
    RunGoals.reach(RunGoals.BARTER);
    if (WorldSeeds.active()) {
      cir.setReturnValue(TascadeBarters.next(piglin));
    }
  }
}
