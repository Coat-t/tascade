package com.coatt.tascade.mixin;

import com.coatt.tascade.TascadeKeys;
import com.coatt.tascade.chat.MatchChatOverlay;
import com.coatt.tascade.modifier.ModifierEffects;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.network.TascadeClient;
import com.coatt.tascade.replay.play.ReplayPlayer;
import com.coatt.tascade.screen.RankedQueueScreen;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.GameOptions;
import org.lwjgl.glfw.GLFW;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
  @Shadow private long debugCrashStartTime;

  @Inject(at = @At("HEAD"), method = "onKey", cancellable = true)
  private void tascade$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
    MinecraftClient client = MinecraftClient.getInstance();
    if (ReplayPlayer.onKey(key, action, modifiers)) {
      ci.cancel();
      return;
    }
    if (MatchChatOverlay.onKey(client, key, scancode, action, modifiers)) {
      ci.cancel();
      return;
    }

    if (action != GLFW.GLFW_PRESS || (modifiers & GLFW.GLFW_MOD_CONTROL) == 0
            || !TascadeKeys.LEAVE_QUEUE.matchesKey(key, scancode)) return;
    if (RunState.queueState != RunState.QueueState.QUEUE) return;

    if (client.currentScreen instanceof RankedQueueScreen) {
      ((RankedQueueScreen) client.currentScreen).cancel();
    } else {
      TascadeClient.getInstance().leaveQueue();
    }
    ci.cancel();
  }

  // claimed here rather than in onKey so F3's release doesn't toggle the debug screen
  @Inject(at = @At("HEAD"), method = "processF3", cancellable = true)
  private void tascade$processF3(int key, CallbackInfoReturnable<Boolean> cir) {
    if (ReplayPlayer.swallowsF3(key)) {
      cir.setReturnValue(true);
    } else if (ModifierEffects.noF3()) {
      cir.setReturnValue(ModifierEffects.swallowsF3(key));
    }
  }

  @Redirect(method = "onKey", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
      target = "Lnet/minecraft/client/options/GameOptions;debugEnabled:Z"))
  private void tascade$noF3DebugScreen(GameOptions options, boolean enabled) {
    options.debugEnabled = enabled && !ModifierEffects.noF3();
  }

  @ModifyArg(method = "onKey", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;openPauseMenu(Z)V"))
  private boolean tascade$noF3Pause(boolean pauseOnly) {
    return pauseOnly && !ModifierEffects.noF3();
  }

  @Inject(at = @At("HEAD"), method = "pollDebugCrash", cancellable = true)
  private void tascade$noF3Crash(CallbackInfo ci) {
    if (!ModifierEffects.noF3()) return;
    this.debugCrashStartTime = -1;
    ci.cancel();
  }

  @Inject(at = @At("HEAD"), method = "onChar", cancellable = true)
  private void tascade$onChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
    if (MatchChatOverlay.onChar(MinecraftClient.getInstance(), codePoint, modifiers)) ci.cancel();
  }
}
