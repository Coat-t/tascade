package com.coatt.tascade.network;

import com.coatt.tascade.Main;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class UpdateCheck {
  private static final String RELEASES_API = "https://api.github.com/repos/Coat-t/tascade/releases?per_page=100";
  public static final String DOWNLOAD_URL = "https://tascade.net";
  // -Dtascade.allowUnreleased=true lets unreleased builds play
  private static final boolean ALLOW_UNRELEASED = Boolean.getBoolean("tascade.allowUnreleased");

  public enum Blocked {
    OUTDATED,
    UNRELEASED
  }

  private static volatile String newerVersion = null;
  private static volatile String latestVersion = null;
  private static volatile Blocked blocked = null;

  private UpdateCheck() {}

  public static void start() {
    Thread check = new Thread(UpdateCheck::run, "Tascade-UpdateCheck");
    check.setDaemon(true);
    check.start();
  }

  public static String newerVersion() {
    return newerVersion;
  }

  public static String latestVersion() {
    return latestVersion;
  }

  public static Blocked blocked() {
    return blocked;
  }

  public static String currentVersion() {
    return currentVersionObject().getFriendlyString();
  }

  private static Version currentVersionObject() {
    return FabricLoader.getInstance().getModContainer(Main.MOD_ID).get().getMetadata().getVersion();
  }

  private static void run() {
    try {
      Version current = currentVersionObject();
      JSONArray releases = fetchReleases();
      if (releases == null) return;
      SemanticVersion latest = null;
      boolean released = false;
      for (int i = 0; i < releases.length(); i++) {
        JSONObject release = releases.getJSONObject(i);
        if (release.optBoolean("draft")) continue;
        SemanticVersion version = parseTag(release.optString("tag_name", ""));
        if (version == null) continue;
        if (version.compareTo(current) == 0) released = true;
        if (!release.optBoolean("prerelease") && (latest == null || version.compareTo(latest) > 0)) latest = version;
      }
      if (latest == null) return;
      latestVersion = latest.getFriendlyString();
      if (latest.compareTo(current) > 0) {
        newerVersion = latest.getFriendlyString();
        Main.LOGGER.info("Tascade {} is out; this is {}", newerVersion, current.getFriendlyString());
      }
      if (FabricLoader.getInstance().isDevelopmentEnvironment()) return;
      if (current instanceof SemanticVersion && newerMajorOrMinor(latest, (SemanticVersion) current)) {
        blocked = Blocked.OUTDATED;
      } else if (!released && !ALLOW_UNRELEASED) {
        blocked = Blocked.UNRELEASED;
      }
      if (blocked != null) Main.LOGGER.info("Tascade {} can't play: {}", current.getFriendlyString(), blocked);
    } catch (Exception e) {
      Main.LOGGER.info("Couldn't check for a newer Tascade: {}", e.toString());
    }
  }

  private static boolean newerMajorOrMinor(SemanticVersion latest, SemanticVersion current) {
    int major = Integer.compare(latest.getVersionComponent(0), current.getVersionComponent(0));
    return major > 0 || (major == 0 && latest.getVersionComponent(1) > current.getVersionComponent(1));
  }

  private static SemanticVersion parseTag(String tag) {
    try {
      return SemanticVersion.parse(tag.startsWith("v") ? tag.substring(1) : tag);
    } catch (VersionParsingException e) {
      return null;
    }
  }

  private static JSONArray fetchReleases() throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(RELEASES_API).openConnection();
    connection.setRequestProperty("Accept", "application/vnd.github+json");
    connection.setRequestProperty("User-Agent", "Tascade");
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(5000);
    int status = connection.getResponseCode();
    if (status != 200) {
      // 403 when over GitHub's rate limit
      Main.LOGGER.info("Couldn't check for a newer Tascade (HTTP {})", status);
      return null;
    }
    try (InputStream body = connection.getInputStream()) {
      return new JSONArray(new JSONTokener(body));
    }
  }
}
