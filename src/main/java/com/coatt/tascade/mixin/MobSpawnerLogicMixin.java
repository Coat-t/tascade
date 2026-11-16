package com.coatt.tascade.mixin;

import com.coatt.tascade.world.MobSpawns;
import net.minecraft.world.MobSpawnerLogic;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

@Mixin(MobSpawnerLogic.class)
public abstract class MobSpawnerLogicMixin {
  @Redirect(method = {"update", "updateSpawns"},
      at = @At(value = "FIELD", target = "Lnet/minecraft/world/World;random:Ljava/util/Random;", opcode = Opcodes.GETFIELD))
  private Random tascade$spawnRandom(World world) {
    return MobSpawns.random(world);
  }
}
