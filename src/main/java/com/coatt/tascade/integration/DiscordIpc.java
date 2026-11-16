package com.coatt.tascade.integration;

import org.json.JSONObject;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// Unix sockets need Java 16+, so they're reached by reflection. Frames: opcode, length (LE ints), JSON. Not thread-safe
final class DiscordIpc implements Closeable {
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int OP_PING = 3;
    private static final int OP_PONG = 4;

    // Discord takes the first free of discord-ipc-0..9
    private static final int PIPE_SLOTS = 10;
    private static final int MAX_FRAME_BYTES = 1 << 20;

    private final Pipe pipe;

    private DiscordIpc(Pipe pipe) {
        this.pipe = pipe;
    }

    static DiscordIpc connect(String clientId) throws IOException {
        Pipe pipe = openPipe();
        DiscordIpc ipc = new DiscordIpc(pipe);
        try {
            JSONObject handshake = new JSONObject();
            handshake.put("v", 1);
            handshake.put("client_id", clientId);
            ipc.write(OP_HANDSHAKE, handshake);
            JSONObject ready = ipc.readReply();
            if (!"READY".equals(ready.optString("evt"))) {
                throw new IOException("Discord refused the handshake: " + ready);
            }
            return ipc;
        } catch (IOException | RuntimeException e) {
            ipc.close();
            throw e;
        }
    }

    void setActivity(JSONObject activity) throws IOException {
        JSONObject args = new JSONObject();
        args.put("pid", processId());
        args.put("activity", activity != null ? activity : JSONObject.NULL);
        JSONObject command = new JSONObject();
        command.put("cmd", "SET_ACTIVITY");
        command.put("args", args);
        command.put("nonce", UUID.randomUUID().toString());
        write(OP_FRAME, command);
        JSONObject reply = readReply();
        if ("ERROR".equals(reply.optString("evt"))) {
            throw new IllegalArgumentException("Discord rejected the activity: " + reply.optJSONObject("data"));
        }
    }

    @Override
    public void close() {
        try {
            pipe.close();
        } catch (IOException ignored) {
        }
    }

    private void write(int opcode, JSONObject payload) throws IOException {
        byte[] json = payload.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(8 + json.length).order(ByteOrder.LITTLE_ENDIAN);
        frame.putInt(opcode).putInt(json.length).put(json);
        pipe.write(frame.array());
    }

    private JSONObject readReply() throws IOException {
        while (true) {
            ByteBuffer header = ByteBuffer.wrap(pipe.read(8)).order(ByteOrder.LITTLE_ENDIAN);
            int opcode = header.getInt();
            int length = header.getInt();
            if (length < 0 || length > MAX_FRAME_BYTES) throw new IOException("Bad frame length " + length);
            JSONObject payload = new JSONObject(new String(pipe.read(length), StandardCharsets.UTF_8));
            switch (opcode) {
                case OP_FRAME:
                    return payload;
                case OP_PING:
                    write(OP_PONG, payload);
                    break;
                case OP_CLOSE:
                    throw new IOException("Discord closed the connection: " + payload);
                default:
                    break;
            }
        }
    }

    private static Pipe openPipe() throws IOException {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        IOException last = null;
        for (String path : candidatePaths(windows)) {
            try {
                return windows ? new FilePipe(path) : SocketPipe.open(Paths.get(path));
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("Discord is not running");
    }

    private static List<String> candidatePaths(boolean windows) {
        List<String> paths = new ArrayList<>();
        if (windows) {
            for (int i = 0; i < PIPE_SLOTS; i++) paths.add("\\\\?\\pipe\\discord-ipc-" + i);
            return paths;
        }
        List<String> dirs = new ArrayList<>();
        for (String variable : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String dir = System.getenv(variable);
            if (dir != null && !dir.isEmpty() && !dirs.contains(dir)) dirs.add(dir);
        }
        if (!dirs.contains("/tmp")) dirs.add("/tmp");
        String[] subdirs = {"", "app/com.discordapp.Discord/", "snap.discord/", ".flatpak/dev.vencord.Vesktop/xdg-run/"};
        for (int i = 0; i < PIPE_SLOTS; i++) {
            for (String dir : dirs) {
                for (String subdir : subdirs) {
                    Path path = Paths.get(dir, subdir + "discord-ipc-" + i);
                    if (Files.exists(path)) paths.add(path.toString());
                }
            }
        }
        return paths;
    }

    private static long processId() {
        // Java 8 has no ProcessHandle, the runtime name is "pid@host"
        String name = ManagementFactory.getRuntimeMXBean().getName();
        try {
            return Long.parseLong(name.substring(0, name.indexOf('@')));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private interface Pipe extends Closeable {
        void write(byte[] bytes) throws IOException;

        byte[] read(int length) throws IOException;
    }

    private static final class FilePipe implements Pipe {
        private final RandomAccessFile file;

        FilePipe(String path) throws IOException {
            file = new RandomAccessFile(path, "rw");
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            file.write(bytes);
        }

        @Override
        public byte[] read(int length) throws IOException {
            byte[] bytes = new byte[length];
            file.readFully(bytes);
            return bytes;
        }

        @Override
        public void close() throws IOException {
            file.close();
        }
    }

    private static final class SocketPipe implements Pipe {
        private final SocketChannel channel;

        private SocketPipe(SocketChannel channel) {
            this.channel = channel;
        }

        static SocketPipe open(Path path) throws IOException {
            SocketChannel channel;
            SocketAddress address;
            try {
                ProtocolFamily unix = StandardProtocolFamily.valueOf("UNIX");
                channel = (SocketChannel) SocketChannel.class.getMethod("open", ProtocolFamily.class).invoke(null, unix);
                address = (SocketAddress) Class.forName("java.net.UnixDomainSocketAddress")
                        .getMethod("of", Path.class).invoke(null, path);
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                throw new IOException("Discord's socket needs Java 16 or newer", e);
            }
            try {
                channel.connect(address);
                return new SocketPipe(channel);
            } catch (IOException | RuntimeException e) {
                channel.close();
                throw e;
            }
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
        }

        @Override
        public byte[] read(int length) throws IOException {
            ByteBuffer buffer = ByteBuffer.allocate(length);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) throw new EOFException("Discord closed the socket");
            }
            return buffer.array();
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }
}
