package com.coatt.tascade.mixin;

import com.coatt.tascade.world.BastionLoot;
import com.coatt.tascade.world.DesertTempleLoot;
import com.coatt.tascade.world.TascadeLoot;
import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(LootTable.class)
public abstract class LootTableMixin {
  @Redirect(method = "supplyInventory", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/loot/LootTable;generateLoot(Lnet/minecraft/loot/context/LootContext;)Ljava/util/List;"))
  private List<ItemStack> tascade$topUp(LootTable table, LootContext context) {
    List<ItemStack> loot = table.generateLoot(context);
    if (!WorldSeeds.active()) {
      return loot;
    }
    TascadeLoot.topUp(table, loot);
    BlockPos pos = context.get(LootContextParameters.POSITION);
    if (pos != null) {
      TascadeLoot.topUpSeedPortal(pos, loot, context.getRandom());
      loot.addAll(BastionLoot.topUp(context.getWorld(), pos));
      loot.addAll(DesertTempleLoot.topUp(context.getWorld(), pos));
    }
    return loot;
  }
}
