package com.coatt.tascade.replay.play;

import com.coatt.tascade.replay.ReplayFormat;
import com.coatt.tascade.replay.io.ReplayFile;
import com.coatt.tascade.replay.io.Records;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public final class ReplayIndex {
    static final int CATEGORY_MASK = 0x1F;
    static final int FULL_CHUNK = 0x20;
    private static final int ARRIVAL_WAIT_MS = 30_000;

    final ReplayFile file;
    final Tracks tracks = new Tracks();

    int count;
    int[] time = new int[4096];
    // high half: block number. Low half: offset of the packet's fields
    long[] location = new long[4096];
    int[] length = new int[4096];
    short[] packetId = new short[4096];
    byte[] meta = new byte[4096];

    final List<Segment> segments = new ArrayList<>();
    final IntArrayList globalCumulative = new IntArrayList();
    final IntArrayList cooldowns = new IntArrayList();
    int selfId = Integer.MIN_VALUE;
    final List<Arrival> arrivals = new ArrayList<>();
    private int awaitingArrival = -1;
    int maxLoadDistance = 2;
    int duration;

    static final class Arrival {
        final int since;
        final int time;
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;
        int settled;

        Arrival(int since, int time, double x, double y, double z, float yaw, float pitch) {
            this.since = since;
            this.time = time;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.settled = time;
        }
    }

    static final class Segment {
        final int start;
        int end;
        final RegistryKey<World> dimension;
        final PlayerRespawnS2CPacket respawn;
        final Long2ObjectOpenHashMap<ChunkHistory> chunks = new Long2ObjectOpenHashMap<>();
        final Int2ObjectOpenHashMap<List<EntityLife>> entityLives = new Int2ObjectOpenHashMap<>();
        final List<EntityLife> lives = new ArrayList<>();
        final Int2ObjectOpenHashMap<IntArrayList> latest = new Int2ObjectOpenHashMap<>();
        final IntArrayList cumulative = new IntArrayList();
        final IntArrayList respawns = new IntArrayList();
        private final Int2ObjectOpenHashMap<EntityLife> current = new Int2ObjectOpenHashMap<>();

        Segment(int start, RegistryKey<World> dimension, PlayerRespawnS2CPacket respawn) {
            this.start = start;
            this.end = Integer.MAX_VALUE;
            this.dimension = dimension;
            this.respawn = respawn;
        }

        EntityLife lifeAt(int entityId, int index) {
            List<EntityLife> lives = entityLives.get(entityId);
            if (lives == null) return null;
            for (int i = lives.size() - 1; i >= 0; i--) {
                EntityLife life = lives.get(i);
                if (life.spawn <= index) return index < life.end ? life : null;
            }
            return null;
        }
    }

    static final class ChunkHistory {
        final IntArrayList events = new IntArrayList(4);
        final IntArrayList bases = new IntArrayList(4);
    }

    static final class EntityLife {
        final int entityId;
        final int spawn;
        int end = Integer.MAX_VALUE;
        final IntArrayList events = new IntArrayList(4);
        final boolean hasPosition;
        final long spawnX;
        final long spawnY;
        final long spawnZ;
        final byte spawnYaw;
        final byte spawnPitch;
        // movement is relative, so every 16 moves the absolute position is noted
        final IntArrayList checkpointEvent = new IntArrayList(0);
        final LongArrayList checkpointPosition = new LongArrayList(0);
        final ByteArrayList checkpointRotation = new ByteArrayList(0);
        private long x;
        private long y;
        private long z;
        private byte yaw;
        private byte pitch;
        private int movesSinceCheckpoint;

        EntityLife(int entityId, int spawn, boolean hasPosition, long x, long y, long z, byte yaw, byte pitch) {
            this.entityId = entityId;
            this.spawn = spawn;
            this.hasPosition = hasPosition;
            this.spawnX = this.x = x;
            this.spawnY = this.y = y;
            this.spawnZ = this.z = z;
            this.spawnYaw = this.yaw = yaw;
            this.spawnPitch = this.pitch = pitch;
        }

        void move(int index, MoveDelta move) {
            events.add(index);
            if (move.absolute) {
                x = move.x;
                y = move.y;
                z = move.z;
            } else if (move.moved) {
                x += move.x;
                y += move.y;
                z += move.z;
            }
            if (move.rotated) {
                yaw = move.yaw;
                pitch = move.pitch;
            }
            if (++movesSinceCheckpoint >= 16) {
                movesSinceCheckpoint = 0;
                checkpointEvent.add(events.size() - 1);
                checkpointPosition.add(x);
                checkpointPosition.add(y);
                checkpointPosition.add(z);
                checkpointRotation.add(yaw);
                checkpointRotation.add(pitch);
            }
        }
    }

    static final class MoveDelta {
        boolean absolute;
        boolean moved;
        boolean rotated;
        long x;
        long y;
        long z;
        byte yaw;
        byte pitch;
    }

    private ReplayIndex(ReplayFile file) {
        this.file = file;
    }

    public static ReplayIndex build(ReplayFile file, Consumer<Float> progress) throws IOException {
        ReplayIndex index = new ReplayIndex(file);
        int blocks = file.blockCount();
        MoveDelta move = new MoveDelta();
        for (int block = 0; block < blocks; block++) {
            byte[] data = file.block(block);
            Records.Cursor cursor = new Records.Cursor(data, data.length);
            while (cursor.next()) {
                index.duration = Math.max(index.duration, cursor.time);
                if (cursor.kind == ReplayFormat.PACKET) {
                    index.addPacket(block, data, cursor, move);
                } else {
                    try {
                        index.tracks.add(cursor.kind, cursor.time, data, cursor.payloadOffset, cursor.payloadLength);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            progress.accept((block + 1f) / blocks);
        }
        if (!index.segments.isEmpty()) index.segments.get(index.segments.size() - 1).end = index.count;
        index.settleArrivals();
        return index;
    }

    private void settleArrivals() {
        for (int k = 0; k < arrivals.size(); k++) {
            Arrival arrival = arrivals.get(k);
            int until = k + 1 < arrivals.size() ? arrivals.get(k + 1).since : duration;
            int settled = tracks.firstPlayerNear(arrival.time, until, arrival.x, arrival.y, arrival.z, 4);
            if (settled >= 0) arrival.settled = settled;
        }
    }

    private void addPacket(int block, byte[] data, Records.Cursor cursor, MoveDelta move) {
        ByteReader idReader = new ByteReader(data, cursor.payloadOffset);
        int id = idReader.readVarInt();
        int fieldsOffset = cursor.payloadOffset + varIntSize(id);
        int fieldsLength = cursor.payloadLength - (fieldsOffset - cursor.payloadOffset);
        Class<?> type = PacketTypes.classOf(id);
        if (type == null) return;

        if (count == time.length) grow();
        int i = count;
        byte category = PacketTypes.category(id);
        int extra = 0;
        try {
            switch (category) {
                case PacketTypes.JOIN: {
                    GameJoinS2CPacket join = (GameJoinS2CPacket) decode(id, data, fieldsOffset, fieldsLength);
                    selfId = join.getEntityId();
                    awaitingArrival = cursor.time;
                    maxLoadDistance = Math.max(maxLoadDistance, join.getChunkLoadDistance());
                    closeSegment(i);
                    segments.add(new Segment(i, join.getDimensionId(), new PlayerRespawnS2CPacket(join.method_29444(),
                            join.getDimensionId(), join.getSha256Seed(), join.getGameMode(), join.method_30116(),
                            join.isDebugWorld(), join.isFlatWorld(), false)));
                    break;
                }
                case PacketTypes.RESPAWN: {
                    PlayerRespawnS2CPacket respawn = (PlayerRespawnS2CPacket) decode(id, data, fieldsOffset, fieldsLength);
                    awaitingArrival = cursor.time;
                    Segment segment = currentSegment();
                    if (segment == null || !segment.dimension.equals(respawn.getDimension())) {
                        closeSegment(i);
                        segments.add(new Segment(i, respawn.getDimension(), respawn));
                    } else {
                        segment.respawns.add(i);
                    }
                    break;
                }
                case PacketTypes.CHUNK: {
                    Segment segment = currentSegment();
                    if (segment == null) {
                        category = PacketTypes.DROP;
                        break;
                    }
                    long key = PacketTypes.chunkKey(type, new ByteReader(data, fieldsOffset));
                    ChunkHistory history = segment.chunks.get(key);
                    if (history == null) segment.chunks.put(key, history = new ChunkHistory());
                    int base = history.bases.isEmpty() ? -1 : history.bases.getInt(history.bases.size() - 1);
                    if (type == ChunkDataS2CPacket.class && PacketTypes.isFullChunk(new ByteReader(data, fieldsOffset))) {
                        extra = FULL_CHUNK;
                        base = i;
                    } else if (type == UnloadChunkS2CPacket.class) {
                        base = -1;
                    }
                    history.events.add(i);
                    history.bases.add(base);
                    break;
                }
                case PacketTypes.ENTITY_SPAWN:
                    category = indexSpawn(i, type, data, fieldsOffset);
                    break;
                case PacketTypes.ENTITY_MOVE:
                case PacketTypes.ENTITY_STATE:
                case PacketTypes.ENTITY_LINK: {
                    int entityId = PacketTypes.entityId(type, new ByteReader(data, fieldsOffset));
                    if (entityId == selfId) {
                        category = type == EntityPassengersSetS2CPacket.class ? category : PacketTypes.DROP;
                        if (category == PacketTypes.DROP) break;
                    }
                    Segment segment = currentSegment();
                    EntityLife life = segment != null ? segment.current.get(entityId) : null;
                    if (life == null) break;
                    if (category == PacketTypes.ENTITY_MOVE) {
                        if (readMove(type, new ByteReader(data, fieldsOffset), move)) life.move(i, move);
                        else life.events.add(i);
                    } else {
                        life.events.add(i);
                    }
                    break;
                }
                case PacketTypes.ENTITY_DESTROY: {
                    Segment segment = currentSegment();
                    if (segment == null) break;
                    ByteReader reader = new ByteReader(data, fieldsOffset);
                    int ids = reader.readVarInt();
                    for (int k = 0; k < ids; k++) {
                        EntityLife life = segment.current.remove(reader.readVarInt());
                        if (life != null) life.end = i;
                    }
                    break;
                }
                case PacketTypes.LATEST: {
                    if (type == ChunkLoadDistanceS2CPacket.class) {
                        maxLoadDistance = Math.max(maxLoadDistance, new ByteReader(data, fieldsOffset).readVarInt());
                    }
                    Segment segment = currentSegment();
                    if (segment != null) latest(segment, id).add(i);
                    break;
                }
                case PacketTypes.GAME_STATE: {
                    int reason = data[fieldsOffset] & 0xFF;
                    if (!PacketTypes.gameStateShown(reason)) {
                        category = PacketTypes.DROP;
                    } else if (PacketTypes.gameStateIsWeather(reason)) {
                        Segment segment = currentSegment();
                        int key = reason == PacketTypes.GAME_STATE_RAIN_STOPPED ? PacketTypes.GAME_STATE_RAIN_STARTED : reason;
                        if (segment != null) latest(segment, 1000 + key).add(i);
                    } else {
                        category = PacketTypes.TRANSIENT;
                    }
                    break;
                }
                case PacketTypes.WORLD_CUMULATIVE: {
                    Segment segment = currentSegment();
                    if (segment != null) segment.cumulative.add(i);
                    break;
                }
                case PacketTypes.GLOBAL_CUMULATIVE:
                    globalCumulative.add(i);
                    break;
                case PacketTypes.DROP: {
                    if (type != PlayerPositionLookS2CPacket.class || awaitingArrival < 0) break;
                    if (cursor.time - awaitingArrival > ARRIVAL_WAIT_MS) {
                        // too long after to be the placing (e.g. an ender pearl)
                        awaitingArrival = -1;
                        break;
                    }
                    PlayerPositionLookS2CPacket look = (PlayerPositionLookS2CPacket) decode(id, data, fieldsOffset, fieldsLength);
                    if (!look.getFlags().isEmpty()) break;
                    arrivals.add(new Arrival(awaitingArrival, cursor.time, look.getX(), look.getY(), look.getZ(), look.getYaw(), look.getPitch()));
                    awaitingArrival = -1;
                    break;
                }
                case PacketTypes.COOLDOWN:
                    cooldowns.add(i);
                    break;
                case PacketTypes.ENTITY_TRANSIENT:
                    if (type == EntityAnimationS2CPacket.class
                            && PacketTypes.entityId(type, new ByteReader(data, fieldsOffset)) == selfId) {
                        category = PacketTypes.DROP;
                    }
                    break;
                default:
                    break;
            }
        } catch (RuntimeException e) {
            category = PacketTypes.DROP;
        }

        time[i] = cursor.time;
        location[i] = (long) block << 32 | fieldsOffset;
        length[i] = fieldsLength;
        packetId[i] = (short) id;
        meta[i] = (byte) (category | extra);
        count++;
    }

    private byte indexSpawn(int index, Class<?> type, byte[] data, int offset) {
        ByteReader r = new ByteReader(data, offset);
        int entityId = r.readVarInt();
        if (entityId == selfId) return PacketTypes.DROP;
        Segment segment = currentSegment();
        if (segment == null) return PacketTypes.DROP;
        boolean hasPosition = true;
        double x = 0;
        double y = 0;
        double z = 0;
        byte yaw = 0;
        byte pitch = 0;
        if (type == EntitySpawnS2CPacket.class) {
            r.skip(16);
            r.readVarInt();
            x = r.readDouble();
            y = r.readDouble();
            z = r.readDouble();
            pitch = r.readByte();
            yaw = r.readByte();
        } else if (type == MobSpawnS2CPacket.class) {
            r.skip(16);
            r.readVarInt();
            x = r.readDouble();
            y = r.readDouble();
            z = r.readDouble();
            yaw = r.readByte();
            pitch = r.readByte();
        } else if (type == PlayerSpawnS2CPacket.class) {
            r.skip(16);
            x = r.readDouble();
            y = r.readDouble();
            z = r.readDouble();
            yaw = r.readByte();
            pitch = r.readByte();
        } else if (type == ExperienceOrbSpawnS2CPacket.class) {
            x = r.readDouble();
            y = r.readDouble();
            z = r.readDouble();
        } else {
            hasPosition = false; // paintings hang where they were placed
        }
        EntityLife previous = segment.current.get(entityId);
        if (previous != null) previous.end = index;
        EntityLife life = new EntityLife(entityId, index, hasPosition, EntityS2CPacket.encodePacketCoordinate(x),
                EntityS2CPacket.encodePacketCoordinate(y), EntityS2CPacket.encodePacketCoordinate(z), yaw, pitch);
        segment.current.put(entityId, life);
        segment.lives.add(life);
        List<EntityLife> lives = segment.entityLives.get(entityId);
        if (lives == null) segment.entityLives.put(entityId, lives = new ArrayList<>(1));
        lives.add(life);
        return PacketTypes.ENTITY_SPAWN;
    }

    static boolean readMove(Class<?> type, ByteReader r, MoveDelta move) {
        r.readVarInt();
        move.absolute = false;
        move.moved = false;
        move.rotated = false;
        if (type == EntityPositionS2CPacket.class) {
            move.absolute = true;
            move.moved = true;
            move.x = EntityS2CPacket.encodePacketCoordinate(r.readDouble());
            move.y = EntityS2CPacket.encodePacketCoordinate(r.readDouble());
            move.z = EntityS2CPacket.encodePacketCoordinate(r.readDouble());
            move.rotated = true;
            move.yaw = r.readByte();
            move.pitch = r.readByte();
            return true;
        }
        if (type == EntityS2CPacket.MoveRelative.class || type == EntityS2CPacket.RotateAndMoveRelative.class) {
            move.moved = true;
            move.x = r.readShort();
            move.y = r.readShort();
            move.z = r.readShort();
        }
        if (type == EntityS2CPacket.Rotate.class || type == EntityS2CPacket.RotateAndMoveRelative.class) {
            move.rotated = true;
            move.yaw = r.readByte();
            move.pitch = r.readByte();
        }
        return move.moved || move.rotated;
    }

    private static IntArrayList latest(Segment segment, int key) {
        IntArrayList list = segment.latest.get(key);
        if (list == null) segment.latest.put(key, list = new IntArrayList(4));
        return list;
    }

    private Segment currentSegment() {
        return segments.isEmpty() ? null : segments.get(segments.size() - 1);
    }

    private void closeSegment(int end) {
        Segment segment = currentSegment();
        if (segment != null) {
            segment.end = end;
            for (EntityLife life : segment.current.values()) life.end = Math.min(life.end, end);
            segment.current.clear();
        }
    }

    private void grow() {
        int n = time.length * 2;
        time = Arrays.copyOf(time, n);
        location = Arrays.copyOf(location, n);
        length = Arrays.copyOf(length, n);
        packetId = Arrays.copyOf(packetId, n);
        meta = Arrays.copyOf(meta, n);
    }

    private static int varIntSize(int value) {
        return Records.varIntSize(value);
    }

    // lookups

    int indexAt(int t) {
        return TimedList.lastAtOrBefore(time, count, t);
    }

    int category(int index) {
        return meta[index] & CATEGORY_MASK;
    }

    boolean isFullChunk(int index) {
        return (meta[index] & FULL_CHUNK) != 0;
    }

    Class<?> type(int index) {
        return PacketTypes.classOf(packetId[index]);
    }

    Segment segmentAt(int index) {
        int low = 0;
        int high = segments.size() - 1;
        Segment found = segments.isEmpty() ? null : segments.get(0);
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (segments.get(mid).start <= index) {
                found = segments.get(mid);
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return found;
    }

    int playerAt(double t, double[] out) {
        int s = tracks.player(t, out);
        Arrival arrival = arrivingAt(t);
        if (s >= 0 && arrival != null) {
            out[0] = arrival.x;
            out[1] = arrival.y;
            out[2] = arrival.z;
            out[3] = arrival.yaw;
            out[4] = arrival.pitch;
            out[5] = arrival.yaw;
            out[6] = arrival.yaw;
        }
        return s;
    }

    Arrival arrivingAt(double t) {
        for (int k = arrivals.size() - 1; k >= 0; k--) {
            Arrival arrival = arrivals.get(k);
            if (arrival.since <= t) return t < arrival.settled ? arrival : null;
        }
        return null;
    }

    ByteReader reader(int index) throws IOException {
        byte[] data = file.block((int) (location[index] >>> 32));
        return new ByteReader(data, (int) location[index]);
    }

    Packet<?> packet(int index) throws IOException {
        byte[] data = file.block((int) (location[index] >>> 32));
        return decode(packetId[index], data, (int) location[index], length[index]);
    }

    static Packet<?> decode(int id, byte[] data, int offset, int length) {
        Packet<?> packet = NetworkState.PLAY.getPacketHandler(NetworkSide.CLIENTBOUND, id);
        if (packet == null) throw new IllegalStateException("Unknown packet " + id);
        try {
            packet.read(new PacketByteBuf(Unpooled.wrappedBuffer(data, offset, length)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return packet;
    }
}
