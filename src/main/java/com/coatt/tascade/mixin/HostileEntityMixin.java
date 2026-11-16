package com.coatt.tascade.mixin;

import com.coatt.tascade.world.ZombifiedPiglins;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HostileEntity.class)
public abstract class HostileEntityMixin {
  // keeps calm zombified piglins out of bastions (see ZombifiedPiglins)
  @Inject(at = @At("RETURN"), method = "getPathfindingFavor", cancellable = true)
  private void tascade$avoidBastions(BlockPos pos, WorldView world, CallbackInfoReturnable<Float> cir) {
    MobEntity self = (MobEntity) (Object) this;
    if (ZombifiedPiglins.keepOut(self) && ZombifiedPiglins.inBastion((ServerWorld) self.world, pos)) {
      cir.setReturnValue(ZombifiedPiglins.inBastion((ServerWorld) self.world, self.getBlockPos())
          ? cir.getReturnValue() - ZombifiedPiglins.INSIDE_PENALTY : Float.NEGATIVE_INFINITY);
    }
  }
}
