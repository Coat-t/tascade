package com.coatt.tascade.mixin;

import com.coatt.tascade.TascadeConfig;
import com.coatt.tascade.finish.RunFinish;
import com.coatt.tascade.hud.RaceHud;
import com.coatt.tascade.network.RoomState;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.network.TascadeClient;
import com.coatt.tascade.screen.SeedChangeScreen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ScreenTexts;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

  @Unique
  private ButtonWidget tascade$runButton;

  @Unique
  private ButtonWidget tascade$forfeitButton;

  protected GameMenuScreenMixin(Text title) {
    super(title);
  }

  @Unique
  private ButtonWidget tascade$endVoteButton;

  @Unique
  private ButtonWidget tascade$hostEndButton;

  @Unique
  private ButtonWidget tascade$resetWorldButton;

  @Unique
  private ButtonWidget tascade$exitRunButton;

  // set on construction so returning from a confirmation or resizing doesn't restart the cooldown
  @Unique
  private long tascade$openedAtMs;

  @Inject(at = @At("RETURN"), method = "<init>")
  private void tascade$markOpened(boolean showMenu, CallbackInfo ci) {
    tascade$openedAtMs = Util.getMeasuringTimeMs();
  }

  @Inject(at = @At("TAIL"), method = "init")
  private void tascade$addRunControls(CallbackInfo ci) {
    tascade$runButton = null;
    tascade$forfeitButton = null;
    tascade$endVoteButton = null;
    tascade$hostEndButton = null;
    tascade$resetWorldButton = null;
    tascade$exitRunButton = null;

    if (RunState.isRoom()) {
      tascade$addRoomControls();
    } else if (RunState.isRunning()) {
      AbstractButtonWidget lanButton = tascade$find("menu.shareToLan");
      if (lanButton != null) {
        tascade$runButton = tascade$replace(lanButton, tascade$runButtonLabel(),
                button -> tascade$pressRunButton());
      }

      if (RunState.isRanked() && !RunFinish.isAwaitingOutcome()) {
        AbstractButtonWidget exitButton = tascade$find("menu.returnToMenu", "menu.disconnect");
        if (exitButton != null) {
          tascade$forfeitButton = tascade$replace(exitButton, "Forfeit Match",
                  button -> tascade$confirmForfeit(), this::tascade$forfeitTooltip);
          tascade$forfeitButton.active = RunState.forfeitAvailable();
        }
      } else if (RunState.isSolo() && !RunFinish.isActive()) {
        AbstractButtonWidget exitButton = tascade$find("menu.returnToMenu", "menu.disconnect");
        if (exitButton instanceof ButtonWidget) {
          ButtonWidget vanillaExit = (ButtonWidget) exitButton;
          tascade$exitRunButton = tascade$replace(exitButton, "Exit Run", button -> tascade$confirmExitRun(vanillaExit));
        }
      }
    }

    if (RunState.isRunning()) tascade$addResetWorldButton();

    if (RunFinish.inTitlePhase()) {
      if (tascade$runButton != null) tascade$runButton.active = false;
      if (tascade$resetWorldButton != null) tascade$resetWorldButton.active = false;
      if (tascade$forfeitButton != null) tascade$forfeitButton.active = false;
      if (tascade$exitRunButton != null) tascade$exitRunButton.active = false;
      AbstractButtonWidget exitButton = tascade$find("menu.returnToMenu", "menu.disconnect");
      if (exitButton != null && !RunFinish.isAwaitingOutcome()) exitButton.active = false;
      AbstractButtonWidget lanButton = tascade$find("menu.shareToLan");
      if (lanButton != null) lanButton.active = false;
    }
    tascade$holdDuringCooldown();
  }

  // only ever disables, so buttons locked for other reasons stay locked
  @Unique
  private void tascade$holdDuringCooldown() {
    if (Util.getMeasuringTimeMs() - tascade$openedAtMs >= TascadeConfig.pauseMenuCooldownMs()) return;
    for (ButtonWidget button : new ButtonWidget[] {tascade$runButton, tascade$forfeitButton, tascade$exitRunButton,
            tascade$endVoteButton, tascade$hostEndButton, tascade$resetWorldButton}) {
      if (button != null) button.active = false;
    }
  }

  @Unique
  private void tascade$confirmExitRun(ButtonWidget vanillaExit) {
    this.client.openScreen(new ConfirmScreen(confirmed -> {
      if (!confirmed) {
        this.client.openScreen(this);
        return;
      }
      vanillaExit.onPress();
    }, new LiteralText("Exit the run?"), new LiteralText("Your run ends here, and you can't come back to it."),
            new LiteralText("Exit Run"), ScreenTexts.CANCEL));
  }

  @Unique
  private void tascade$addRoomControls() {
    AbstractButtonWidget lanButton = tascade$find("menu.shareToLan");
    if (lanButton != null) {
      tascade$runButton = tascade$replace(lanButton, tascade$roomVoteLabel("seed"),
              button -> tascade$roomVote("seed"), this::tascade$roomVoteTooltip);
    }
    AbstractButtonWidget feedbackButton = tascade$find("menu.sendFeedback");
    if (feedbackButton != null) {
      tascade$endVoteButton = tascade$replace(feedbackButton, tascade$roomVoteLabel("end"),
              button -> tascade$roomVote("end"), this::tascade$roomVoteTooltip);
    }
    RoomState.Room room = RoomState.room();
    if (room != null && room.amPrivileged()) {
      AbstractButtonWidget bugsButton = tascade$find("menu.reportBugs");
      if (bugsButton != null) {
        tascade$hostEndButton = tascade$replace(bugsButton, "End Race", button -> tascade$confirmEndRace());
      }
    }
    if (!RunFinish.isActive()) {
      AbstractButtonWidget exitButton = tascade$find("menu.returnToMenu", "menu.disconnect");
      if (exitButton != null) {
        tascade$forfeitButton = tascade$replace(exitButton, "Leave Race", button -> tascade$confirmLeaveRace());
      }
    }
    tascade$refreshRoomButtons();
  }

  @Unique
  private void tascade$refreshRoomButtons() {
    boolean locked = RunFinish.inTitlePhase();
    if (tascade$runButton != null) {
      tascade$runButton.setMessage(new LiteralText(tascade$roomVoteLabel("seed")));
      tascade$runButton.active = !locked && tascade$roomVoteOpen("seed");
    }
    if (tascade$endVoteButton != null) {
      tascade$endVoteButton.setMessage(new LiteralText(tascade$roomVoteLabel("end")));
      tascade$endVoteButton.active = !locked && tascade$roomVoteOpen("end");
    }
    if (tascade$hostEndButton != null) tascade$hostEndButton.active = !locked && RoomState.finalStandings == null;
    if (tascade$forfeitButton != null) tascade$forfeitButton.active = !locked;
  }

  @Unique
  private static String tascade$roomVoteLabel(String type) {
    String what = "seed".equals(type) ? "New Seed" : "End Race";
    RoomState.Vote vote = RoomState.vote;
    if (vote != null && vote.type.equals(type)) {
      String count = " (" + vote.yes.size() + "/" + vote.needed + ")";
      return (RoomState.voted ? "Voted: " : "Agree: ") + what + count;
    }
    return "Vote: " + what;
  }

  @Unique
  private static boolean tascade$roomVoteOpen(String type) {
    return tascade$roomVoteRefusal(type) == null;
  }

  @Unique
  private static String tascade$roomVoteRefusal(String type) {
    RoomState.Room room = RoomState.room();
    if (room == null || !RunState.isRoom() || RoomState.finalStandings != null) return "The race is over.";
    if (RunFinish.isActive()) return "You're out of the race, so you don't vote.";
    String rule = "seed".equals(type) ? room.settings.voteSeed : room.settings.voteEnd;
    if (RoomState.VOTE_OFF.equals(rule)) return "The room's rules turn this vote off.";
    RoomState.Vote vote = RoomState.vote;
    if (vote != null) {
      if (!vote.type.equals(type)) return "Another vote is under way.";
      return RoomState.voted ? "You voted. It needs " + vote.needed + " in " + vote.secondsLeft() + "s." : null;
    }
    if ("seed".equals(type)) {
      for (RoomState.Racer racer : RoomState.racers()) {
        if (racer.finished()) return "Someone has finished, so the seed can't change.";
      }
      int window = room.settings.voteSeedWindowSec;
      long started = RunState.raceStartedAtMs;
      if (window > 0 && started > 0 && System.currentTimeMillis() - started > window * 1000L) {
        return "The seed can only change in the first " + RoomState.durationLabel(window) + ".";
      }
    }
    return null;
  }

  @Unique
  private void tascade$roomVoteTooltip(ButtonWidget button, MatrixStack matrices, int mouseX, int mouseY) {
    String type = button == tascade$runButton ? "seed" : "end";
    String refusal = tascade$roomVoteRefusal(type);
    String rule = RoomState.room() != null
            ? ("seed".equals(type) ? RoomState.room().settings.voteSeed : RoomState.room().settings.voteEnd) : null;
    String text = refusal != null ? refusal
            : RoomState.VOTE_UNANIMOUS.equals(rule) ? "Every player still racing has to agree."
            : "More than half of the players still racing have to agree.";
    this.renderTooltip(matrices, new LiteralText(text), mouseX, mouseY);
  }

  @Unique
  private void tascade$roomVote(String type) {
    TascadeClient.getInstance().roomVote(type);
    this.client.openScreen(null);
  }

  @Unique
  private void tascade$confirmEndRace() {
    this.client.openScreen(new ConfirmScreen(confirmed -> {
      if (confirmed) TascadeClient.getInstance().endRoomRace();
      this.client.openScreen(confirmed ? null : this);
    }, new LiteralText("End the race for everyone?"), new LiteralText("Whoever hasn't finished by now doesn't."),
            new LiteralText("End Race"), ScreenTexts.CANCEL));
  }

  @Unique
  private void tascade$confirmLeaveRace() {
    this.client.openScreen(new ConfirmScreen(confirmed -> {
      if (!confirmed) {
        this.client.openScreen(this);
        return;
      }
      TascadeClient.getInstance().leaveRoomRace();
    }, new LiteralText("Leave the race?"), new LiteralText("You give up this race, and go back to the room's lobby."),
            new LiteralText("Leave Race"), ScreenTexts.CANCEL));
  }

  @Inject(at = @At("HEAD"), method = "render")
  private void tascade$refreshRunButtons(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    if (tascade$resetWorldButton != null) {
      tascade$resetWorldButton.active = !RunFinish.inTitlePhase() && TascadeClient.getInstance().resetWorldRefusal() == null;
    }
    if (RunState.isRoom()) {
      tascade$refreshRoomButtons();
    } else {
      boolean locked = RunFinish.inTitlePhase();
      if (tascade$runButton != null) {
        tascade$runButton.setMessage(new LiteralText(tascade$runButtonLabel()));
        tascade$runButton.active = !locked && !tascade$runButtonWaiting();
      }
      if (tascade$forfeitButton != null) {
        tascade$forfeitButton.active = RunState.forfeitAvailable() && !locked;
      }
      if (tascade$exitRunButton != null) tascade$exitRunButton.active = !locked;
    }
    tascade$holdDuringCooldown();
  }

  @Inject(at = @At("TAIL"), method = "render")
  private void tascade$renderRaceHud(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    List<StringRenderable> tooltip = RaceHud.render(this.client, matrices, mouseX, mouseY);
    if (tooltip != null) this.renderTooltip(matrices, tooltip, mouseX, mouseY);
  }

  @Unique
  private void tascade$addResetWorldButton() {
    AbstractButtonWidget exitButton = tascade$forfeitButton != null ? tascade$forfeitButton
            : tascade$exitRunButton != null ? tascade$exitRunButton
            : tascade$find("menu.returnToMenu", "menu.disconnect");
    int x = exitButton != null ? exitButton.x : this.width / 2 - 102;
    int y = exitButton != null ? exitButton.y + 24 : this.height / 4 + 128;
    int width = exitButton != null ? exitButton.getWidth() : 204;
    tascade$resetWorldButton = this.addButton(new ButtonWidget(x, y, width, 20, new LiteralText("Reset World"),
            button -> tascade$confirmResetWorld(), this::tascade$resetWorldTooltip));
    tascade$resetWorldButton.active = TascadeClient.getInstance().resetWorldRefusal() == null;
  }

  @Unique
  private void tascade$confirmResetWorld() {
    this.client.openScreen(new ConfirmScreen(confirmed -> {
      if (!confirmed) {
        this.client.openScreen(this);
        return;
      }
      TascadeClient.getInstance().resetWorld();
      this.client.openScreen(null);
    }, new LiteralText("Reset the world?"),
            new LiteralText("You start over in a new world of this seed. The timer keeps running, and your splits so far stay."),
            new LiteralText("Reset World"), ScreenTexts.CANCEL));
  }

  @Unique
  private void tascade$resetWorldTooltip(ButtonWidget button, MatrixStack matrices, int mouseX, int mouseY) {
    if (button.active || RunFinish.inTitlePhase()) return;
    String refusal = TascadeClient.getInstance().resetWorldRefusal();
    if (refusal != null) this.renderTooltip(matrices, new LiteralText(refusal), mouseX, mouseY);
  }

  @Unique
  private void tascade$confirmForfeit() {
    this.client.openScreen(new ConfirmScreen(confirmed -> {
      if (!confirmed) {
        this.client.openScreen(this);
        return;
      }
      TascadeClient.getInstance().forfeit();
      this.client.openScreen(null);
    }, new LiteralText("Forfeit the match?"), new LiteralText("Your opponent wins, and you lose Elo."),
            new LiteralText("Forfeit"), ScreenTexts.CANCEL));
  }

  @Unique
  private void tascade$forfeitTooltip(ButtonWidget button, MatrixStack matrices, int mouseX, int mouseY) {
    int seconds = RunState.secondsUntilForfeit();
    if (button.active || seconds == 0) return;
    this.renderTooltip(matrices, new LiteralText("You can forfeit in " + seconds
            + (seconds == 1 ? " second" : " seconds")), mouseX, mouseY);
  }

  // ButtonWidget's action is final, so buttons are swapped rather than relabelled
  @Unique
  private ButtonWidget tascade$replace(AbstractButtonWidget original, String label, ButtonWidget.PressAction action) {
    return tascade$replace(original, label, action, ButtonWidget.EMPTY);
  }

  @Unique
  private ButtonWidget tascade$replace(AbstractButtonWidget original, String label, ButtonWidget.PressAction action,
                                       ButtonWidget.TooltipSupplier tooltip) {
    this.buttons.remove(original);
    this.children.remove(original);
    return this.addButton(new ButtonWidget(
            original.x, original.y, original.getWidth(), 20, new LiteralText(label), action, tooltip));
  }

  @Unique
  private AbstractButtonWidget tascade$find(String... translationKeys) {
    for (AbstractButtonWidget widget : this.buttons) {
      Text message = widget.getMessage();
      if (!(message instanceof TranslatableText)) continue;
      String key = ((TranslatableText) message).getKey();
      for (String wanted : translationKeys) {
        if (wanted.equals(key)) return widget;
      }
    }
    return null;
  }

  @Unique
  private static String tascade$runButtonLabel() {
    if (RunState.isSolo()) return RunState.soloSeedRequestedAtMs > 0 ? "Changing Seed..." : "Change Seed";

    if (RunState.pendingVote == RunState.Vote.DRAW) {
      if (RunState.voteAgreed) return "Draw Accepted";
      return RunState.pendingVoteIsMine ? "Draw Offered" : "Accept Draw";
    }
    if (RunState.pendingVote == RunState.Vote.SEED) {
      if (RunState.voteAgreed) return "Changing Seed...";
      return RunState.pendingVoteIsMine ? "Seed Requested" : "Accept Seed";
    }
    return RunState.seedChangeAvailable() ? "Change Seed" : "Offer Draw";
  }

  @Unique
  private static boolean tascade$runButtonWaiting() {
    if (RunState.isSolo()) return RunState.soloSeedRequestedAtMs > 0;
    return RunState.pendingVote != RunState.Vote.NONE && (RunState.pendingVoteIsMine || RunState.voteAgreed);
  }

  @Unique
  private void tascade$pressRunButton() {
    TascadeClient tascade = TascadeClient.getInstance();
    boolean solo = RunState.isSolo();
    if (solo || RunState.pendingVote == RunState.Vote.SEED
            || (RunState.pendingVote == RunState.Vote.NONE && RunState.seedChangeAvailable())) {
      tascade.requestSeedChange();
    } else {
      tascade.offerDraw();
    }
    this.client.openScreen(solo && RunState.soloSeedRequestedAtMs > 0 ? SeedChangeScreen.awaitingSeed() : null);
  }
}
