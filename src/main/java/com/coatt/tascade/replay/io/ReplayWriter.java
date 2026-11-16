package com.coatt.tascade.replay.io;

import com.coatt.tascade.replay.ReplayFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

// written as name.tcr.part and renamed once the footer is in place
public final class ReplayWriter {
    private static final Logger LOGGER = LogManager.getLogger("Tascade/Replay");

    private static final class Finish {
        final JSONObject meta;
        final boolean keep;

        Finish(JSONObject meta, boolean keep) {
            this.meta = meta;
            this.keep = keep;
        }
    }

    private final Path partPath;
    private final Path finalPath;
    private final RandomAccessFile out;
    private final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    private final Thread thread;
    private final Deflater deflater = new Deflater(5);

    private byte[] block = new byte[ReplayFormat.BLOCK_TARGET_SIZE + 64 * 1024];
    private byte[] compressed = new byte[ReplayFormat.BLOCK_TARGET_SIZE + 64 * 1024];
    private int blockLength = 0;
    private int blockFirstTime = 0;
    private long blockStartedAtMs = 0;
    private final List<long[]> blocks = new ArrayList<>();
    private volatile boolean failed = false;
    private volatile boolean finished = false;

    public ReplayWriter(Path finalPath, long createdAtMs) throws IOException {
        this.finalPath = finalPath;
        this.partPath = finalPath.resolveSibling(stripExtension(finalPath.getFileName().toString())
                + ReplayFormat.PARTIAL_EXTENSION);
        Files.createDirectories(finalPath.getParent());
        this.out = new RandomAccessFile(partPath.toFile(), "rw");
        out.setLength(0);
        out.writeInt(ReplayFormat.MAGIC);
        out.writeShort(ReplayFormat.VERSION);
        out.writeShort(0);
        out.writeLong(createdAtMs);
        this.thread = new Thread(this::run, "Tascade-ReplayWriter");
        thread.setDaemon(true);
        thread.start();
    }

    public Path path() {
        return finalPath;
    }

    public void submit(byte[] record) {
        if (!failed && !finished) queue.add(record);
    }

    public void finish(JSONObject meta, boolean keep) {
        if (finished) return;
        finished = true;
        queue.add(new Finish(meta, keep));
        try {
            thread.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void run() {
        try {
            while (true) {
                Object next = queue.poll(1, TimeUnit.SECONDS);
                if (next == null) {
                    if (blockLength > 0 && System.currentTimeMillis() - blockStartedAtMs > ReplayFormat.BLOCK_MAX_AGE_MS) {
                        flushBlock();
                    }
                    continue;
                }
                if (next instanceof byte[]) {
                    append((byte[]) next);
                    continue;
                }
                Finish finish = (Finish) next;
                complete(finish.meta, finish.keep);
                return;
            }
        } catch (Throwable t) {
            failed = true;
            LOGGER.error("Replay recording failed; {} is incomplete", partPath, t);
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void append(byte[] record) throws IOException {
        if (blockLength > 0 && blockLength + record.length > ReplayFormat.BLOCK_TARGET_SIZE) flushBlock();
        if (blockLength == 0) {
            blockFirstTime = readInt(record, 0);
            blockStartedAtMs = System.currentTimeMillis();
        }
        if (blockLength + record.length > block.length) {
            byte[] bigger = new byte[blockLength + record.length];
            System.arraycopy(block, 0, bigger, 0, blockLength);
            block = bigger;
        }
        System.arraycopy(record, 0, block, blockLength, record.length);
        blockLength += record.length;
        if (System.currentTimeMillis() - blockStartedAtMs > ReplayFormat.BLOCK_MAX_AGE_MS) flushBlock();
    }

    private void flushBlock() throws IOException {
        if (blockLength == 0) return;
        deflater.reset();
        deflater.setInput(block, 0, blockLength);
        deflater.finish();
        int compressedLength = 0;
        while (!deflater.finished()) {
            if (compressedLength == compressed.length) {
                byte[] bigger = new byte[compressed.length * 2];
                System.arraycopy(compressed, 0, bigger, 0, compressedLength);
                compressed = bigger;
            }
            compressedLength += deflater.deflate(compressed, compressedLength, compressed.length - compressedLength);
        }
        long offset = out.getFilePointer();
        out.writeByte(ReplayFormat.BLOCK_TAG);
        out.writeInt(blockLength);
        out.writeInt(compressedLength);
        out.writeInt(blockFirstTime);
        out.write(compressed, 0, compressedLength);
        blocks.add(new long[] {offset, blockLength, blockFirstTime});
        blockLength = 0;
    }

    private void complete(JSONObject meta, boolean keep) throws IOException {
        flushBlock();
        long footerOffset = out.getFilePointer();
        writeFooter(out, meta, blocks);
        out.writeLong(footerOffset);
        out.writeInt(ReplayFormat.END_MAGIC);
        out.close();
        deflater.end();
        if (keep) {
            Files.move(partPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Saved replay {}", finalPath);
        } else {
            Files.deleteIfExists(partPath);
            LOGGER.info("Discarded replay of a run that wasn't kept");
        }
    }

    static void writeFooter(RandomAccessFile out, JSONObject meta, List<long[]> blocks) throws IOException {
        byte[] metaBytes = meta.toString().getBytes(StandardCharsets.UTF_8);
        out.writeByte(ReplayFormat.FOOTER_TAG);
        out.writeInt(metaBytes.length);
        out.write(metaBytes);
        out.writeInt(blocks.size());
        for (long[] block : blocks) {
            out.writeLong(block[0]);
            out.writeInt((int) block[1]);
            out.writeInt((int) block[2]);
        }
    }

    static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 24 | (bytes[offset + 1] & 0xFF) << 16
                | (bytes[offset + 2] & 0xFF) << 8 | (bytes[offset + 3] & 0xFF);
    }

    private static String stripExtension(String name) {
        return name.endsWith(ReplayFormat.EXTENSION) ? name.substring(0, name.length() - ReplayFormat.EXTENSION.length()) : name;
    }
}
