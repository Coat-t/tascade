package com.coatt.tascade.world;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

final class RunCounts extends PersistentState {
  private static final String ID = "tascade_counts";
  private final Object2IntMap<String> counts = new Object2IntOpenHashMap<>();

  private RunCounts() {
    super(ID);
  }

  static int next(MinecraftServer server, String key) {
    RunCounts runCounts = server.getOverworld().getPersistentStateManager().getOrCreate(RunCounts::new, ID);
    runCounts.markDirty();
    return runCounts.counts.put(key, runCounts.counts.getInt(key) + 1);
  }

  @Override
  public void fromTag(CompoundTag tag) {
    for (String key : tag.getKeys()) {
      counts.put(key, tag.getInt(key));
    }
  }

  @Override
  public CompoundTag toTag(CompoundTag tag) {
    for (Object2IntMap.Entry<String> entry : counts.object2IntEntrySet()) {
      tag.putInt(entry.getKey(), entry.getIntValue());
    }
    return tag;
  }
}
