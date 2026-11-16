package com.coatt.tascade.replay;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecordCodec {
    private static final int MAX_STRING = 262_144;

    private RecordCodec() {}

    // PLAYER

    public static final int FLAG_ON_GROUND = 1;
    public static final int FLAG_SNEAKING = 2;
    public static final int FLAG_SPRINTING = 4;
    public static final int FLAG_SWIMMING = 8;
    public static final int FLAG_FALL_FLYING = 16;
    public static final int FLAG_USING_ITEM = 32;
    public static final int FLAG_OFFHAND_ACTIVE = 64;
    public static final int FLAG_INVISIBLE = 128;

    public static final int INPUT_FORWARD = 1;
    public static final int INPUT_BACK = 2;
    public static final int INPUT_LEFT = 4;
    public static final int INPUT_RIGHT = 8;
    public static final int INPUT_JUMP = 16;
    public static final int INPUT_SNEAK = 32;
    public static final int INPUT_SPRINT = 64;
    public static final int INPUT_ATTACK = 128;
    public static final int INPUT_USE = 256;

    public static void writePlayer(PacketByteBuf buf, double x, double y, double z, float yaw, float pitch,
                                   float headYaw, float bodyYaw, int pose, int flags, int input) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeFloat(headYaw);
        buf.writeFloat(bodyYaw);
        buf.writeByte(pose);
        buf.writeByte(flags);
        buf.writeShort(input);
    }

    // VITALS

    public static final class Vitals {
        public float health = 20;
        public float maxHealth = 20;
        public float absorption;
        public int food = 20;
        public float saturation = 5;
        public int armor;
        public int air = 300;
        public int maxAir = 300;
        public int xpLevel;
        public float xpProgress;
        public int totalXp;
        public int selectedSlot;
        public int gameMode;
        public int hurtTime;
        public int maxHurtTime = 10;
        public float knockbackVelocity;
        public boolean onFire;
        public boolean dead;
        public int itemUseTimeLeft;
        public float attackCooldown = 1;
        public float nausea;
        public float lastNausea;
        public int deathTime;
        public int sleepTimer;
        public List<StatusEffectInstance> effects = Collections.emptyList();
    }

    public static void writeVitals(PacketByteBuf buf, Vitals v) {
        buf.writeFloat(v.health);
        buf.writeFloat(v.maxHealth);
        buf.writeFloat(v.absorption);
        buf.writeVarInt(v.food);
        buf.writeFloat(v.saturation);
        buf.writeVarInt(v.armor);
        buf.writeVarInt(v.air);
        buf.writeVarInt(v.maxAir);
        buf.writeVarInt(v.xpLevel);
        buf.writeFloat(v.xpProgress);
        buf.writeVarInt(v.totalXp);
        buf.writeByte(v.selectedSlot);
        buf.writeByte(v.gameMode);
        buf.writeByte(v.hurtTime);
        buf.writeByte(v.maxHurtTime);
        buf.writeFloat(v.knockbackVelocity);
        buf.writeByte((v.onFire ? 1 : 0) | (v.dead ? 2 : 0));
        buf.writeVarInt(v.itemUseTimeLeft);
        buf.writeFloat(v.attackCooldown);
        buf.writeFloat(v.nausea);
        buf.writeFloat(v.lastNausea);
        buf.writeVarInt(v.deathTime);
        buf.writeVarInt(v.sleepTimer);
        buf.writeVarInt(v.effects.size());
        for (StatusEffectInstance effect : v.effects) {
            buf.writeVarInt(StatusEffect.getRawId(effect.getEffectType()));
            buf.writeByte(effect.getAmplifier());
            buf.writeVarInt(effect.getDuration());
            buf.writeByte((effect.isAmbient() ? 1 : 0) | (effect.shouldShowParticles() ? 2 : 0) | (effect.shouldShowIcon() ? 4 : 0));
        }
    }

    public static Vitals readVitals(PacketByteBuf buf) {
        Vitals v = new Vitals();
        v.health = buf.readFloat();
        v.maxHealth = buf.readFloat();
        v.absorption = buf.readFloat();
        v.food = buf.readVarInt();
        v.saturation = buf.readFloat();
        v.armor = buf.readVarInt();
        v.air = buf.readVarInt();
        v.maxAir = buf.readVarInt();
        v.xpLevel = buf.readVarInt();
        v.xpProgress = buf.readFloat();
        v.totalXp = buf.readVarInt();
        v.selectedSlot = buf.readByte();
        v.gameMode = buf.readByte();
        v.hurtTime = buf.readByte();
        v.maxHurtTime = buf.readByte();
        v.knockbackVelocity = buf.readFloat();
        int flags = buf.readByte();
        v.onFire = (flags & 1) != 0;
        v.dead = (flags & 2) != 0;
        v.itemUseTimeLeft = buf.readVarInt();
        v.attackCooldown = buf.readFloat();
        v.nausea = buf.readFloat();
        v.lastNausea = buf.readFloat();
        v.deathTime = buf.readVarInt();
        v.sleepTimer = buf.readVarInt();
        int count = buf.readVarInt();
        List<StatusEffectInstance> effects = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            StatusEffect type = StatusEffect.byRawId(buf.readVarInt());
            int amplifier = buf.readByte();
            int duration = buf.readVarInt();
            int effectFlags = buf.readByte();
            if (type != null) {
                effects.add(new StatusEffectInstance(type, duration, amplifier,
                        (effectFlags & 1) != 0, (effectFlags & 2) != 0, (effectFlags & 4) != 0));
            }
        }
        v.effects = effects;
        return v;
    }

    // INVENTORY: varint count, then (byte slot, item) pairs

    public static final int INVENTORY_SIZE = 41;

    // SCREEN

    public static final int SCREEN_NONE = 0;
    public static final int SCREEN_HANDLED = 1;
    public static final int SCREEN_OTHER = 2;

    public static final class ScreenInfo {
        public int kind;
        // handled screens: handler type ("minecraft:generic_9x3"), "inventory" or "creative", otherwise "death", "pause", ...
        public String type = "";
        public Text title;
        public Text detail;
        public int x;
        public int y;
        public int backgroundWidth;
        public int backgroundHeight;
        public int scaledWidth;
        public int scaledHeight;
        public int slotCount;
        public int recipeBook;
    }

    public static final int RECIPE_BOOK_OPEN = 1;
    public static final int RECIPE_BOOK_CRAFTABLE = 2;
    public static final int RECIPE_BOOK_FURNACE_OPEN = 4;
    public static final int RECIPE_BOOK_FURNACE_CRAFTABLE = 8;
    public static final int RECIPE_BOOK_BLAST_FURNACE_OPEN = 16;
    public static final int RECIPE_BOOK_BLAST_FURNACE_CRAFTABLE = 32;
    public static final int RECIPE_BOOK_SMOKER_OPEN = 64;
    public static final int RECIPE_BOOK_SMOKER_CRAFTABLE = 128;

    public static void writeScreen(PacketByteBuf buf, ScreenInfo s) {
        buf.writeByte(s.kind);
        if (s.kind == SCREEN_NONE) return;
        buf.writeString(s.type, MAX_STRING);
        writeOptionalText(buf, s.title);
        writeOptionalText(buf, s.detail);
        buf.writeShort(s.x);
        buf.writeShort(s.y);
        buf.writeShort(s.backgroundWidth);
        buf.writeShort(s.backgroundHeight);
        buf.writeShort(s.scaledWidth);
        buf.writeShort(s.scaledHeight);
        buf.writeVarInt(s.slotCount);
        buf.writeByte(s.recipeBook);
    }

    public static ScreenInfo readScreen(PacketByteBuf buf) {
        ScreenInfo s = new ScreenInfo();
        s.kind = buf.readByte();
        if (s.kind == SCREEN_NONE) return s;
        s.type = buf.readString(MAX_STRING);
        s.title = readOptionalText(buf);
        s.detail = readOptionalText(buf);
        s.x = buf.readShort();
        s.y = buf.readShort();
        s.backgroundWidth = buf.readShort();
        s.backgroundHeight = buf.readShort();
        s.scaledWidth = buf.readShort();
        s.scaledHeight = buf.readShort();
        s.slotCount = buf.readVarInt();
        // older recordings have no recipe book state
        s.recipeBook = buf.isReadable() ? buf.readUnsignedByte() : 0;
        return s;
    }

    // SCREEN_SLOTS: varint count, (varint slot, item) pairs, bool cursor changed, [item],
    //               varint count, (varint property, varint value) pairs

    // CURSOR: float x, float y. CLICK: varint syncId, short slot, byte button, byte action ordinal
    // SWING: byte hand ordinal. CHAT: varint message id, text. ACTION_BAR: text, bool tinted
    // CHAT_CLEAR: empty. SETTINGS: JSON string

    // PIE_CHART: bool shown, if shown: string path, varint count, (string name, double ofParent, double ofTotal) entries,
    //            the first being the open section itself, as ProfileResult#getTimings returns them

    // SOUND

    public static final class Sound {
        public Identifier id;
        public int category;
        public float volume;
        public float pitch;
        public boolean repeat;
        public int repeatDelay;
        public int attenuation;
        public double x;
        public double y;
        public double z;
        public boolean relative;
    }

    public static void writeSound(PacketByteBuf buf, Sound s) {
        buf.writeIdentifier(s.id);
        buf.writeByte(s.category);
        buf.writeFloat(s.volume);
        buf.writeFloat(s.pitch);
        buf.writeBoolean(s.repeat);
        buf.writeVarInt(s.repeatDelay);
        buf.writeByte(s.attenuation);
        buf.writeDouble(s.x);
        buf.writeDouble(s.y);
        buf.writeDouble(s.z);
        buf.writeBoolean(s.relative);
    }

    public static Sound readSound(PacketByteBuf buf) {
        Sound s = new Sound();
        s.id = buf.readIdentifier();
        s.category = buf.readByte();
        s.volume = buf.readFloat();
        s.pitch = buf.readFloat();
        s.repeat = buf.readBoolean();
        s.repeatDelay = buf.readVarInt();
        s.attenuation = buf.readByte();
        s.x = buf.readDouble();
        s.y = buf.readDouble();
        s.z = buf.readDouble();
        s.relative = buf.readBoolean();
        return s;
    }

    // BLOCK_PARTICLES: byte kind, long block position, then by kind: varint block state id (break),
    // direction id (mining), or crack stage 0-9, -1 for none (crack)

    public static final int PARTICLES_BREAK = 0;
    public static final int PARTICLES_MINING = 1;
    public static final int PARTICLES_CRACK = 2;

    // MARKER

    public static void writeMarker(PacketByteBuf buf, String type, String name, JSONObject extra) {
        buf.writeString(type, MAX_STRING);
        buf.writeString(name != null ? name : "", MAX_STRING);
        buf.writeString(extra != null ? extra.toString() : "{}", MAX_STRING);
    }

    // shared

    public static void writeString(PacketByteBuf buf, String s) {
        buf.writeString(s, MAX_STRING);
    }

    public static String readString(PacketByteBuf buf) {
        return buf.readString(MAX_STRING);
    }

    public static void writeText(PacketByteBuf buf, Text text) {
        buf.writeString(Text.Serializer.toJson(text), MAX_STRING);
    }

    public static Text readText(PacketByteBuf buf) {
        return Text.Serializer.fromJson(buf.readString(MAX_STRING));
    }

    public static void writeOptionalText(PacketByteBuf buf, Text text) {
        buf.writeBoolean(text != null);
        if (text != null) writeText(buf, text);
    }

    public static Text readOptionalText(PacketByteBuf buf) {
        return buf.readBoolean() ? readText(buf) : null;
    }

    public static void writeItem(PacketByteBuf buf, ItemStack stack) {
        buf.writeItemStack(stack);
    }

    public static ItemStack readItem(PacketByteBuf buf) {
        return buf.readItemStack();
    }
}
