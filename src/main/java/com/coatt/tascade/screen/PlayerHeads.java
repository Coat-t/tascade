package com.coatt.tascade.screen;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class PlayerHeads {
    private static final Map<UUID, PlayerListEntry> ENTRIES = new HashMap<>();

    private PlayerHeads() {}

    public static void draw(MatrixStack matrices, String uuid, int x, int y, int size) {
        PlayerListEntry entry = entry(uuid);
        if (entry == null) return;
        MinecraftClient.getInstance().getTextureManager().bindTexture(entry.getSkinTexture());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.color4f(1f, 1f, 1f, 1f);
        DrawableHelper.drawTexture(matrices, x, y, size, size, 8f, 8f, 8, 8, 64, 64);
        DrawableHelper.drawTexture(matrices, x, y, size, size, 40f, 8f, 8, 8, 64, 64);
        RenderSystem.disableBlend();
    }

    static PlayerListEntry entry(String uuid) {
        UUID id;
        try {
            id = UUID.fromString(uuid);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
        // a real UUID is enough for vanilla's skin loader
        return ENTRIES.computeIfAbsent(id, key -> new PlayerListEntry(
                new PlayerListS2CPacket().new Entry(new GameProfile(key, null), 0, GameMode.SURVIVAL, null)));
    }
}
