package com.coatt.tascade.world;

import com.coatt.tascade.network.RunResume;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// POI folders are skipped: written on their own thread, and rebuilt by the game when missing
public final class WorldSnapshot {
    private static final String DIR = "tascade/world-snapshots";
    private static final String PARTIAL = ".partial";
    private static final Set<String> LEFT_OUT = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "session.lock", "tascade_seal.journal", "speedrunigt", "standardoptions.txt",
            "poi", "DIM-1/poi", "DIM1/poi")));

    private static volatile String current = null;
    private static String requestedFor = null;

    private WorldSnapshot() {}

    // on the paused server thread, so nothing changes meanwhile
    public static void takeOnce(MinecraftServer server, String worldName) {
        if (server == null || worldName == null || worldName.equals(requestedFor)) return;
        requestedFor = worldName;
        Path world = saves().resolve(worldName);
        server.execute(() -> {
            long started = System.currentTimeMillis();
            try {
                server.getPlayerManager().saveAllPlayerData();
                server.save(true, true, true);
                Path target = dir().resolve(worldName);
                Path partial = dir().resolve(worldName + PARTIAL);
                delete(partial);
                copy(world, partial);
                delete(target);
                Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
                current = worldName;
                RunResume.snapshotTaken(worldName);
                deleteAllBut(worldName);
                System.out.println("Took a snapshot of " + worldName + " for Reset World in "
                        + (System.currentTimeMillis() - started) + " ms.");
            } catch (Exception e) {
                System.err.println("Couldn't take a snapshot of " + worldName + "; Reset World will generate it again: " + e);
            }
        });
    }

    public static boolean available() {
        String name = current;
        return name != null && Files.isRegularFile(dir().resolve(name).resolve("level.dat"));
    }

    public static void resume(String name) {
        current = name != null && Files.isDirectory(dir().resolve(name)) ? name : null;
        requestedFor = name;
    }

    public static String restore() {
        String name = current;
        if (name == null) return null;
        String worldName = freeWorldName();
        Path target = saves().resolve(worldName);
        try {
            copy(dir().resolve(name), target);
            return worldName;
        } catch (IOException e) {
            System.err.println("Couldn't copy the snapshot of " + name + ": " + e);
            try {
                delete(target);
            } catch (IOException ignored) {
            }
            return null;
        }
    }

    public static String freeWorldName() {
        String name = "Tascade " + System.currentTimeMillis() % 1000000L;
        String free = name;
        for (int i = 2; Files.exists(saves().resolve(free)); i++) free = name + " (" + i + ")";
        return free;
    }

    public static void discard() {
        current = null;
        requestedFor = null;
        try {
            deleteAllBut(null);
        } catch (IOException e) {
            System.err.println("Couldn't delete world snapshots: " + e);
        }
    }

    private static void copy(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (leftOut(from, dir)) return FileVisitResult.SKIP_SUBTREE;
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!leftOut(from, file)) {
                    Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean leftOut(Path root, Path path) {
        if (path.equals(root)) return false;
        return LEFT_OUT.contains(root.relativize(path).toString().replace('\\', '/'));
    }

    private static void deleteAllBut(String keep) throws IOException {
        Path dir = dir();
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path snapshot : stream) {
                if (keep == null || !snapshot.getFileName().toString().equals(keep)) delete(snapshot);
            }
        }
    }

    private static void delete(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve(DIR);
    }

    private static Path saves() {
        return FabricLoader.getInstance().getGameDir().resolve("saves");
    }
}
