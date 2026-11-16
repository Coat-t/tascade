package com.coatt.tascade.replay.record;

import com.coatt.tascade.TascadeConfig;
import com.coatt.tascade.integration.RunRecorder;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.replay.RecordCodec;
import com.coatt.tascade.replay.ReplayFormat;
import com.coatt.tascade.replay.ReplayStorage;
import com.coatt.tascade.replay.io.ReplayFile;
import com.coatt.tascade.replay.io.ReplayWriter;
import com.coatt.tascade.replay.io.Records;
import io.netty.buffer.Unpooled;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.KeepAliveS2CPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ReplayRecorder {
    private static final Logger LOGGER = LogManager.getLogger("Tascade/Replay");

    public enum Outcome {
        COMPLETED,
        FINISHED,
        VICTORY,
        DEFEAT,
        DRAW,
        ENDED,
        LEFT,
        SEED_CHANGED,
        ABANDONED
    }

    private static final class Armed {
        final JSONObject meta;
        final boolean ranked;

        Armed(JSONObject meta, boolean ranked) {
            this.meta = meta;
            this.ranked = ranked;
        }
    }

    static final class Session {
        final ReplayWriter writer;
        final ClientConnection connection;
        final JSONObject meta;
        final boolean ranked;
        final long startNanos = System.nanoTime();
        private int lastTime = 0;
        private volatile boolean stopped = false;
        volatile int raceStartTime = -1;
        private int deathTime = -1;
        private UUID deathOf;

        Session(ReplayWriter writer, ClientConnection connection, JSONObject meta, boolean ranked) {
            this.writer = writer;
            this.connection = connection;
            this.meta = meta;
            this.ranked = ranked;
        }

        int now() {
            return (int) ((System.nanoTime() - startNanos) / 1_000_000L);
        }

        synchronized void writeAt(int time, int kind, PacketByteBuf payload) {
            if (stopped) return;
            writer.submit(Records.frame(time, kind, payload));
        }

        synchronized void write(int kind, PacketByteBuf payload) {
            if (stopped) return;
            int time = Math.max(lastTime, now());
            lastTime = time;
            writer.submit(Records.frame(time, kind, payload));
        }
    }

    private static final class AwaitingResult {
        final Path path;
        final JSONObject meta;
        final String matchId;

        AwaitingResult(Path path, JSONObject meta, String matchId) {
            this.path = path;
            this.meta = meta;
            this.matchId = matchId;
        }
    }

    private static volatile Armed armed;
    private static volatile Session session;
    private static volatile AwaitingResult awaitingResult;
    private static final Set<Class<?>> failedPackets = Collections.synchronizedSet(new HashSet<>());

    private ReplayRecorder() {}

    public static void arm(String worldName) {
        if (session != null) stop(Outcome.ABANDONED);
        armed = null;
        boolean ranked = RunState.isRanked();
        if (!anyWanted(ranked)) {
            LOGGER.info("Not recording this run: the replay settings keep none of its endings");
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        JSONObject meta = new JSONObject();
        meta.put("formatVersion", ReplayFormat.VERSION);
        meta.put("modVersion", FabricLoader.getInstance().getModContainer("tascade")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
        meta.put("minecraftVersion", "1.16.1");
        meta.put("cameraTiming", 2);
        meta.put("mode", ranked ? "RANKED" : "SOLO");
        meta.put("worldName", worldName);
        meta.put("playerName", client.getSession().getUsername());
        meta.put("playerUuid", client.getSession().getProfile().getId() != null
                ? client.getSession().getProfile().getId().toString() : "");
        putIfPresent(meta, "matchId", RunState.matchId);
        putIfPresent(meta, "seedType", RunState.seedType);
        putIfPresent(meta, "bastionType", RunState.bastionType);
        if (RunState.cheats) meta.put("cheats", true);
        if (ranked) {
            putIfPresent(meta, "opponentName", RunState.opponentName);
            putIfPresent(meta, "opponentUuid", RunState.opponentUuid);
            meta.put("opponentElo", RunState.opponentElo);
        }
        armed = new Armed(meta, ranked);
    }

    public static void onPacket(ClientConnection connection, Packet<?> packet) {
        Session current = session;
        if (current == null) {
            Armed pending = armed;
            if (pending == null || !(packet instanceof GameJoinS2CPacket) || !RunState.isRunning()) return;
            current = begin(pending, connection);
            if (current == null) return;
        } else if (current.connection != connection) {
            return;
        }
        if (packet instanceof KeepAliveS2CPacket) return;
        Integer id = NetworkState.PLAY.getPacketId(NetworkSide.CLIENTBOUND, packet);
        if (id == null) return;
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer(256));
        try {
            buf.writeVarInt(id);
            packet.write(buf);
            current.write(ReplayFormat.PACKET, buf);
        } catch (Exception e) {
            if (failedPackets.add(packet.getClass())) LOGGER.warn("Couldn't record a {}", packet.getClass().getSimpleName(), e);
        } finally {
            buf.release();
        }
    }

    private static synchronized Session begin(Armed pending, ClientConnection connection) {
        if (session != null) return session.connection == connection ? session : null;
        if (armed != pending) return null;
        armed = null;
        try {
            long now = System.currentTimeMillis();
            pending.meta.put("startedAt", now);
            pending.meta.put("startedAtIso", Instant.ofEpochMilli(now).toString());
            Path path = ReplayStorage.newReplayPath(pending.meta.getString("mode"));
            ReplayWriter writer = new ReplayWriter(path, now);
            Session started = new Session(writer, connection, pending.meta, pending.ranked);
            PacketByteBuf metaRecord = new PacketByteBuf(Unpooled.buffer());
            RecordCodec.writeString(metaRecord, pending.meta.toString());
            started.write(ReplayFormat.META, metaRecord);
            session = started;
            ClientSampler.reset();
            LOGGER.info("Recording run to {}", path.getFileName());
            return started;
        } catch (IOException e) {
            LOGGER.error("Couldn't start recording the run", e);
            return null;
        }
    }

    static Session current() {
        return session;
    }

    public static boolean isRecording() {
        return session != null;
    }

    public static Path currentPath() {
        Session current = session;
        return current != null ? current.writer.path() : null;
    }

    static void write(int kind, PacketByteBuf payload) {
        Session current = session;
        if (current != null) current.write(kind, payload);
    }

    public static void marker(String type, String name, JSONObject extra) {
        Session current = session;
        if (current == null) return;
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            RecordCodec.writeMarker(buf, type, name, extra);
            current.write(ReplayFormat.MARKER, buf);
        } finally {
            buf.release();
        }
    }

    static void died(Session session, UUID player) {
        synchronized (session) {
            writeDeath(session);
            session.deathTime = session.now();
            session.deathOf = player;
        }
    }

    static void respawned(Session session) {
        writeDeath(session);
    }

    private static void writeDeath(Session session) {
        synchronized (session) {
            if (session.deathTime < 0) return;
            PacketByteBuf buf = markerPayload(DeathResets.wasReset(session.deathOf) ? "death_reset" : "death", "");
            try {
                session.writeAt(session.deathTime, ReplayFormat.MARKER, buf);
            } finally {
                buf.release();
            }
            session.deathTime = -1;
        }
    }

    public static void raceStarted() {
        Session current = session;
        if (current == null) return;
        current.raceStartTime = current.now();
        marker("race_start", "", null);
    }

    public static void split(String name, long rta) {
        JSONObject extra = new JSONObject();
        extra.put("rta", rta);
        marker("split", name, extra);
    }

    public static void goal(String name, long rta) {
        JSONObject extra = new JSONObject();
        extra.put("rta", rta);
        marker("goal", name, extra);
    }

    public static void opponentSplit(String name, long rta) {
        JSONObject extra = new JSONObject();
        extra.put("rta", rta);
        marker("opponent_split", name, extra);
    }

    public static void opponentGoal(String name, long rta) {
        JSONObject extra = new JSONObject();
        extra.put("rta", rta);
        marker("opponent_goal", name, extra);
    }

    public static void runCompleted(long rta) {
        Session current = session;
        if (current == null) return;
        current.meta.put("finalRtaMs", rta);
        stop(current.ranked ? Outcome.FINISHED : Outcome.COMPLETED);
    }

    public static void matchResult(Outcome outcome, boolean forfeit, String matchId) {
        Session current = session;
        if (current != null && current.ranked) {
            if (forfeit) current.meta.put("forfeit", true);
            stop(outcome);
            return;
        }
        AwaitingResult waiting = awaitingResult;
        if (waiting == null) return;
        if (matchId != null && waiting.matchId != null && !matchId.equals(waiting.matchId)) return;
        awaitingResult = null;
        if (!wanted(true, outcome, forfeit)) {
            if (!ReplayStorage.delete(waiting.path)) LOGGER.warn("Couldn't delete {}", waiting.path.getFileName());
            return;
        }
        waiting.meta.put("outcome", outcome.name());
        if (forfeit) waiting.meta.put("forfeit", true);
        try {
            ReplayFile.rewriteMeta(waiting.path, waiting.meta);
        } catch (IOException e) {
            LOGGER.warn("Couldn't write the match result into {}", waiting.path.getFileName(), e);
        }
    }

    public static void leftWorld(boolean seedChange) {
        armed = null;
        Session current = session;
        if (current == null) return;
        if (seedChange) stop(Outcome.SEED_CHANGED);
        else stop(current.ranked ? Outcome.LEFT : Outcome.ABANDONED);
    }

    public static synchronized void stop(Outcome outcome) {
        Session current = session;
        if (current == null) return;
        writeDeath(current);
        int endTime = current.now();
        current.write(ReplayFormat.MARKER, markerPayload("run_end", outcome.name()));
        session = null;
        current.stopped = true;

        JSONObject meta = current.meta;
        meta.put("outcome", outcome.name());
        meta.put("durationMs", endTime);
        meta.put("endedAt", System.currentTimeMillis());
        if (current.raceStartTime >= 0) meta.put("raceStartMs", current.raceStartTime);
        JSONObject splits = new JSONObject();
        for (Map.Entry<String, Long> split : RunRecorder.splitTimes().entrySet()) splits.put(split.getKey(), split.getValue());
        meta.put("splits", splits);

        boolean keep = wanted(current.ranked, outcome, meta.optBoolean("forfeit"));
        current.writer.finish(meta, keep);
        if (keep && outcome == Outcome.FINISHED) {
            awaitingResult = new AwaitingResult(current.writer.path(), meta, meta.optString("matchId", null));
        }
    }

    private static boolean wanted(boolean ranked, Outcome outcome, boolean forfeit) {
        TascadeConfig settings = TascadeConfig.replays();
        if (!ranked) {
            switch (outcome) {
                case COMPLETED: return settings.replaySoloCompleted;
                case SEED_CHANGED: return settings.replaySoloSeedChanged;
                default: return settings.replaySoloAbandoned;
            }
        }
        switch (outcome) {
            case FINISHED: return settings.replayRankedWon || settings.replayRankedLost;
            case VICTORY: return settings.replayRankedWon;
            case DEFEAT: return forfeit ? settings.replayRankedForfeited : settings.replayRankedLost;
            case LEFT:
            case ABANDONED: return settings.replayRankedForfeited;
            case SEED_CHANGED: return settings.replayRankedSeedChanged;
            default: return settings.replayRankedDrawn;
        }
    }

    private static boolean anyWanted(boolean ranked) {
        for (Outcome outcome : Outcome.values()) {
            if (wanted(ranked, outcome, false) || wanted(ranked, outcome, true)) return true;
        }
        return false;
    }

    private static PacketByteBuf markerPayload(String type, String name) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        RecordCodec.writeMarker(buf, type, name, null);
        return buf;
    }

    private static void putIfPresent(JSONObject meta, String key, Object value) {
        if (value != null) meta.put(key, value);
    }
}
