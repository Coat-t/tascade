package com.coatt.tascade.replay;

import com.coatt.tascade.replay.io.ReplayFile;
import net.fabricmc.loader.api.FabricLoader;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ReplayStorage {
    private ReplayStorage() {}

    public static Path directory() {
        return FabricLoader.getInstance().getGameDir().resolve("tascade").resolve("replays");
    }

    public static Path newReplayPath(String mode) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date());
        String base = stamp + "_" + mode.toLowerCase(Locale.ROOT);
        Path path = directory().resolve(base + ReplayFormat.EXTENSION);
        for (int n = 2; Files.exists(path); n++) {
            path = directory().resolve(base + "_" + n + ReplayFormat.EXTENSION);
        }
        return path;
    }

    public static final class Entry {
        public final Path path;
        public final long sizeBytes;
        public final long modifiedMs;
        public final JSONObject meta;
        public final boolean complete;

        Entry(Path path, long sizeBytes, long modifiedMs, JSONObject meta, JSONObject started) {
            this.path = path;
            this.sizeBytes = sizeBytes;
            this.modifiedMs = modifiedMs;
            this.meta = meta != null ? meta : started != null ? started : new JSONObject();
            this.complete = meta != null;
        }
    }

    public static List<Entry> list() {
        List<Entry> entries = new ArrayList<>();
        Path dir = directory();
        if (!Files.isDirectory(dir)) return entries;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                boolean partial = name.endsWith(ReplayFormat.PARTIAL_EXTENSION);
                if (!partial && !name.endsWith(ReplayFormat.EXTENSION)) continue;
                try {
                    long modified = Files.getLastModifiedTime(path).toMillis();
                    if (partial && (isBeingRecorded(path) || System.currentTimeMillis() - modified < 60_000)) continue;
                    JSONObject meta = partial ? null : ReplayFile.readMeta(path);
                    JSONObject started = meta == null ? ReplayFile.readStreamMeta(path) : null;
                    entries.add(new Entry(path, Files.size(path), modified, meta, started));
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        entries.sort(Comparator.comparingLong((Entry e) -> e.meta.optLong("startedAt", e.modifiedMs)).reversed());
        return entries;
    }

    private static boolean isBeingRecorded(Path partialPath) {
        Path recording = com.coatt.tascade.replay.record.ReplayRecorder.currentPath();
        if (recording == null) return false;
        String base = recording.getFileName().toString();
        base = base.substring(0, base.length() - ReplayFormat.EXTENSION.length());
        return partialPath.getFileName().toString().equals(base + ReplayFormat.PARTIAL_EXTENSION);
    }

    public static boolean delete(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            return false;
        }
    }
}
