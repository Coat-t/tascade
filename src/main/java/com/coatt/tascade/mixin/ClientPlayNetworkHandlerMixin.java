package com.coatt.tascade.mixin;

import com.coatt.tascade.Main;
import com.coatt.tascade.finish.RunFinish;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.replay.play.ReplayPlayer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
  // skip credits in a run. After forceMainThread, so client thread only
  @Inject(method = "onGameStateChange", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
          target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V"))
  private void tascade$skipCredits(GameStateChangeS2CPacket packet, CallbackInfo ci) {
    if (packet.getReason() != GameStateChangeS2CPacket.GAME_WON) return;
    if (!RunState.isRunning() && !RunFinish.isActive()) return;
    RunFinish.awaitRespawn();
    // SpeedRunIGT completes any% when credits open, do it here instead
    if (!Main.completeRunOnGameWon()) return;
    ci.cancel();
    // the credits screen's "done" action
    ((ClientPlayNetworkHandler) (Object) this).sendPacket(
            new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN));
  }

  // the camera takes its own entity id, the recorded player keeps theirs
  @Redirect(method = "onGameJoin", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/network/packet/s2c/play/GameJoinS2CPacket;getEntityId()I"))
  private int tascade$replayCameraId(GameJoinS2CPacket packet) {
    if (ReplayPlayer.isReplayConnection(((ClientPlayNetworkHandler) (Object) this).getConnection())) {
      return ReplayPlayer.CAMERA_ENTITY_ID;
    }
    return packet.getEntityId();
  }
}
