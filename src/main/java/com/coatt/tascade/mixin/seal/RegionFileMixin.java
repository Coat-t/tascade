package com.coatt.tascade.mixin.seal;

import com.coatt.tascade.network.WorldSeal;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.RegionFile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.nio.file.Path;

@Mixin(RegionFile.class)
public abstract class RegionFileMixin {
  @Shadow @Final private Path directory;

  @Inject(at = @At("HEAD"), method = "writeChunk")
  private void tascade$logChunk(ChunkPos pos, ByteBuffer byteBuffer, CallbackInfo ci) {
    WorldSeal.beforeChunk(directory, pos, byteBuffer);
  }

  @Inject(at = @At("TAIL"), method = "writeChunk")
  private void tascade$sealChunk(ChunkPos pos, ByteBuffer byteBuffer, CallbackInfo ci) {
    WorldSeal.afterWrite();
  }
}
