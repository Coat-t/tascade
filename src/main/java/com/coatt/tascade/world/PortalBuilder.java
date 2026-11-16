package com.coatt.tascade.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

public final class PortalBuilder {
  private static final int MIN_SURFACE_Y = 50;
  private static final int MIN_FLOATING_Y = 70;
  private static final int SEARCH_RADIUS = 16;

  private PortalBuilder() {
  }

  public static final class Spot {
    public final BlockPos base;
    public final boolean floating;

    Spot(BlockPos base, boolean floating) {
      this.base = base;
      this.floating = floating;
    }
  }

  public static void build(ServerWorld world, BlockPos base, Direction.Axis axis, boolean floating) {
    int ax = axis == Direction.Axis.X ? 1 : 0, az = 1 - ax; // along the portal
    int cx = az, cz = ax; // across it
    BlockState obsidian = Blocks.OBSIDIAN.getDefaultState();
    for (int a = 0; a <= 1; a++) {
      for (int c = -1; c <= 1; c += 2) {
        int x = base.getX() + a * ax + c * cx, z = base.getZ() + a * az + c * cz;
        if (floating) {
          world.setBlockState(new BlockPos(x, base.getY() - 1, z), obsidian, 3);
        }
        for (int dy = 0; dy <= 2; dy++) {
          BlockPos p = new BlockPos(x, base.getY() + dy, z);
          if (!world.getBlockState(p).isAir()) {
            world.setBlockState(p, Blocks.AIR.getDefaultState(), 3);
          }
        }
      }
    }
    for (int a = -1; a <= 2; a++) {
      for (int dy = -1; dy <= 3; dy++) {
        if (a == -1 || a == 2 || dy == -1 || dy == 3) {
          world.setBlockState(base.add(a * ax, dy, a * az), obsidian, 3);
        }
      }
    }
    BlockState portal = Blocks.NETHER_PORTAL.getDefaultState().with(NetherPortalBlock.AXIS, axis);
    for (int a = 0; a <= 1; a++) {
      for (int dy = 0; dy <= 2; dy++) {
        world.setBlockState(base.add(a * ax, dy, a * az), portal, 18);
      }
    }
  }

  public static void ensure(ServerWorld world, BlockPos base, Direction.Axis axis) {
    if (!world.getBlockState(base).isOf(Blocks.NETHER_PORTAL)) {
      build(world, base, axis, false);
    }
  }

  public static boolean isEntryPortal(BlockPos pos, WorldSeeds.Entry entry) {
    return Math.abs(pos.getX() - entry.x) <= 3 && Math.abs(pos.getZ() - entry.z) <= 3
        && pos.getY() >= entry.y - 2 && pos.getY() <= entry.y + 4;
  }

  public static Spot findSurfaceSpot(ServerWorld world, BlockPos target) {
    BlockPos best = null;
    double bestScore = Double.MAX_VALUE;
    for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
      for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
        int x = target.getX() + dx, z = target.getZ() + dz;
        int y = topY(world, x, z);
        if (y < MIN_SURFACE_Y) {
          continue;
        }
        BlockPos base = new BlockPos(x, y, z);
        BlockState below = world.getBlockState(base.down());
        if (!below.getMaterial().isSolid() || !below.getFluidState().isEmpty()) {
          continue; // water, lava, plants: not ground
        }
        double score = dx * dx + dz * dz
            + (open(world, base) ? 0 : 128)
            + (world.isSkyVisible(base) ? 0 : 64);
        if (score < bestScore) {
          bestScore = score;
          best = base;
        }
      }
    }
    if (best != null) {
      return new Spot(best, false);
    }
    int y = topY(world, target.getX(), target.getZ());
    return new Spot(new BlockPos(target.getX(), Math.max(MIN_FLOATING_Y, y + 1), target.getZ()), true);
  }

  private static int topY(ServerWorld world, int x, int z) {
    world.getChunk(x >> 4, z >> 4); // getTopY reads 0 for unloaded chunks
    return world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
  }

  private static boolean open(ServerWorld world, BlockPos base) {
    for (int a = -1; a <= 2; a++) {
      for (int dy = 0; dy <= 3; dy++) {
        BlockState s = world.getBlockState(base.add(a, dy, 0));
        if (!s.isAir() && !(s.getMaterial().isReplaceable() && s.getFluidState().isEmpty())) {
          return false;
        }
      }
    }
    return true;
  }
}
