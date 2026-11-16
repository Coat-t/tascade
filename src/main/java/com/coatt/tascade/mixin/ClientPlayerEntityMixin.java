package com.coatt.tascade.mixin;

import com.coatt.tascade.chat.MatchChat;
import com.coatt.tascade.mixin.access.LivingEntityAccessor;
import com.coatt.tascade.modifier.ModifierEffects;
import com.coatt.tascade.replay.record.ClientSampler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
  @Shadow public Input input;

  @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick(Z)V", shift = At.Shift.AFTER),
      method = "tickMovement")
  private void tascade$noJump(CallbackInfo ci) {
    if (ModifierEffects.noJump((ClientPlayerEntity) (Object) this)) this.input.jumping = false;
  }

  @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick(Z)V", shift = At.Shift.AFTER),
      method = "tickMovement")
  private void tascade$oneLegged(CallbackInfo ci) {
    ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
    if (!ModifierEffects.hopOnly(self, this.input.jumping, ((LivingEntityAccessor) self).tascade$getJumpingCooldown())) return;
    this.input.movementForward = 0;
    this.input.movementSideways = 0;
    this.input.pressingForward = false;
    this.input.pressingBack = false;
    this.input.pressingLeft = false;
    this.input.pressingRight = false;
  }

  @Inject(at = @At("HEAD"), method = "autoJump", cancellable = true)
  private void tascade$noAutoJump(float dx, float dz, CallbackInfo ci) {
    ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
    if (ModifierEffects.noJump(self) || ModifierEffects.oneLegged(self)) ci.cancel();
  }

  @Inject(at = @At("HEAD"), method = "sendChatMessage")
  private void tascade$relayChat(String message, CallbackInfo ci) {
    if (message.startsWith("/")) return;
    if (!MinecraftClient.getInstance().isInSingleplayer()) return;
    MatchChat.sentFromWorld(message);
  }

  @Inject(at = @At("HEAD"), method = "swingHand")
  private void tascade$recordSwing(Hand hand, CallbackInfo ci) {
    if ((Object) this == MinecraftClient.getInstance().player) ClientSampler.swing(hand);
  }
}
