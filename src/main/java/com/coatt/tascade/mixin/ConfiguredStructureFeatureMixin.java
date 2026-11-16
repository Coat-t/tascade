package com.coatt.tascade.mixin;

import com.coatt.tascade.world.Clutter;
import net.minecraft.structure.StructureManager;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.StructureConfig;
import net.minecraft.world.gen.feature.ConfiguredStructureFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConfiguredStructureFeature.class)
public abstract class ConfiguredStructureFeatureMixin {
  @Inject(at = @At("HEAD"), method = "method_28622", cancellable = true)
  private void tascade$skipClutter(ChunkGenerator generator, BiomeSource biomeSource, StructureManager manager, long seed,
      ChunkPos chunkPos, Biome biome, int references, StructureConfig config, CallbackInfoReturnable<StructureStart<?>> cir) {
    if (Clutter.skipStart(((ConfiguredStructureFeature<?, ?>) (Object) this).field_24835, chunkPos, biome)) {
      cir.setReturnValue(StructureStart.DEFAULT);
    }
  }
}
