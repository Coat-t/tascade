package com.coatt.tascade.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

public final class SpawnSpot {
  private static final int SEARCH_RADIUS = 6;

  private SpawnSpot() {
  }

  public static BlockPos onGround(ServerWorld world, int x, int z) {
    BlockPos fallback = null;
    for (int r = 0; r <= SEARCH_RADIUS; r++) {
      for (int dx = -r; dx <= r; dx++) {
        for (int dz = -r; dz <= r; dz++) {
          if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
            continue; // ring by ring, nearest first
          }
          BlockPos top = top(world, x + dx, z + dz);
          if (fallback == null) {
            fallback = top;
          }
          if (fits(world, top)) {
            return top;
          }
        }
      }
    }
    return fallback;
  }

  private static boolean fits(ServerWorld world, BlockPos feet) {
    BlockState ground = world.getBlockState(feet.down());
    if (treePart(ground) || !ground.getFluidState().isEmpty()) {
      return false;
    }
    return free(world, feet) && free(world, feet.up());
  }

  private static boolean treePart(BlockState state) {
    return state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES) || state.isOf(Blocks.MUSHROOM_STEM)
        || state.isOf(Blocks.BROWN_MUSHROOM_BLOCK) || state.isOf(Blocks.RED_MUSHROOM_BLOCK);
  }

  private static boolean free(ServerWorld world, BlockPos pos) {
    BlockState state = world.getBlockState(pos);
    return state.getFluidState().isEmpty() && state.getCollisionShape(world, pos).isEmpty();
  }

  private static BlockPos top(ServerWorld world, int x, int z) {
    world.getChunk(x >> 4, z >> 4); // getTopY reads 0 for unloaded chunks
    return new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
  }
}
