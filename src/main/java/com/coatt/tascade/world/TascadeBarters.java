package com.coatt.tascade.world;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.potion.PotionUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;

// a world's n-th barter is fixed by its seeds, the first ones are topped up to meet MINIMUMS
public final class TascadeBarters {
  private static final Logger LOGGER = LogManager.getLogger("tascade");

  static final int GUARANTEED_BARTERS = 140;
  static final Minimum[] MINIMUMS = {
      new Minimum("fire resistance potions", 3, TascadeBarters::isFireResistancePotion),
      // with a fully looted bastion's chests, at least 20
      new Minimum("obsidian", 15, stack -> stack.getItem() == Items.OBSIDIAN),
      new Minimum("string", 64, stack -> stack.getItem() == Items.STRING),
      new Minimum("ender pearls", 30, stack -> stack.getItem() == Items.ENDER_PEARL),
  };
  private static final int MAX_TOP_UP_ROLLS = 10_000;
  private static final long TOP_UP_STREAM = -1;

  private static List<List<ItemStack>> plan;
  private static long plannedSeed;

  private TascadeBarters() {
  }

  public static List<ItemStack> next(PiglinEntity piglin) {
    ServerWorld world = (ServerWorld) piglin.world;
    MinecraftServer server = world.getServer();
    LootTable table = server.getLootManager().getTable(LootTables.PIGLIN_BARTERING_GAMEPLAY);
    Function<Random, List<ItemStack>> roll = random -> table.generateLoot(new LootContext.Builder(world)
        .parameter(LootContextParameters.THIS_ENTITY, piglin)
        .random(random)
        .build(LootContextTypes.BARTER));
    return barter(WorldSeeds.combinedSeed(server), BarterCount.of(server).next(), roll);
  }

  static List<ItemStack> barter(long seed, int index, Function<Random, List<ItemStack>> roll) {
    if (index >= GUARANTEED_BARTERS) {
      return roll.apply(random(seed, index));
    }
    if (plan == null || plannedSeed != seed) {
      plan = plan(seed, roll);
      plannedSeed = seed;
    }
    // copies: picking a stack up empties it, and later worlds reuse the plan
    List<ItemStack> barter = new ArrayList<>();
    for (ItemStack stack : plan.get(index)) {
      barter.add(stack.copy());
    }
    return barter;
  }

  static List<List<ItemStack>> plan(long seed, Function<Random, List<ItemStack>> roll) {
    List<List<ItemStack>> barters = new ArrayList<>();
    for (int i = 0; i < GUARANTEED_BARTERS; i++) {
      barters.add(roll.apply(random(seed, i)));
    }
    Random topUp = random(seed, TOP_UP_STREAM);
    for (Minimum minimum : MINIMUMS) {
      int total = 0;
      for (List<ItemStack> barter : barters) {
        total += minimum.count(barter);
      }
      while (total < minimum.amount) {
        // only barters that count toward no minimum are swapped
        List<Integer> spare = new ArrayList<>();
        for (int i = 0; i < barters.size(); i++) {
          if (countsTowardNone(barters.get(i))) spare.add(i);
        }
        List<ItemStack> replacement = spare.isEmpty() ? null : rollGiving(minimum, roll, topUp);
        if (replacement == null) {
          LOGGER.warn("Barters can only guarantee {} of {} {}", total, minimum.amount, minimum.name);
          break;
        }
        barters.set(spare.get(topUp.nextInt(spare.size())), replacement);
        total += minimum.count(replacement);
      }
    }
    return barters;
  }

  private static List<ItemStack> rollGiving(Minimum minimum, Function<Random, List<ItemStack>> roll, Random random) {
    for (int i = 0; i < MAX_TOP_UP_ROLLS; i++) {
      List<ItemStack> barter = roll.apply(random);
      if (minimum.count(barter) > 0) return barter;
    }
    return null;
  }

  private static boolean countsTowardNone(List<ItemStack> barter) {
    for (Minimum minimum : MINIMUMS) {
      if (minimum.count(barter) > 0) return false;
    }
    return true;
  }

  private static boolean isFireResistancePotion(ItemStack stack) {
    Item item = stack.getItem();
    if (item != Items.POTION && item != Items.SPLASH_POTION) return false;
    for (StatusEffectInstance effect : PotionUtil.getPotionEffects(stack)) {
      if (effect.getEffectType() == StatusEffects.FIRE_RESISTANCE) return true;
    }
    return false;
  }

  private static Random random(long seed, long stream) {
    return new Random(HashCommon.murmurHash3(HashCommon.murmurHash3(seed) + stream));
  }

  static final class Minimum {
    final String name;
    final int amount;
    private final Predicate<ItemStack> counts;

    Minimum(String name, int amount, Predicate<ItemStack> counts) {
      this.name = name;
      this.amount = amount;
      this.counts = counts;
    }

    int count(List<ItemStack> barter) {
      int count = 0;
      for (ItemStack stack : barter) {
        if (counts.test(stack)) count += stack.getCount();
      }
      return count;
    }
  }

  private static final class BarterCount extends PersistentState {
    private static final String ID = "tascade_barters";
    private int barters = 0;

    private BarterCount() {
      super(ID);
    }

    static BarterCount of(MinecraftServer server) {
      return server.getOverworld().getPersistentStateManager().getOrCreate(BarterCount::new, ID);
    }

    int next() {
      markDirty();
      return barters++;
    }

    @Override
    public void fromTag(CompoundTag tag) {
      barters = tag.getInt("Barters");
    }

    @Override
    public CompoundTag toTag(CompoundTag tag) {
      tag.putInt("Barters", barters);
      return tag;
    }
  }
}
