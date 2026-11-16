package com.coatt.tascade.mixin;

import com.coatt.tascade.modifier.ModifierEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
  @Inject(at = @At("HEAD"), method = "jump", cancellable = true)
  private void tascade$noJump(CallbackInfo ci) {
    if (ModifierEffects.noJump((PlayerEntity) (Object) this)) ci.cancel();
  }

  // respawn side is in ServerPlayerEntityMixin
  @Redirect(method = {"dropInventory", "getCurrentExperience"}, require = 2, at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/GameRules;getBoolean(Lnet/minecraft/world/GameRules$Key;)Z"))
  private boolean tascade$keepInventory(GameRules rules, GameRules.Key<GameRules.BooleanRule> key) {
    boolean value = rules.getBoolean(key);
    return key == GameRules.KEEP_INVENTORY ? ModifierEffects.keepInventory((PlayerEntity) (Object) this, value) : value;
  }
}
