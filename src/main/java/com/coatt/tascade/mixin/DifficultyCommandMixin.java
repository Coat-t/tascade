package com.coatt.tascade.mixin;

import com.coatt.tascade.world.RunDifficulty;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.server.command.DifficultyCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DifficultyCommand.class)
public abstract class DifficultyCommandMixin {
  @Unique
  private static final SimpleCommandExceptionType TASCADE$PEACEFUL_REFUSED =
      new SimpleCommandExceptionType(new LiteralText("Peaceful isn't allowed in Tascade runs"));

  @Inject(at = @At("HEAD"), method = "execute")
  private static void tascade$refusePeaceful(ServerCommandSource source, Difficulty difficulty,
                                             CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
    if (RunDifficulty.forbidden(difficulty)) throw TASCADE$PEACEFUL_REFUSED.create();
  }
}
