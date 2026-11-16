package com.coatt.tascade.mixin;

import com.coatt.tascade.world.MobSpawns;
import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.Structure;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Structure.class)
public abstract class StructureMixin {
  @Redirect(method = "place(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;"
      + "Lnet/minecraft/structure/StructurePlacementData;Ljava/util/Random;I)Z", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/structure/Structure;spawnEntities(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;"
          + "Lnet/minecraft/util/BlockMirror;Lnet/minecraft/util/BlockRotation;Lnet/minecraft/util/math/BlockPos;"
          + "Lnet/minecraft/util/math/BlockBox;Z)V"))
  private void tascade$seededMobs(Structure self, WorldAccess world, BlockPos pos, BlockMirror mirror, BlockRotation rotation,
      BlockPos pivot, BlockBox area, boolean initializeMobs) {
    StructureInvoker structure = (StructureInvoker) self;
    if (WorldSeeds.active() && world.getWorld() instanceof ServerWorld) {
      MobSpawns.structure((ServerWorld) world.getWorld(), pos, area,
          () -> structure.tascade$spawnEntities(world, pos, mirror, rotation, pivot, area, initializeMobs));
    } else {
      structure.tascade$spawnEntities(world, pos, mirror, rotation, pivot, area, initializeMobs);
    }
  }
}
