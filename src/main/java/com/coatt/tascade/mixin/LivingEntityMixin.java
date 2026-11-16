package com.coatt.tascade.mixin;

import com.coatt.tascade.modifier.ModifierEffects;
import com.coatt.tascade.world.MobDrops;
import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContext;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
  @Shadow protected int playerHitTimer;

  @Shadow
  protected abstract void drop(DamageSource source);

  @Redirect(method = "onDeath", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/entity/LivingEntity;drop(Lnet/minecraft/entity/damage/DamageSource;)V"))
  private void tascade$seededDrops(LivingEntity self, DamageSource source) {
    if (WorldSeeds.active() && self.world instanceof ServerWorld && playerHitTimer > 0) {
      MobDrops.kill(self, () -> drop(source));
    } else {
      drop(source);
    }
  }

  @Redirect(method = "dropLoot", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/loot/LootTable;generateLoot(Lnet/minecraft/loot/context/LootContext;Ljava/util/function/Consumer;)V"))
  private void tascade$fixedLoot(LootTable table, LootContext context, Consumer<ItemStack> drop) {
    if (WorldSeeds.active() && ((LivingEntity) (Object) this).getType() == EntityType.IRON_GOLEM) {
      MobDrops.ironGolemLoot(table.generateLoot(context)).forEach(drop);
    } else {
      table.generateLoot(context, drop);
    }
  }

  @ModifyVariable(method = "addStatusEffect", at = @At("HEAD"), argsOnly = true)
  private StatusEffectInstance tascade$flammable(StatusEffectInstance effect) {
    return ModifierEffects.incoming((LivingEntity) (Object) this, effect);
  }
}
