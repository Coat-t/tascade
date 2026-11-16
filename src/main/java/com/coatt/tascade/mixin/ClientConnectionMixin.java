package com.coatt.tascade.mixin;

import com.coatt.tascade.replay.play.ReplayPlayer;
import com.coatt.tascade.replay.record.ReplayRecorder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
  @Shadow @Final private NetworkSide side;

  // runs on the network thread
  @Inject(at = @At("HEAD"),
          method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V")
  private void tascade$recordPacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
    if (side == NetworkSide.CLIENTBOUND) ReplayRecorder.onPacket((ClientConnection) (Object) this, packet);
  }

  @Inject(at = @At("HEAD"), cancellable = true,
          method = "send(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;)V")
  private void tascade$dropReplaySends(Packet<?> packet, GenericFutureListener<? extends Future<? super Void>> callback,
                                       CallbackInfo ci) {
    if (ReplayPlayer.isReplayConnection((ClientConnection) (Object) this)) ci.cancel();
  }
}
