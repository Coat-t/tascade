package com.coatt.tascade.mixin;

import com.coatt.tascade.world.DragonTweaks;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.AbstractPhase;
import net.minecraft.entity.boss.dragon.phase.TakeoffPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

@Mixin(TakeoffPhase.class)
public abstract class TakeoffPhaseMixin extends AbstractPhase {
  private TakeoffPhaseMixin(EnderDragonEntity dragon) {
    super(dragon);
  }

  @ModifyConstant(method = "method_6859", constant = @Constant(floatValue = 20.0F))
  private float tascade$maxHeightOffset(float vanilla) {
    return DragonTweaks.MAX_HEIGHT_OFFSET;
  }

  @Redirect(method = "method_6859", at = @At(value = "INVOKE", target = "Ljava/util/Random;nextFloat()F"))
  private float tascade$seededHeightRoll(Random random) {
    return DragonTweaks.nextHeightRoll(this.dragon);
  }
}
