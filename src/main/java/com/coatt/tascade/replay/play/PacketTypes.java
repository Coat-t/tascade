package com.coatt.tascade.replay.play;

import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PacketTypes {
    static final byte DROP = 0;
    static final byte TRANSIENT = 1;
    static final byte NORMAL = 2;
    static final byte JOIN = 3;
    static final byte RESPAWN = 4;
    static final byte CHUNK = 5;
    static final byte ENTITY_SPAWN = 6;
    static final byte ENTITY_MOVE = 7;
    static final byte ENTITY_STATE = 8;
    static final byte ENTITY_LINK = 9;
    static final byte ENTITY_DESTROY = 10;
    static final byte LATEST = 11;
    static final byte WORLD_CUMULATIVE = 12;
    static final byte GLOBAL_CUMULATIVE = 13;
    static final byte COOLDOWN = 14;
    static final byte ENTITY_TRANSIENT = 15;
    static final byte GAME_STATE = 16;
    static final byte EXPLOSION = 17;

    private static final Class<?>[] CLASSES;
    private static final byte[] CATEGORIES;
    private static final Map<Class<?>, Integer> IDS = new HashMap<>();

    static {
        List<Class<?>> classes = new ArrayList<>();
        for (int id = 0; id < 256; id++) {
            Packet<?> packet;
            try {
                packet = NetworkState.PLAY.getPacketHandler(NetworkSide.CLIENTBOUND, id);
            } catch (IndexOutOfBoundsException e) {
                break; // past the last id
            }
            if (packet == null) break;
            classes.add(packet.getClass());
            IDS.put(packet.getClass(), id);
        }
        CLASSES = classes.toArray(new Class<?>[0]);
        CATEGORIES = new byte[CLASSES.length];
        for (int id = 0; id < CLASSES.length; id++) CATEGORIES[id] = categorize(CLASSES[id]);
    }

    private PacketTypes() {}

    static int count() {
        return CLASSES.length;
    }

    static Class<?> classOf(int id) {
        return id >= 0 && id < CLASSES.length ? CLASSES[id] : null;
    }

    static int idOf(Class<?> type) {
        Integer id = IDS.get(type);
        return id != null ? id : -1;
    }

    static byte category(int id) {
        return id >= 0 && id < CATEGORIES.length ? CATEGORIES[id] : DROP;
    }

    private static byte categorize(Class<?> c) {
        if (c == GameJoinS2CPacket.class) return JOIN;
        if (c == PlayerRespawnS2CPacket.class) return RESPAWN;

        if (c == ChunkDataS2CPacket.class || c == UnloadChunkS2CPacket.class || c == BlockUpdateS2CPacket.class
                || c == ChunkDeltaUpdateS2CPacket.class || c == BlockEntityUpdateS2CPacket.class
                || c == LightUpdateS2CPacket.class) return CHUNK;

        if (c == EntitySpawnS2CPacket.class || c == ExperienceOrbSpawnS2CPacket.class || c == MobSpawnS2CPacket.class
                || c == PaintingSpawnS2CPacket.class || c == PlayerSpawnS2CPacket.class) return ENTITY_SPAWN;
        if (c == EntityS2CPacket.class || c == EntityS2CPacket.MoveRelative.class || c == EntityS2CPacket.Rotate.class
                || c == EntityS2CPacket.RotateAndMoveRelative.class || c == EntityPositionS2CPacket.class) return ENTITY_MOVE;
        if (c == EntityTrackerUpdateS2CPacket.class || c == EntityEquipmentUpdateS2CPacket.class
                || c == EntityAttributesS2CPacket.class || c == EntityStatusEffectS2CPacket.class
                || c == RemoveEntityStatusEffectS2CPacket.class || c == EntityVelocityUpdateS2CPacket.class
                || c == EntitySetHeadYawS2CPacket.class) return ENTITY_STATE;
        if (c == EntityPassengersSetS2CPacket.class || c == EntityAttachS2CPacket.class) return ENTITY_LINK;
        if (c == EntitiesDestroyS2CPacket.class) return ENTITY_DESTROY;
        if (c == EntityAnimationS2CPacket.class || c == EntityStatusS2CPacket.class
                || c == ItemPickupAnimationS2CPacket.class) return ENTITY_TRANSIENT;

        if (c == WorldTimeUpdateS2CPacket.class || c == ChunkRenderDistanceCenterS2CPacket.class
                || c == ChunkLoadDistanceS2CPacket.class || c == DifficultyS2CPacket.class
                || c == PlayerSpawnPositionS2CPacket.class || c == PlayerListHeaderS2CPacket.class) return LATEST;
        if (c == GameStateChangeS2CPacket.class) return GAME_STATE;
        if (c == WorldBorderS2CPacket.class || c == BossBarS2CPacket.class || c == TeamS2CPacket.class
                || c == ScoreboardObjectiveUpdateS2CPacket.class || c == ScoreboardDisplayS2CPacket.class
                || c == ScoreboardPlayerUpdateS2CPacket.class || c == MapUpdateS2CPacket.class) return WORLD_CUMULATIVE;
        if (c == PlayerListS2CPacket.class || c == AdvancementUpdateS2CPacket.class
                || c == SynchronizeTagsS2CPacket.class || c == SynchronizeRecipesS2CPacket.class
                || c == UnlockRecipesS2CPacket.class) return GLOBAL_CUMULATIVE;
        if (c == CooldownUpdateS2CPacket.class) return COOLDOWN;
        if (c == ExplosionS2CPacket.class) return EXPLOSION;

        if (c == BlockBreakingProgressS2CPacket.class || c == BlockEventS2CPacket.class || c == WorldEventS2CPacket.class
                || c == ParticleS2CPacket.class || c == PlaySoundIdS2CPacket.class || c == PlaySoundS2CPacket.class
                || c == PlaySoundFromEntityS2CPacket.class || c == StopSoundS2CPacket.class
                || c == TitleS2CPacket.class) return TRANSIENT;

        return DROP;
    }

    static final int GAME_STATE_RAIN_STARTED = 1;
    static final int GAME_STATE_RAIN_STOPPED = 2;
    static final int GAME_STATE_RAIN_GRADIENT = 7;
    static final int GAME_STATE_THUNDER_GRADIENT = 8;

    static boolean gameStateShown(int reason) {
        switch (reason) {
            case 0: // no respawn block message
            case GAME_STATE_RAIN_STARTED:
            case GAME_STATE_RAIN_STOPPED:
            case 6: // arrow hit player sound
            case GAME_STATE_RAIN_GRADIENT:
            case GAME_STATE_THUNDER_GRADIENT:
            case 9: // pufferfish sting sound
            case 10: // elder guardian
                return true;
            default: // game mode change, game won, demo, immediate respawn
                return false;
        }
    }

    static boolean gameStateIsWeather(int reason) {
        return reason == GAME_STATE_RAIN_STARTED || reason == GAME_STATE_RAIN_STOPPED
                || reason == GAME_STATE_RAIN_GRADIENT || reason == GAME_STATE_THUNDER_GRADIENT;
    }

    // reading ids and positions straight from packet bytes, without building the packet

    static long chunkKey(Class<?> c, ByteReader r) {
        int x;
        int z;
        if (c == ChunkDataS2CPacket.class || c == UnloadChunkS2CPacket.class || c == ChunkDeltaUpdateS2CPacket.class) {
            x = r.readInt();
            z = r.readInt();
        } else if (c == LightUpdateS2CPacket.class) {
            x = r.readVarInt();
            z = r.readVarInt();
        } else { // BlockUpdate, BlockEntityUpdate: a block position first
            long pos = r.readLong();
            x = net.minecraft.util.math.BlockPos.unpackLongX(pos) >> 4;
            z = net.minecraft.util.math.BlockPos.unpackLongZ(pos) >> 4;
        }
        return chunkKey(x, z);
    }

    static long chunkKey(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    static int chunkX(long key) {
        return (int) key;
    }

    static int chunkZ(long key) {
        return (int) (key >>> 32);
    }

    static boolean isFullChunk(ByteReader r) {
        r.skip(8);
        return r.readBoolean();
    }

    static int entityId(Class<?> c, ByteReader r) {
        if (c == EntityAttachS2CPacket.class || c == EntityStatusS2CPacket.class) return r.readInt();
        return r.readVarInt();
    }
}
