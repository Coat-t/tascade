package com.coatt.tascade.mixin;

import com.coatt.tascade.world.ZombifiedPiglins;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.world.BlockView;
import net.minecraft.world.chunk.ChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(LandPathNodeMaker.class)
public abstract class LandPathNodeMakerMixin {
  @Unique private Long2ObjectMap<List<BlockBox>> tascade$bastionPieces;

  @Inject(at = @At("TAIL"), method = "init")
  private void tascade$lookForBastions(ChunkCache cachedWorld, MobEntity entity, CallbackInfo ci) {
    tascade$bastionPieces = ZombifiedPiglins.pathsAround(entity) ? new Long2ObjectOpenHashMap<>() : null;
  }

  // also used by MobNavigation for corner cutting
  @Inject(at = @At("RETURN"), cancellable = true,
      method = "getNodeType(Lnet/minecraft/world/BlockView;IIILnet/minecraft/entity/mob/MobEntity;IIIZZ)Lnet/minecraft/entity/ai/pathing/PathNodeType;")
  private void tascade$bastionsAreWalls(BlockView world, int x, int y, int z, MobEntity mob, int sizeX, int sizeY, int sizeZ,
      boolean canOpenDoors, boolean canEnterOpenDoors, CallbackInfoReturnable<PathNodeType> cir) {
    if (tascade$bastionPieces != null && ZombifiedPiglins.inBastion((ServerWorld) mob.world, tascade$bastionPieces, x, y, z)) {
      cir.setReturnValue(PathNodeType.BLOCKED);
    }
  }
}
