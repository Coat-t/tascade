package com.coatt.tascade.world;

import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldSeeds {
  private static final Logger LOGGER = LogManager.getLogger("tascade");
  private static final String FILE = "tascade_world.json";
  private static final Gson GSON = new Gson();

  public static final class Data {
    // null: the Nether follows the overworld seed
    public Long netherSeed;
    // null: vanilla spawn search
    public int[] spawn;
    public Clean clean;
    public int[][] lavaPools;
    public int[] lavaPortalChest;
    public int[] portalChest;
    public Entry entry;
  }

  public static final class Clean {
    public int radius;
    public int[][] centers;
    public String keepFeature;
    public int keepChunkX, keepChunkZ;
  }

  public static final class Entry {
    public int x, y, z;
    public String axis;

    public BlockPos pos() {
      return new BlockPos(x, y, z);
    }

    public Direction.Axis portalAxis() {
      return "z".equals(axis) ? Direction.Axis.Z : Direction.Axis.X;
    }
  }

  private static volatile Data pending;
  private static volatile Data current;
  private static final Map<UUID, BlockPos> overworldPortals = new ConcurrentHashMap<>();

  private WorldSeeds() {
  }

  public static void prepare(Long netherSeed, JSONObject overworldMeta, JSONObject netherMeta) {
    Data data = new Data();
    data.netherSeed = netherSeed;
    JSONObject spawn = overworldMeta == null ? null : overworldMeta.optJSONObject("spawn");
    if (spawn != null && spawn.has("x") && spawn.has("z")) {
      data.spawn = new int[]{spawn.getInt("x"), spawn.getInt("z")};
    }
    JSONArray pools = overworldMeta == null ? null : overworldMeta.optJSONArray("lava_pools");
    JSONObject single = overworldMeta == null ? null : overworldMeta.optJSONObject("lava_pool"); // older rows
    if (pools != null) {
      data.lavaPools = new int[pools.length()][];
      for (int i = 0; i < pools.length(); i++) {
        JSONObject pool = pools.getJSONObject(i);
        data.lavaPools[i] = new int[]{pool.getInt("x"), pool.getInt("z")};
      }
    } else if (single != null && single.has("x") && single.has("z")) {
      data.lavaPools = new int[][]{{single.getInt("x"), single.getInt("z")}};
    }
    JSONObject details = overworldMeta == null ? null : overworldMeta.optJSONObject("details");
    JSONObject chest = details == null ? null : details.optJSONObject("chest");
    // older rows have no completion: all of them were lava
    if (chest != null && details.optString("template").startsWith("minecraft:ruined_portal/")) {
      data.portalChest = new int[]{chest.getInt("x"), chest.getInt("y"), chest.getInt("z")};
      if ("lava".equals(details.optString("completion", "lava"))) {
        data.lavaPortalChest = data.portalChest;
      }
    }
    JSONObject clean = overworldMeta == null ? null : overworldMeta.optJSONObject("clean");
    if (clean != null) {
      data.clean = new Clean();
      data.clean.radius = clean.getInt("radius");
      JSONArray centers = clean.getJSONArray("centers");
      data.clean.centers = new int[centers.length()][];
      for (int i = 0; i < centers.length(); i++) {
        data.clean.centers[i] = new int[]{centers.getJSONArray(i).getInt(0), centers.getJSONArray(i).getInt(1)};
      }
      JSONObject keep = clean.getJSONObject("keep");
      data.clean.keepFeature = keep.getString("feature");
      data.clean.keepChunkX = keep.getInt("chunk_x");
      data.clean.keepChunkZ = keep.getInt("chunk_z");
    }
    JSONObject entry = netherMeta == null ? null : netherMeta.optJSONObject("entry");
    if (entry != null && entry.has("x") && entry.has("y") && entry.has("z")) {
      data.entry = new Entry();
      data.entry.x = entry.getInt("x");
      data.entry.y = entry.getInt("y");
      data.entry.z = entry.getInt("z");
      data.entry.axis = entry.optString("axis", "x");
    }
    pending = data;
  }

  public static void onCreateWorlds(Path worldDir) {
    overworldPortals.clear();
    Path file = worldDir.resolve(FILE);
    Data data = pending;
    pending = null;
    if (data != null) {
      try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
        GSON.toJson(data, w);
      } catch (IOException e) {
        LOGGER.error("Could not save " + file, e);
      }
      // part of the world's seal too
      com.coatt.tascade.network.WorldSeal.written(file.toFile());
    } else if (Files.exists(file)) {
      try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
        data = GSON.fromJson(r, Data.class);
      } catch (Exception e) {
        LOGGER.error("Could not read " + file, e);
      }
    }
    current = data;
  }

  public static boolean active() {
    return current != null;
  }

  public static Long netherSeed() {
    Data data = current;
    return data == null ? null : data.netherSeed;
  }

  public static long combinedSeed(MinecraftServer server) {
    long seed = server.getOverworld().getSeed();
    Long netherSeed = netherSeed();
    return netherSeed == null ? seed : seed * 31 + netherSeed;
  }

  public static int[] spawn() {
    Data data = current;
    return data == null ? null : data.spawn;
  }

  public static int[][] lavaPools() {
    Data data = current;
    return data == null ? null : data.lavaPools;
  }

  public static Clean clean() {
    Data data = current;
    return data == null ? null : data.clean;
  }

  public static BlockPos lavaPortalChest() {
    Data data = current;
    int[] chest = data == null ? null : data.lavaPortalChest;
    return chest == null ? null : new BlockPos(chest[0], chest[1], chest[2]);
  }

  public static BlockPos portalChest() {
    Data data = current;
    int[] chest = data == null ? null : data.portalChest;
    return chest == null ? null : new BlockPos(chest[0], chest[1], chest[2]);
  }

  public static Entry entry() {
    Data data = current;
    return data == null ? null : data.entry;
  }

  public static void rememberOverworldPortal(UUID player, BlockPos pos) {
    overworldPortals.put(player, pos);
  }

  public static BlockPos overworldPortal(UUID player) {
    return overworldPortals.get(player);
  }
}
