package com.coatt.tascade.mixin;

import com.coatt.tascade.hud.TascadeTabList;
import com.coatt.tascade.network.RunState;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

  @Inject(at = @At("HEAD"), method = "render")
  private void tascade$setRunInfo(MatrixStack matrices, int scaledWindowWidth, Scoreboard scoreboard,
                                  ScoreboardObjective objective, CallbackInfo ci) {
    if (!RunState.isRunning()) return;
    PlayerListHud hud = (PlayerListHud) (Object) this;
    hud.setHeader(TascadeTabList.header());
    hud.setFooter(TascadeTabList.footer());
  }

  @Redirect(method = "render", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;getPlayerList()Ljava/util/Collection;"))
  private Collection<PlayerListEntry> tascade$addOpponent(ClientPlayNetworkHandler handler) {
    return TascadeTabList.withOpponent(handler.getPlayerList());
  }
}
