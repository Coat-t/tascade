package com.coatt.tascade.mixin;

import net.minecraft.world.MobSpawnerLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MobSpawnerLogic.class)
public interface MobSpawnerLogicAccessor {
  @Accessor("spawnDelay")
  int tascade$getSpawnDelay();

  @Invoker("isPlayerInRange")
  boolean tascade$isPlayerInRange();
}
