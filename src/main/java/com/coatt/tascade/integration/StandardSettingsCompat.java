package com.coatt.tascade.integration;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Method;

// a restored world is opened rather than created, which StandardSettings ignores, replay its world-creation calls via reflection
public final class StandardSettingsCompat {
  private static final String CLASS = "me.contaria.standardsettings.StandardSettings";

  private StandardSettingsCompat() {}

  public static void beforeNewWorld() {
    if (!loaded()) return;
    try {
      Class<?> settings = Class.forName(CLASS);
      settings.getMethod("createCache").invoke(null);
      if (enabled(settings)) settings.getMethod("reset").invoke(null);
    } catch (Throwable t) {
      System.err.println("Couldn't have StandardSettings reset the options: " + t);
    }
  }

  public static void afterNewWorld(String worldName) {
    if (!loaded()) return;
    try {
      Class<?> settings = Class.forName(CLASS);
      settings.getMethod("saveToWorldFile", String.class).invoke(null, worldName);
      if (!enabled(settings)) return;
      if (MinecraftClient.getInstance().isWindowFocused()) {
        settings.getMethod("onWorldJoin").invoke(null);
      } else {
        settings.getField("onWorldJoinPending").setBoolean(null, true);
      }
    } catch (Throwable t) {
      System.err.println("Couldn't have StandardSettings apply the options on joining: " + t);
    }
  }

  private static boolean enabled(Class<?> settings) throws ReflectiveOperationException {
    Method isEnabled = settings.getMethod("isEnabled");
    return (Boolean) isEnabled.invoke(null);
  }

  private static boolean loaded() {
    return FabricLoader.getInstance().isModLoaded("standardsettings");
  }
}
