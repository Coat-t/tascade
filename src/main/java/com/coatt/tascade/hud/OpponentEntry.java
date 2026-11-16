package com.coatt.tascade.hud;

import com.coatt.tascade.network.RoomState;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.network.TascadeClient;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class OpponentEntry {
    // built once: building one starts an async skin fetch
    private static final Map<String, PlayerListEntry> entries = new HashMap<>();

    private OpponentEntry() {}

    public static PlayerListEntry get() {
        if (!RunState.isRanked()) return null;
        return of(RunState.opponentUuid, RunState.opponentName);
    }

    public static List<PlayerListEntry> all() {
        List<PlayerListEntry> list = new ArrayList<>();
        if (RunState.isRanked()) {
            PlayerListEntry opponent = get();
            if (opponent != null) list.add(opponent);
        } else if (RunState.isRoom()) {
            String me = TascadeClient.getInstance().getPlayerUuid();
            for (RoomState.Racer racer : RoomState.racers()) {
                if (racer.uuid.equals(me)) continue;
                PlayerListEntry entry = of(racer.uuid, racer.username);
                if (entry != null) list.add(entry);
            }
        }
        return list;
    }

    public static PlayerListEntry of(String uuid, String name) {
        if (uuid == null || name == null) return null;
        PlayerListEntry entry = entries.get(uuid);
        if (entry != null) return entry;
        UUID id;
        try {
            id = UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
        // with no skin data, vanilla's loader looks the skin up from the UUID
        GameProfile profile = new GameProfile(id, name);
        entry = new PlayerListEntry(new PlayerListS2CPacket().new Entry(profile, 0, GameMode.SURVIVAL, null));
        if (entries.size() > 64) entries.clear();
        entries.put(uuid, entry);
        return entry;
    }
}
