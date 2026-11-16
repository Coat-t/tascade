package com.coatt.tascade.mixin;

import com.coatt.tascade.chat.MatchChat;
import com.coatt.tascade.finish.RunFinish;
import com.coatt.tascade.integration.DiscordPresence;
import com.coatt.tascade.integration.RunGoals;
import com.coatt.tascade.integration.RunRecorder;
import com.coatt.tascade.modifier.ModifierEffects;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.network.TascadeClient;
import com.coatt.tascade.replay.play.ReplayPlayer;
import com.coatt.tascade.replay.record.ClientSampler;
import com.coatt.tascade.network.RoomState;
import com.coatt.tascade.screen.MainScreen;
import com.coatt.tascade.screen.RoomLobbyScreen;
import com.coatt.tascade.screen.RunNoticeScreen;
import com.coatt.tascade.world.RunWorldOpening;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Lifecycle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.sound.SoundCategory;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
  @Shadow private boolean paused;

  @Unique private boolean tascade$leftRunWorld;

  @Shadow
  protected abstract void handleInputEvents();

  @ModifyExpressionValue(method = "startIntegratedServer(Ljava/lang/String;Lnet/minecraft/util/registry/RegistryTracker$Modifiable;"
          + "Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function4;ZLnet/minecraft/client/MinecraftClient$WorldLoadAction;)V",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/world/SaveProperties;method_29588()Lcom/mojang/serialization/Lifecycle;"))
  private Lifecycle tascade$runWorldsAreStable(Lifecycle lifecycle) {
    return RunWorldOpening.active() ? Lifecycle.stable() : lifecycle;
  }

  @Inject(at = @At("TAIL"), method = "tick")
  private void tascade$tick(CallbackInfo ci) {
    ClientSampler.tick((MinecraftClient) (Object) this);
    MatchChat.applyPending();
    TascadeClient.getInstance().tickRequests();
    TascadeClient.getInstance().tickConnection();
    TascadeClient.getInstance().tickRaceGate();
    RunFinish.tick((MinecraftClient) (Object) this);
    RunGoals.tick((MinecraftClient) (Object) this);
    DiscordPresence.tick((MinecraftClient) (Object) this);
    ModifierEffects.noF3Tick((MinecraftClient) (Object) this);
    TascadeClient.getInstance().startPendingWorld();
  }

  // seed changes spare MASTER sounds so the solo seed change cue isn't cut off
  @Redirect(method = "reset", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/SoundManager;stopAll()V"))
  private void tascade$keepCuesOnSeedChange(SoundManager sounds) {
    if (!RunState.seedChanging) {
      sounds.stopAll();
      return;
    }
    for (SoundCategory category : SoundCategory.values()) {
      if (category != SoundCategory.MASTER) sounds.stopSounds(null, category);
    }
  }

  @Inject(at = @At("HEAD"), method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V")
  private void tascade$onDisconnect(Screen screen, CallbackInfo ci) {
    if (ReplayPlayer.isActive()) {
      ReplayPlayer.onDisconnect();
      return;
    }
    ClientWorld world = ((MinecraftClient) (Object) this).world;
    if (world != null) {
      tascade$leftRunWorld = !RunState.seedChanging && tascade$inRunWorld();
      if (RunFinish.isAwaitingOutcome()) TascadeClient.getInstance().leavingWhileAwaitingOutcome();
      RunFinish.reset();
      TascadeClient.getInstance().onPlayerLeftWorld();
    }
  }

  @Unique
  private boolean tascade$inRunWorld() {
    IntegratedServer server = ((MinecraftClient) (Object) this).getServer();
    String runWorld = RunRecorder.getWorldName();
    return server != null && runWorld != null && runWorld.equals(server.getSaveProperties().getLevelName());
  }

  // opened over the title screen, not instead of it, so vanilla's title-screen cleanup still runs
  @Inject(at = @At("TAIL"), method = "openScreen")
  private void tascade$backToTascadeMenu(Screen screen, CallbackInfo ci) {
    MinecraftClient client = (MinecraftClient) (Object) this;
    if (!tascade$leftRunWorld || client.world != null) return;
    tascade$leftRunWorld = false;
    if (!(client.currentScreen instanceof TitleScreen)) return;
    MainScreen menu = new MainScreen(client.currentScreen);
    if (RoomState.inRoom()) {
      client.openScreen(new RoomLobbyScreen(menu));
      return;
    }
    String removed = RoomState.removedReason;
    RunNoticeScreen notice = removed != null ? RunNoticeScreen.removedFromRoom(menu, removed) : null;
    if (notice != null) RoomState.removedReason = null;
    client.openScreen(notice != null ? notice : menu);
  }

  @Inject(at = @At("TAIL"), method = "joinWorld")
  private void tascade$onJoinWorld(ClientWorld world, CallbackInfo ci) {
    TascadeClient.getInstance().onJoinedWorld();
  }

  // replays tick the world on their own clock
  @Redirect(method = "tick", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
          target = "Lnet/minecraft/client/MinecraftClient;paused:Z"))
  private boolean tascade$replayOwnsWorldTicks(MinecraftClient client) {
    return paused || ReplayPlayer.ownsWorldTicks();
  }

  @Redirect(method = "tick", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/texture/TextureManager;tick()V"))
  private void tascade$replayTextures(TextureManager textures) {
    if (!ReplayPlayer.ownsWorldTicks()) textures.tick();
  }

  @Redirect(method = "tick", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/MinecraftClient;handleInputEvents()V"))
  private void tascade$replayInput(MinecraftClient client) {
    if (ReplayPlayer.isActive()) ReplayPlayer.handleInput();
    else handleInputEvents();
  }

  @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Mouse;updateMouse()V"))
  private void tascade$replayFrame(boolean tick, CallbackInfo ci) {
    ReplayPlayer.frame();
  }

  @ModifyArg(method = "render", index = 0,
          at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;render(FJZ)V"))
  private float tascade$replayTickDelta(float tickDelta) {
    return ReplayPlayer.tickDelta(tickDelta);
  }

  @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;endWrite()V"))
  private void tascade$replayPieChart(boolean tick, CallbackInfo ci) {
    ReplayPlayer.renderPieChart();
  }

  @ModifyArg(method = "onResolutionChanged",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;setScaleFactor(D)V"))
  private double tascade$replayGuiScale(double scale) {
    return ReplayPlayer.guiScale(scale);
  }

  // avoids the world flashing between the terrain screen and RaceWaitScreen
  @ModifyVariable(at = @At("HEAD"), method = "openScreen", argsOnly = true)
  private Screen tascade$holdForRaceStart(Screen screen) {
    return screen != null ? screen : TascadeClient.getInstance().raceGateScreen();
  }

  @Inject(at = @At("HEAD"), method = "openScreen", cancellable = true)
  private void tascade$replayScreens(Screen screen, CallbackInfo ci) {
    if (ReplayPlayer.captureScreen(screen) || ReplayPlayer.blocksScreen(screen)) ci.cancel();
  }

  @Inject(at = @At("HEAD"), method = "openPauseMenu", cancellable = true)
  private void tascade$replayControls(boolean pause, CallbackInfo ci) {
    if (ReplayPlayer.openControls()) ci.cancel();
  }
}
