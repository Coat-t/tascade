package com.coatt.tascade.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Material;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

import java.util.Random;

// scaled-down copy of vanilla's LakeFeature, seeded by position
public final class LavaPool {
  private static final int SIZE = 12;
  private static final int HEIGHT = 6;
  private static final int SURFACE = 3;

  private LavaPool() {
  }

  private static final int SHIFT = 12, MARGIN = 2;

  public static void place(ServerWorld world, int cx, int cz) {
    int reach = SIZE + SHIFT + MARGIN;
    for (int x = (cx - reach) >> 4; x <= (cx + reach) >> 4; x++) {
      for (int z = (cz - reach) >> 4; z <= (cz + reach) >> 4; z++) {
        world.getChunk(x, z); // getTopY reads 0 for unloaded chunks
      }
    }
    // water pouring in would turn the lava to obsidian
    Lake lake = find(world, cx, cz, true);
    if (lake == null) {
      lake = find(world, cx, cz, false);
    }
    if (lake == null) {
      lake = new Lake(world, cx, cz);
    }
    lake.carve(world);
  }

  private static Lake find(ServerWorld world, int cx, int cz, boolean strict) {
    for (int r = 0; r <= SHIFT; r++) {
      for (int dx = -r; dx <= r; dx++) {
        for (int dz = -r; dz <= r; dz++) {
          if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
            continue;
          }
          Lake lake = new Lake(world, cx + dx, cz + dz);
          if (lake.clear(world, strict)) {
            return lake;
          }
        }
      }
    }
    return null;
  }

  private static final class Lake {
    private final BlockPos base;
    private final Random random;
    private final boolean[][][] lake = new boolean[SIZE][SIZE][HEIGHT];

    Lake(ServerWorld world, int cx, int cz) {
      int ground = ground(world, cx, cz);
      this.base = new BlockPos(cx - SIZE / 2, ground - SURFACE, cz - SIZE / 2);
      this.random = new Random(MathHelper.hashCode(cx, ground, cz));
      Random random = this.random;
      boolean[][][] lake = this.lake;
      int blobs = random.nextInt(3) + 4;
      for (int b = 0; b < blobs; b++) {
        double dx = random.nextDouble() * 4.0 + 3.0;
        double dz = random.nextDouble() * 4.0 + 3.0;
        double ry = random.nextDouble() * 0.8 + 1.2;
        double gx = random.nextDouble() * (SIZE - dx - 2.0) + 1.0 + dx / 2.0;
        double gz = random.nextDouble() * (SIZE - dz - 2.0) + 1.0 + dz / 2.0;
        double gy = SURFACE - 0.5 + random.nextDouble() * 0.5;
        for (int x = 1; x < SIZE - 1; x++) {
          for (int z = 1; z < SIZE - 1; z++) {
            for (int y = 1; y < HEIGHT - 1; y++) {
              double ox = (x - gx) / (dx / 2.0), oy = (y - gy) / ry, oz = (z - gz) / (dz / 2.0);
              if (ox * ox + oy * oy + oz * oz < 1.0) {
                lake[x][z][y] = true;
              }
            }
          }
        }
      }
    }

    boolean clear(ServerWorld world, boolean strict) {
      BlockPos.Mutable p = new BlockPos.Mutable();
      for (int x = -MARGIN; x < SIZE + MARGIN; x++) {
        for (int z = -MARGIN; z < SIZE + MARGIN; z++) {
          for (int y = 0; y <= HEIGHT; y++) {
            if (!world.getFluidState(p.set(this.base, x, y, z)).isEmpty()) {
              return false;
            }
          }
        }
      }
      if (!strict) {
        return true;
      }
      for (int x = 0; x < SIZE; x++) {
        for (int z = 0; z < SIZE; z++) {
          for (int y = 0; y < HEIGHT; y++) {
            BlockState s = world.getBlockState(p.set(this.base, x, y, z));
            if (this.lake[x][z][y] ? tree(s) : y < SURFACE && touches(this.lake, x, z, y) && (!s.getMaterial().isSolid() || grown(s))) {
              return false;
            }
          }
          if (this.lake[x][z][SURFACE]) {
            for (int y = SURFACE + 1; y <= SURFACE + 3; y++) {
              if (tree(world.getBlockState(p.set(this.base, x, y, z)))) {
                return false;
              }
            }
          }
        }
      }
      return true;
    }

    void carve(ServerWorld world) {
      BlockPos base = this.base;
      Random random = this.random;
      boolean[][][] lake = this.lake;
      BlockState stone = Blocks.STONE.getDefaultState();
      for (int x = 0; x < SIZE; x++) {
        for (int z = 0; z < SIZE; z++) {
          for (int y = 0; y < HEIGHT; y++) {
            if (lake[x][z][y] || !touches(lake, x, z, y)) {
              continue;
            }
            BlockPos p = base.add(x, y, z);
            BlockState s = world.getBlockState(p);
            boolean solid = s.getMaterial().isSolid() && s.getFluidState().isEmpty() && !grown(s);
            if (y < SURFACE || solid && random.nextInt(2) != 0) {
              world.setBlockState(p, stone, 2);
            }
          }
        }
      }

      for (int x = 0; x < SIZE; x++) {
        for (int z = 0; z < SIZE; z++) {
          for (int y = 0; y < HEIGHT; y++) {
            if (lake[x][z][y]) {
              world.setBlockState(base.add(x, y, z), y < SURFACE ? Blocks.LAVA.getDefaultState() : Blocks.CAVE_AIR.getDefaultState(), 2);
            }
          }
        }
      }

      for (int x = 0; x < SIZE; x++) {
        for (int z = 0; z < SIZE; z++) {
          int top = HEIGHT - 1;
          while (top >= SURFACE && !lake[x][z][top]) {
            top--;
          }
          if (top < SURFACE) {
            continue;
          }
          BlockPos.Mutable p = base.add(x, top + 1, z).mutableCopy();
          while (world.getBlockState(p).isIn(BlockTags.LOGS)) {
            world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
            p.move(Direction.UP);
          }
        }
      }

      for (int x = 0; x < SIZE; x++) {
        for (int z = 0; z < SIZE; z++) {
          for (int y = SURFACE; y < HEIGHT; y++) {
            if (lake[x][z][y]) {
              BlockPos below = base.add(x, y - 1, z);
              if (world.getBlockState(below).isOf(Blocks.DIRT) && world.isSkyVisible(base.add(x, y, z))) {
                world.setBlockState(below, Blocks.GRASS_BLOCK.getDefaultState(), 2);
              }
            }
          }
        }
      }
    }
  }

  private static int ground(ServerWorld world, int x, int z) {
    BlockPos.Mutable p = new BlockPos.Mutable(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    while (p.getY() > 0 && grown(world.getBlockState(p))) {
      p.move(Direction.DOWN);
    }
    return p.getY();
  }

  private static boolean tree(BlockState s) {
    Material m = s.getMaterial();
    return m == Material.WOOD || m == Material.LEAVES;
  }

  private static boolean grown(BlockState s) {
    Material m = s.getMaterial();
    return m == Material.WOOD || m == Material.LEAVES || m == Material.GOURD || s.isOf(Blocks.MOSSY_COBBLESTONE)
        || !m.blocksMovement() && s.getFluidState().isEmpty();
  }

  private static boolean touches(boolean[][][] lake, int x, int z, int y) {
    return x < SIZE - 1 && lake[x + 1][z][y] || x > 0 && lake[x - 1][z][y]
        || z < SIZE - 1 && lake[x][z + 1][y] || z > 0 && lake[x][z - 1][y]
        || y < HEIGHT - 1 && lake[x][z][y + 1] || y > 0 && lake[x][z][y - 1];
  }
}
