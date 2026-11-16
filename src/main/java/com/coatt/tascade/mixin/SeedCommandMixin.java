package com.coatt.tascade.mixin;

import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.server.command.SeedCommand;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

// no /seed in Tascade worlds, removed entirely so it reads as unknown
@Mixin(SeedCommand.class)
public abstract class SeedCommandMixin {
  @ModifyArg(method = "register", at = @At(value = "INVOKE",
      target = "Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;requires(Ljava/util/function/Predicate;)Lcom/mojang/brigadier/builder/ArgumentBuilder;"))
  private static Predicate<ServerCommandSource> tascade$notInTascadeWorlds(Predicate<ServerCommandSource> requirement) {
    return requirement.and(source -> !WorldSeeds.active());
  }
}
