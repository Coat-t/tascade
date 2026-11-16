package com.coatt.tascade.mixin.access;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.StringRenderable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatHud.class)
public interface ChatHudInvoker {
    @Invoker("addMessage")
    void tascade$addMessage(StringRenderable message, int messageId, int timestamp, boolean refreshing);
}
