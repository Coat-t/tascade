package com.coatt.tascade.mixin;

import com.coatt.tascade.world.MobSpawns;
import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.MobSpawnerLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MobSpawnerBlockEntity.class)
public abstract class MobSpawnerBlockEntityMixin {
  @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/MobSpawnerLogic;update()V"))
  private void tascade$seededSpawn(MobSpawnerLogic logic) {
    BlockEntity self = (BlockEntity) (Object) this;
    MobSpawnerLogicAccessor spawner = (MobSpawnerLogicAccessor) logic;
    if (self.getWorld() instanceof ServerWorld && WorldSeeds.active()
        && spawner.tascade$getSpawnDelay() <= 0 && spawner.tascade$isPlayerInRange()) {
      MobSpawns.spawner((ServerWorld) self.getWorld(), self.getPos(), logic::update);
    } else {
      logic.update();
    }
  }
}
