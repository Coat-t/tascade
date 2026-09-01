package com.coatt.tascade.network;

import com.mojang.authlib.GameProfile;
import io.socket.client.Socket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Session;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AuthHandler {
  private static final String MOJANG_JOIN_URL = "https://sessionserver.mojang.com/session/minecraft/join";
  public static void doMojangAuth(Socket socket, String serverId) {
    try {
      MinecraftClient mcClient = MinecraftClient.getInstance();
      Session mcSession = mcClient.getSession();
      String accessToken = mcSession.getAccessToken();
      String username = mcSession.getUsername();

      GameProfile gameProfile = mcSession.getProfile();
      // uuid without dashes is needed for mojang
      String uuidNoDashes = gameProfile.getId().toString().replace("-", "");

      JSONObject mojangJoinBody = new JSONObject();
      mojangJoinBody.put("accessToken", accessToken);
      mojangJoinBody.put("selectedProfile", uuidNoDashes);
      mojangJoinBody.put("serverId", serverId);

      int status = postJson(MOJANG_JOIN_URL, mojangJoinBody.toString());

      if (status != 204) {
        System.err.println("[Tascade] Mojang join rejected (HTTP " + status + ")");
        return;
      }

      JSONObject serverVerify = new JSONObject();
      serverVerify.put("username", username);
      socket.emit(Events.AUTH_VERIFY, serverVerify);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static int postJson(String urlString, String body) throws Exception {
    URL url = new URL(urlString);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    connection.setDoOutput(true);
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(5000);
    try (OutputStream os = connection.getOutputStream()) {
      os.write(body.getBytes(StandardCharsets.UTF_8));
    }
    return connection.getResponseCode();
  }
}
