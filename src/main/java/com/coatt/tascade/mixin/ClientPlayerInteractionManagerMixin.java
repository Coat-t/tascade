package com.coatt.tascade.mixin;

import com.coatt.tascade.replay.play.CameraEntity;
import com.coatt.tascade.replay.play.ReplayPlayer;
import com.coatt.tascade.replay.record.ClientSampler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.stat.StatHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
  @Shadow @Final private MinecraftClient client;
  @Shadow @Final private ClientPlayNetworkHandler networkHandler;

  @Inject(at = @At("HEAD"), method = "createPlayer", cancellable = true)
  private void tascade$replayCamera(ClientWorld world, StatHandler stats, ClientRecipeBook recipeBook,
                                    boolean sneaking, boolean sprinting, CallbackInfoReturnable<ClientPlayerEntity> cir) {
    if (ReplayPlayer.isReplayConnection(networkHandler.getConnection())) {
      cir.setReturnValue(new CameraEntity(client, world, networkHandler, stats, recipeBook));
    }
  }

  @Inject(at = @At("HEAD"), method = "clickSlot")
  private void tascade$recordClick(int syncId, int slotId, int clickData, SlotActionType actionType, PlayerEntity player,
                                   CallbackInfoReturnable<ItemStack> cir) {
    ClientSampler.click(syncId, slotId, clickData, actionType);
  }
}
