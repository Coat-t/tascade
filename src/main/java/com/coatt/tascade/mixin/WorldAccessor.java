package com.coatt.tascade.mixin;

import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(World.class)
public interface WorldAccessor {
  @Mutable
  @Accessor("biomeAccess")
  void tascade$setBiomeAccess(BiomeAccess biomeAccess);
}
