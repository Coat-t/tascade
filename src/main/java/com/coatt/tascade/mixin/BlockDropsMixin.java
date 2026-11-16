package com.coatt.tascade.mixin;

import com.coatt.tascade.world.BlockDrops;
import com.coatt.tascade.world.MobSpawns;
import com.coatt.tascade.world.WorldSeeds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Random;

@Mixin(Block.class)
public abstract class BlockDropsMixin {
  @Redirect(method = "dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
      + "Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getDroppedStacks(Lnet/minecraft/block/BlockState;"
          + "Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;"
          + "Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Ljava/util/List;"))
  private static List<ItemStack> tascade$seededDrops(BlockState state, ServerWorld world, BlockPos pos, BlockEntity blockEntity,
      Entity entity, ItemStack tool) {
    if (WorldSeeds.active() && entity instanceof PlayerEntity) {
      return BlockDrops.mined(state, world, () -> Block.getDroppedStacks(state, world, pos, blockEntity, entity, tool));
    }
    return Block.getDroppedStacks(state, world, pos, blockEntity, entity, tool);
  }

  @Redirect(method = "getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;"
      + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;"
      + "Lnet/minecraft/item/ItemStack;)Ljava/util/List;",
      at = @At(value = "FIELD", target = "Lnet/minecraft/server/world/ServerWorld;random:Ljava/util/Random;", opcode = Opcodes.GETFIELD))
  private static Random tascade$minedRandom(ServerWorld world) {
    return MobSpawns.random(world);
  }
}
