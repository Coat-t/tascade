package com.coatt.tascade.mixin;

import com.coatt.tascade.world.BastionLoot;
import com.coatt.tascade.world.DesertTempleLoot;
import com.coatt.tascade.world.StructureChests;
import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LootableContainerBlockEntity.class)
public abstract class LootableContainerBlockEntityMixin {
  @Shadow protected Identifier lootTableId;
  @Shadow protected long lootTableSeed;

  @Inject(at = @At("HEAD"), method = "checkLootInteraction")
  private void tascade$structureChestSeed(PlayerEntity player, CallbackInfo ci) {
    BlockEntity self = (BlockEntity) (Object) this;
    if (lootTableId != null && self.getWorld() instanceof ServerWorld && WorldSeeds.active()
        && (BastionLoot.isBastionTable(lootTableId) || DesertTempleLoot.isDesertTempleTable(lootTableId))) {
      lootTableSeed = StructureChests.chestSeed((ServerWorld) self.getWorld(), self.getPos());
    }
  }
}
