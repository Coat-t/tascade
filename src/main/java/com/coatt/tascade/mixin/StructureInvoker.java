package com.coatt.tascade.mixin;

import net.minecraft.structure.Structure;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Structure.class)
public interface StructureInvoker {
  @Invoker("spawnEntities")
  void tascade$spawnEntities(WorldAccess world, BlockPos pos, BlockMirror mirror, BlockRotation rotation, BlockPos pivot,
      @Nullable BlockBox area, boolean initializeMobs);
}
