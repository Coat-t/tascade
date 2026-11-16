package com.coatt.tascade.integration;

import com.coatt.tascade.Main;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.MinecraftClient;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// requested by skin texture, not UUID: mc-heads' UUID lookup gets rate-limited and caches Steve for hours
final class PlayerHeads {
    private static final String URL = "https://mc-heads.net/avatar/%s/64";

    private static final Map<UUID, String> HEADS = new ConcurrentHashMap<>();
    private static final Set<UUID> REQUESTED = ConcurrentHashMap.newKeySet();

    private PlayerHeads() {}

    static String url(MinecraftClient client, UUID player) {
        String url = HEADS.get(player);
        if (url == null && REQUESTED.add(player)) {
            MinecraftSessionService sessions = client.getSessionService();
            Thread lookup = new Thread(() -> HEADS.put(player, lookUp(sessions, player)), "Tascade-SkinLookup");
            lookup.setDaemon(true);
            lookup.start();
        }
        return url;
    }

    private static String lookUp(MinecraftSessionService sessions, UUID player) {
        try {
            GameProfile profile = sessions.fillProfileProperties(new GameProfile(player, null), false);
            MinecraftProfileTexture skin = sessions.getTextures(profile, false).get(MinecraftProfileTexture.Type.SKIN);
            if (skin != null) return String.format(URL, skin.getHash());
        } catch (RuntimeException e) {
            Main.LOGGER.info("Couldn't look up the skin of {}: {}", player, e.getMessage());
        }
        return String.format(URL, player);
    }
}
