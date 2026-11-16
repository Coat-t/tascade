package com.coatt.tascade.network;

import com.coatt.tascade.integration.RunRecorder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.ChunkPos;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// fingerprints every world file as it's written so an edited world can't be rejoined (see backend seal.js). Each fingerprint is first logged to a signed journal so a crash before sending isn't a mismatch
public final class WorldSeal {
  private static final String SEED_FILE = "tascade_world.json";
  // lives in the world folder but isn't sealed
  private static final String JOURNAL = "tascade_seal.journal";
  private static final String[] DATA_DIRS = {"data", "DIM-1/data", "DIM1/data"};
  private static final String[] CHUNK_DIRS = {"region", "DIM-1/region", "DIM1/region", "poi", "DIM-1/poi", "DIM1/poi"};
  // a save cut off by a crash can leave "<uuid>-123.dat" behind
  private static final Pattern PLAYER_FILE = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.dat");
  private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
  private static final int SECTOR = 4096;
  private static final int EXTERNAL = 0x80;
  private static final String GONE = "-";

  private static String world = null;
  private static byte[] secret = null;
  private static long seq = 0;
  private static FileOutputStream journal = null;

  private static final ThreadLocal<String[]> writing = new ThreadLocal<>();

  // world name, signing key, file key, hash (null: gone), seq
  private static final LinkedBlockingQueue<String[]> pending = new LinkedBlockingQueue<>();
  private static final AtomicLong sent = new AtomicLong();
  private static final AtomicLong acked = new AtomicLong();
  private static volatile boolean sending = false;

  static {
    Thread sender = new Thread(WorldSeal::sendLoop, "Tascade-WorldSeal");
    sender.setDaemon(true);
    sender.start();
  }

  private WorldSeal() {}

  // what the game writes

  public static void beforeChunk(Path chunkDir, ChunkPos pos, ByteBuffer buffer) {
    Path runWorld = runWorldDir();
    if (runWorld == null) return;
    String dir = keyOf(runWorld, chunkDir);
    if (dir == null || !isChunkDir(dir) || !buffer.hasArray() || buffer.limit() < 5) return;
    byte[] bytes = buffer.array();
    int base = buffer.arrayOffset();
    MessageDigest sha = sha256();
    sha.update((byte) (bytes[base + 4] & ~EXTERNAL));
    sha.update(bytes, base + 5, buffer.limit() - 5);
    logAhead(runWorld, dir + "/c." + pos.x + "." + pos.z, hex(sha.digest()));
  }

  public static void beforeFile(File file, byte[] bytes) {
    Path runWorld = runWorldDir();
    if (runWorld == null) return;
    String key = keyOf(runWorld, file.toPath());
    if (key == null || !sealed(key)) return;
    logAhead(runWorld, key, hex(sha256().digest(bytes)));
  }

  public static void afterWrite() {
    String[] write = writing.get();
    if (write == null) return;
    writing.remove();
    pending.add(write);
  }

  public static void written(File file) {
    Path runWorld = runWorldDir();
    if (runWorld == null) return;
    String key = keyOf(runWorld, file.toPath());
    if (key == null || !sealed(key)) return;
    try {
      logAhead(runWorld, key, file.isFile() ? hex(sha256().digest(Files.readAllBytes(file.toPath()))) : null);
      afterWrite();
    } catch (IOException e) {
      System.err.println("Couldn't fingerprint " + key + ": " + e);
    }
  }

  public static void sealCopied(Path runWorld) throws IOException {
    Path world = runWorld.toAbsolutePath().normalize();
    for (Map.Entry<String, String> file : files(world).entrySet()) {
      logAhead(world, file.getKey(), file.getValue());
      afterWrite();
    }
  }

  private static void logAhead(Path runWorld, String key, String hash) {
    synchronized (WorldSeal.class) {
      String name = runWorld.getFileName().toString();
      if (!name.equals(world)) start(runWorld);
      long n = ++seq;
      String h = hash == null ? GONE : hash;
      try {
        journal.write((n + " " + key + " " + h + " " + sign(secret, n, key, h) + "\n").getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        System.err.println("Couldn't log " + key + " in the seal journal: " + e);
      }
      writing.set(new String[] {name, hex(secret), key, hash, String.valueOf(n)});
    }
  }

  private static void start(Path runWorld) {
    SecureRandom random = new SecureRandom();
    byte[] key = new byte[32];
    random.nextBytes(key);
    open(runWorld, key, 0, false);
  }

  public static void resume(String worldName, JSONObject seal, long lastSeq) {
    Path runWorld = FabricLoader.getInstance().getGameDir().resolve("saves").resolve(worldName).toAbsolutePath().normalize();
    synchronized (WorldSeal.class) {
      open(runWorld, unhex(seal.optString("key")), lastSeq, true);
    }
  }

  private static void open(Path runWorld, byte[] key, long lastSeq, boolean append) {
    try {
      if (journal != null) journal.close();
    } catch (IOException ignored) {
    }
    world = runWorld.getFileName().toString();
    secret = key;
    seq = lastSeq;
    try {
      journal = new FileOutputStream(runWorld.resolve(JOURNAL).toFile(), append);
    } catch (IOException e) {
      System.err.println("Couldn't open the seal journal: " + e);
      journal = new FileOutputStream(java.io.FileDescriptor.out) {
        @Override public void write(byte[] b) {}
      };
    }
  }

  private static Path runWorldDir() {
    String name = RunRecorder.getWorldName();
    if (name == null || !RunState.isRunning()) return null;
    return FabricLoader.getInstance().getGameDir().resolve("saves").resolve(name).toAbsolutePath().normalize();
  }

  private static void sendLoop() {
    while (true) {
      try {
        String[] first = pending.take();
        sending = true;
        List<String[]> batch = new ArrayList<>();
        batch.add(first);
        pending.drainTo(batch, 499);
        Map<String, JSONArray> byWorld = new TreeMap<>();
        Map<String, String> keys = new HashMap<>();
        for (String[] write : batch) {
          JSONArray entry = new JSONArray();
          entry.put(write[2]);
          entry.put(write[3] == null ? JSONObject.NULL : write[3]);
          entry.put(Long.parseLong(write[4]));
          byWorld.computeIfAbsent(write[0], w -> new JSONArray()).put(entry);
          keys.put(write[0], write[1]);
        }
        for (Map.Entry<String, JSONArray> target : byWorld.entrySet()) {
          long n = sent.incrementAndGet();
          TascadeClient.getInstance().sendSeal(target.getKey(), keys.get(target.getKey()), target.getValue(),
                  () -> acked.accumulateAndGet(n, Math::max));
        }
        sending = false;
      } catch (InterruptedException e) {
        return;
      } catch (Throwable t) {
        sending = false;
        t.printStackTrace();
      }
    }
  }

  public static void flush(long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    try {
      // older backends send no acks
      while (System.currentTimeMillis() < deadline
              && (!pending.isEmpty() || sending || (acked.get() > 0 && acked.get() < sent.get()))) {
        TimeUnit.MILLISECONDS.sleep(20);
      }
    } catch (InterruptedException ignored) {
    }
  }

  // checking a world on disk

  public static final class Check {
    public final List<String> mismatched;
    public final long lastSeq;
    public final int files;

    private Check(List<String> mismatched, long lastSeq, int files) {
      this.mismatched = mismatched;
      this.lastSeq = lastSeq;
      this.files = files;
    }
  }

  public static Check check(Path runWorld, JSONObject seal) throws IOException {
    byte[] key = unhex(seal.optString("key"));
    JSONObject sealed = seal.optJSONObject("files");
    if (sealed == null) sealed = new JSONObject();
    Map<String, String[]> logged = readJournal(runWorld, key);
    TreeMap<String, String> disk = files(runWorld);

    TreeSet<String> all = new TreeSet<>(disk.keySet());
    all.addAll(sealed.keySet());
    List<String> mismatched = new ArrayList<>();
    long lastSeq = 0;
    for (String file : all) {
      String onDisk = disk.get(file);
      JSONArray entry = sealed.optJSONArray(file);
      String sealedHash = entry == null || entry.isNull(0) ? null : entry.optString(0);
      long sealedSeq = entry == null ? -1 : entry.optLong(1, -1);
      lastSeq = Math.max(lastSeq, sealedSeq);
      String[] journal = logged.get(file);
      boolean matches = Objects.equals(onDisk, sealedHash)
              || (journal != null && Long.parseLong(journal[1]) > sealedSeq && Objects.equals(onDisk, journal[0]));
      if (!matches) mismatched.add(file);
    }
    for (String[] journal : logged.values()) lastSeq = Math.max(lastSeq, Long.parseLong(journal[1]));
    return new Check(mismatched, lastSeq, disk.size());
  }

  private static Map<String, String[]> readJournal(Path runWorld, byte[] key) throws IOException {
    Map<String, String[]> newest = new HashMap<>();
    Path file = runWorld.resolve(JOURNAL);
    if (!Files.isRegularFile(file) || key.length == 0) return newest;
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split(" ");
        if (parts.length != 4) continue;
        long n;
        try {
          n = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
          continue;
        }
        if (!MessageDigest.isEqual(sign(key, n, parts[1], parts[2]).getBytes(StandardCharsets.UTF_8),
                parts[3].getBytes(StandardCharsets.UTF_8))) continue;
        String[] current = newest.get(parts[1]);
        if (current == null || n > Long.parseLong(current[1])) {
          newest.put(parts[1], new String[] {GONE.equals(parts[2]) ? null : parts[2], String.valueOf(n)});
        }
      }
    }
    return newest;
  }

  public static TreeMap<String, String> files(Path world) throws IOException {
    TreeMap<String, String> files = new TreeMap<>();
    addFile(files, world, world.resolve("level.dat"));
    addFile(files, world, world.resolve(SEED_FILE));
    addDir(files, world, world.resolve("playerdata"), PLAYER_FILE);
    for (String dir : DATA_DIRS) addDir(files, world, world.resolve(dir), Pattern.compile("[^/]+\\.dat"));
    for (String dir : CHUNK_DIRS) addChunks(files, world, world.resolve(dir), dir);
    return files;
  }

  private static boolean sealed(String key) {
    if (key.equals("level.dat") || key.equals(SEED_FILE)) return true;
    int slash = key.lastIndexOf('/');
    if (slash < 0) return false;
    String dir = key.substring(0, slash);
    String name = key.substring(slash + 1);
    if (dir.equals("playerdata")) return PLAYER_FILE.matcher(name).matches();
    for (String dataDir : DATA_DIRS) if (dir.equals(dataDir)) return name.endsWith(".dat");
    return false;
  }

  private static boolean isChunkDir(String dir) {
    for (String chunkDir : CHUNK_DIRS) if (dir.equals(chunkDir)) return true;
    return false;
  }

  private static void addFile(TreeMap<String, String> files, Path world, Path file) throws IOException {
    if (Files.isRegularFile(file)) files.put(keyOf(world, file), hex(sha256().digest(Files.readAllBytes(file))));
  }

  private static void addDir(TreeMap<String, String> files, Path world, Path dir, Pattern names) throws IOException {
    if (!Files.isDirectory(dir)) return;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path file : stream) {
        if (names.matcher(file.getFileName().toString()).matches()) addFile(files, world, file);
      }
    }
  }

  private static void addChunks(TreeMap<String, String> files, Path world, Path dir, String key) throws IOException {
    if (!Files.isDirectory(dir)) return;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "r.*.mca")) {
      for (Path region : stream) {
        Matcher name = REGION_FILE.matcher(region.getFileName().toString());
        if (!name.matches()) continue;
        int regionX = Integer.parseInt(name.group(1));
        int regionZ = Integer.parseInt(name.group(2));
        ByteBuffer data = ByteBuffer.wrap(Files.readAllBytes(region)).order(ByteOrder.BIG_ENDIAN);
        for (int index = 0; index < 1024 && data.capacity() >= 8192; index++) {
          int sectors = data.getInt(index * 4);
          if (sectors == 0) continue;
          int chunkX = regionX * 32 + index % 32;
          int chunkZ = regionZ * 32 + index / 32;
          long start = (long) (sectors >>> 8) * SECTOR;
          MessageDigest sha = sha256();
          if (start + 5 > data.capacity()) {
            sha.update("truncated".getBytes(StandardCharsets.UTF_8));
          } else {
            int length = data.getInt((int) start);
            byte type = data.get((int) start + 4);
            sha.update((byte) (type & ~EXTERNAL));
            if ((type & EXTERNAL) != 0) {
              Path external = dir.resolve("c." + chunkX + "." + chunkZ + ".mcc");
              if (Files.isRegularFile(external)) sha.update(Files.readAllBytes(external));
            } else {
              int size = Math.max(0, Math.min(length - 1, data.capacity() - (int) start - 5));
              sha.update(data.array(), (int) start + 5, size);
            }
          }
          files.put(key + "/c." + chunkX + "." + chunkZ, hex(sha.digest()));
        }
      }
    }
  }

  private static String keyOf(Path world, Path path) {
    Path absolute = path.toAbsolutePath().normalize();
    if (!absolute.startsWith(world) || absolute.equals(world)) return null;
    return world.relativize(absolute).toString().replace(File.separatorChar, '/');
  }

  private static String sign(byte[] key, long n, String file, String hash) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return hex(mac.doFinal((n + " " + file + " " + hash).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder out = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
    return out.toString();
  }

  private static byte[] unhex(String hex) {
    if (hex == null || hex.length() % 2 != 0) return new byte[0];
    byte[] out = new byte[hex.length() / 2];
    for (int i = 0; i < out.length; i++) {
      int hi = Character.digit(hex.charAt(2 * i), 16);
      int lo = Character.digit(hex.charAt(2 * i + 1), 16);
      if (hi < 0 || lo < 0) return new byte[0];
      out[i] = (byte) (hi << 4 | lo);
    }
    return out;
  }
}
