package com.coatt.tascade.mixin;

import com.coatt.tascade.modifier.ModifierEffects;
import com.coatt.tascade.replay.record.DeathResets;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
  @Unique private ModifierEffects.Respawn tascade$respawn;

  @Inject(at = @At("HEAD"), method = "respawnPlayer")
  private void tascade$planRespawn(ServerPlayerEntity player, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
    this.tascade$respawn = ModifierEffects.planRespawn(player, alive);
  }

  @Redirect(method = "respawnPlayer", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/entity/player/PlayerEntity;findRespawnPosition(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;ZZ)Ljava/util/Optional;"))
  private Optional<Vec3d> tascade$splitSpawnPosition(ServerWorld world, BlockPos pos, boolean forced, boolean alive) {
    ModifierEffects.Respawn plan = this.tascade$respawn;
    return plan != null ? plan.at : PlayerEntity.findRespawnPosition(world, pos, forced, alive);
  }

  @Redirect(method = "respawnPlayer", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/server/network/ServerPlayerEntity;refreshPositionAndAngles(DDDFF)V"))
  private void tascade$splitSpawnFacing(ServerPlayerEntity player, double x, double y, double z, float yaw, float pitch) {
    ModifierEffects.Respawn plan = this.tascade$respawn;
    if (plan != null && plan.at.isPresent()) {
      yaw = plan.spot.yaw;
      pitch = plan.spot.pitch;
    }
    player.refreshPositionAndAngles(x, y, z, yaw, pitch);
  }

  // the first packet respawnPlayer sends
  @Redirect(method = "respawnPlayer", at = @At(value = "INVOKE", ordinal = 0,
      target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/Packet;)V"))
  private void tascade$noBedMessage(ServerPlayNetworkHandler handler, Packet<?> packet) {
    if (this.tascade$respawn != null && packet instanceof GameStateChangeS2CPacket) return;
    handler.sendPacket(packet);
  }

  @Inject(at = @At("RETURN"), method = "respawnPlayer")
  private void tascade$noteDeathReset(ServerPlayerEntity player, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
    ServerPlayerEntity respawned = cir.getReturnValue();
    boolean atRespawnPoint = respawned.getSpawnPointPosition() != null;
    ModifierEffects.Respawn plan = this.tascade$respawn;
    this.tascade$respawn = null;
    if (plan != null) ModifierEffects.respawned(respawned, plan);
    if (!alive) DeathResets.respawned(player, atRespawnPoint);
  }

  @Inject(at = @At("HEAD"), method = "remove")
  private void tascade$settleDeath(ServerPlayerEntity player, CallbackInfo ci) {
    DeathResets.left(player);
  }
}
