package com.coatt.tascade.mixin;

import com.coatt.tascade.world.Clutter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.DungeonFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

@Mixin(DungeonFeature.class)
public abstract class DungeonFeatureMixin {
  @Inject(at = @At("HEAD"), method = "generate", cancellable = true)
  private void tascade$skipClutter(ServerWorldAccess world, StructureAccessor accessor, ChunkGenerator generator, Random random,
      BlockPos pos, DefaultFeatureConfig config, CallbackInfoReturnable<Boolean> cir) {
    if (Clutter.skipDungeon(pos)) {
      cir.setReturnValue(false);
    }
  }
}
