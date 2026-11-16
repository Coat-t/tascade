package com.coatt.tascade.mixin.seal;

import com.coatt.tascade.network.WorldSeal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.PersistentState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

@Mixin(PersistentState.class)
public abstract class PersistentStateMixin {
  @Redirect(method = "save", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/OutputStream;)V"))
  private void tascade$writeSealed(CompoundTag tag, OutputStream out, File file) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    NbtIo.writeCompressed(tag, bytes);
    WorldSeal.beforeFile(file, bytes.toByteArray());
    try {
      out.write(bytes.toByteArray());
    } finally {
      out.close();
    }
    WorldSeal.afterWrite();
  }
}
