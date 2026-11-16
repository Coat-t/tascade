package com.coatt.tascade.mixin;

import com.coatt.tascade.world.DragonTweaks;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.AbstractPhase;
import net.minecraft.entity.boss.dragon.phase.StrafePlayerPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

@Mixin(StrafePlayerPhase.class)
public abstract class StrafePlayerPhaseMixin extends AbstractPhase {
  private StrafePlayerPhaseMixin(EnderDragonEntity dragon) {
    super(dragon);
  }

  @ModifyConstant(method = "method_6861", constant = @Constant(floatValue = 20.0F))
  private float tascade$maxHeightOffset(float vanilla) {
    return DragonTweaks.MAX_HEIGHT_OFFSET;
  }

  @Redirect(method = "method_6861", at = @At(value = "INVOKE", target = "Ljava/util/Random;nextFloat()F"))
  private float tascade$seededHeightRoll(Random random) {
    return DragonTweaks.nextHeightRoll(this.dragon);
  }
}
