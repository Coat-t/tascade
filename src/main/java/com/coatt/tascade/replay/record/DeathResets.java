package com.coatt.tascade.replay.record;

import com.coatt.tascade.Main;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.network.TascadeClient;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class DeathResets {
    private DeathResets() {}

    private static final Map<UUID, Boolean> latest = new ConcurrentHashMap<>();
    private static long unsentRta = -1;

    public static void died(ServerPlayerEntity player) {
        latest.put(player.getUuid(), hasActiveRespawnPoint(player));
        if (!isHost(player)) return;
        unsentRta = RunState.isRanked() && !RunState.waitingForRaceStart ? Main.runRta() : -1;
    }

    public static void respawned(ServerPlayerEntity player, boolean atRespawnPoint) {
        latest.put(player.getUuid(), atRespawnPoint);
        if (isHost(player)) tellMatch(atRespawnPoint);
    }

    public static void left(ServerPlayerEntity player) {
        if (isHost(player)) tellMatch(wasReset(player.getUuid()));
    }

    public static boolean wasReset(UUID player) {
        return Boolean.TRUE.equals(latest.get(player));
    }

    private static void tellMatch(boolean reset) {
        if (unsentRta < 0) return;
        TascadeClient.getInstance().sendDeath(reset, unsentRta);
        unsentRta = -1;
    }

    private static boolean isHost(ServerPlayerEntity player) {
        return player.server.isHost(player.getGameProfile());
    }

    public static boolean hasActiveRespawnPoint(ServerPlayerEntity player) {
        BlockPos pos = player.getSpawnPointPosition();
        ServerWorld world = player.server.getWorld(player.getSpawnPointDimension());
        return pos != null && world != null
            && PlayerEntity.findRespawnPosition(world, pos, player.isSpawnPointSet(), true).isPresent();
    }
}
