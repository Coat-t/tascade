package com.coatt.tascade.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ZombifiedPiglins {
  private ZombifiedPiglins() {
  }

  public static final float INSIDE_PENALTY = 10.0F;

  public static boolean keepOut(MobEntity mob) {
    if (!(mob instanceof ZombifiedPiglinEntity) || !(mob.world instanceof ServerWorld) || !WorldSeeds.active()
        || mob.world.getRegistryKey() != World.NETHER) {
      return false;
    }
    // ones angered along with a nearby one only get its target, not anger time
    ZombifiedPiglinEntity zombifiedPiglin = (ZombifiedPiglinEntity) mob;
    return zombifiedPiglin.getTarget() == null && !zombifiedPiglin.hasAngerTime();
  }

  public static boolean pathsAround(MobEntity mob) {
    return keepOut(mob) && !inBastion((ServerWorld) mob.world, mob.getBlockPos());
  }

  public static boolean inBastion(ServerWorld world, BlockPos pos) {
    return contains(pieces(world, pos.getX() >> 4, pos.getZ() >> 4), pos.getX(), pos.getY(), pos.getZ());
  }

  public static boolean inBastion(ServerWorld world, Long2ObjectMap<List<BlockBox>> cache, int x, int y, int z) {
    List<BlockBox> pieces = cache.get(ChunkPos.toLong(x >> 4, z >> 4));
    if (pieces == null) {
      pieces = pieces(world, x >> 4, z >> 4);
      cache.put(ChunkPos.toLong(x >> 4, z >> 4), pieces);
    }
    return contains(pieces, x, y, z);
  }

  private static boolean contains(List<BlockBox> boxes, int x, int y, int z) {
    for (BlockBox box : boxes) {
      if (x >= box.minX && x <= box.maxX && z >= box.minZ && z <= box.maxZ && y >= box.minY && y <= box.maxY + 1) {
        return true;
      }
    }
    return false;
  }

  private static List<BlockBox> pieces(ServerWorld world, int chunkX, int chunkZ) {
    // FULL, so the server's small chunk cache keeps serving them
    Chunk chunk = world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    LongSet starts = chunk == null ? null : chunk.getStructureReferences(StructureFeature.BASTION_REMNANT);
    if (starts == null || starts.isEmpty()) {
      return Collections.emptyList();
    }
    int minX = chunkX << 4, minZ = chunkZ << 4;
    List<BlockBox> boxes = new ArrayList<>();
    for (long start : starts) {
      Chunk startChunk = world.getChunk(ChunkPos.getPackedX(start), ChunkPos.getPackedZ(start), ChunkStatus.STRUCTURE_STARTS, false);
      StructureStart<?> bastion = startChunk == null ? null : startChunk.getStructureStart(StructureFeature.BASTION_REMNANT);
      if (bastion == null || !bastion.hasChildren()) {
        continue;
      }
      // worldgen workers lock on this too
      synchronized (bastion.getChildren()) {
        for (StructurePiece piece : bastion.getChildren()) {
          BlockBox box = piece.getBoundingBox();
          if (box.intersectsXZ(minX, minZ, minX + 15, minZ + 15)) {
            boxes.add(box);
          }
        }
      }
    }
    return boxes;
  }
}
