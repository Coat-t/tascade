package com.coatt.tascade.hud;

import com.coatt.tascade.network.RoomState;
import com.coatt.tascade.network.RunState;
import com.coatt.tascade.network.UpdateCheck;
import com.coatt.tascade.screen.QueueStatus;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class TascadeTabList {
    private static final String SIDE_PADDING = "  ";
    private static final String BLANK_LINE = " ";

    private TascadeTabList() {}

    public static Text header() {
        MutableText title = colored("Tascade v" + UpdateCheck.currentVersion(), QueueStatus.COLOR_ACCENT);
        MutableText mode = colored(RunState.isRanked() ? "Ranked mode" : RunState.isRoom() ? roomName() : "Solo mode",
                QueueStatus.COLOR_TEXT);
        return padded(title, mode);
    }

    private static String roomName() {
        RoomState.Room room = RoomState.room();
        return room != null ? room.settings.name : "Custom Room";
    }

    public static Text footer() {
        MutableText connection = colored("Connection: ", QueueStatus.COLOR_MUTED)
                .append(colored(QueueStatus.connection(), QueueStatus.connectionColor()));
        String seedType = RunState.seedType;
        if (seedType == null || seedType.isEmpty()) return padded(connection);
        MutableText seed = colored("Seed type: ", QueueStatus.COLOR_TEXT)
                .append(colored(RunLabels.seedType(seedType), QueueStatus.COLOR_ACCENT).formatted(Formatting.BOLD));
        return padded(connection, seed);
    }

    // vanilla hugs the text, so padding goes in the text, a blank line is a single space so it isn't dropped
    private static Text padded(MutableText... lines) {
        MutableText out = new LiteralText(BLANK_LINE);
        for (MutableText line : lines) {
            out.append("\n").append(new LiteralText(SIDE_PADDING).append(line).append(SIDE_PADDING));
        }
        return out.append("\n" + BLANK_LINE);
    }

    public static Collection<PlayerListEntry> withOpponent(Collection<PlayerListEntry> players) {
        List<PlayerListEntry> others = OpponentEntry.all();
        if (others.isEmpty()) return players;
        List<PlayerListEntry> all = new ArrayList<>(players);
        all.addAll(others);
        return all;
    }

    private static MutableText colored(String text, int rgb) {
        return new LiteralText(text).styled(style -> style.withColor(TextColor.fromRgb(rgb)));
    }
}
