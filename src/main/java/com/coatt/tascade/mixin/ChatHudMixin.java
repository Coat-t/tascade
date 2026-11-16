package com.coatt.tascade.mixin;

import com.coatt.tascade.chat.MatchChatOverlay;
import com.coatt.tascade.replay.record.ClientSampler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
  // hidden while the match chat panel is open
  @Inject(at = @At("HEAD"), method = "render", cancellable = true)
  private void tascade$hideUnderMatchChat(MatrixStack matrices, int ticks, CallbackInfo ci) {
    if (MatchChatOverlay.hidesVanillaChat(MinecraftClient.getInstance())) ci.cancel();
  }

  @Inject(at = @At("HEAD"), method = "addMessage(Lnet/minecraft/text/Text;I)V")
  private void tascade$recordChat(Text message, int messageId, CallbackInfo ci) {
    ClientSampler.chat(message, messageId);
  }

  @Inject(at = @At("HEAD"), method = "clear")
  private void tascade$recordChatClear(boolean clearHistory, CallbackInfo ci) {
    ClientSampler.chatCleared();
  }
}
