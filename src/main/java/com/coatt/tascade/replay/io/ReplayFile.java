package com.coatt.tascade.replay.io;

import com.coatt.tascade.replay.RecordCodec;
import com.coatt.tascade.replay.ReplayFormat;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.json.JSONObject;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class ReplayFile implements Closeable {
    private static final long CACHE_BYTES = 96L * 1024 * 1024;

    public final Path path;
    public final JSONObject meta;
    public final boolean complete;
    public final long createdAtMs;
    private final long[] blockOffsets;
    private final int[] blockRawLengths;
    private final int[] blockFirstTimes;
    private final RandomAccessFile raf;
    private final Inflater inflater = new Inflater();
    private long cachedBytes = 0;
    private final LinkedHashMap<Integer, byte[]> cache = new LinkedHashMap<>(64, 0.75f, true);

    private ReplayFile(Path path, RandomAccessFile raf, JSONObject meta, boolean complete, long createdAtMs,
                       List<long[]> blocks) {
        this.path = path;
        this.raf = raf;
        this.meta = meta;
        this.complete = complete;
        this.createdAtMs = createdAtMs;
        this.blockOffsets = new long[blocks.size()];
        this.blockRawLengths = new int[blocks.size()];
        this.blockFirstTimes = new int[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            blockOffsets[i] = blocks.get(i)[0];
            blockRawLengths[i] = (int) blocks.get(i)[1];
            blockFirstTimes[i] = (int) blocks.get(i)[2];
        }
    }

    public static ReplayFile open(Path path) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
        try {
            long createdAtMs = readHeader(raf);
            Footer footer = readFooter(raf);
            if (footer != null) return new ReplayFile(path, raf, footer.meta, true, createdAtMs, footer.blocks);
            return new ReplayFile(path, raf, new JSONObject(), false, createdAtMs, scanBlocks(raf));
        } catch (IOException | RuntimeException e) {
            raf.close();
            throw e;
        }
    }

    public static JSONObject readMeta(Path path) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            readHeader(raf);
            Footer footer = readFooter(raf);
            return footer != null ? footer.meta : null;
        }
    }

    public static JSONObject readStreamMeta(Path path) throws IOException {
        try (ReplayFile file = open(path)) {
            if (file.blockCount() == 0) return null;
            byte[] block = file.block(0);
            Records.Cursor cursor = new Records.Cursor(block, block.length);
            if (!cursor.next() || cursor.kind != ReplayFormat.META) return null;
            PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(block, cursor.payloadOffset, cursor.payloadLength));
            return new JSONObject(RecordCodec.readString(buf));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void rewriteMeta(Path path, JSONObject meta) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
            readHeader(raf);
            long footerOffset = footerOffset(raf);
            Footer footer = footerOffset >= 0 ? readFooterAt(raf, footerOffset) : null;
            if (footer == null) throw new IOException("Replay has no footer: " + path);
            raf.setLength(footerOffset);
            raf.seek(footerOffset);
            ReplayWriter.writeFooter(raf, meta, footer.blocks);
            raf.writeLong(footerOffset);
            raf.writeInt(ReplayFormat.END_MAGIC);
        }
    }

    public int blockCount() {
        return blockOffsets.length;
    }

    public int blockRawLength(int block) {
        return blockRawLengths[block];
    }

    // shared with the cache, don't modify
    public synchronized byte[] block(int index) throws IOException {
        byte[] cached = cache.get(index);
        if (cached != null) return cached;
        raf.seek(blockOffsets[index]);
        if (raf.readByte() != ReplayFormat.BLOCK_TAG) throw new IOException("Corrupt replay block " + index);
        int rawLength = raf.readInt();
        int compressedLength = raf.readInt();
        raf.readInt();
        byte[] compressed = new byte[compressedLength];
        raf.readFully(compressed);
        byte[] raw = new byte[rawLength];
        inflater.reset();
        inflater.setInput(compressed);
        try {
            int done = 0;
            while (done < rawLength) {
                int n = inflater.inflate(raw, done, rawLength - done);
                if (n == 0 && (inflater.finished() || inflater.needsInput())) break;
                done += n;
            }
            if (done != rawLength) throw new IOException("Truncated replay block " + index);
        } catch (DataFormatException e) {
            throw new IOException("Corrupt replay block " + index, e);
        }
        cache.put(index, raw);
        cachedBytes += raw.length;
        while (cachedBytes > CACHE_BYTES && cache.size() > 1) {
            Map.Entry<Integer, byte[]> eldest = cache.entrySet().iterator().next();
            cachedBytes -= eldest.getValue().length;
            cache.remove(eldest.getKey());
        }
        return raw;
    }

    @Override
    public synchronized void close() throws IOException {
        cache.clear();
        inflater.end();
        raf.close();
    }

    private static long readHeader(RandomAccessFile raf) throws IOException {
        if (raf.length() < ReplayFormat.HEADER_SIZE || raf.readInt() != ReplayFormat.MAGIC) {
            throw new IOException("Not a Tascade replay");
        }
        short version = raf.readShort();
        if (version > ReplayFormat.VERSION) throw new IOException("Replay is from a newer Tascade (format " + version + ")");
        raf.readShort();
        return raf.readLong();
    }

    private static final class Footer {
        final JSONObject meta;
        final List<long[]> blocks;

        Footer(JSONObject meta, List<long[]> blocks) {
            this.meta = meta;
            this.blocks = blocks;
        }
    }

    private static long footerOffset(RandomAccessFile raf) throws IOException {
        long length = raf.length();
        if (length < ReplayFormat.HEADER_SIZE + ReplayFormat.TRAILER_SIZE) return -1;
        raf.seek(length - ReplayFormat.TRAILER_SIZE);
        long offset = raf.readLong();
        if (raf.readInt() != ReplayFormat.END_MAGIC) return -1;
        return offset >= ReplayFormat.HEADER_SIZE && offset < length ? offset : -1;
    }

    private static Footer readFooter(RandomAccessFile raf) throws IOException {
        long offset = footerOffset(raf);
        return offset >= 0 ? readFooterAt(raf, offset) : null;
    }

    private static Footer readFooterAt(RandomAccessFile raf, long offset) throws IOException {
        raf.seek(offset);
        if (raf.readByte() != ReplayFormat.FOOTER_TAG) return null;
        int metaLength = raf.readInt();
        byte[] metaBytes = new byte[metaLength];
        raf.readFully(metaBytes);
        JSONObject meta;
        try {
            meta = new JSONObject(new String(metaBytes, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            meta = new JSONObject();
        }
        int count = raf.readInt();
        List<long[]> blocks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            blocks.add(new long[] {raf.readLong(), raf.readInt(), raf.readInt()});
        }
        return new Footer(meta, blocks);
    }

    private static List<long[]> scanBlocks(RandomAccessFile raf) throws IOException {
        List<long[]> blocks = new ArrayList<>();
        long length = raf.length();
        long position = ReplayFormat.HEADER_SIZE;
        while (position + ReplayFormat.BLOCK_HEADER_SIZE <= length) {
            raf.seek(position);
            try {
                if (raf.readByte() != ReplayFormat.BLOCK_TAG) break;
                int rawLength = raf.readInt();
                int compressedLength = raf.readInt();
                int firstTime = raf.readInt();
                long next = position + ReplayFormat.BLOCK_HEADER_SIZE + compressedLength;
                if (rawLength <= 0 || compressedLength <= 0 || next > length) break;
                blocks.add(new long[] {position, rawLength, firstTime});
                position = next;
            } catch (EOFException e) {
                break;
            }
        }
        return blocks;
    }
}
