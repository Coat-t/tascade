package com.coatt.tascade.replay.play;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import com.coatt.tascade.mixin.access.LivingEntityAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.recipe.Recipe;
import net.minecraft.network.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// seeks without restarting: chunks are rewound via an undo log or reloaded from their load packet, entities respawned and placed, world state set from its last packets
final class ReplayWorld {
    private static final Logger LOGGER = LogManager.getLogger("Tascade/Replay");
    private static final int SEQUENTIAL_SEEK_LIMIT = 3000;
    private static final int LIGHT_UPDATES_ON_LOAD = 16;

    private final ReplayPlayer player;
    private final ReplayIndex index;
    private final MinecraftClient client;
    private final ClientPlayNetworkHandler handler;

    ReplayIndex.Segment segment;
    int cursor = -1;

    private final Long2IntOpenHashMap chunkBase = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap chunkApplied = new Long2IntOpenHashMap();
    private final Long2ObjectOpenHashMap<ArrayList<Undo>> undo = new Long2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Tracked> entities = new Int2ObjectOpenHashMap<>();
    private int globalApplied = 0;
    private final Set<Class<?>> failed = new HashSet<>();
    private final ReplayIndex.MoveDelta move = new ReplayIndex.MoveDelta();

    private static final class Undo {
        final int index;
        final long pos;
        final BlockState state;
        final CompoundTag blockEntity;

        Undo(int index, long pos, BlockState state, CompoundTag blockEntity) {
            this.index = index;
            this.pos = pos;
            this.state = state;
            this.blockEntity = blockEntity;
        }
    }

    private static final class Tracked {
        final ReplayIndex.EntityLife life;
        int applied;

        Tracked(ReplayIndex.EntityLife life, int applied) {
            this.life = life;
            this.applied = applied;
        }
    }

    ReplayWorld(ReplayPlayer player, ReplayIndex index, MinecraftClient client, ClientPlayNetworkHandler handler) {
        this.player = player;
        this.index = index;
        this.client = client;
        this.handler = handler;
        chunkBase.defaultReturnValue(-1);
        chunkApplied.defaultReturnValue(-1);
    }

    // applying in order

    void applyUpTo(int target, boolean live, boolean sounds) {
        while (cursor < target) {
            cursor++;
            apply(cursor, live, sounds);
        }
    }

    private void apply(int i, boolean live, boolean sounds) {
        try {
            switch (index.category(i)) {
                case PacketTypes.DROP:
                    return;
                case PacketTypes.TRANSIENT:
                    if (isSound(index.type(i)) ? sounds : live) applyPacket(i);
                    return;
                case PacketTypes.ENTITY_TRANSIENT:
                    if (live) applyPacket(i);
                    return;
                case PacketTypes.EXPLOSION:
                    if (live) applyExplosion(i);
                    return;
                case PacketTypes.JOIN:
                    applyPacket(i);
                    onNewWorld(index.segmentAt(i));
                    return;
                case PacketTypes.RESPAWN:
                    applyRespawn(i);
                    return;
                case PacketTypes.CHUNK:
                    applyChunkPacket(i);
                    return;
                case PacketTypes.ENTITY_SPAWN: {
                    applyPacket(i);
                    int id = PacketTypes.entityId(index.type(i), index.reader(i));
                    ReplayIndex.EntityLife life = segment != null ? segment.lifeAt(id, i) : null;
                    if (life != null && life.spawn == i) entities.put(id, new Tracked(life, i));
                    else entities.remove(id);
                    return;
                }
                case PacketTypes.ENTITY_MOVE:
                case PacketTypes.ENTITY_STATE:
                case PacketTypes.ENTITY_LINK: {
                    applyPacket(i);
                    Tracked tracked = entities.get(PacketTypes.entityId(index.type(i), index.reader(i)));
                    if (tracked != null) tracked.applied = i;
                    return;
                }
                case PacketTypes.ENTITY_DESTROY: {
                    applyPacket(i);
                    ByteReader reader = index.reader(i);
                    int count = reader.readVarInt();
                    for (int k = 0; k < count; k++) entities.remove(reader.readVarInt());
                    return;
                }
                case PacketTypes.GLOBAL_CUMULATIVE:
                    applyPacket(i);
                    if (globalApplied < index.globalCumulative.size() && index.globalCumulative.getInt(globalApplied) == i) {
                        globalApplied++;
                    }
                    return;
                default:
                    applyPacket(i);
            }
        } catch (Exception e) {
            reportFailure(index.type(i), e);
        }
    }

    private void applyRespawn(int i) throws IOException {
        PlayerRespawnS2CPacket respawn = (PlayerRespawnS2CPacket) index.packet(i);
        boolean newWorld = client.world == null || !respawn.getDimension().equals(client.world.getRegistryKey());
        player.saveCamera();
        applyPacket(respawn);
        if (newWorld) {
            onNewWorld(index.segmentAt(i));
            player.restoreCameraAtRunner(index.time[i]);
        } else {
            segment = index.segmentAt(i);
            player.onRespawn();
            player.restoreCamera();
        }
    }

    private void onNewWorld(ReplayIndex.Segment newSegment) {
        segment = newSegment;
        chunkBase.clear();
        chunkApplied.clear();
        undo.clear();
        entities.clear();
        if (client.world != null) client.world.getChunkManager().updateLoadDistance(index.maxLoadDistance);
        player.onNewWorld();
    }

    private void applyChunkPacket(int i) throws IOException {
        Class<?> type = index.type(i);
        long key = PacketTypes.chunkKey(type, index.reader(i));
        Packet<?> packet = index.packet(i);
        if (packet instanceof BlockUpdateS2CPacket) {
            captureUndo(i, ((BlockUpdateS2CPacket) packet).getPos());
        } else if (packet instanceof ChunkDeltaUpdateS2CPacket) {
            for (ChunkDeltaUpdateS2CPacket.ChunkDeltaRecord record : ((ChunkDeltaUpdateS2CPacket) packet).getRecords()) {
                captureUndo(i, record.getBlockPos());
            }
        }
        applyPacket(packet);
        if (packet instanceof UnloadChunkS2CPacket) {
            forgetChunk(key);
            return;
        }
        if (packet instanceof ChunkDataS2CPacket && index.isFullChunk(i)) {
            chunkBase.put(key, i);
            undo.remove(key);
        }
        chunkApplied.put(key, i);
    }

    // explosions break blocks client-side before the server's block updates arrive
    private void applyExplosion(int i) throws IOException {
        ExplosionS2CPacket explosion = (ExplosionS2CPacket) index.packet(i);
        for (BlockPos pos : explosion.getAffectedBlocks()) captureUndo(i, pos);
        applyPacket(explosion);
    }

    private void captureUndo(int i, BlockPos pos) {
        ClientWorld world = client.world;
        if (world == null || !world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return;
        BlockState state = world.getBlockState(pos);
        CompoundTag blockEntity = null;
        if (state.getBlock().hasBlockEntity()) {
            BlockEntity entity = world.getBlockEntity(pos);
            if (entity != null) blockEntity = entity.toTag(new CompoundTag());
        }
        long key = PacketTypes.chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        ArrayList<Undo> list = undo.get(key);
        if (list == null) undo.put(key, list = new ArrayList<>());
        list.add(new Undo(i, pos.asLong(), state, blockEntity));
    }

    private void forgetChunk(long key) {
        chunkBase.remove(key);
        chunkApplied.remove(key);
        undo.remove(key);
    }

    // seeking

    void seekTo(int target) {
        if (target >= cursor && target - cursor <= SEQUENTIAL_SEEK_LIMIT) {
            applyUpTo(target, false, false);
        } else {
            try {
                rebuild(target);
            } catch (Exception e) {
                LOGGER.error("Couldn't rebuild the replay world", e);
            }
            cursor = target;
        }
        client.getToastManager().clear();
    }

    private void rebuild(int target) throws IOException {
        ReplayIndex.Segment targetSegment = index.segmentAt(target);
        if (targetSegment == null) return;
        if (client.world == null || !client.world.getRegistryKey().equals(targetSegment.dimension)) {
            player.saveCamera();
            applyPacket(targetSegment.respawn);
            onNewWorld(targetSegment);
            player.restoreCameraAtRunner(index.time[target]);
        } else if (segment != targetSegment) {
            segment = targetSegment;
            player.onRespawn();
        }
        applyLatest(targetSegment, target);
        reconcileChunks(targetSegment, target);
        reconcileEntities(targetSegment, target);
        applyWorldCumulative(targetSegment, target);
        applyGlobalCumulative(target);
        applyCooldowns(target);
    }

    private void applyLatest(ReplayIndex.Segment segment, int target) {
        // chunks are only accepted near the chunk map's centre
        double[] at = new double[7];
        if (index.playerAt(index.time[target], at) >= 0 && client.world != null) {
            client.world.getChunkManager().setChunkMapCenter((int) Math.floor(at[0]) >> 4, (int) Math.floor(at[2]) >> 4);
        }
        IntArrayList chosen = new IntArrayList();
        for (Int2ObjectMap.Entry<IntArrayList> entry : segment.latest.int2ObjectEntrySet()) {
            IntArrayList list = entry.getValue();
            int k = lastAtOrBefore(list, target);
            if (k >= 0) chosen.add(list.getInt(k));
        }
        chosen.sort(null);
        for (int i = 0; i < chosen.size(); i++) applyPacketSafely(chosen.getInt(i));
    }

    private void reconcileChunks(ReplayIndex.Segment segment, int target) throws IOException {
        LongOpenHashSet loaded = new LongOpenHashSet();
        for (Long2ObjectMap.Entry<ReplayIndex.ChunkHistory> entry : segment.chunks.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            ReplayIndex.ChunkHistory history = entry.getValue();
            int k = lastAtOrBefore(history.events, target);
            if (k < 0) continue;
            int base = history.bases.getInt(k);
            if (base < 0) continue;
            loaded.add(key);
            int last = history.events.getInt(k);
            if (chunkBase.get(key) == base) {
                int applied = chunkApplied.get(key);
                if (applied > last) {
                    if (canUndo(history, last, applied)) {
                        undoChunk(key, target);
                        chunkApplied.put(key, last);
                    } else {
                        unloadChunk(key);
                        loadChunk(key, history, base, k);
                    }
                } else if (applied < last) {
                    applyChunkEvents(key, history, applied, k);
                }
            } else {
                if (chunkBase.get(key) >= 0) unloadChunk(key);
                loadChunk(key, history, base, k);
            }
        }
        for (long key : chunkBase.keySet().toLongArray()) {
            if (!loaded.contains(key)) unloadChunk(key);
        }
    }

    private boolean canUndo(ReplayIndex.ChunkHistory history, int after, int applied) {
        int from = lastAtOrBefore(history.events, after) + 1;
        for (int p = from; p < history.events.size(); p++) {
            int e = history.events.getInt(p);
            if (e > applied) break;
            Class<?> type = index.type(e);
            if (type == BlockEntityUpdateS2CPacket.class || type == ChunkDataS2CPacket.class) return false;
        }
        return true;
    }

    private void undoChunk(long key, int target) {
        ArrayList<Undo> list = undo.get(key);
        ClientWorld world = client.world;
        if (list == null || world == null) return;
        for (int k = list.size() - 1; k >= 0; k--) {
            Undo entry = list.get(k);
            if (entry.index <= target) break;
            list.remove(k);
            BlockPos pos = BlockPos.fromLong(entry.pos);
            world.setBlockStateWithoutNeighborUpdates(pos, entry.state);
            if (entry.blockEntity != null) {
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity != null) blockEntity.fromTag(entry.state, entry.blockEntity);
            }
        }
    }

    private void loadChunk(long key, ReplayIndex.ChunkHistory history, int base, int k) throws IOException {
        int basePosition = k;
        while (basePosition > 0 && history.events.getInt(basePosition) != base) basePosition--;
        int lightFrom = basePosition;
        while (lightFrom > 0 && history.bases.getInt(lightFrom - 1) < 0
                && index.type(history.events.getInt(lightFrom - 1)) == LightUpdateS2CPacket.class) {
            lightFrom--;
        }
        for (int p = lightFrom; p < basePosition; p++) applyPacketSafely(history.events.getInt(p));
        applyPacketSafely(base);
        chunkBase.put(key, base);
        chunkApplied.put(key, base);
        undo.remove(key);
        applyChunkEvents(key, history, base, k);
    }

    private void applyChunkEvents(long key, ReplayIndex.ChunkHistory history, int after, int k) throws IOException {
        int from = lastAtOrBefore(history.events, after) + 1;
        int lights = 0;
        for (int p = from; p <= k; p++) {
            if (index.type(history.events.getInt(p)) == LightUpdateS2CPacket.class) lights++;
        }
        int skipLights = Math.max(0, lights - LIGHT_UPDATES_ON_LOAD);
        for (int p = from; p <= k; p++) {
            int e = history.events.getInt(p);
            Class<?> type = index.type(e);
            if (type == LightUpdateS2CPacket.class && skipLights > 0) {
                skipLights--;
                continue;
            }
            try {
                applyChunkPacket(e);
            } catch (Exception ex) {
                reportFailure(type, ex);
            }
        }
        if (k >= 0 && k < history.events.size()) chunkApplied.put(key, history.events.getInt(k));
    }

    private void unloadChunk(long key) {
        applyPacket(new UnloadChunkS2CPacket(PacketTypes.chunkX(key), PacketTypes.chunkZ(key)));
        forgetChunk(key);
    }

    private void reconcileEntities(ReplayIndex.Segment segment, int target) throws IOException {
        ClientWorld world = client.world;
        if (world == null) return;
        IntArrayList stale = new IntArrayList();
        for (Int2ObjectMap.Entry<Tracked> entry : entities.int2ObjectEntrySet()) {
            Tracked tracked = entry.getValue();
            if (segment.lifeAt(entry.getIntKey(), target) != tracked.life || tracked.applied > target) {
                stale.add(entry.getIntKey());
            }
        }
        for (int i = 0; i < stale.size(); i++) {
            world.removeEntity(stale.getInt(i));
            entities.remove(stale.getInt(i));
        }

        List<ReplayIndex.EntityLife> alive = new ArrayList<>();
        for (ReplayIndex.EntityLife life : segment.lives) {
            if (life.spawn > target) break;
            if (life.end <= target) continue;
            Tracked tracked = entities.get(life.entityId);
            if (tracked == null) {
                world.removeEntity(life.entityId);
                applyPacketSafely(life.spawn);
                tracked = new Tracked(life, life.spawn);
                entities.put(life.entityId, tracked);
            }
            applyLifeState(life, tracked.applied, target);
            tracked.applied = target;
            alive.add(life);
        }
        for (ReplayIndex.EntityLife life : alive) place(life, target);
        for (ReplayIndex.EntityLife life : alive) applyLatestLinks(life, target);
    }

    private void applyLifeState(ReplayIndex.EntityLife life, int after, int target) {
        IntArrayList events = life.events;
        for (int p = lastAtOrBefore(events, after) + 1; p < events.size(); p++) {
            int e = events.getInt(p);
            if (e > target) break;
            if (index.category(e) == PacketTypes.ENTITY_STATE) applyPacketSafely(e);
        }
    }

    private void place(ReplayIndex.EntityLife life, int target) throws IOException {
        if (!life.hasPosition) return;
        Entity entity = client.world.getEntityById(life.entityId);
        if (entity == null) return;
        IntArrayList events = life.events;
        int k = lastAtOrBefore(events, target);
        int checkpoint = lastAtOrBefore(life.checkpointEvent, k);
        long x;
        long y;
        long z;
        byte yaw;
        byte pitch;
        int from;
        if (checkpoint >= 0) {
            x = life.checkpointPosition.getLong(checkpoint * 3);
            y = life.checkpointPosition.getLong(checkpoint * 3 + 1);
            z = life.checkpointPosition.getLong(checkpoint * 3 + 2);
            yaw = life.checkpointRotation.getByte(checkpoint * 2);
            pitch = life.checkpointRotation.getByte(checkpoint * 2 + 1);
            from = life.checkpointEvent.getInt(checkpoint) + 1;
        } else {
            x = life.spawnX;
            y = life.spawnY;
            z = life.spawnZ;
            yaw = life.spawnYaw;
            pitch = life.spawnPitch;
            from = 0;
        }
        for (int p = from; p <= k; p++) {
            int e = events.getInt(p);
            if (index.category(e) != PacketTypes.ENTITY_MOVE) continue;
            if (!ReplayIndex.readMove(index.type(e), index.reader(e), move)) continue;
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
        }
        Vec3d pos = EntityS2CPacket.decodePacketCoordinates(x, y, z);
        float yawDegrees = yaw * 360 / 256.0F;
        float pitchDegrees = pitch * 360 / 256.0F;
        entity.updateTrackedPosition(pos.x, pos.y, pos.z);
        entity.refreshPositionAndAngles(pos.x, pos.y, pos.z, yawDegrees, pitchDegrees);
        entity.updateTrackedPositionAndAngles(pos.x, pos.y, pos.z, yawDegrees, pitchDegrees, 0, false);
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            LivingEntityAccessor access = (LivingEntityAccessor) living;
            boolean headKnown = false;
            for (int p = k; p >= 0; p--) {
                if (index.type(events.getInt(p)) == EntitySetHeadYawS2CPacket.class) {
                    headKnown = true;
                    break;
                }
            }
            float head = headKnown ? (float) access.tascade$getServerHeadYaw() : yawDegrees;
            living.headYaw = head;
            living.prevHeadYaw = head;
            living.bodyYaw = yawDegrees;
            living.prevBodyYaw = yawDegrees;
            access.tascade$setHeadTrackingIncrements(0);
            access.tascade$setBodyTrackingIncrements(0);
        }
    }

    private void applyLatestLinks(ReplayIndex.EntityLife life, int target) {
        IntArrayList events = life.events;
        boolean passengers = false;
        boolean leash = false;
        for (int p = lastAtOrBefore(events, target); p >= 0 && !(passengers && leash); p--) {
            int e = events.getInt(p);
            Class<?> type = index.type(e);
            if (!passengers && type == EntityPassengersSetS2CPacket.class) {
                passengers = true;
                applyPacketSafely(e);
            } else if (!leash && type == EntityAttachS2CPacket.class) {
                leash = true;
                applyPacketSafely(e);
            }
        }
    }

    private void applyWorldCumulative(ReplayIndex.Segment segment, int target) {
        client.inGameHud.getBossBarHud().clear();
        for (int i = 0; i < segment.cumulative.size(); i++) {
            int e = segment.cumulative.getInt(i);
            if (e > target) break;
            applyPacketSafely(e);
        }
    }

    private void applyGlobalCumulative(int target) {
        IntArrayList global = index.globalCumulative;
        int upTo = lastAtOrBefore(global, target) + 1;
        boolean fromStart = upTo < globalApplied;
        int from = fromStart ? 0 : globalApplied;
        if (fromStart && client.player != null) {
            // later unlocks are forgotten, those up to the target come back below
            ClientRecipeBook book = client.player.getRecipeBook();
            for (Recipe<?> recipe : handler.getRecipeManager().values()) book.remove(recipe);
        }
        for (int k = from; k < upTo; k++) {
            int e = global.getInt(k);
            if (fromStart && k < globalApplied && index.type(e) == SynchronizeTagsS2CPacket.class) continue;
            applyPacketSafely(e);
        }
        globalApplied = upTo;
    }

    private void applyCooldowns(int target) throws IOException {
        if (client.player == null) return;
        IntArrayList cooldowns = index.cooldowns;
        int now = index.time[target];
        Set<Item> seen = new HashSet<>();
        for (int k = lastAtOrBefore(cooldowns, target); k >= 0; k--) {
            int e = cooldowns.getInt(k);
            if (now - index.time[e] > 120_000) break;
            CooldownUpdateS2CPacket packet = (CooldownUpdateS2CPacket) index.packet(e);
            if (!seen.add(packet.getItem())) continue;
            int left = packet.getCooldown() - (now - index.time[e]) / 50;
            if (packet.getCooldown() > 0 && left > 0) client.player.getItemCooldownManager().set(packet.getItem(), left);
            else client.player.getItemCooldownManager().remove(packet.getItem());
        }
    }

    // helpers

    private void applyPacket(int i) throws IOException {
        applyPacket(index.packet(i));
    }

    private void applyPacketSafely(int i) {
        try {
            applyPacket(index.packet(i));
        } catch (Exception e) {
            reportFailure(index.type(i), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyPacket(Packet<?> packet) {
        ((Packet<ClientPlayPacketListener>) packet).apply(handler);
    }

    private void reportFailure(Class<?> type, Exception e) {
        if (failed.add(type)) LOGGER.warn("Couldn't replay a {}", type != null ? type.getSimpleName() : "packet", e);
    }

    private static boolean isSound(Class<?> type) {
        return type == PlaySoundS2CPacket.class || type == PlaySoundIdS2CPacket.class || type == PlaySoundFromEntityS2CPacket.class;
    }

    static int lastAtOrBefore(IntArrayList sorted, int key) {
        return TimedList.lastAtOrBefore(sorted.elements(), sorted.size(), key);
    }
}
