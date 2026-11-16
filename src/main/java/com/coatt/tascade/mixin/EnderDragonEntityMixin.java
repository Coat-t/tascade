package com.coatt.tascade.mixin;

import com.coatt.tascade.integration.RunGoals;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragonEntity.class)
public abstract class EnderDragonEntityMixin {
  // ticks every frame at 0 health, the server's first call is the animation's start
  @Inject(at = @At("HEAD"), method = "updatePostDeath")
  private void tascade$dragonDyingGoal(CallbackInfo ci) {
    EnderDragonEntity dragon = (EnderDragonEntity) (Object) this;
    if (!dragon.world.isClient && dragon.ticksSinceDeath == 0) RunGoals.reach(RunGoals.DRAGON_DYING);
  }
}
