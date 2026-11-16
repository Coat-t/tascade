package com.coatt.tascade.mixin;

import com.coatt.tascade.modifier.ModifierEffects;
import com.coatt.tascade.modifier.SplitSpawn;
import com.coatt.tascade.replay.record.DeathResets;
import com.coatt.tascade.world.PortalBuilder;
import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

// standardized Nether entry: unlinked overworld -> Nether trips land at the seed's fixed portal
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin implements SplitSpawn.Holder, ModifierEffects.Charges {
  @Unique private static final String USED_CHARGES_TAG = "TascadeUsedModifiers";
  @Unique private static final String KEEPS_INVENTORY_TAG = "TascadeKeepsInventory";

  @Unique private RegistryKey<World> tascade$from;
  @Unique private BlockPos tascade$fromPos;
  @Unique private long tascade$bubbleCycle = -1;
  @Unique private long tascade$cursedCycle = -1;
  @Unique private SplitSpawn tascade$splitSpawn;
  @Unique private final Set<String> tascade$usedCharges = new HashSet<>();
  @Unique private boolean tascade$keepsInventory;

  @Override
  public Set<String> tascade$usedCharges() {
    return this.tascade$usedCharges;
  }

  @Override
  public boolean tascade$keepsInventory() {
    return this.tascade$keepsInventory;
  }

  @Override
  public void tascade$setKeepsInventory(boolean keeps) {
    this.tascade$keepsInventory = keeps;
  }

  @Override
  public SplitSpawn tascade$splitSpawn() {
    return this.tascade$splitSpawn;
  }

  @Override
  public void tascade$setSplitSpawn(SplitSpawn spot) {
    this.tascade$splitSpawn = spot;
  }

  @Inject(at = @At("TAIL"), method = "writeCustomDataToTag")
  private void tascade$saveSplitSpawn(CompoundTag tag, CallbackInfo ci) {
    if (this.tascade$splitSpawn != null) tag.put(SplitSpawn.TAG, this.tascade$splitSpawn.toTag());
    if (!this.tascade$usedCharges.isEmpty()) {
      ListTag used = new ListTag();
      for (String id : this.tascade$usedCharges) used.add(StringTag.of(id));
      tag.put(USED_CHARGES_TAG, used);
    }
    if (this.tascade$keepsInventory) tag.putBoolean(KEEPS_INVENTORY_TAG, true);
  }

  @Inject(at = @At("TAIL"), method = "readCustomDataFromTag")
  private void tascade$loadSplitSpawn(CompoundTag tag, CallbackInfo ci) {
    this.tascade$splitSpawn = tag.contains(SplitSpawn.TAG, 10) ? SplitSpawn.fromTag(tag.getCompound(SplitSpawn.TAG)) : null;
    this.tascade$usedCharges.clear();
    ListTag used = tag.getList(USED_CHARGES_TAG, 8);
    for (int i = 0; i < used.size(); i++) this.tascade$usedCharges.add(used.getString(i));
    this.tascade$keepsInventory = tag.getBoolean(KEEPS_INVENTORY_TAG);
  }

  @Inject(at = @At("HEAD"), method = "copyFrom")
  private void tascade$copyKeepsInventory(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
    this.tascade$keepsInventory = ((ModifierEffects.Charges) oldPlayer).tascade$keepsInventory();
  }

  @Inject(at = @At("TAIL"), method = "copyFrom")
  private void tascade$copySplitSpawn(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
    this.tascade$splitSpawn = ((SplitSpawn.Holder) oldPlayer).tascade$splitSpawn();
    this.tascade$usedCharges.clear();
    this.tascade$usedCharges.addAll(((ModifierEffects.Charges) oldPlayer).tascade$usedCharges());
    this.tascade$keepsInventory = false;
  }

  @Inject(at = @At("HEAD"), method = "playerTick")
  private void tascade$modifierEffects(CallbackInfo ci) {
    ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
    ModifierEffects.fragile(self);
    this.tascade$bubbleCycle = ModifierEffects.bubble(self, this.tascade$bubbleCycle);
    this.tascade$cursedCycle = ModifierEffects.cursed(self, this.tascade$cursedCycle);
    ModifierEffects.easyMode(self);
    ModifierEffects.mirrorCharges(self);
  }

  @Redirect(method = "copyFrom", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/GameRules;getBoolean(Lnet/minecraft/world/GameRules$Key;)Z"))
  private boolean tascade$keepInventory(GameRules rules, GameRules.Key<GameRules.BooleanRule> key) {
    boolean value = rules.getBoolean(key);
    return key == GameRules.KEEP_INVENTORY ? ModifierEffects.keepInventory((ServerPlayerEntity) (Object) this, value) : value;
  }

  // exact spawn from SpawnSpot, vanilla would re-pick the height
  @Inject(at = @At("HEAD"), method = "moveToSpawn", cancellable = true)
  private void tascade$filterSpawn(ServerWorld world, CallbackInfo ci) {
    if (WorldSeeds.spawn() == null || world.getRegistryKey() != World.OVERWORLD) {
      return;
    }
    ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
    self.refreshPositionAndAngles(world.getSpawnPos(), 0.0F, 0.0F);
    while (!world.doesNotCollide(self) && self.getY() < 255.0) {
      self.updatePosition(self.getX(), self.getY() + 1.0, self.getZ());
    }
    ci.cancel();
  }

  @Inject(at = @At("HEAD"), method = "onDeath")
  private void tascade$noteDeathReset(DamageSource source, CallbackInfo ci) {
    DeathResets.died((ServerPlayerEntity) (Object) this);
    ModifierEffects.died((ServerPlayerEntity) (Object) this);
  }

  @Inject(at = @At("HEAD"), method = "changeDimension")
  private void tascade$rememberSource(ServerWorld destination, CallbackInfoReturnable<Entity> cir) {
    ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
    this.tascade$from = self.getServerWorld().getRegistryKey();
    this.tascade$fromPos = self.getBlockPos();
  }

  @Unique
  private static boolean hasPortalToLink(ServerWorld world, BlockPos pos) {
    PointOfInterestStorage poi = world.getPointOfInterestStorage();
    poi.preloadChunks(world, pos, 128);
    return poi.getInSquare(type -> type == PointOfInterestType.NETHER_PORTAL, pos, 128, PointOfInterestStorage.OccupationStatus.ANY)
        .findAny().isPresent();
  }

  @Inject(at = @At(value = "INVOKE", ordinal = 0,
      target = "Lnet/minecraft/world/PortalForcer;usePortal(Lnet/minecraft/entity/Entity;F)Z"),
      method = "changeDimension")
  private void tascade$fixedEntry(ServerWorld destination, CallbackInfoReturnable<Entity> cir) {
    WorldSeeds.Entry entry = WorldSeeds.entry();
    if (entry == null || this.tascade$from == null) {
      return;
    }
    ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
    RegistryKey<World> to = destination.getRegistryKey();
    BlockPos target = null;
    if (this.tascade$from == World.OVERWORLD && to == World.NETHER) {
      WorldSeeds.rememberOverworldPortal(self.getUuid(), this.tascade$fromPos);
      if (!hasPortalToLink(destination, self.getBlockPos())) {
        PortalBuilder.ensure(destination, entry.pos(), entry.portalAxis());
        target = entry.pos();
      }
    } else if (this.tascade$from == World.NETHER && to == World.OVERWORLD
        && PortalBuilder.isEntryPortal(this.tascade$fromPos, entry)) {
      target = WorldSeeds.overworldPortal(self.getUuid());
    }
    if (target != null) {
      // the portal forcer links to the portal nearest the player
      self.refreshPositionAndAngles(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, self.yaw, self.pitch);
    }
  }
}
