package com.coatt.tascade.mixin;

import com.coatt.tascade.world.PortalBuilder;
import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PortalForcer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// nether portals at y >= 50 get their overworld portal on the surface, lower ones stay vanilla (stronghold entries)
@Mixin(PortalForcer.class)
public abstract class PortalForcerMixin {
  private static final int SURFACE_FROM_NETHER_Y = 50;

  @Shadow @Final private ServerWorld world;

  @Inject(at = @At("HEAD"), method = "createPortal", cancellable = true)
  private void tascade$surfacePortal(Entity entity, CallbackInfoReturnable<Boolean> cir) {
    // x/z are already scaled, y is still the Nether portal's
    if (!WorldSeeds.active() || this.world.getRegistryKey() != World.OVERWORLD
        || entity.getY() < SURFACE_FROM_NETHER_Y) {
      return;
    }
    PortalBuilder.Spot spot = PortalBuilder.findSurfaceSpot(this.world, entity.getBlockPos());
    PortalBuilder.build(this.world, spot.base, Direction.Axis.X, spot.floating);
    cir.setReturnValue(true);
  }
}
