package com.coatt.tascade.mixin;

import com.coatt.tascade.integration.RunGoals;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockMixin {
  // only called when the tool actually drops the block
  @Inject(at = @At("HEAD"), method = "afterBreak")
  private void tascade$goldBlockGoal(World world, PlayerEntity player, BlockPos pos, BlockState state,
                                     BlockEntity blockEntity, ItemStack stack, CallbackInfo ci) {
    if (!world.isClient && (Object) this == Blocks.GOLD_BLOCK) RunGoals.reach(RunGoals.GOLD_BLOCK);
  }
}
