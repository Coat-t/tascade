package com.coatt.tascade.modifier;

import com.coatt.tascade.Main;
import com.coatt.tascade.integration.RunGoals;
import com.coatt.tascade.mixin.access.DebugRendererAccessor;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.replay.play.ReplayPlayer;
import com.coatt.tascade.replay.record.DeathResets;
import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ModifierEffects {
  public static final String FRAGILE = "fragile";
  public static final String KEEP_INVENTORY = "keep_inventory";
  public static final String NO_JUMP = "no_jump";
  public static final String FLAMMABLE = "flammable";
  public static final String BUBBLE = "bubble";
  public static final String CURSED = "cursed";
  public static final String NO_F3 = "no_f3";
  public static final String ONE_LEGGED = "one_legged";
  public static final String EASY_MODE = "easy_mode";
  public static final String SMALL_POCKETS = "small_pockets";
  public static final String DELUSIONAL = "delusional";

  private static final int POCKET_SLOTS = 9;
  private static final int MAIN_END = 36;

  public static final int DELUSIONAL_HEALTH = 4;
  public static final int DELUSIONAL_FOOD = 4;

  private static final double FRAGILE_MAX_HEALTH = 10;
  private static final UUID FRAGILE_MODIFIER = UUID.fromString("5e1f0c9a-7a0b-4d4e-9c39-3f1b6f2a7d10");

  private static final long BUBBLE_EVERY_MS = 16_000;
  private static final long BUBBLE_FOR_MS = 8_000;
  private static final long CURSED_EVERY_MS = 15_000;
  private static final long CURSED_FOR_MS = 10_000;

  // listed explicitly so the order (and which cycle gets which effect) can't shift
  private static final StatusEffect[] CURSED_POOL = {
      StatusEffects.SPEED, StatusEffects.SLOWNESS, StatusEffects.HASTE, StatusEffects.MINING_FATIGUE,
      StatusEffects.STRENGTH, StatusEffects.JUMP_BOOST, StatusEffects.NAUSEA, StatusEffects.REGENERATION,
      StatusEffects.RESISTANCE, StatusEffects.FIRE_RESISTANCE, StatusEffects.WATER_BREATHING,
      StatusEffects.INVISIBILITY, StatusEffects.BLINDNESS, StatusEffects.NIGHT_VISION, StatusEffects.HUNGER,
      StatusEffects.WEAKNESS, StatusEffects.POISON, StatusEffects.WITHER, StatusEffects.HEALTH_BOOST,
      StatusEffects.ABSORPTION, StatusEffects.GLOWING, StatusEffects.LEVITATION, StatusEffects.LUCK,
      StatusEffects.UNLUCK, StatusEffects.SLOW_FALLING, StatusEffects.CONDUIT_POWER,
      StatusEffects.DOLPHINS_GRACE, StatusEffects.BAD_OMEN, StatusEffects.HERO_OF_THE_VILLAGE
  };

  private static final int[] F3_COMBO_KEYS = {
      GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_G,
      GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_P, GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_T,
      GLFW.GLFW_KEY_F4
  };

  private static final long TRAIL_MS = 10_000;
  private static final long SPLIT_BEFORE_MS = 1_000;
  private static final long SPLIT_AFTER_MS = 3_000;
  private static final long AIRBORNE_PENALTY_MS = 1_000;

  private static final ArrayDeque<Sample> trail = new ArrayDeque<>();
  private static final ConcurrentLinkedQueue<Split> pendingSplits = new ConcurrentLinkedQueue<>();

  private ModifierEffects() {}

  public static boolean active(String id) {
    return RunState.isRunning() && RunState.modifiers.contains(id) && WorldSeeds.active() && !ReplayPlayer.isActive();
  }

  public interface Charges {
    Set<String> tascade$usedCharges();

    boolean tascade$keepsInventory();

    void tascade$setKeepsInventory(boolean keeps);
  }

  private static boolean used(ServerPlayerEntity player, String id) {
    return ((Charges) player).tascade$usedCharges().contains(id);
  }

  private static void use(ServerPlayerEntity player, String id) {
    ((Charges) player).tascade$usedCharges().add(id);
    mirrorCharges(player);
  }

  private static boolean isDeathReset(ServerPlayerEntity player) {
    SplitSpawn spot = ((SplitSpawn.Holder) player).tascade$splitSpawn();
    if (spot != null && spot.isRespawnPointOf(player)) return false;
    return DeathResets.hasActiveRespawnPoint(player);
  }

  private static volatile Set<String> hostUsedCharges = Collections.emptySet();
  private static volatile MinecraftServer hostChargesServer;

  private static volatile Set<String> carriedCharges;

  public static void carryChargesOver() {
    carriedCharges = MinecraftClient.getInstance().getServer() == hostChargesServer
        ? new HashSet<>(hostUsedCharges) : null;
  }

  public static void mirrorCharges(ServerPlayerEntity player) {
    if (!player.server.isHost(player.getGameProfile())) return;
    Set<String> used = ((Charges) player).tascade$usedCharges();
    Set<String> carried = carriedCharges;
    if (carried != null) {
      carriedCharges = null;
      if (RunState.worldReset) used.addAll(carried);
    }
    if (hostChargesServer != player.server || !hostUsedCharges.equals(used)) {
      hostUsedCharges = Collections.unmodifiableSet(new HashSet<>(used));
      hostChargesServer = player.server;
    }
  }

  public static Boolean charge(String id) {
    if (!EASY_MODE.equals(id) && !KEEP_INVENTORY.equals(id)) return null;
    MinecraftServer server = MinecraftClient.getInstance().getServer();
    if (!active(id) || server == null || server != hostChargesServer) return null;
    return !hostUsedCharges.contains(id);
  }

  public static void died(ServerPlayerEntity player) {
    boolean keeps = active(KEEP_INVENTORY) && !used(player, KEEP_INVENTORY) && !isDeathReset(player);
    if (keeps) use(player, KEEP_INVENTORY);
    ((Charges) player).tascade$setKeepsInventory(keeps);
  }

  public static boolean keepInventory(PlayerEntity player, boolean gamerule) {
    return gamerule || (player instanceof ServerPlayerEntity && ((Charges) player).tascade$keepsInventory());
  }

  public static int pocketSize(int size) {
    return active(SMALL_POCKETS) ? Math.min(size, POCKET_SLOTS) : size;
  }

  public static boolean pocketLocked(Inventory inventory, int index) {
    return inventory instanceof PlayerInventory && index >= POCKET_SLOTS && index < MAIN_END && active(SMALL_POCKETS);
  }

  public static boolean delusional() {
    return active(DELUSIONAL);
  }

  // spectators keep the key
  public static boolean noJump(PlayerEntity player) {
    return !player.isSpectator() && active(NO_JUMP);
  }

  public static StatusEffectInstance incoming(LivingEntity entity, StatusEffectInstance effect) {
    if (effect.getEffectType() != StatusEffects.FIRE_RESISTANCE || !(entity instanceof ServerPlayerEntity)
        || !active(FLAMMABLE)) {
      return effect;
    }
    return new StatusEffectInstance(effect.getEffectType(), Math.max(1, effect.getDuration() / 4),
        effect.getAmplifier(), effect.isAmbient(), effect.shouldShowParticles(), effect.shouldShowIcon());
  }

  public static void fragile(ServerPlayerEntity player) {
    EntityAttributeInstance maxHealth = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
    if (maxHealth == null) return;
    EntityAttributeModifier ours = maxHealth.getModifier(FRAGILE_MODIFIER);
    double current = ours == null ? 0 : ours.getValue();
    double wanted = 0;
    if (active(FRAGILE)) {
      // vanilla max-health modifiers are all additions, so this is the value without ours
      double without = maxHealth.getValue() - current;
      wanted = Math.min(0, FRAGILE_MAX_HEALTH - without);
    }
    if (wanted != current) {
      if (ours != null) maxHealth.removeModifier(FRAGILE_MODIFIER);
      if (wanted != 0) {
        maxHealth.addTemporaryModifier(new EntityAttributeModifier(FRAGILE_MODIFIER, "Tascade fragile", wanted,
            EntityAttributeModifier.Operation.ADDITION));
      }
    }
    if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
  }

  public static long bubble(ServerPlayerEntity player, long given) {
    return pulse(player, BUBBLE, given, BUBBLE_EVERY_MS, BUBBLE_FOR_MS, StatusEffects.LEVITATION, 0);
  }

  public static long cursed(ServerPlayerEntity player, long given) {
    if (!active(CURSED)) return given;
    long cycle = (System.currentTimeMillis() - RunState.raceStartedAtMs) / CURSED_EVERY_MS;
    long seed = WorldSeeds.combinedSeed(player.server);
    StatusEffect effect = CURSED_POOL[(int) Math.floorMod(mix(seed + cycle * 0x9E3779B97F4A7C15L), (long) CURSED_POOL.length)];
    return pulse(player, CURSED, given, CURSED_EVERY_MS, CURSED_FOR_MS, effect, 0);
  }

  // timed by the race clock rather than ticks, so it lines up across rejoins
  private static long pulse(ServerPlayerEntity player, String id, long given, long everyMs, long forMs,
                            StatusEffect effect, int amplifier) {
    long started = RunState.raceStartedAtMs;
    if (!active(id) || started <= 0 || RunState.waitingForRaceStart || player.isSpectator() || !player.isAlive()) {
      return given;
    }
    long elapsed = System.currentTimeMillis() - started;
    long cycle = elapsed / everyMs;
    long left = forMs - elapsed % everyMs;
    if (elapsed < 0 || cycle == 0 || cycle == given || left <= 0) return given;
    player.addStatusEffect(new StatusEffectInstance(effect, (int) ((left + 49) / 50), amplifier));
    return cycle;
  }

  public static boolean noF3() {
    return active(NO_F3);
  }

  public static boolean swallowsF3(int key) {
    for (int combo : F3_COMBO_KEYS) {
      if (combo == key) return true;
    }
    return false;
  }

  public static void noF3Tick(MinecraftClient client) {
    if (!noF3()) return;
    GameOptions options = client.options;
    options.debugEnabled = false;
    options.debugProfilerEnabled = false;
    options.debugTpsEnabled = false;
    EntityRenderDispatcher entities = client.getEntityRenderManager();
    if (entities != null && entities.shouldRenderHitboxes()) entities.setRenderHitboxes(false);
    if (client.debugRenderer != null && ((DebugRendererAccessor) client.debugRenderer).tascade$showsChunkBorder()) {
      client.debugRenderer.toggleShowChunkBorder();
    }
  }

  public static boolean oneLegged(PlayerEntity player) {
    return active(ONE_LEGGED) && !player.isSpectator() && !player.abilities.flying && !player.hasVehicle()
        && !player.isFallFlying() && !player.isClimbing() && !player.isTouchingWater() && !player.isInLava();
  }

  // mirrors LivingEntity#tickMovement's jump check for this tick
  public static boolean hopOnly(PlayerEntity player, boolean jumping, int jumpingCooldown) {
    if (!player.isOnGround() || !oneLegged(player)) return false;
    boolean jumpsNow = jumping && jumpingCooldown <= 1;
    return !jumpsNow;
  }

  public static void newWorld() {
    trail.clear();
    pendingSplits.clear();
  }

  public static void splitReached(String name, long rta) {
    if (!active(EASY_MODE) || "kill_ender_dragon".equals(name)) return;
    long now = Main.runRta();
    if (now < 0) return;
    pendingSplits.add(new Split(name, System.currentTimeMillis() - Math.max(0, now - rta)));
  }

  public static void easyMode(ServerPlayerEntity player) {
    if (!active(EASY_MODE)) {
      trail.clear();
      pendingSplits.clear();
      return;
    }
    if (!player.server.isHost(player.getGameProfile())) return;
    if (used(player, EASY_MODE)) {
      trail.clear();
      pendingSplits.clear();
      return;
    }
    long now = System.currentTimeMillis();
    if (player.isAlive() && !player.isSpectator()) {
      trail.addLast(new Sample(now, player.getServerWorld().getRegistryKey(), player.getBlockPos(),
          player.yaw, player.pitch, player.isOnGround()));
    }
    while (!trail.isEmpty() && trail.peekFirst().at < now - TRAIL_MS) trail.removeFirst();

    for (Iterator<Split> it = pendingSplits.iterator(); it.hasNext(); ) {
      Split split = it.next();
      Sample at = whereReached(split);
      if (at != null) {
        player.setSpawnPoint(at.dimension, at.pos, true, false);
        ((SplitSpawn.Holder) player).tascade$setSplitSpawn(new SplitSpawn(at.dimension, at.pos, at.yaw, at.pitch));
        it.remove();
      } else if (now > split.at + SPLIT_AFTER_MS) {
        it.remove();
      }
    }
  }

  private static Sample whereReached(Split split) {
    RegistryKey<World> dimension = dimensionOf(split.name);
    Sample best = null;
    long bestScore = Long.MAX_VALUE;
    for (Sample sample : trail) {
      if (sample.at < split.at - SPLIT_BEFORE_MS || sample.at > split.at + SPLIT_AFTER_MS) continue;
      if (dimension != null && sample.dimension != dimension) continue;
      long score = Math.abs(sample.at - split.at) + (sample.onGround ? 0 : AIRBORNE_PENALTY_MS);
      if (score < bestScore) {
        best = sample;
        bestScore = score;
      }
    }
    return best;
  }

  private static RegistryKey<World> dimensionOf(String split) {
    switch (split) {
      case "enter_nether":
      case "enter_bastion":
      case "enter_fortress":
        return World.NETHER;
      case RunGoals.FINDING_STRONGHOLD:
      case "enter_stronghold":
        return World.OVERWORLD;
      case "enter_end":
        return World.END;
      default:
        return null;
    }
  }

  // vanilla's forced-spawn check only needs two free blocks, so it'd allow lava, void or portals
  public static Respawn planRespawn(ServerPlayerEntity player, boolean alive) {
    SplitSpawn spot = ((SplitSpawn.Holder) player).tascade$splitSpawn();
    if (spot == null) return null;
    boolean atSpot = spot.isRespawnPointOf(player);
    if (alive || !active(EASY_MODE) || used(player, EASY_MODE)) {
      return atSpot ? new Respawn(spot, Optional.empty(), false) : null;
    }
    if (!atSpot) {
      if (DeathResets.hasActiveRespawnPoint(player)) return null;
      // read by respawnPlayer right after this
      player.setSpawnPoint(spot.dimension, spot.pos, true, false);
    }
    ServerWorld world = player.server.getWorld(spot.dimension);
    Optional<Vec3d> at = world != null ? SplitSpawn.findStandingSpot(world, spot.pos) : Optional.empty();
    if (at.isPresent()) use(player, EASY_MODE);
    return new Respawn(spot, at, !at.isPresent());
  }

  public static void respawned(ServerPlayerEntity respawned, Respawn plan) {
    if (!plan.blocked) return;
    SplitSpawn spot = plan.spot;
    respawned.setSpawnPoint(spot.dimension, spot.pos, true, false);
    ((SplitSpawn.Holder) respawned).tascade$setSplitSpawn(spot);
    Text message = new LiteralText("Easy Mode: there's nowhere safe to stand at your latest split's spot, so you're back at world spawn.")
        .formatted(Formatting.GRAY);
    respawned.sendSystemMessage(message, Util.NIL_UUID);
  }

  public static final class Respawn {
    public final SplitSpawn spot;
    public final Optional<Vec3d> at;
    public final boolean blocked;

    Respawn(SplitSpawn spot, Optional<Vec3d> at, boolean blocked) {
      this.spot = spot;
      this.at = at;
      this.blocked = blocked;
    }
  }

  private static final class Sample {
    final long at;
    final RegistryKey<World> dimension;
    final BlockPos pos;
    final float yaw;
    final float pitch;
    final boolean onGround;

    Sample(long at, RegistryKey<World> dimension, BlockPos pos, float yaw, float pitch, boolean onGround) {
      this.at = at;
      this.dimension = dimension;
      this.pos = pos;
      this.yaw = yaw;
      this.pitch = pitch;
      this.onGround = onGround;
    }
  }

  private static final class Split {
    final String name;
    final long at;

    Split(String name, long at) {
      this.name = name;
      this.at = at;
    }
  }

  // SplitMix64 finalizer
  private static long mix(long z) {
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }
}
