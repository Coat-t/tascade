package com.coatt.tascade.world;

import net.minecraft.block.Blocks;
import net.minecraft.structure.Structure;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.processor.StructureProcessor;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

import java.util.Set;

public final class FrameObsidianProcessor extends StructureProcessor {
  private final Set<BlockPos> slots;

  public FrameObsidianProcessor(Set<BlockPos> slots) {
    this.slots = slots;
  }

  @Override
  public Structure.StructureBlockInfo process(WorldView world, BlockPos pos, BlockPos pivot,
      Structure.StructureBlockInfo original, Structure.StructureBlockInfo current, StructurePlacementData data) {
    if (current != null && current.state.isOf(Blocks.CRYING_OBSIDIAN) && this.slots.contains(original.pos)) {
      return new Structure.StructureBlockInfo(current.pos, Blocks.OBSIDIAN.getDefaultState(), current.tag);
    }
    return current;
  }

  @Override
  protected StructureProcessorType<?> getType() {
    // never serialized: ruined portal pieces rebuild their processors on load
    return StructureProcessorType.NOP;
  }
}
