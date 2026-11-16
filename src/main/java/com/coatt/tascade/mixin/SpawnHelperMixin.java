package com.coatt.tascade.mixin;

import com.coatt.tascade.world.MobSpawns;
import com.coatt.tascade.world.WorldSeeds;
import com.coatt.tascade.world.ZombifiedPiglins;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

@Mixin(SpawnHelper.class)
public abstract class SpawnHelperMixin {
  @Inject(at = @At("HEAD"), method = "spawn", cancellable = true)
  private static void tascade$waitForTurn(ServerWorld world, WorldChunk chunk, SpawnHelper.Info info, boolean spawnAnimals,
      boolean spawnMonsters, boolean shouldSpawnAnimals, CallbackInfo ci) {
    if (WorldSeeds.active() && MobSpawns.queueTurn(world, chunk,
        () -> SpawnHelper.spawn(world, chunk, info, spawnAnimals, spawnMonsters, shouldSpawnAnimals))) {
      ci.cancel();
    }
  }

  @Redirect(method = "spawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/SpawnHelper;spawnEntitiesInChunk("
      + "Lnet/minecraft/entity/SpawnGroup;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/WorldChunk;"
      + "Lnet/minecraft/world/SpawnHelper$Checker;Lnet/minecraft/world/SpawnHelper$Runner;)V"))
  private static void tascade$seededTry(SpawnGroup group, ServerWorld world, WorldChunk chunk,
      SpawnHelper.Checker checker, SpawnHelper.Runner runner) {
    if (WorldSeeds.active()) {
      MobSpawns.natural(world, chunk.getPos(), group, () -> SpawnHelper.spawnEntitiesInChunk(group, world, chunk, checker, runner));
    } else {
      SpawnHelper.spawnEntitiesInChunk(group, world, chunk, checker, runner);
    }
  }

  @Redirect(method = {
      "spawnEntitiesInChunk(Lnet/minecraft/entity/SpawnGroup;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/Chunk;"
          + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/SpawnHelper$Checker;Lnet/minecraft/world/SpawnHelper$Runner;)V",
      "canSpawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/SpawnGroup;Lnet/minecraft/world/gen/StructureAccessor;"
          + "Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/world/biome/Biome$SpawnEntry;Lnet/minecraft/util/math/BlockPos$Mutable;D)Z"},
      at = @At(value = "FIELD", target = "Lnet/minecraft/server/world/ServerWorld;random:Ljava/util/Random;", opcode = Opcodes.GETFIELD))
  private static Random tascade$tryRandom(ServerWorld world) {
    return MobSpawns.random(world);
  }

  @Redirect(method = "getSpawnPos", at = @At(value = "FIELD", target = "Lnet/minecraft/world/World;random:Ljava/util/Random;", opcode = Opcodes.GETFIELD))
  private static Random tascade$spotRandom(World world) {
    return MobSpawns.random(world);
  }

  // tries that pick a zombified piglin in a bastion just fail
  @Inject(at = @At("HEAD"), cancellable = true, method = "canSpawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/SpawnGroup;"
      + "Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/world/biome/Biome$SpawnEntry;"
      + "Lnet/minecraft/util/math/BlockPos$Mutable;D)Z")
  private static void tascade$noZombifiedPiglinsInBastions(ServerWorld world, SpawnGroup group, StructureAccessor structures,
      ChunkGenerator generator, Biome.SpawnEntry entry, BlockPos.Mutable pos, double squaredDistance, CallbackInfoReturnable<Boolean> cir) {
    if (entry.type == EntityType.ZOMBIFIED_PIGLIN && WorldSeeds.active() && ZombifiedPiglins.inBastion(world, pos)) {
      cir.setReturnValue(false);
    }
  }
}
