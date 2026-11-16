package com.coatt.tascade.mixin;

import com.coatt.tascade.world.LavaPool;
import com.coatt.tascade.world.RunDifficulty;
import com.coatt.tascade.world.SpawnSpot;
import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
  @Shadow @Final protected LevelStorage.Session session;

  @Shadow
  public abstract SaveProperties getSaveProperties();

  @Shadow
  public abstract void setDifficulty(Difficulty difficulty, boolean forceUpdate);

  @Inject(at = @At("HEAD"), method = "createWorlds")
  private void tascade$loadWorldSeeds(WorldGenerationProgressListener listener, CallbackInfo ci) {
    WorldSeeds.onCreateWorlds(this.session.getDirectory(WorldSavePath.ROOT));
  }

  @Inject(at = @At("HEAD"), method = "setupSpawn", cancellable = true)
  private static void tascade$filterSpawn(ServerWorld world, ServerWorldProperties properties,
      boolean bonusChest, boolean debugWorld, boolean searchSpawn, CallbackInfo ci) {
    int[] spawn = WorldSeeds.spawn();
    if (spawn == null || debugWorld || !searchSpawn) {
      return;
    }
    properties.setSpawnPos(SpawnSpot.onGround(world, spawn[0], spawn[1]));
    int[][] pools = WorldSeeds.lavaPools();
    if (pools != null) {
      for (int[] pool : pools) {
        LavaPool.place(world, pool[0], pool[1]);
      }
    }
    ci.cancel();
  }

  // peaceful is never allowed, forced to Hard on the first unpaused tick
  @Inject(at = @At("HEAD"), method = "tick(Ljava/util/function/BooleanSupplier;)V")
  private void tascade$noPeacefulTicks(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
    if (RunDifficulty.forbidden(getSaveProperties().getDifficulty())) {
      setDifficulty(RunDifficulty.INSTEAD_OF_PEACEFUL, true);
    }
  }
}
