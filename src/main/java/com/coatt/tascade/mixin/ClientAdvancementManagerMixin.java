package com.coatt.tascade.mixin;

import com.coatt.tascade.replay.record.ClientSampler;
import net.minecraft.client.network.ClientAdvancementManager;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientAdvancementManager.class)
public abstract class ClientAdvancementManagerMixin {
  @Inject(at = @At("TAIL"), method = "onAdvancements")
  private void tascade$recordAdvancements(AdvancementUpdateS2CPacket packet, CallbackInfo ci) {
    ClientSampler.advancements((ClientAdvancementManager) (Object) this, packet);
  }
}
