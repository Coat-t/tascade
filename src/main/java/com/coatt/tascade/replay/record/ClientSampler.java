package com.coatt.tascade.replay.record;

import com.coatt.tascade.mixin.access.AbstractSoundInstanceAccessor;
import com.coatt.tascade.mixin.access.ClientAdvancementManagerAccessor;
import com.coatt.tascade.mixin.access.DeathScreenAccessor;
import com.coatt.tascade.mixin.access.DebugRendererAccessor;
import com.coatt.tascade.mixin.access.HandledScreenAccessor;
import com.coatt.tascade.mixin.access.MinecraftClientAccessor;
import com.coatt.tascade.mixin.access.ScreenHandlerAccessor;
import com.coatt.tascade.replay.RecordCodec;
import com.coatt.tascade.replay.ReplayFormat;
import com.coatt.tascade.replay.ReplayScreens;
import io.netty.buffer.Unpooled;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientAdvancementManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.client.util.Window;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;
import net.minecraft.recipe.book.RecipeBook;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.profiler.ProfileResult;
import net.minecraft.util.profiler.ProfilerTiming;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ClientSampler {
    private static final long FRAME_INTERVAL_NANOS = 1_000_000_000L / 60 - 500_000L;
    private static final long PIE_INTERVAL_NANOS = 50_000_000L;

    private static long lastCameraNanos;
    private static long lastCursorNanos;
    private static long lastPieNanos;
    private static boolean pieShown;
    private static volatile double lastFov = 70;
    private static byte[] lastVitals;
    private static String lastSettings;
    private static final ItemStack[] lastInventory = new ItemStack[RecordCodec.INVENTORY_SIZE];
    private static byte[] lastScreen;
    private static ItemStack[] lastSlots = new ItemStack[0];
    private static ItemStack lastCursorStack = ItemStack.EMPTY;
    private static int[] lastProperties = new int[0];
    private static float lastCursorX = Float.NaN;
    private static float lastCursorY = Float.NaN;
    private static boolean wasDead;
    private static final Set<Identifier> advancementsSeen = new HashSet<>();

    private ClientSampler() {}

    static synchronized void reset() {
        lastCameraNanos = 0;
        lastCameraTime = -1;
        previousPlayerTime = -1;
        lastPlayerTime = -1;
        lastCursorNanos = 0;
        lastPieNanos = 0;
        pieShown = false;
        lastVitals = null;
        lastSettings = null;
        Arrays.fill(lastInventory, null);
        lastScreen = null;
        lastSlots = new ItemStack[0];
        lastCursorStack = ItemStack.EMPTY;
        lastProperties = new int[0];
        lastCursorX = Float.NaN;
        lastCursorY = Float.NaN;
        wasDead = false;
        advancementsSeen.clear();
    }

    private static ReplayRecorder.Session sessionFor(MinecraftClient client) {
        ReplayRecorder.Session session = ReplayRecorder.current();
        if (session == null || client.player == null || client.world == null || client.getNetworkHandler() == null) return null;
        return client.getNetworkHandler().getConnection() == session.connection ? session : null;
    }

    // every client tick

    public static void tick(MinecraftClient client) {
        ReplayRecorder.Session session = sessionFor(client);
        if (session == null) return;
        ClientPlayerEntity player = client.player;
        recordPlayer(session, client, player);
        recordVitals(session, client, player);
        recordInventory(session, player.inventory);
        recordScreen(session, client);
        recordSettings(session, client);
        boolean dead = player.isDead();
        if (dead && !wasDead) ReplayRecorder.died(session, player.getUuid());
        else if (!dead && wasDead) ReplayRecorder.respawned(session);
        wasDead = dead;
    }

    private static void recordPlayer(ReplayRecorder.Session session, MinecraftClient client, ClientPlayerEntity player) {
        int flags = 0;
        if (player.isOnGround()) flags |= RecordCodec.FLAG_ON_GROUND;
        if (player.isSneaking()) flags |= RecordCodec.FLAG_SNEAKING;
        if (player.isSprinting()) flags |= RecordCodec.FLAG_SPRINTING;
        if (player.isSwimming()) flags |= RecordCodec.FLAG_SWIMMING;
        if (player.isFallFlying()) flags |= RecordCodec.FLAG_FALL_FLYING;
        if (player.isUsingItem()) flags |= RecordCodec.FLAG_USING_ITEM;
        if (player.getActiveHand() == Hand.OFF_HAND) flags |= RecordCodec.FLAG_OFFHAND_ACTIVE;
        if (player.isInvisible()) flags |= RecordCodec.FLAG_INVISIBLE;

        int input = 0;
        if (client.currentScreen == null) {
            GameOptions o = client.options;
            if (o.keyForward.isPressed()) input |= RecordCodec.INPUT_FORWARD;
            if (o.keyBack.isPressed()) input |= RecordCodec.INPUT_BACK;
            if (o.keyLeft.isPressed()) input |= RecordCodec.INPUT_LEFT;
            if (o.keyRight.isPressed()) input |= RecordCodec.INPUT_RIGHT;
            if (o.keyJump.isPressed()) input |= RecordCodec.INPUT_JUMP;
            if (o.keySneak.isPressed()) input |= RecordCodec.INPUT_SNEAK;
            if (o.keySprint.isPressed()) input |= RecordCodec.INPUT_SPRINT;
            if (o.keyAttack.isPressed()) input |= RecordCodec.INPUT_ATTACK;
            if (o.keyUse.isPressed()) input |= RecordCodec.INPUT_USE;
        }

        PacketByteBuf buf = buffer();
        RecordCodec.writePlayer(buf, player.getX(), player.getY(), player.getZ(), player.yaw, player.pitch,
                player.headYaw, player.bodyYaw, player.getPose().ordinal(), flags, input);
        int time = Math.max(session.now(), lastPlayerTime);
        session.writeAt(time, ReplayFormat.PLAYER, buf);
        previousPlayerTime = lastPlayerTime >= 0 ? lastPlayerTime : time;
        lastPlayerTime = time;
    }

    private static void recordVitals(ReplayRecorder.Session session, MinecraftClient client, ClientPlayerEntity player) {
        RecordCodec.Vitals v = new RecordCodec.Vitals();
        v.health = player.getHealth();
        v.maxHealth = player.getMaxHealth();
        v.absorption = player.getAbsorptionAmount();
        v.food = player.getHungerManager().getFoodLevel();
        v.saturation = player.getHungerManager().getSaturationLevel();
        v.armor = player.getArmor();
        v.air = player.getAir();
        v.maxAir = player.getMaxAir();
        v.xpLevel = player.experienceLevel;
        v.xpProgress = player.experienceProgress;
        v.totalXp = player.totalExperience;
        v.selectedSlot = player.inventory.selectedSlot;
        v.gameMode = client.interactionManager != null ? client.interactionManager.getCurrentGameMode().getId() : 0;
        v.hurtTime = player.hurtTime;
        v.maxHurtTime = player.maxHurtTime;
        v.knockbackVelocity = player.knockbackVelocity;
        v.onFire = player.isOnFire();
        v.dead = player.isDead();
        v.itemUseTimeLeft = player.getItemUseTimeLeft();
        v.attackCooldown = player.getAttackCooldownProgress(0f);
        v.nausea = player.nextNauseaStrength;
        v.lastNausea = player.lastNauseaStrength;
        v.deathTime = player.deathTime;
        v.sleepTimer = player.getSleepTimer();
        v.effects = new ArrayList<>(player.getStatusEffects());

        PacketByteBuf buf = buffer();
        RecordCodec.writeVitals(buf, v);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(0, bytes);
        if (Arrays.equals(bytes, lastVitals)) return;
        lastVitals = bytes;
        session.write(ReplayFormat.VITALS, buf);
    }

    private static void recordInventory(ReplayRecorder.Session session, PlayerInventory inventory) {
        PacketByteBuf changes = buffer();
        int count = 0;
        for (int slot = 0; slot < RecordCodec.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventorySlot(inventory, slot);
            ItemStack last = lastInventory[slot];
            if (last != null && ItemStack.areEqual(stack, last)) continue;
            lastInventory[slot] = stack.copy();
            changes.writeByte(slot);
            RecordCodec.writeItem(changes, stack);
            count++;
        }
        if (count == 0) return;
        PacketByteBuf buf = buffer();
        buf.writeVarInt(count);
        buf.writeBytes(changes);
        session.write(ReplayFormat.INVENTORY, buf);
    }

    // 0-35 main (0-8 hotbar), 36-39 armor (feet first), 40 offhand
    public static ItemStack inventorySlot(PlayerInventory inventory, int slot) {
        if (slot < 36) return inventory.main.get(slot);
        if (slot < 40) return inventory.armor.get(slot - 36);
        return inventory.offHand.get(0);
    }

    private static void recordScreen(ReplayRecorder.Session session, MinecraftClient client) {
        Screen screen = client.currentScreen;
        RecordCodec.ScreenInfo info = describe(screen);
        PacketByteBuf buf = buffer();
        RecordCodec.writeScreen(buf, info);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(0, bytes);
        if (!Arrays.equals(bytes, lastScreen)) {
            lastScreen = bytes;
            session.write(ReplayFormat.SCREEN, buf);
            lastSlots = new ItemStack[info.slotCount];
            lastCursorStack = ItemStack.EMPTY;
            lastProperties = new int[0];
            lastCursorX = Float.NaN;
        }
        if (screen instanceof HandledScreen) recordSlots(session, client, ((HandledScreen<?>) screen).getScreenHandler());
    }

    private static RecordCodec.ScreenInfo describe(Screen screen) {
        RecordCodec.ScreenInfo info = new RecordCodec.ScreenInfo();
        if (screen == null) {
            info.kind = RecordCodec.SCREEN_NONE;
            return info;
        }
        info.title = screen.getTitle();
        info.scaledWidth = screen.width;
        info.scaledHeight = screen.height;
        if (screen instanceof HandledScreen) {
            HandledScreen<?> handled = (HandledScreen<?>) screen;
            HandledScreenAccessor layout = (HandledScreenAccessor) handled;
            info.kind = RecordCodec.SCREEN_HANDLED;
            info.type = ReplayScreens.typeOf(handled);
            info.x = layout.tascade$getX();
            info.y = layout.tascade$getY();
            info.backgroundWidth = layout.tascade$getBackgroundWidth();
            info.backgroundHeight = layout.tascade$getBackgroundHeight();
            info.slotCount = handled.getScreenHandler().slots.size();
            if (MinecraftClient.getInstance().player != null) info.recipeBook = recipeBookState(MinecraftClient.getInstance().player.getRecipeBook());
        } else {
            info.kind = RecordCodec.SCREEN_OTHER;
            info.type = ReplayScreens.nameOf(screen);
            if (screen instanceof DeathScreen) info.detail = ((DeathScreenAccessor) screen).tascade$getMessage();
        }
        return info;
    }

    private static int recipeBookState(RecipeBook book) {
        int state = 0;
        if (book.isGuiOpen()) state |= RecordCodec.RECIPE_BOOK_OPEN;
        if (book.isFilteringCraftable()) state |= RecordCodec.RECIPE_BOOK_CRAFTABLE;
        if (book.isFurnaceGuiOpen()) state |= RecordCodec.RECIPE_BOOK_FURNACE_OPEN;
        if (book.isFurnaceFilteringCraftable()) state |= RecordCodec.RECIPE_BOOK_FURNACE_CRAFTABLE;
        if (book.isBlastFurnaceGuiOpen()) state |= RecordCodec.RECIPE_BOOK_BLAST_FURNACE_OPEN;
        if (book.isBlastFurnaceFilteringCraftable()) state |= RecordCodec.RECIPE_BOOK_BLAST_FURNACE_CRAFTABLE;
        if (book.isSmokerGuiOpen()) state |= RecordCodec.RECIPE_BOOK_SMOKER_OPEN;
        if (book.isSmokerFilteringCraftable()) state |= RecordCodec.RECIPE_BOOK_SMOKER_CRAFTABLE;
        return state;
    }

    private static void recordSlots(ReplayRecorder.Session session, MinecraftClient client, ScreenHandler handler) {
        int slotCount = handler.slots.size();
        if (lastSlots.length != slotCount) lastSlots = new ItemStack[slotCount];
        PacketByteBuf slotChanges = buffer();
        int changed = 0;
        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (lastSlots[i] != null && ItemStack.areEqual(stack, lastSlots[i])) continue;
            lastSlots[i] = stack.copy();
            slotChanges.writeVarInt(i);
            RecordCodec.writeItem(slotChanges, stack);
            changed++;
        }
        ItemStack cursor = client.player.inventory.getCursorStack();
        boolean cursorChanged = !ItemStack.areEqual(cursor, lastCursorStack);
        if (cursorChanged) lastCursorStack = cursor.copy();

        List<Property> properties = ((ScreenHandlerAccessor) handler).tascade$getProperties();
        if (lastProperties.length != properties.size()) {
            lastProperties = new int[properties.size()];
            Arrays.fill(lastProperties, Integer.MIN_VALUE);
        }
        PacketByteBuf propertyChanges = buffer();
        int propertiesChanged = 0;
        for (int i = 0; i < properties.size(); i++) {
            int value = properties.get(i).get();
            if (value == lastProperties[i]) continue;
            lastProperties[i] = value;
            propertyChanges.writeVarInt(i);
            propertyChanges.writeVarInt(value);
            propertiesChanged++;
        }
        if (changed == 0 && !cursorChanged && propertiesChanged == 0) return;

        PacketByteBuf buf = buffer();
        buf.writeVarInt(changed);
        buf.writeBytes(slotChanges);
        buf.writeBoolean(cursorChanged);
        if (cursorChanged) RecordCodec.writeItem(buf, cursor);
        buf.writeVarInt(propertiesChanged);
        buf.writeBytes(propertyChanges);
        session.write(ReplayFormat.SCREEN_SLOTS, buf);
    }

    private static void recordSettings(ReplayRecorder.Session session, MinecraftClient client) {
        GameOptions o = client.options;
        Window window = client.getWindow();
        JSONObject s = new JSONObject();
        s.put("renderDistance", o.viewDistance);
        s.put("entityDistance", (double) o.entityDistanceScaling);
        s.put("fov", o.fov);
        s.put("brightness", o.gamma);
        s.put("graphics", o.graphicsMode.ordinal());
        s.put("smoothLighting", o.ao.ordinal());
        s.put("maxFps", o.maxFps);
        s.put("vsync", o.enableVsync);
        s.put("viewBobbing", o.bobView);
        s.put("guiScale", o.guiScale);
        s.put("attackIndicator", o.attackIndicator.ordinal());
        s.put("clouds", o.cloudRenderMode.ordinal());
        s.put("fullscreen", o.fullscreen);
        s.put("particles", o.particles.ordinal());
        s.put("mipmapLevels", o.mipmapLevels);
        s.put("entityShadows", o.entityShadows);
        s.put("biomeBlend", o.biomeBlendRadius);
        s.put("perspective", o.perspective);
        s.put("hudHidden", o.hudHidden);
        s.put("debug", o.debugEnabled);
        s.put("pieChart", o.debugProfilerEnabled);
        s.put("tpsGraph", o.debugTpsEnabled);
        s.put("hitboxes", client.getEntityRenderManager().shouldRenderHitboxes());
        s.put("chunkBorders", ((DebugRendererAccessor) client.debugRenderer).tascade$showsChunkBorder());
        s.put("advancedTooltips", o.advancedItemTooltips);
        s.put("heldItemTooltips", o.heldItemTooltips);
        s.put("sensitivity", o.mouseSensitivity);
        s.put("smoothCamera", o.smoothCameraEnabled);
        s.put("toggleSneak", o.sneakToggled);
        s.put("toggleSprint", o.sprintToggled);
        s.put("mainArm", o.mainArm.ordinal());
        int parts = 0;
        for (PlayerModelPart part : o.getEnabledPlayerModelParts()) parts |= part.getBitFlag();
        s.put("modelParts", parts);
        s.put("windowWidth", window.getFramebufferWidth());
        s.put("windowHeight", window.getFramebufferHeight());
        s.put("scaledWidth", window.getScaledWidth());
        s.put("scaledHeight", window.getScaledHeight());
        String json = s.toString();
        if (json.equals(lastSettings)) return;
        lastSettings = json;
        PacketByteBuf buf = buffer();
        RecordCodec.writeString(buf, json);
        session.write(ReplayFormat.SETTINGS, buf);
    }

    // every frame

    private static int previousPlayerTime = -1;
    private static int lastPlayerTime = -1;
    private static int lastCameraTime = -1;

    // the world is drawn between the last two ticks, so the camera is stamped that far between the last two samples
    private static int frameTime(MinecraftClient client) {
        if (lastPlayerTime < 0) return -1;
        float delta = MathHelper.clamp(client.getTickDelta(), 0, 1);
        int time = previousPlayerTime + Math.round((lastPlayerTime - previousPlayerTime) * delta);
        return Math.max(time, lastCameraTime);
    }

    public static void frame(MinecraftClient client) {
        ReplayRecorder.Session session = sessionFor(client);
        if (session == null) return;
        long now = System.nanoTime();
        int cameraTime = frameTime(client);
        if (cameraTime >= 0 && now - lastCameraNanos >= FRAME_INTERVAL_NANOS) {
            lastCameraNanos = now;
            lastCameraTime = cameraTime;
            Camera camera = client.gameRenderer.getCamera();
            PacketByteBuf buf = buffer();
            buf.writeDouble(camera.getPos().x);
            buf.writeDouble(camera.getPos().y);
            buf.writeDouble(camera.getPos().z);
            buf.writeFloat(camera.getYaw());
            buf.writeFloat(camera.getPitch());
            buf.writeFloat((float) lastFov);
            buf.writeByte(client.options.perspective);
            session.writeAt(cameraTime, ReplayFormat.CAMERA, buf);
        }
        if (client.currentScreen != null && now - lastCursorNanos >= FRAME_INTERVAL_NANOS) {
            Window window = client.getWindow();
            float x = (float) (client.mouse.getX() * window.getScaledWidth() / window.getWidth());
            float y = (float) (client.mouse.getY() * window.getScaledHeight() / window.getHeight());
            if (x != lastCursorX || y != lastCursorY) {
                lastCursorNanos = now;
                lastCursorX = x;
                lastCursorY = y;
                PacketByteBuf buf = buffer();
                buf.writeFloat(x);
                buf.writeFloat(y);
                session.write(ReplayFormat.CURSOR, buf);
            }
        }
        recordPieChart(session, client, now);
    }

    private static void recordPieChart(ReplayRecorder.Session session, MinecraftClient client, long now) {
        MinecraftClientAccessor access = (MinecraftClientAccessor) client;
        ProfileResult result = access.tascade$getTickProfilerResult();
        if (result == null) {
            if (pieShown) {
                pieShown = false;
                PacketByteBuf buf = buffer();
                buf.writeBoolean(false);
                session.write(ReplayFormat.PIE_CHART, buf);
            }
            return;
        }
        if (pieShown && now - lastPieNanos < PIE_INTERVAL_NANOS) return;
        pieShown = true;
        lastPieNanos = now;
        String path = access.tascade$getOpenProfilerSection();
        List<ProfilerTiming> timings = result.getTimings(path);
        PacketByteBuf buf = buffer();
        buf.writeBoolean(true);
        RecordCodec.writeString(buf, path);
        buf.writeVarInt(timings.size());
        for (ProfilerTiming timing : timings) {
            RecordCodec.writeString(buf, timing.name);
            buf.writeDouble(timing.parentSectionUsagePercentage);
            buf.writeDouble(timing.totalUsagePercentage);
        }
        session.write(ReplayFormat.PIE_CHART, buf);
    }

    public static void onFov(double fov) {
        lastFov = fov;
    }

    // as they happen

    public static void swing(Hand hand) {
        if (sessionFor(MinecraftClient.getInstance()) == null) return;
        PacketByteBuf buf = buffer();
        buf.writeByte(hand.ordinal());
        ReplayRecorder.write(ReplayFormat.SWING, buf);
    }

    public static void chat(Text message, int messageId) {
        if (sessionFor(MinecraftClient.getInstance()) == null) return;
        PacketByteBuf buf = buffer();
        buf.writeVarInt(messageId);
        RecordCodec.writeText(buf, message);
        ReplayRecorder.write(ReplayFormat.CHAT, buf);
    }

    public static void chatCleared() {
        if (sessionFor(MinecraftClient.getInstance()) == null) return;
        ReplayRecorder.write(ReplayFormat.CHAT_CLEAR, buffer());
    }

    public static void actionBar(Text message, boolean tinted) {
        if (sessionFor(MinecraftClient.getInstance()) == null) return;
        PacketByteBuf buf = buffer();
        RecordCodec.writeText(buf, message);
        buf.writeBoolean(tinted);
        ReplayRecorder.write(ReplayFormat.ACTION_BAR, buf);
    }

    public static void click(int syncId, int slot, int button, SlotActionType action) {
        if (sessionFor(MinecraftClient.getInstance()) == null) return;
        PacketByteBuf buf = buffer();
        buf.writeVarInt(syncId);
        buf.writeShort(slot);
        buf.writeByte(button);
        buf.writeByte(action.ordinal());
        ReplayRecorder.write(ReplayFormat.CLICK, buf);
    }

    public static void sound(SoundInstance sound) {
        if (sound instanceof TickableSoundInstance || !(sound instanceof AbstractSoundInstance)) return;
        SoundCategory category = sound.getCategory();
        if (category == SoundCategory.MUSIC || category == SoundCategory.MASTER) return;
        if (sessionFor(MinecraftClient.getInstance()) == null) return;
        AbstractSoundInstanceAccessor raw = (AbstractSoundInstanceAccessor) sound;
        RecordCodec.Sound s = new RecordCodec.Sound();
        s.id = sound.getId();
        s.category = category.ordinal();
        s.volume = raw.tascade$getVolume();
        s.pitch = raw.tascade$getPitch();
        s.repeat = sound.isRepeatable();
        s.repeatDelay = sound.getRepeatDelay();
        s.attenuation = sound.getAttenuationType().ordinal();
        s.x = sound.getX();
        s.y = sound.getY();
        s.z = sound.getZ();
        s.relative = sound.isLooping();
        PacketByteBuf buf = buffer();
        RecordCodec.writeSound(buf, s);
        ReplayRecorder.write(ReplayFormat.SOUND, buf);
    }

    public static void blockParticles(int kind, BlockPos pos, int value) {
        if (sessionFor(MinecraftClient.getInstance()) == null) return;
        PacketByteBuf buf = buffer();
        buf.writeByte(kind);
        buf.writeLong(pos.asLong());
        buf.writeVarInt(value);
        ReplayRecorder.write(ReplayFormat.BLOCK_PARTICLES, buf);
    }

    public static void advancements(ClientAdvancementManager manager, AdvancementUpdateS2CPacket packet) {
        if (sessionFor(MinecraftClient.getInstance()) == null) return;
        for (Identifier id : packet.getAdvancementsToProgress().keySet()) {
            Advancement advancement = manager.getManager().get(id);
            if (advancement == null) continue;
            AdvancementDisplay display = advancement.getDisplay();
            AdvancementProgress progress = ((ClientAdvancementManagerAccessor) manager).tascade$getProgresses().get(advancement);
            if (display == null || progress == null || !progress.isDone()) continue;
            if (!advancementsSeen.add(id)) continue;
            String path = id.getPath();
            if (path.equals("root") || path.endsWith("/root")) continue;
            JSONObject extra = new JSONObject();
            extra.put("title", Text.Serializer.toJson(display.getTitle()));
            extra.put("frame", display.getFrame().ordinal());
            ReplayRecorder.marker("advancement", id.toString(), extra);
        }
    }

    private static PacketByteBuf buffer() {
        return new PacketByteBuf(Unpooled.buffer());
    }
}
