package com.coatt.tascade.modifier;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.Optional;

public final class SplitSpawn {
  public static final String TAG = "TascadeSplitSpawn";

  private static final int SEARCH_REACH = 6;
  private static final double HALF_WIDTH = 0.3;
  private static final double HEIGHT = 1.8;
  private static final double LIFT = 0.01;

  public final RegistryKey<World> dimension;
  public final BlockPos pos;
  public final float yaw;
  public final float pitch;

  public SplitSpawn(RegistryKey<World> dimension, BlockPos pos, float yaw, float pitch) {
    this.dimension = dimension;
    this.pos = pos.toImmutable();
    this.yaw = yaw;
    this.pitch = pitch;
  }

  public interface Holder {
    SplitSpawn tascade$splitSpawn();

    void tascade$setSplitSpawn(SplitSpawn spot);
  }

  public boolean isRespawnPointOf(ServerPlayerEntity player) {
    return player.isSpawnPointSet() && pos.equals(player.getSpawnPointPosition())
        && dimension.equals(player.getSpawnPointDimension());
  }

  public CompoundTag toTag() {
    CompoundTag tag = new CompoundTag();
    tag.putString("Dimension", dimension.getValue().toString());
    tag.putInt("X", pos.getX());
    tag.putInt("Y", pos.getY());
    tag.putInt("Z", pos.getZ());
    tag.putFloat("Yaw", yaw);
    tag.putFloat("Pitch", pitch);
    return tag;
  }

  public static SplitSpawn fromTag(CompoundTag tag) {
    try {
      RegistryKey<World> dimension = RegistryKey.of(Registry.DIMENSION, new Identifier(tag.getString("Dimension")));
      BlockPos pos = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
      return new SplitSpawn(dimension, pos, tag.getFloat("Yaw"), tag.getFloat("Pitch"));
    } catch (InvalidIdentifierException e) {
      return null;
    }
  }

  public static Optional<Vec3d> findStandingSpot(ServerWorld world, BlockPos spot) {
    // collision checks treat unloaded chunks as empty
    for (int cx = (spot.getX() - SEARCH_REACH) >> 4; cx <= (spot.getX() + SEARCH_REACH) >> 4; cx++) {
      for (int cz = (spot.getZ() - SEARCH_REACH) >> 4; cz <= (spot.getZ() + SEARCH_REACH) >> 4; cz++) {
        world.getChunk(cx, cz);
      }
    }
    for (BlockPos floor : BlockPos.iterateOutwards(spot.down(), SEARCH_REACH, SEARCH_REACH, SEARCH_REACH)) {
      Optional<Vec3d> standing = standingOn(world, floor);
      if (standing.isPresent()) return standing;
    }
    return Optional.empty();
  }

  private static Optional<Vec3d> standingOn(ServerWorld world, BlockPos floor) {
    BlockState floorState = world.getBlockState(floor);
    VoxelShape shape = floorState.getCollisionShape(world, floor);
    if (shape.isEmpty() || hurtsToStandOn(floorState.getBlock())) return Optional.empty();
    double x = floor.getX() + 0.5;
    double y = floor.getY() + shape.getMax(Direction.Axis.Y) + LIFT;
    double z = floor.getZ() + 0.5;
    if (y < 1 || y + HEIGHT >= 256) return Optional.empty();
    Box box = new Box(x - HALF_WIDTH, y, z - HALF_WIDTH, x + HALF_WIDTH, y + HEIGHT, z + HALF_WIDTH);
    if (!world.getWorldBorder().contains(box) || !world.doesNotCollide(box) || world.containsFluid(box)) {
      return Optional.empty();
    }
    for (int by = MathHelper.floor(y); by <= MathHelper.floor(y + HEIGHT); by++) {
      if (hurtsToBeIn(world.getBlockState(new BlockPos(floor.getX(), by, floor.getZ())).getBlock())) {
        return Optional.empty();
      }
    }
    return Optional.of(new Vec3d(x, y, z));
  }

  private static boolean hurtsToStandOn(Block block) {
    return block == Blocks.MAGMA_BLOCK || block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE
        || block == Blocks.CACTUS || hurtsToBeIn(block);
  }

  private static boolean hurtsToBeIn(Block block) {
    return block == Blocks.FIRE || block == Blocks.SOUL_FIRE || block == Blocks.SWEET_BERRY_BUSH
        || block == Blocks.WITHER_ROSE || block == Blocks.COBWEB || block == Blocks.NETHER_PORTAL
        || block == Blocks.END_PORTAL || block == Blocks.END_GATEWAY;
  }
}
