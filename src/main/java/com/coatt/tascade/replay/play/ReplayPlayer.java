package com.coatt.tascade.replay.play;

import com.coatt.tascade.TascadeConfig;
import com.coatt.tascade.hud.RunLabels;
import com.coatt.tascade.mixin.access.ChatHudInvoker;
import com.coatt.tascade.replay.RecordCodec;
import com.coatt.tascade.replay.gui.ReplayControlScreen;
import com.coatt.tascade.replay.gui.ReplayHud;
import com.coatt.tascade.replay.gui.ReplayLoadingScreen;
import com.coatt.tascade.replay.io.ReplayFile;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.CreditsScreen;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.block.Block;
import net.minecraft.client.render.Camera;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// the world only ticks on the replay's clock, so pausing freezes it and speed-ups run it faster
public final class ReplayPlayer {
    private static final Logger LOGGER = LogManager.getLogger("Tascade/Replay");
    public static final int CAMERA_ENTITY_ID = -0x54434100;
    public static final double[] SPEEDS = {0.1, 0.25, 0.5, 1, 2, 4, 8, 16};
    private static final int NORMAL_SPEED = 3;
    public static final int MAX_GUI_SCALE = 3;

    private static ReplayPlayer active;
    private static volatile ClientConnection activeConnection;

    final MinecraftClient client;
    final ReplayIndex index;
    final Tracks tracks;
    public final JSONObject meta;
    public final Path path;
    private final ReplayFile file;
    private final ClientConnection connection;
    private final ReplayWorld world;
    private final RunnerView view;
    private final RecordedScreenView screenView;
    private final Screen exitScreen;
    private final List<Marker> markers;
    private final List<Band> bands;
    public final int raceStart;
    public final int duration;

    double time;
    private boolean playing = true;
    private int speedIndex = NORMAL_SPEED;
    private long lastFrameNanos;
    private int nextTickTime;
    private float tickDelta;
    private boolean scrubbing;
    private boolean wasPlayingBeforeScrub;
    private boolean inFrame;
    boolean pov = true;
    boolean runnerSettings = true;
    public boolean overlayShown = true;
    private final FreeCamera freeCamera = new FreeCamera();
    private long lastActionMs;
    private String lastAction = "";

    private int swingCursor;
    private int soundCursor;
    private int particleCursor;
    private boolean soundsOn = true;
    private static boolean playingRecorded;
    private int chatCursor;
    private int clearCursor;
    private int actionBarCursor;

    private boolean cameraSaved;
    private double savedX;
    private double savedY;
    private double savedZ;
    private float savedYaw;
    private float savedPitch;

    public static final class Marker {
        public final double time;
        public final String label;
        public final int color;
        public final boolean major;

        Marker(double time, String label, int color, boolean major) {
            this.time = time;
            this.label = label;
            this.color = color;
            this.major = major;
        }
    }

    public static final class Band {
        public final double start;
        public final double end;
        public final int color;
        public final String name;

        Band(double start, double end, int color, String name) {
            this.start = start;
            this.end = end;
            this.color = color;
            this.name = name;
        }
    }

    private ReplayPlayer(MinecraftClient client, ReplayFile file, ReplayIndex index, Screen exitScreen) {
        this.client = client;
        this.file = file;
        this.index = index;
        this.tracks = index.tracks;
        this.meta = file.complete || index.tracks.streamMeta == null ? file.meta : index.tracks.streamMeta;
        this.path = file.path;
        this.exitScreen = exitScreen;
        this.duration = Math.max(index.duration, 1);
        int race = meta.optInt("raceStartMs", -1);
        if (race < 0) {
            for (Tracks.Marker marker : tracks.markers) {
                if ("race_start".equals(marker.type)) {
                    race = marker.time;
                    break;
                }
            }
        }
        this.raceStart = race;
        this.markers = buildMarkers();
        this.bands = buildBands();

        UUID uuid;
        try {
            uuid = UUID.fromString(meta.optString("playerUuid", ""));
        } catch (IllegalArgumentException e) {
            uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + meta.optString("playerName", "Runner")).getBytes());
        }
        GameProfile profile = new GameProfile(uuid, meta.optString("playerName", "Runner"));

        this.connection = new ClientConnection(NetworkSide.CLIENTBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                ReferenceCountUtil.release(message);
                promise.setSuccess();
            }
        }, connection);
        channel.attr(ClientConnection.ATTR_KEY_PROTOCOL).set(NetworkState.PLAY);
        ClientPlayNetworkHandler handler = new ClientPlayNetworkHandler(client, exitScreen, connection, profile);
        connection.setPacketListener(handler);
        this.world = new ReplayWorld(this, index, client, handler);
        this.view = new RunnerView(this, client, tracks, profile, index.selfId);
        this.screenView = new RecordedScreenView(client);
    }

    // opening and closing

    public static void open(MinecraftClient client, Path path, Screen parent) {
        // blocks can't be drawn until their models have loaded
        if (client.world != null || active != null || client.getOverlay() != null) return;
        ReplayLoadingScreen loading = new ReplayLoadingScreen(parent);
        client.openScreen(loading);
        Thread loader = new Thread(() -> {
            ReplayFile file = null;
            try {
                file = ReplayFile.open(path);
                ReplayFile opened = file;
                ReplayIndex index = ReplayIndex.build(file, loading::setProgress);
                if (index.segments.isEmpty()) throw new IOException("The recording has no world in it");
                client.execute(() -> start(client, opened, index, parent, loading));
            } catch (Throwable t) {
                LOGGER.error("Couldn't open replay {}", path, t);
                if (file != null) {
                    try {
                        file.close();
                    } catch (IOException ignored) {
                    }
                }
                String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                client.execute(() -> loading.fail(message));
            }
        }, "Tascade-ReplayLoader");
        loader.setDaemon(true);
        loader.start();
    }

    private static void start(MinecraftClient client, ReplayFile file, ReplayIndex index, Screen parent, ReplayLoadingScreen loading) {
        if (client.currentScreen != loading || client.world != null) {
            try {
                file.close();
            } catch (IOException ignored) {
            }
            return;
        }
        ReplayPlayer player = new ReplayPlayer(client, file, index, parent);
        active = player;
        activeConnection = player.connection;
        try {
            int startAt = player.raceStart >= 0 ? Math.max(0, player.raceStart - 1000) : 0;
            player.world.applyUpTo(Math.max(0, index.indexAt(startAt)), false, false);
            player.time = startAt;
            player.nextTickTime = (startAt / 50) * 50 + 50;
            player.afterSeek();
            player.lastFrameNanos = 0;
            client.openScreen(null);
            // only now: catching up to the race start takes a few seconds and redraws the loading screen
            updateGuiScale(client);
            LOGGER.info("Watching replay {}", file.path.getFileName());
        } catch (Throwable t) {
            LOGGER.error("Couldn't start replay", t);
            player.exit();
            client.openScreen(parent);
        }
    }

    public void exit() {
        client.disconnect(exitScreen);
    }

    public static void onDisconnect() {
        ReplayPlayer player = active;
        if (player == null) return;
        active = null;
        activeConnection = null;
        updateGuiScale(player.client);
        player.view.restoreSettings();
        player.screenView.reset();
        try {
            player.file.close();
        } catch (IOException ignored) {
        }
    }

    private static void updateGuiScale(MinecraftClient client) {
        if (client.getWindow().calculateScaleFactor(client.options.guiScale, client.forcesUnicodeFont()) > MAX_GUI_SCALE) {
            client.onResolutionChanged();
        }
    }

    public static double guiScale(double scale) {
        return active != null ? Math.min(scale, MAX_GUI_SCALE) : scale;
    }

    // hooks

    public static boolean isActive() {
        return active != null;
    }

    public static ReplayPlayer get() {
        return active;
    }

    public static boolean isReplayConnection(ClientConnection connection) {
        return connection != null && connection == activeConnection;
    }

    public static boolean ownsWorldTicks() {
        return active != null && active.client.world != null;
    }

    public static void frame() {
        if (active != null) active.onFrame();
    }

    public static float tickDelta(float original) {
        return active != null && active.client.world != null ? active.tickDelta : original;
    }

    public static boolean blocksScreen(Screen screen) {
        if (active == null) return false;
        return screen instanceof DownloadingTerrainScreen || screen instanceof ProgressScreen || screen instanceof DeathScreen
                || screen instanceof CreditsScreen || screen instanceof SleepingChatScreen;
    }

    public static boolean blocksSound(SoundInstance sound) {
        ReplayPlayer player = active;
        if (player == null || playingRecorded || player.tracks.sounds.size() == 0) return false;
        if (sound instanceof TickableSoundInstance) return false;
        SoundCategory category = sound.getCategory();
        return category != SoundCategory.MASTER && category != SoundCategory.MUSIC;
    }

    public static boolean blocksBlockParticles() {
        ReplayPlayer player = active;
        return player != null && !playingRecorded && player.tracks.hasBlockParticles();
    }

    public static boolean captureScreen(Screen screen) {
        return RecordedScreenView.capture(screen);
    }

    public static void overrideCamera(Camera camera) {
        if (active != null) active.view.overrideCamera(camera);
    }

    public static double overrideFov(double fov) {
        return active != null ? active.view.overrideFov(fov) : fov;
    }

    public static boolean hidesNameTag(Entity entity) {
        return active != null && active.pov && entity == active.view.runner;
    }

    public static boolean hidesEntity(Entity entity) {
        if (active == null) return false;
        return entity instanceof CameraEntity || active.view.hides(entity);
    }

    public static void renderHud(MatrixStack matrices, float delta) {
        ReplayPlayer player = active;
        if (player == null || player.client.world == null) return;
        if (player.pov) player.screenView.render(matrices, player.tracks, player.time, delta);
        if (!(player.client.currentScreen instanceof ReplayControlScreen)) ReplayHud.render(matrices, player);
    }

    private static boolean writingOptions;

    public static boolean writeViewerOptions(net.minecraft.client.options.GameOptions options) {
        ReplayPlayer player = active;
        if (player == null || writingOptions || !player.view.showsRunnerSettings()) return false;
        writingOptions = true;
        try {
            player.view.restoreSettings();
            options.write();
        } finally {
            writingOptions = false;
        }
        return true;
    }

    public static void renderPieChart() {
        if (active != null && active.client.world != null) active.view.drawPieChart();
    }

    // the clock

    private void onFrame() {
        if (inFrame || client.world == null || !(client.player instanceof CameraEntity)) return;
        inFrame = true;
        try {
            long now = System.nanoTime();
            double realMs = lastFrameNanos == 0 ? 0 : Math.min(200, (now - lastFrameNanos) / 1_000_000.0);
            lastFrameNanos = now;
            if (playing && !scrubbing) {
                double target = time + realMs * speed();
                if (target >= duration) {
                    target = duration;
                    playing = false;
                }
                advance(target);
            }
            view.frame(realMs);
        } catch (Exception e) {
            LOGGER.error("Replay frame failed", e);
        } finally {
            inFrame = false;
        }
    }

    private void advance(double target) {
        boolean live = speed() <= 4;
        soundsOn = speed() >= 0.5 && speed() <= TascadeConfig.replays().replaySoundMaxSpeed;
        int ticks = 0;
        while (nextTickTime <= target) {
            if (++ticks > 40) {
                // too far behind to tick every step: jump instead
                seekInternal(target);
                return;
            }
            applyUpTo(nextTickTime, live);
            worldTick(nextTickTime);
            nextTickTime += 50;
        }
        applyUpTo(target, live);
        time = target;
        tickDelta = (float) MathHelper.clamp((time - (nextTickTime - 50)) / 50.0, 0, 1);
    }

    private void applyUpTo(double t, boolean live) {
        int target = index.indexAt((int) Math.floor(t));
        world.applyUpTo(target, live, soundsOn);
        clientEvents(t, live);
    }

    private void clientEvents(double t, boolean live) {
        ChatHud chat = client.inGameHud.getChatHud();
        while (swingCursor < tracks.swings.size() && tracks.swings.time(swingCursor) <= t) {
            if (live) view.swing(tracks.swings.get(swingCursor));
            swingCursor++;
        }
        while (soundCursor < tracks.sounds.size() && tracks.sounds.time(soundCursor) <= t) {
            if (soundsOn) playRecorded(tracks.sounds.get(soundCursor));
            soundCursor++;
        }
        while (particleCursor < tracks.blockParticles.size() && tracks.blockParticles.time(particleCursor) <= t) {
            if (live) showRecorded(tracks.blockParticles.get(particleCursor));
            particleCursor++;
        }
        while (clearCursor < tracks.chatClears.size() && tracks.chatClears.time(clearCursor) <= t) {
            chat.clear(false);
            clearCursor++;
        }
        while (chatCursor < tracks.chat.size() && tracks.chat.time(chatCursor) <= t) {
            Tracks.ChatLine line = tracks.chat.get(chatCursor);
            ((ChatHudInvoker) chat).tascade$addMessage(line.text, line.messageId, client.inGameHud.getTicks(), false);
            chatCursor++;
        }
        while (actionBarCursor < tracks.actionBar.size() && tracks.actionBar.time(actionBarCursor) <= t) {
            Tracks.ActionBar message = tracks.actionBar.get(actionBarCursor);
            client.inGameHud.setOverlayMessage(message.text, message.tinted);
            actionBarCursor++;
        }
    }

    private void playRecorded(RecordCodec.Sound s) {
        SoundCategory[] categories = SoundCategory.values();
        SoundInstance.AttenuationType[] attenuations = SoundInstance.AttenuationType.values();
        SoundInstance sound = new RecordedSound(s.id,
                s.category >= 0 && s.category < categories.length ? categories[s.category] : SoundCategory.MASTER,
                s.volume, s.pitch, s.repeat, s.repeatDelay,
                s.attenuation >= 0 && s.attenuation < attenuations.length ? attenuations[s.attenuation] : SoundInstance.AttenuationType.LINEAR,
                s.x, s.y, s.z, s.relative, (float) speed());
        playingRecorded = true;
        try {
            client.getSoundManager().play(sound);
        } finally {
            playingRecorded = false;
        }
    }

    // pitch is scaled by speed after vanilla's 0.5-2 clamp, see SoundSystemMixin
    public static final class RecordedSound extends PositionedSoundInstance {
        public final float speed;

        RecordedSound(Identifier id, SoundCategory category, float volume, float pitch, boolean repeat, int repeatDelay,
                SoundInstance.AttenuationType attenuation, double x, double y, double z, boolean relative, float speed) {
            super(id, category, volume, pitch, repeat, repeatDelay, attenuation, x, y, z, relative);
            this.speed = speed;
        }
    }

    private void showRecorded(long[] particles) {
        BlockPos pos = BlockPos.fromLong(particles[1]);
        playingRecorded = true;
        try {
            if (particles[0] == RecordCodec.PARTICLES_CRACK) {
                if (client.world != null) client.world.setBlockBreakingInfo(index.selfId, pos, (int) particles[2]);
            } else if (particles[0] == RecordCodec.PARTICLES_BREAK) {
                client.particleManager.addBlockBreakParticles(pos, Block.getStateFromRawId((int) particles[2]));
            } else if (client.world != null && !client.world.getBlockState(pos).isAir()) {
                client.particleManager.addBlockBreakingParticles(pos, Direction.byId((int) particles[2]));
            }
        } finally {
            playingRecorded = false;
        }
    }

    private void worldTick(int tickTime) {
        ClientWorld clientWorld = client.world;
        if (clientWorld == null) return;
        view.beforeWorldTick(tickTime);
        client.inGameHud.tick();
        client.gameRenderer.tick();
        client.worldRenderer.tick();
        if (clientWorld.getLightningTicksLeft() > 0) clientWorld.setLightningTicksLeft(clientWorld.getLightningTicksLeft() - 1);
        clientWorld.tickEntities();
        view.afterEntitiesTick(tickTime);
        client.getTextureManager().tick();
        client.getSoundManager().tick(false);
        clientWorld.tick(() -> true);
        if (client.player != null) {
            clientWorld.doRandomBlockDisplayTicks(MathHelper.floor(client.player.getX()), MathHelper.floor(client.player.getY()),
                    MathHelper.floor(client.player.getZ()));
        }
        client.particleManager.tick();
    }

    // controls

    float frameTickDelta() {
        return tickDelta;
    }

    public double speed() {
        return SPEEDS[speedIndex];
    }

    public boolean isPlaying() {
        return playing;
    }

    public double time() {
        return time;
    }

    public void togglePlaying() {
        if (!playing && time >= duration) seek(raceStart >= 0 ? raceStart : 0);
        playing = !playing;
        lastFrameNanos = 0;
        action(playing ? "Playing" : "Paused");
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
        lastFrameNanos = 0;
    }

    public void faster() {
        speedIndex = Math.min(SPEEDS.length - 1, speedIndex + 1);
        action("Speed " + speedLabel());
    }

    public void slower() {
        speedIndex = Math.max(0, speedIndex - 1);
        action("Speed " + speedLabel());
    }

    public void normalSpeed() {
        speedIndex = NORMAL_SPEED;
        action("Speed " + speedLabel());
    }

    public String speedLabel() {
        double speed = speed();
        return (speed == Math.floor(speed) ? String.valueOf((int) speed) : String.valueOf(speed)) + "x";
    }

    public String runnerResolution() {
        JSONObject settings = tracks.settings.at((int) time);
        if (settings == null) return null;
        int width = settings.optInt("windowWidth", 0);
        int height = settings.optInt("windowHeight", 0);
        return width > 0 && height > 0 ? width + "×" + height : null;
    }

    public void skip(double ms) {
        seek(time + ms);
        String amount = Math.abs(ms) >= 1000 ? (Math.abs(ms) / 1000.0 == Math.floor(Math.abs(ms) / 1000.0)
                ? (int) (Math.abs(ms) / 1000) + "s" : String.format("%.1fs", Math.abs(ms) / 1000)) : (int) Math.abs(ms) + "ms";
        action((ms < 0 ? "-" : "+") + amount);
    }

    public void step(int ticks) {
        playing = false;
        seek(time + ticks * 50);
        action(ticks < 0 ? "Back a tick" : "Forward a tick");
    }

    public void seek(double t) {
        seekInternal(t);
        lastFrameNanos = 0;
    }

    private void seekInternal(double t) {
        t = MathHelper.clamp(t, 0, duration);
        int target = Math.max(0, index.indexAt((int) Math.floor(t)));
        world.seekTo(target);
        time = t;
        nextTickTime = (int) Math.floor(t / 50) * 50 + 50;
        tickDelta = (float) MathHelper.clamp((t - (nextTickTime - 50)) / 50.0, 0, 1);
        afterSeek();
    }

    private void afterSeek() {
        client.particleManager.setWorld(client.world);
        client.getSoundManager().stopAll();
        client.inGameHud.setTitles(null, null, -1, -1, -1);
        client.getToastManager().clear();
        rebuildChat();
        swingCursor = tracks.swings.indexAt((int) time) + 1;
        soundCursor = tracks.sounds.indexAt((int) time) + 1;
        particleCursor = tracks.blockParticles.indexAt((int) time) + 1;
        restoreCrack();
        actionBarCursor = tracks.actionBar.indexAt((int) time) + 1;
        Tracks.ActionBar message = tracks.actionBar.at((int) time);
        if (message != null && time - tracks.actionBar.time(actionBarCursor - 1) < 2000) {
            client.inGameHud.setOverlayMessage(message.text, message.tinted);
        }
        view.snap(time);
    }

    private void restoreCrack() {
        if (client.world == null) return;
        for (int i = particleCursor - 1; i >= 0; i--) {
            long[] record = tracks.blockParticles.get(i);
            if (record[0] != RecordCodec.PARTICLES_CRACK) continue;
            boolean showing = record[2] >= 0 && time - tracks.blockParticles.time(i) < 1000;
            client.world.setBlockBreakingInfo(index.selfId, BlockPos.fromLong(record[1]), showing ? (int) record[2] : -1);
            return;
        }
    }

    private void rebuildChat() {
        ChatHud chat = client.inGameHud.getChatHud();
        chat.clear(false);
        int clear = tracks.chatClears.indexAt((int) time);
        int clearedAt = clear >= 0 ? tracks.chatClears.time(clear) : Integer.MIN_VALUE;
        clearCursor = clear + 1;
        int last = tracks.chat.indexAt((int) time);
        chatCursor = last + 1;
        int ticks = client.inGameHud.getTicks();
        for (int i = Math.max(0, last - 99); i <= last; i++) {
            if (tracks.chat.time(i) <= clearedAt) continue;
            Tracks.ChatLine line = tracks.chat.get(i);
            int age = (int) ((time - tracks.chat.time(i)) / 50);
            ((ChatHudInvoker) chat).tascade$addMessage(line.text, line.messageId, ticks - age, false);
        }
    }

    public void beginScrub() {
        if (scrubbing) return;
        scrubbing = true;
        wasPlayingBeforeScrub = playing;
    }

    public void endScrub() {
        if (!scrubbing) return;
        scrubbing = false;
        playing = wasPlayingBeforeScrub;
        lastFrameNanos = 0;
    }

    public void togglePov() {
        pov = !pov;
        view.restoreSettings();
        if (!pov) screenView.reset();
        view.snap(time);
        action(pov ? "Runner's view" : "Free camera");
    }

    public boolean isPov() {
        return pov;
    }

    public void toggleRunnerSettings() {
        if (!pov) return;
        runnerSettings = !runnerSettings;
        if (!runnerSettings) view.restoreSettings();
        action(runnerSettings ? "Runner's settings on" : "Runner's settings off");
    }

    public boolean usesRunnerSettings() {
        return pov && runnerSettings;
    }

    public void toggleOverlay() {
        overlayShown = !overlayShown;
    }

    public void adjustCameraSpeed(double notches) {
        freeCamera.adjustSpeed(notches);
        action(String.format("Camera speed %.1f blocks/s", freeCamera.topSpeed()));
    }

    private void action(String what) {
        lastAction = what;
        lastActionMs = System.currentTimeMillis();
    }

    public String recentAction() {
        return System.currentTimeMillis() - lastActionMs < 1500 ? lastAction : null;
    }

    public long msSinceAction() {
        return System.currentTimeMillis() - lastActionMs;
    }

    // the free camera

    void moveFreeCamera(CameraEntity camera, double realMs) {
        freeCamera.move(client, camera, realMs);
    }

    void saveCamera() {
        if (client.player == null) return;
        cameraSaved = true;
        savedX = client.player.getX();
        savedY = client.player.getY();
        savedZ = client.player.getZ();
        savedYaw = client.player.yaw;
        savedPitch = client.player.pitch;
    }

    void restoreCamera() {
        if (cameraSaved && client.player instanceof CameraEntity) {
            ((CameraEntity) client.player).place(savedX, savedY, savedZ, savedYaw, savedPitch);
        }
        cameraSaved = false;
    }

    void restoreCameraAtRunner(int time) {
        double[] at = new double[7];
        if (index.playerAt(time, at) >= 0) {
            savedX = at[0];
            savedY = at[1];
            savedZ = at[2];
        }
        restoreCamera();
    }

    void onNewWorld() {
        view.onNewWorld();
        screenView.reset();
    }

    void onRespawn() {
        view.onNewWorld();
    }

    // the timeline

    public double runTime(double replayTime) {
        return raceStart >= 0 ? replayTime - raceStart : replayTime;
    }

    public double replayTime(double runTime) {
        return raceStart >= 0 ? runTime + raceStart : runTime;
    }

    public String formatRunTime(double replayTime) {
        double run = runTime(replayTime);
        return run < 0 ? "-" + RunLabels.time((long) -run) : RunLabels.time((long) run);
    }

    public List<Marker> markers() {
        return markers;
    }

    public List<Band> bands() {
        return bands;
    }

    private List<Marker> buildMarkers() {
        List<Marker> list = new ArrayList<>();
        for (Tracks.Marker marker : tracks.markers) {
            double at = marker.time;
            long rta = marker.extra.optLong("rta", -1);
            if (rta >= 0 && raceStart >= 0) at = raceStart + rta;
            switch (marker.type) {
                case "race_start":
                    list.add(new Marker(at, "Race start", 0xFFFFFF, true));
                    break;
                case "split":
                    list.add(new Marker(at, RunLabels.split(marker.name).getString(), 0xFFD24A, true));
                    break;
                case "goal":
                    list.add(new Marker(at, titleCase(marker.name), 0x7FD8FF, false));
                    break;
                case "opponent_split":
                    list.add(new Marker(at, opponent() + ": " + RunLabels.split(marker.name).getString(), 0xC08AFF, true));
                    break;
                case "opponent_goal":
                    list.add(new Marker(at, opponent() + ": " + titleCase(marker.name), 0xC08AFF, false));
                    break;
                case "advancement": {
                    String title = marker.name;
                    try {
                        title = net.minecraft.text.Text.Serializer.fromJson(marker.extra.optString("title", "\"\"")).getString();
                    } catch (RuntimeException ignored) {
                    }
                    list.add(new Marker(at, title, 0x7CE36B, false));
                    break;
                }
                case "death":
                    list.add(new Marker(at, "Died", 0xFF5555, true));
                    break;
                case "death_reset":
                    list.add(new Marker(at, "Death Reset", 0xFFAA00, true));
                    break;
                case "run_end":
                    list.add(new Marker(at, "Run ended", 0xFFFFFF, true));
                    break;
                default:
                    break;
            }
        }
        list.sort((a, b) -> Double.compare(a.time, b.time));
        return Collections.unmodifiableList(list);
    }

    private String opponent() {
        return meta.optString("opponentName", "Opponent");
    }

    private List<Band> buildBands() {
        List<Band> list = new ArrayList<>();
        for (int i = 0; i < index.segments.size(); i++) {
            ReplayIndex.Segment segment = index.segments.get(i);
            double start = index.time[segment.start];
            double end = i + 1 < index.segments.size() ? index.time[index.segments.get(i + 1).start] : duration;
            RegistryKey<World> dimension = segment.dimension;
            int color;
            String name;
            if (World.NETHER.equals(dimension)) {
                color = 0xB3412F;
                name = "Nether";
            } else if (World.END.equals(dimension)) {
                color = 0x8C6BB1;
                name = "End";
            } else {
                color = 0x4E9A4A;
                name = "Overworld";
            }
            list.add(new Band(start, end, color, name));
        }
        return Collections.unmodifiableList(list);
    }

    private static String titleCase(String snakeCase) {
        StringBuilder out = new StringBuilder();
        for (String word : snakeCase.split("_")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    // keys

    public static boolean onKey(int key, int action, int modifiers) {
        ReplayPlayer player = active;
        if (player == null || player.client.currentScreen != null) return false;
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) {
            return action == GLFW.GLFW_RELEASE && player.handles(key);
        }
        return player.handleKey(key, modifiers, action == GLFW.GLFW_REPEAT);
    }

    private boolean handles(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_P: case GLFW.GLFW_KEY_K: case GLFW.GLFW_KEY_J: case GLFW.GLFW_KEY_L:
            case GLFW.GLFW_KEY_LEFT: case GLFW.GLFW_KEY_RIGHT: case GLFW.GLFW_KEY_UP: case GLFW.GLFW_KEY_DOWN:
            case GLFW.GLFW_KEY_HOME: case GLFW.GLFW_KEY_END: case GLFW.GLFW_KEY_V: case GLFW.GLFW_KEY_H:
            case GLFW.GLFW_KEY_O: case GLFW.GLFW_KEY_G: case GLFW.GLFW_KEY_COMMA: case GLFW.GLFW_KEY_PERIOD:
                return true;
            case GLFW.GLFW_KEY_SPACE:
                return pov;
            default:
                return false;
        }
    }

    public static boolean swallowsF3(int key) {
        return active != null && (key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_F);
    }

    public boolean handleKey(int key, int modifiers, boolean repeat) {
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_SPACE:
                if (!pov) return false;
                // fall through: in the runner's view space pauses, like a video
            case GLFW.GLFW_KEY_P:
            case GLFW.GLFW_KEY_K:
                if (!repeat) togglePlaying();
                return true;
            case GLFW.GLFW_KEY_LEFT:
                skip(control ? -50 : shift ? -30_000 : -5_000);
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                skip(control ? 50 : shift ? 30_000 : 5_000);
                return true;
            case GLFW.GLFW_KEY_J:
                skip(-10_000);
                return true;
            case GLFW.GLFW_KEY_L:
                skip(10_000);
                return true;
            case GLFW.GLFW_KEY_COMMA:
                step(-1);
                return true;
            case GLFW.GLFW_KEY_PERIOD:
                step(1);
                return true;
            case GLFW.GLFW_KEY_UP:
                if (!repeat) faster();
                return true;
            case GLFW.GLFW_KEY_DOWN:
                if (!repeat) slower();
                return true;
            case GLFW.GLFW_KEY_HOME:
                seek(raceStart >= 0 ? raceStart : 0);
                action("Race start");
                return true;
            case GLFW.GLFW_KEY_END:
                seek(duration);
                action("End");
                return true;
            case GLFW.GLFW_KEY_V:
                if (!repeat) togglePov();
                return true;
            case GLFW.GLFW_KEY_H:
                if (!repeat) toggleOverlay();
                return true;
            case GLFW.GLFW_KEY_O:
                if (!repeat) toggleRunnerSettings();
                return true;
            case GLFW.GLFW_KEY_G:
                if (!repeat && client.currentScreen == null) client.openScreen(new ReplayControlScreen(this, true));
                return true;
            default:
                return false;
        }
    }

    public static boolean openControls() {
        ReplayPlayer player = active;
        if (player == null) return false;
        if (player.client.currentScreen == null) player.client.openScreen(new ReplayControlScreen(player, false));
        return true;
    }

    public static boolean onScroll(double amount) {
        ReplayPlayer player = active;
        if (player == null || player.client.currentScreen != null) return false;
        if (!player.pov) player.adjustCameraSpeed(amount);
        return true;
    }

    public static void handleInput() {
        ReplayPlayer player = active;
        if (player == null) return;
        while (player.client.options.keyTogglePerspective.wasPressed()) {
            if (!player.pov || player.runnerSettings) continue;
            player.client.options.perspective = (player.client.options.perspective + 1) % 3;
        }
    }
}
