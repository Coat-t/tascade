package com.coatt.tascade.mixin.seal;

import com.coatt.tascade.network.WorldSeal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

@Mixin(LevelStorage.Session.class)
public abstract class LevelStorageSessionMixin {
  @Shadow @Final private Path directory;

  @Redirect(method = "method_27426", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/OutputStream;)V"))
  private void tascade$writeSealed(CompoundTag tag, OutputStream out, RegistryTracker registryTracker,
                                   SaveProperties saveProperties, CompoundTag playerTag) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    NbtIo.writeCompressed(tag, bytes);
    WorldSeal.beforeFile(directory.resolve("level.dat").toFile(), bytes.toByteArray());
    try {
      out.write(bytes.toByteArray());
    } finally {
      out.close();
    }
  }

  @Inject(at = @At("TAIL"), method = "method_27426")
  private void tascade$sealLevel(RegistryTracker registryTracker, SaveProperties saveProperties, CompoundTag playerTag,
                                 CallbackInfo ci) {
    WorldSeal.afterWrite();
  }
}
