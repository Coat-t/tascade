package com.coatt.tascade.mixin;

import com.coatt.tascade.world.MobSpawns;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

// these use the world's random field directly, which WorldRandomMixin doesn't reach
@Mixin({AnimalEntity.class, WaterCreatureEntity.class})
public abstract class ExperienceRandomMixin {
  @Redirect(method = "getCurrentExperience",
      at = @At(value = "FIELD", target = "Lnet/minecraft/world/World;random:Ljava/util/Random;", opcode = Opcodes.GETFIELD))
  private Random tascade$killRandom(World world) {
    return MobSpawns.random(world);
  }
}
