package com.coatt.tascade.replay.play;

import com.coatt.tascade.mixin.access.CameraInvoker;
import com.coatt.tascade.mixin.access.DebugRendererAccessor;
import com.coatt.tascade.mixin.access.EntityInvoker;
import com.coatt.tascade.mixin.access.LivingEntityAccessor;
import com.coatt.tascade.mixin.access.MinecraftClientAccessor;
import com.coatt.tascade.mixin.access.PlayerEntityAccessor;
import com.coatt.tascade.replay.RecordCodec;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.options.AttackIndicator;
import net.minecraft.client.options.CloudRenderMode;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.ParticlesOption;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.profiler.ProfileResult;
import net.minecraft.util.profiler.ProfilerTiming;
import net.minecraft.world.GameMode;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class RunnerView {
    private final ReplayPlayer player;
    private final MinecraftClient client;
    private final Tracks tracks;
    private final GameProfile profile;
    private final int selfId;

    OtherClientPlayerEntity runner;
    private final double[] sample = new double[7];
    private final double[] camera = new double[6];
    private boolean cameraValid;
    private boolean fovValid;
    private int lastGameMode = -1;
    private int lastPose = -1;
    private Saved saved;
    private JSONObject appliedSettings;

    // older recordings stamped camera samples about a tick late
    private final int runnerDelay;

    RunnerView(ReplayPlayer player, MinecraftClient client, Tracks tracks, GameProfile profile, int selfId) {
        this.player = player;
        this.runnerDelay = player.meta.optInt("cameraTiming", 1) >= 2 ? 0 : 50;
        this.client = client;
        this.tracks = tracks;
        this.profile = profile;
        this.selfId = selfId;
    }

    void onNewWorld() {
        runner = null;
        lastGameMode = -1;
        lastPose = -1;
    }

    private CameraEntity camera() {
        return client.player instanceof CameraEntity ? (CameraEntity) client.player : null;
    }

    // the runner's body

    private void ensureRunner(double time) {
        ClientWorld world = client.world;
        if (world == null || !tracks.hasPlayer()) return;
        if (runner != null && !runner.removed && runner.world == world && world.getEntityById(selfId) == runner) return;
        runner = new OtherClientPlayerEntity(world, profile);
        runner.setEntityId(selfId);
        snapRunner(time);
        world.addPlayer(selfId, runner);
        applyModelParts(runner, time);
    }

    private void applyModelParts(PlayerEntity entity, double time) {
        JSONObject settings = tracks.settings.at((int) time);
        int parts = settings != null ? settings.optInt("modelParts", 0x7F) : 0x7F;
        entity.getDataTracker().set(PlayerEntityAccessor.tascade$modelParts(), (byte) parts);
    }

    void beforeWorldTick(int tickTime) {
        ensureRunner(tickTime);
        if (runner != null) {
            applyModelParts(runner, tickTime);
            int s = player.index.playerAt(tickTime + 50 - runnerDelay, sample);
            if (s >= 0) {
                runner.updateTrackedPositionAndAngles(sample[0], sample[1], sample[2], (float) sample[3], (float) sample[4], 1, true);
                runner.updateTrackedHeadRotation((float) sample[5], 1);
                applyRunnerState(s, tickTime);
            }
        }
        mirrorToCamera(tickTime);
    }

    // vehicles here follow late server updates, so the runner's vehicle is moved to seat them, bodies in unloaded chunks aren't ticked, so they're moved here too
    void afterEntitiesTick(int tickTime) {
        if (runner == null || player.index.playerAt(tickTime + 50 - runnerDelay, sample) < 0) return;
        Entity vehicle = runner.getVehicle();
        if (vehicle != null && !vehicle.removed && vehicle.world == client.world) {
            carryRunner(vehicle, false);
        } else if (!runner.updateNeeded) {
            runner.updatePosition(sample[0], sample[1], sample[2]);
            runner.yaw = (float) sample[3];
            runner.pitch = (float) sample[4];
            runner.prevHeadYaw = runner.headYaw;
            runner.prevBodyYaw = runner.bodyYaw;
            runner.headYaw = (float) sample[5];
            runner.bodyYaw = (float) sample[6];
        }
    }

    private void carryRunner(Entity vehicle, boolean snap) {
        if (vehicle instanceof BoatEntity) {
            // a boat turns its rider's body its own way
            vehicle.yaw += MathHelper.wrapDegrees((float) sample[6] - vehicle.yaw);
            if (snap) vehicle.prevYaw = vehicle.yaw;
        }
        vehicle.updatePassengerPosition(runner);
        double x = sample[0] - (runner.getX() - vehicle.getX());
        double y = sample[1] - (runner.getY() - vehicle.getY());
        double z = sample[2] - (runner.getZ() - vehicle.getZ());
        if (snap) vehicle.refreshPositionAndAngles(x, y, z, vehicle.yaw, vehicle.pitch);
        else vehicle.updatePosition(x, y, z);
        for (Entity passenger : vehicle.getPassengerList()) vehicle.updatePassengerPosition(passenger);
    }

    void snap(double time) {
        ensureRunner(time);
        if (runner != null) snapRunner(time);
        mirrorToCamera((int) time);
        frame(0);
    }

    private void snapRunner(double time) {
        int s = player.index.playerAt(time - runnerDelay, sample);
        if (s < 0) return;
        float yaw = (float) sample[3];
        float pitch = (float) sample[4];
        runner.updateTrackedPosition(sample[0], sample[1], sample[2]);
        runner.refreshPositionAndAngles(sample[0], sample[1], sample[2], yaw, pitch);
        runner.updateTrackedPositionAndAngles(sample[0], sample[1], sample[2], yaw, pitch, 0, false);
        runner.headYaw = runner.prevHeadYaw = (float) sample[5];
        runner.bodyYaw = runner.prevBodyYaw = (float) sample[6];
        ((LivingEntityAccessor) runner).tascade$setHeadTrackingIncrements(0);
        Entity vehicle = runner.getVehicle();
        if (vehicle != null && !vehicle.removed && vehicle.world == client.world) carryRunner(vehicle, true);
        applyRunnerState(s, (int) time);
    }

    private void applyRunnerState(int s, int time) {
        int flags = tracks.playerFlags(s);
        runner.setSneaking((flags & RecordCodec.FLAG_SNEAKING) != 0);
        runner.setSprinting((flags & RecordCodec.FLAG_SPRINTING) != 0);
        runner.setSwimming((flags & RecordCodec.FLAG_SWIMMING) != 0);
        runner.setInvisible((flags & RecordCodec.FLAG_INVISIBLE) != 0);
        ((EntityInvoker) runner).tascade$setFlag(7, (flags & RecordCodec.FLAG_FALL_FLYING) != 0);
        int pose = tracks.playerPose(s);
        if (pose >= 0 && pose < EntityPose.values().length) runner.setPose(EntityPose.values()[pose]);
        byte living = (byte) (((flags & RecordCodec.FLAG_USING_ITEM) != 0 ? 1 : 0)
                | ((flags & RecordCodec.FLAG_OFFHAND_ACTIVE) != 0 ? 2 : 0));
        runner.getDataTracker().set(LivingEntityAccessor.tascade$livingFlags(), living);

        ItemStack[] inventory = tracks.inventory.at(time);
        RecordCodec.Vitals vitals = tracks.vitals.at(time);
        if (inventory != null) copyInventory(inventory, runner.inventory);
        if (vitals != null) {
            runner.inventory.selectedSlot = clampSlot(vitals.selectedSlot);
            ((EntityInvoker) runner).tascade$setFlag(0, vitals.onFire);
            setEffects(runner, vitals.effects);
        }
    }

    void swing(int hand) {
        Hand which = hand == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND;
        if (runner != null) runner.swingHand(which);
        CameraEntity cam = camera();
        if (cam != null && player.pov) cam.swingHand(which);
    }

    boolean hides(Entity entity) {
        return entity == runner && player.pov && client.options.perspective == 0;
    }

    // the camera: the runner's state, in POV

    private void mirrorToCamera(int time) {
        CameraEntity cam = camera();
        if (cam == null) return;
        cam.pov = player.pov;
        applyModelParts(cam, time);
        ItemStack[] inventory = tracks.inventory.at(time);
        if (inventory != null) copyInventory(inventory, cam.inventory);
        RecordCodec.Vitals v = tracks.vitals.at(time);
        if (v == null) v = new RecordCodec.Vitals();
        cam.inventory.selectedSlot = clampSlot(v.selectedSlot);

        if (!player.pov) {
            neutral(cam);
            setGameMode(GameMode.SPECTATOR.getId());
            return;
        }
        EntityAttributeInstance maxHealth = cam.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(Math.max(1, v.maxHealth));
        cam.setHealth(v.dead ? 0 : Math.max(v.health, 0.0001F));
        cam.setAbsorptionAmount(v.absorption);
        cam.getHungerManager().setFoodLevel(v.food);
        cam.getHungerManager().setSaturationLevelClient(v.saturation);
        EntityAttributeInstance armor = cam.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (armor != null) armor.setBaseValue(v.armor);
        cam.setAir(v.air);
        cam.experienceLevel = v.xpLevel;
        cam.experienceProgress = v.xpProgress;
        cam.totalExperience = v.totalXp;
        cam.hurtTime = v.hurtTime;
        cam.maxHurtTime = Math.max(1, v.maxHurtTime);
        cam.knockbackVelocity = v.knockbackVelocity;
        cam.deathTime = v.deathTime;
        cam.lastNauseaStrength = v.lastNausea;
        cam.nextNauseaStrength = v.nausea;
        cam.runnerOnFire = v.onFire;
        cam.runnerItemUseTimeLeft = v.itemUseTimeLeft;
        cam.runnerAttackCooldown = v.attackCooldown;
        setEffects(cam, v.effects);
        int s = tracks.player(time, sample);
        if (s >= 0) {
            int flags = tracks.playerFlags(s);
            cam.runnerSneaking = (flags & RecordCodec.FLAG_SNEAKING) != 0;
            cam.runnerUsingItem = (flags & RecordCodec.FLAG_USING_ITEM) != 0;
            cam.runnerActiveHand = (flags & RecordCodec.FLAG_OFFHAND_ACTIVE) != 0 ? Hand.OFF_HAND : Hand.MAIN_HAND;
            int pose = tracks.playerPose(s);
            if (pose != lastPose && pose >= 0 && pose < EntityPose.values().length) {
                lastPose = pose;
                cam.setPose(EntityPose.values()[pose]);
            }
        }
        setGameMode(v.gameMode);
    }

    private void neutral(CameraEntity cam) {
        cam.setHealth(cam.getMaxHealth());
        cam.hurtTime = 0;
        cam.deathTime = 0;
        cam.lastNauseaStrength = 0;
        cam.nextNauseaStrength = 0;
        cam.runnerOnFire = false;
        cam.runnerUsingItem = false;
        cam.runnerSneaking = false;
        cam.getActiveStatusEffects().clear();
        if (lastPose != EntityPose.STANDING.ordinal()) {
            lastPose = EntityPose.STANDING.ordinal();
            cam.setPose(EntityPose.STANDING);
        }
    }

    private void setGameMode(int id) {
        if (id == lastGameMode || client.interactionManager == null) return;
        lastGameMode = id;
        client.interactionManager.setGameMode(GameMode.byId(id, GameMode.SURVIVAL));
    }

    // copies: ticking counts effects down, but the recorded ones must stay as recorded
    private static void setEffects(net.minecraft.entity.LivingEntity entity, List<StatusEffectInstance> effects) {
        entity.getActiveStatusEffects().clear();
        for (StatusEffectInstance effect : effects) {
            entity.getActiveStatusEffects().put(effect.getEffectType(), new StatusEffectInstance(effect));
        }
    }

    private static void copyInventory(ItemStack[] slots, PlayerInventory inventory) {
        for (int i = 0; i < 36; i++) inventory.main.set(i, slots[i]);
        for (int i = 0; i < 4; i++) inventory.armor.set(i, slots[36 + i]);
        inventory.offHand.set(0, slots[40]);
    }

    private static int clampSlot(int slot) {
        return Math.max(0, Math.min(8, slot));
    }

    void frame(double realMs) {
        CameraEntity cam = camera();
        if (cam == null) return;
        double time = player.time;
        if (player.pov) {
            if (player.runnerSettings) {
                applySettings(tracks.settings.at((int) time));
                if (showsRunnerSettings()) client.options.perspective = recordedPerspective(time);
            } else {
                restoreSettings();
            }
            fovValid = tracks.camera(time, camera);
            // while waiting to be placed, the recorded camera was at world spawn
            boolean recorded = fovValid && player.index.arrivingAt(time) == null;
            int perspective = recordedPerspective(time);
            cameraValid = recorded && client.options.perspective == perspective;
            float yaw = recorded ? (float) camera[3] : 0;
            float pitch = recorded ? (float) camera[4] : 0;
            if (perspective == 2) {
                yaw -= 180;
                pitch = -pitch;
            }
            if (runner != null && runner.world == client.world) {
                float delta = player.frameTickDelta();
                if (!recorded) {
                    yaw = runner.yaw;
                    pitch = runner.pitch;
                }
                cam.place(MathHelper.lerp(delta, runner.lastRenderX, runner.getX()),
                        MathHelper.lerp(delta, runner.lastRenderY, runner.getY()),
                        MathHelper.lerp(delta, runner.lastRenderZ, runner.getZ()), yaw, pitch);
            } else if (player.index.playerAt(time - runnerDelay, sample) >= 0) {
                cam.place(sample[0], sample[1], sample[2], recorded ? yaw : (float) sample[3], recorded ? pitch : (float) sample[4]);
            }
        } else {
            fovValid = false;
            cameraValid = false;
            restoreSettings();
            client.options.perspective = 0;
            player.moveFreeCamera(cam, realMs);
        }
    }

    void overrideCamera(Camera camera) {
        if (!cameraValid || !player.pov) return;
        ((CameraInvoker) camera).tascade$setPos(this.camera[0], this.camera[1], this.camera[2]);
        ((CameraInvoker) camera).tascade$setRotation((float) this.camera[3], (float) this.camera[4]);
    }

    double overrideFov(double fov) {
        return fovValid && player.pov ? this.camera[5] : fov;
    }

    private int recordedPerspective(double time) {
        int fromCamera = tracks.cameraPerspective(time);
        if (fromCamera >= 0) return fromCamera;
        JSONObject settings = tracks.settings.at((int) time);
        return settings != null ? settings.optInt("perspective", 0) : 0;
    }

    // the runner's settings

    private static final class Saved {
        int renderDistance;
        float entityDistance;
        double brightness;
        boolean viewBobbing;
        int perspective;
        AttackIndicator attackIndicator;
        ParticlesOption particles;
        boolean entityShadows;
        CloudRenderMode clouds;
        boolean debug;
        boolean pieChart;
        boolean tpsGraph;
        boolean advancedTooltips;
        boolean heldItemTooltips;
        boolean hitboxes;
        boolean chunkBorders;
    }

    private void applySettings(JSONObject s) {
        if (s == null || s == appliedSettings) return;
        GameOptions o = client.options;
        if (saved == null) {
            saved = new Saved();
            saved.renderDistance = o.viewDistance;
            saved.entityDistance = o.entityDistanceScaling;
            saved.brightness = o.gamma;
            saved.viewBobbing = o.bobView;
            saved.perspective = o.perspective;
            saved.attackIndicator = o.attackIndicator;
            saved.particles = o.particles;
            saved.entityShadows = o.entityShadows;
            saved.clouds = o.cloudRenderMode;
            saved.debug = o.debugEnabled;
            saved.pieChart = o.debugProfilerEnabled;
            saved.tpsGraph = o.debugTpsEnabled;
            saved.advancedTooltips = o.advancedItemTooltips;
            saved.heldItemTooltips = o.heldItemTooltips;
            saved.hitboxes = client.getEntityRenderManager().shouldRenderHitboxes();
            saved.chunkBorders = ((DebugRendererAccessor) client.debugRenderer).tascade$showsChunkBorder();
        }
        appliedSettings = s;
        setRenderDistance(s.optInt("renderDistance", o.viewDistance));
        o.entityDistanceScaling = (float) s.optDouble("entityDistance", o.entityDistanceScaling);
        o.gamma = s.optDouble("brightness", o.gamma);
        o.bobView = s.optBoolean("viewBobbing", o.bobView);
        o.perspective = s.optInt("perspective", 0);
        o.attackIndicator = byOrdinal(AttackIndicator.values(), s.optInt("attackIndicator", -1), o.attackIndicator);
        o.particles = byOrdinal(ParticlesOption.values(), s.optInt("particles", -1), o.particles);
        o.entityShadows = s.optBoolean("entityShadows", o.entityShadows);
        o.cloudRenderMode = byOrdinal(CloudRenderMode.values(), s.optInt("clouds", -1), o.cloudRenderMode);
        o.debugEnabled = s.optBoolean("debug", false);
        o.debugProfilerEnabled = false;
        o.debugTpsEnabled = s.optBoolean("tpsGraph", false);
        o.advancedItemTooltips = s.optBoolean("advancedTooltips", o.advancedItemTooltips);
        o.heldItemTooltips = s.optBoolean("heldItemTooltips", o.heldItemTooltips);
        client.getEntityRenderManager().setRenderHitboxes(s.optBoolean("hitboxes", false));
        setChunkBorders(s.optBoolean("chunkBorders", false));
    }

    boolean showsRunnerSettings() {
        return saved != null;
    }

    void restoreSettings() {
        appliedSettings = null;
        if (saved == null) return;
        Saved s = saved;
        saved = null;
        GameOptions o = client.options;
        setRenderDistance(s.renderDistance);
        o.entityDistanceScaling = s.entityDistance;
        o.gamma = s.brightness;
        o.bobView = s.viewBobbing;
        o.perspective = s.perspective;
        o.attackIndicator = s.attackIndicator;
        o.particles = s.particles;
        o.entityShadows = s.entityShadows;
        o.cloudRenderMode = s.clouds;
        o.debugEnabled = s.debug;
        o.debugProfilerEnabled = s.pieChart;
        o.debugTpsEnabled = s.tpsGraph;
        o.advancedItemTooltips = s.advancedTooltips;
        o.heldItemTooltips = s.heldItemTooltips;
        client.getEntityRenderManager().setRenderHitboxes(s.hitboxes);
        setChunkBorders(s.chunkBorders);
    }

    private void setRenderDistance(int distance) {
        if (distance < 2 || client.options.viewDistance == distance) return;
        client.options.viewDistance = distance;
        client.worldRenderer.scheduleTerrainUpdate();
    }

    private void setChunkBorders(boolean on) {
        if (((DebugRendererAccessor) client.debugRenderer).tascade$showsChunkBorder() != on) {
            client.debugRenderer.toggleShowChunkBorder();
        }
    }

    private static <E> E byOrdinal(E[] values, int ordinal, E fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }

    // the runner's pie chart

    void drawPieChart() {
        if (!player.pov || !player.runnerSettings || client.options.hudHidden) return;
        Tracks.PieChart pie = tracks.pieCharts.at((int) player.time);
        if (pie == null || !pie.shown || pie.timings.isEmpty()) return;
        List<ProfilerTiming> timings = pie.timings;
        ProfileResult recorded = new ProfileResult() {
            @Override
            public List<ProfilerTiming> getTimings(String parentPath) {
                return new ArrayList<>(timings);
            }

            @Override
            public boolean save(File file) {
                return false;
            }

            @Override
            public long getStartTime() {
                return 0;
            }

            @Override
            public int getStartTick() {
                return 0;
            }

            @Override
            public long getEndTime() {
                return 0;
            }

            @Override
            public int getEndTick() {
                return 0;
            }
        };
        RenderSystem.pushMatrix();
        ((MinecraftClientAccessor) client).tascade$drawProfilerResults(new MatrixStack(), recorded);
        RenderSystem.popMatrix();
    }
}
