package com.coatt.tascade.mixin;

import com.coatt.tascade.world.EnderEyes;
import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EyeOfEnderEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EyeOfEnderEntity.class)
public abstract class EyeOfEnderEntityMixin {
  @Shadow private boolean dropsItem;

  // called once per throw, server-side
  @Inject(at = @At("TAIL"), method = "moveTowards")
  private void tascade$seededBreak(BlockPos pos, CallbackInfo ci) {
    Entity self = (Entity) (Object) this;
    if (self.world instanceof ServerWorld && WorldSeeds.active()) {
      dropsItem = EnderEyes.nextDrops(((ServerWorld) self.world).getServer());
    }
  }
}
