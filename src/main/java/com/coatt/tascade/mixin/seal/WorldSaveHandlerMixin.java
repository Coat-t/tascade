package com.coatt.tascade.mixin.seal;

import com.coatt.tascade.network.WorldSeal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.WorldSaveHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

@Mixin(WorldSaveHandler.class)
public abstract class WorldSaveHandlerMixin {
  @Shadow @Final private File playerDataDir;

  @Redirect(method = "savePlayerData", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/OutputStream;)V"))
  private void tascade$writeSealed(CompoundTag tag, OutputStream out, PlayerEntity player) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    NbtIo.writeCompressed(tag, bytes);
    WorldSeal.beforeFile(new File(playerDataDir, player.getUuidAsString() + ".dat"), bytes.toByteArray());
    try {
      out.write(bytes.toByteArray());
    } finally {
      out.close();
    }
  }

  @Inject(at = @At("TAIL"), method = "savePlayerData")
  private void tascade$sealPlayer(PlayerEntity player, CallbackInfo ci) {
    WorldSeal.afterWrite();
  }
}
