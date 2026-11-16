package com.coatt.tascade.mixin;

import com.coatt.tascade.world.FrameObsidianProcessor;
import com.coatt.tascade.world.FrameSlots;
import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.structure.RuinedPortalStructurePiece;
import net.minecraft.structure.SimpleStructurePiece;
import net.minecraft.structure.Structure;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(RuinedPortalStructurePiece.class)
public abstract class RuinedPortalStructurePieceMixin extends SimpleStructurePiece {
  @Shadow @Final private Identifier template;

  protected RuinedPortalStructurePieceMixin(StructurePieceType type, int length) {
    super(type, length);
  }

  @Inject(at = @At("RETURN"), method = "processProperties")
  private void tascade$frameObsidian(Structure structure, BlockPos center, CallbackInfo ci) {
    if (!WorldSeeds.active()) {
      return;
    }
    Set<BlockPos> slots = FrameSlots.get(this.template);
    if (!slots.isEmpty()) {
      this.placementData.addProcessor(new FrameObsidianProcessor(slots));
    }
  }
}
