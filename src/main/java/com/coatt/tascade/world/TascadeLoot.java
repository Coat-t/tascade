package com.coatt.tascade.world;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

public final class TascadeLoot {
  private static final int PORTAL_NUGGETS = 27;
  private static final int PORTAL_NUGGETS_SPREAD = 38;
  private static final int PORTAL_GOLDEN_APPLES = 2;
  private static final Map<Identifier, List<Floor>> FLOORS = new HashMap<>();
  private static final Map<LootTable, List<Floor>> TABLES = Collections.synchronizedMap(new WeakHashMap<>());

  static {
    floors("chests/buried_treasure",
        new Floor(7, Items.IRON_INGOT),
        new Floor(2, Items.GOLD_INGOT),
        new Floor(4, Items.COOKED_SALMON, Items.COOKED_COD));
    floors("chests/ruined_portal",
        new Floor(1, Items.FLINT_AND_STEEL, Items.FIRE_CHARGE));
    floors("chests/shipwreck_supply",
        new Floor(3, Items.CARROT),
        new Floor(6, Items.WHEAT));
    floors("chests/shipwreck_treasure",
        new Floor(7, Items.IRON_INGOT),
        new Floor(2, Items.GOLD_INGOT));
    for (String smith : new String[] {"armorer", "toolsmith", "weaponsmith"}) {
      floors("chests/village/village_" + smith,
          new Floor(4, Items.IRON_INGOT),
          new Floor(2, Items.GOLD_INGOT),
          new Floor(2, Items.APPLE));
    }
  }

  private TascadeLoot() {
  }

  private static void floors(String table, Floor... floors) {
    FLOORS.put(new Identifier("minecraft", table), Arrays.asList(floors));
  }

  public static void track(Identifier id, LootTable table) {
    List<Floor> floors = FLOORS.get(id);
    if (floors != null && table != LootTable.EMPTY) {
      TABLES.put(table, floors);
    }
  }

  public static void topUp(LootTable table, List<ItemStack> loot) {
    List<Floor> floors = TABLES.get(table);
    if (floors == null) {
      return;
    }
    for (Floor floor : floors) {
      ItemStack shortfall = floor.shortfall(loot);
      if (shortfall != null) loot.add(shortfall);
    }
  }

  public static void topUpSeedPortal(BlockPos pos, List<ItemStack> loot, Random random) {
    // always drawn in this order, whatever the chest rolled
    if (pos.equals(WorldSeeds.lavaPortalChest())) {
      topUp(loot, new Floor(PORTAL_NUGGETS + random.nextInt(PORTAL_NUGGETS_SPREAD + 1), Items.IRON_NUGGET));
    }
    if (pos.equals(WorldSeeds.portalChest())) {
      topUp(loot, new Floor(random.nextInt(PORTAL_GOLDEN_APPLES + 1), Items.GOLDEN_APPLE));
    }
  }

  private static void topUp(List<ItemStack> loot, Floor floor) {
    ItemStack shortfall = floor.shortfall(loot);
    // up to 65 nuggets is more than one stack
    while (shortfall != null && !shortfall.isEmpty()) {
      loot.add(shortfall.split(shortfall.getMaxCount()));
    }
  }

  static final class Floor {
    final int min;
    final Item[] items;

    Floor(int min, Item... items) {
      this.min = min;
      this.items = items;
    }

    ItemStack shortfall(List<ItemStack> loot) {
      int have = 0;
      for (ItemStack stack : loot) {
        if (counts(stack.getItem())) {
          have += stack.getCount();
        }
      }
      return have < min ? new ItemStack(items[0], min - have) : null;
    }

    private boolean counts(Item item) {
      for (Item counted : items) {
        if (counted == item) {
          return true;
        }
      }
      return false;
    }
  }
}
