package com.coatt.tascade.world;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

// each spawn rolls from its own random, seeded by world seed, place and count, so both players meet the same mobs
public final class MobSpawns {
  private static final ThreadLocal<Random> ROLLING = new ThreadLocal<>();
  // distinct per kind so rolls never share seeds
  private static final long NATURAL = 1, TURNS = 2, SPAWNER = 3, GENERATED = 4, STRUCTURE = 5;
  private static final List<Turn> turns = new ArrayList<>();
  private static boolean takingTurns;

  private MobSpawns() {
  }

  @Nullable
  public static Random rolling() {
    return ROLLING.get();
  }

  public static Random random(World world) {
    Random rolling = ROLLING.get();
    return rolling != null ? rolling : world.random;
  }

  public static void natural(ServerWorld world, ChunkPos chunk, SpawnGroup group, Runnable spawn) {
    int tries = Counts.of(world).nextTry(chunk.toLong(), group);
    roll(mix(mix(mix(seed(world, NATURAL), chunk.toLong()), group.ordinal()), tries), spawn);
  }

  public static boolean queueTurn(ServerWorld world, WorldChunk chunk, Runnable spawn) {
    if (takingTurns) {
      return false;
    }
    long pos = chunk.getPos().toLong();
    turns.add(new Turn(mix(mix(seed(world, TURNS), pos), Counts.of(world).tries(pos)), spawn));
    return true;
  }

  public static void spawnTurns() {
    List<Turn> queued = new ArrayList<>(turns);
    turns.clear();
    queued.sort(Comparator.comparingLong(turn -> turn.key));
    takingTurns = true;
    try {
      for (Turn turn : queued) {
        turn.spawn.run();
      }
    } finally {
      takingTurns = false;
    }
  }

  public static void spawner(ServerWorld world, BlockPos pos, Runnable spawn) {
    int spawns = Counts.of(world).nextSpawn(pos.asLong());
    roll(mix(mix(seed(world, SPAWNER), pos.asLong()), spawns), spawn);
  }

  public static void generated(ServerWorld world, int chunkX, int chunkZ, Runnable spawn) {
    roll(mix(seed(world, GENERATED), ChunkPos.toLong(chunkX, chunkZ)), spawn);
  }

  public static void structure(ServerWorld world, BlockPos piece, @Nullable BlockBox area, Runnable spawn) {
    long clip = area == null ? 0 : BlockPos.asLong(area.minX, area.minY, area.minZ);
    roll(mix(mix(seed(world, STRUCTURE), piece.asLong()), clip), spawn);
  }

  static void roll(long seed, Runnable spawn) {
    Random outer = ROLLING.get();
    ROLLING.set(new Random(seed));
    try {
      spawn.run();
    } finally {
      ROLLING.set(outer);
    }
  }

  private static long seed(ServerWorld world, long kind) {
    return mix(mix(world.getSeed(), world.getRegistryKey().getValue().hashCode()), kind);
  }

  static long mix(long seed, long value) {
    return HashCommon.murmurHash3(HashCommon.murmurHash3(seed) + value);
  }

  private static final class Turn {
    final long key;
    final Runnable spawn;

    Turn(long key, Runnable spawn) {
      this.key = key;
      this.spawn = spawn;
    }
  }

  private static final class Counts extends PersistentState {
    private static final String ID = "tascade_spawns";
    private static final int GROUPS = SpawnGroup.values().length;
    private final Long2ObjectMap<int[]> chunks = new Long2ObjectOpenHashMap<>();
    private final Long2IntMap spawners = new Long2IntOpenHashMap();

    private Counts() {
      super(ID);
    }

    static Counts of(ServerWorld world) {
      return world.getPersistentStateManager().getOrCreate(Counts::new, ID);
    }

    int nextTry(long chunk, SpawnGroup group) {
      markDirty();
      return chunks.computeIfAbsent(chunk, c -> new int[GROUPS])[group.ordinal()]++;
    }

    int tries(long chunk) {
      int[] tries = chunks.get(chunk);
      int total = 0;
      if (tries != null) {
        for (int n : tries) total += n;
      }
      return total;
    }

    int nextSpawn(long spawner) {
      markDirty();
      return spawners.put(spawner, spawners.get(spawner) + 1);
    }

    @Override
    public void fromTag(CompoundTag tag) {
      long[] chunkKeys = tag.getLongArray("Chunks");
      int[] tries = tag.getIntArray("ChunkTries");
      for (int i = 0; i < chunkKeys.length && (i + 1) * GROUPS <= tries.length; i++) {
        int[] counts = new int[GROUPS];
        System.arraycopy(tries, i * GROUPS, counts, 0, GROUPS);
        chunks.put(chunkKeys[i], counts);
      }
      long[] spawnerKeys = tag.getLongArray("Spawners");
      int[] spawns = tag.getIntArray("SpawnerSpawns");
      for (int i = 0; i < spawnerKeys.length && i < spawns.length; i++) {
        spawners.put(spawnerKeys[i], spawns[i]);
      }
    }

    @Override
    public CompoundTag toTag(CompoundTag tag) {
      long[] chunkKeys = new long[chunks.size()];
      int[] tries = new int[chunks.size() * GROUPS];
      int i = 0;
      for (Long2ObjectMap.Entry<int[]> entry : chunks.long2ObjectEntrySet()) {
        chunkKeys[i] = entry.getLongKey();
        System.arraycopy(entry.getValue(), 0, tries, i * GROUPS, GROUPS);
        i++;
      }
      tag.putLongArray("Chunks", chunkKeys);
      tag.putIntArray("ChunkTries", tries);
      long[] spawnerKeys = new long[spawners.size()];
      int[] spawns = new int[spawners.size()];
      i = 0;
      for (Long2IntMap.Entry entry : spawners.long2IntEntrySet()) {
        spawnerKeys[i] = entry.getLongKey();
        spawns[i] = entry.getIntValue();
        i++;
      }
      tag.putLongArray("Spawners", spawnerKeys);
      tag.putIntArray("SpawnerSpawns", spawns);
      return tag;
    }
  }
}
