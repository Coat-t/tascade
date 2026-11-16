package com.coatt.tascade.mixin;

import com.coatt.tascade.world.TascadeLoot;
import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.loot.LootManager;
import net.minecraft.loot.LootTable;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LootManager.class)
public abstract class LootManagerMixin {
  @Inject(at = @At("RETURN"), method = "getTable")
  private void tascade$tascadeLoot(Identifier id, CallbackInfoReturnable<LootTable> cir) {
    if (WorldSeeds.active()) {
      TascadeLoot.track(id, cir.getReturnValue());
    }
  }
}
