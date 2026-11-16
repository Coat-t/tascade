package com.coatt.tascade.mixin;

import com.coatt.tascade.world.MobSpawns;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(Entity.class)
public abstract class EntityMixin {
  @Shadow @Final protected Random random;

  @Inject(at = @At("RETURN"), method = "<init>")
  private void tascade$seededRandom(EntityType<?> type, World world, CallbackInfo ci) {
    Random rolling = MobSpawns.rolling();
    if (rolling != null) {
      random.setSeed(rolling.nextLong());
    }
  }
}
