package com.coatt.tascade.chat;

import com.coatt.tascade.TascadeConfig;
import com.coatt.tascade.network.TascadeClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

// client thread only, socket listeners hand updates over via post()
@Environment(EnvType.CLIENT)
public final class MatchChat {
    private static final int MAX_HISTORY = 100;
    private static final long LINGER_MS = 5_000;

    // also read from the pinger thread
    private static volatile boolean open = false;
    private static String partnerName = null;
    private static boolean matchOver = false;
    private static long closedAtMs = 0;
    private static final List<Text> history = new ArrayList<>();
    private static int revision = 0;
    private static final Queue<Runnable> incoming = new ConcurrentLinkedQueue<>();

    private MatchChat() {}

    // not MinecraftClient#execute: its queue doesn't run while a world generates
    public static void post(Runnable update) {
        incoming.add(update);
    }

    public static void applyPending() {
        Runnable update;
        while ((update = incoming.poll()) != null) update.run();
    }

    public static boolean isOpen() {
        return open;
    }

    public static boolean isShowing() {
        return open || (closedAtMs > 0 && System.currentTimeMillis() - closedAtMs < LINGER_MS);
    }

    public static String partnerName() {
        return partnerName;
    }

    public static List<Text> history() {
        return Collections.unmodifiableList(history);
    }

    public static int revision() {
        return revision;
    }

  private static String roomCode = null;

  public static boolean isRoomChat() {
    return open && roomCode != null;
  }

  public static void openRoom(String code, String name) {
    if (open && code.equals(roomCode)) return;
    open = true;
    roomCode = code;
    partnerName = "the room";
    matchOver = false;
    closedAtMs = 0;
    history.clear();
    add(notice(TascadeConfig.opponentChat()
            ? "You're in " + name + ". Everyone in the room can read this chat."
            : "You're in " + name + ". Opponent chat is off in Tascade's settings."), true);
  }

    public static void open(String opponent) {
        open = true;
        partnerName = opponent;
        matchOver = false;
        closedAtMs = 0;
        history.clear();
        add(notice(TascadeConfig.opponentChat()
                ? "Match found. You are now chatting with " + opponent + "."
                : "Match found against " + opponent + ". Opponent chat is off in Tascade's settings."), true);
    }

    public static void rejoin(String opponent) {
        open = true;
        partnerName = opponent;
        matchOver = false;
        closedAtMs = 0;
        history.clear();
        add(notice(TascadeConfig.opponentChat()
                ? "Rejoined the match. You are chatting with " + opponent + "."
                : "Rejoined the match against " + opponent + ". Opponent chat is off in Tascade's settings."), true);
    }

    public static void markMatchOver() {
        matchOver = true;
    }

    public static boolean isMatchOver() {
        return matchOver;
    }

    public static void receive(Text line) {
        if (open) add(line, true);
        else toVanillaChat(line);
    }

    public static void send(String text) {
        if (!open || text.trim().isEmpty()) return;
        if (!TascadeConfig.opponentChat()) {
            add(notSent(), true);
            return;
        }
        TascadeClient.getInstance().sendChatMessage(text);
        add(own(text), true);
    }

    public static void sentFromWorld(String text) {
        if (!open || text.trim().isEmpty()) return;
        if (!TascadeConfig.opponentChat()) {
            add(notSent(), true);
            return;
        }
        TascadeClient.getInstance().sendChatMessage(text);
        add(own(text), false);
    }

    public static void closedByServer(String reason, String username) {
        String who = username != null ? username : partnerName;
        if ("left".equals(reason)) close(notice(who + " left."));
        else if ("disconnected".equals(reason)) close(notice(who + " disconnected."));
        else close(notice("The chat with " + partnerName + " has ended."));
    }

    public static void close(Text reason) {
        if (!open) return;
        open = false;
        closedAtMs = 0;
        if (reason != null) {
            add(reason, true);
            closedAtMs = System.currentTimeMillis();
        }
    }

    private static void add(Text line, boolean toVanilla) {
        history.add(line);
        if (history.size() > MAX_HISTORY) history.remove(0);
        revision++;
        if (toVanilla) toVanillaChat(line);
    }

    private static void toVanillaChat(Text line) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) client.inGameHud.getChatHud().addMessage(line);
    }

    private static Text notice(String text) {
        return new LiteralText(text).formatted(Formatting.YELLOW);
    }

    private static Text notSent() {
        return notice("Not sent to " + partnerName + ": opponent chat is off in Tascade's settings.");
    }

    private static Text own(String text) {
        return new LiteralText("<" + MinecraftClient.getInstance().getSession().getUsername() + "> " + text);
    }
}
