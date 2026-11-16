package com.coatt.tascade.mixin;

import com.coatt.tascade.world.MobSpawns;
import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.SurfaceChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

@Mixin(SurfaceChunkGenerator.class)
public abstract class SurfaceChunkGeneratorMixin {
  @Redirect(method = "populateEntities", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/SpawnHelper;populateEntities(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/world/biome/Biome;IILjava/util/Random;)V"))
  private void tascade$seededAnimals(WorldAccess world, Biome biome, int chunkX, int chunkZ, Random random) {
    if (WorldSeeds.active()) {
      MobSpawns.generated((ServerWorld) world.getWorld(), chunkX, chunkZ,
          () -> SpawnHelper.populateEntities(world, biome, chunkX, chunkZ, random));
    } else {
      SpawnHelper.populateEntities(world, biome, chunkX, chunkZ, random);
    }
  }
}
