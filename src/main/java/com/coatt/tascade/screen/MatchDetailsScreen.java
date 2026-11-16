package com.coatt.tascade.screen;

import com.coatt.tascade.hud.RunLabels;
import com.coatt.tascade.integration.RunRecorder;
import com.coatt.tascade.modifier.RunModifier;
import com.coatt.tascade.modifier.RunModifiers;
import com.coatt.tascade.network.TascadeClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import static com.coatt.tascade.screen.TextTable.cell;

@Environment(EnvType.CLIENT)
public class MatchDetailsScreen extends BackendScreen {
    private static final int FACE_SIZE = 8;
    private static final int PLAYER_COLUMN = 0;
    private static final int SECTION_GAP = 8;
    private static final int COLOR_WON = 0x55FF55;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm", Locale.ENGLISH);

    private final boolean ranked;
    private final String id;
    private final List<JSONObject> players = new ArrayList<>();
    private TextTable playerTable;

    public MatchDetailsScreen(Screen parent, boolean ranked, String id) {
        super(new LiteralText(ranked ? "Ranked Match" : "Solo Run"), parent);
        this.ranked = ranked;
        this.id = id;
    }

    @Override
    protected void send(Consumer<JSONObject> onReply) {
        TascadeClient.getInstance().requestRunDetails(ranked, id, onReply);
    }

    @Override
    protected String cacheKey() {
        return (ranked ? "match:" : "run:") + id;
    }

    @Override
    protected boolean unchanging() {
        return true;
    }

    @Override
    protected void renderData(MatrixStack matrices, JSONObject data, int mouseX, int mouseY) {
        players.clear();
        JSONArray list = data.optJSONArray("players");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject player = list.optJSONObject(i);
                if (player != null) players.add(player);
            }
        } else if (data.optJSONObject("player") != null) {
            JSONObject player = new JSONObject(data.getJSONObject("player").toString());
            for (String key : new String[] {"rtaMs", "pp", "modifiers", "ppMultiplier", "splits"}) {
                if (data.has(key)) player.put(key, data.get(key));
            }
            player.put("isWinner", "COMPLETE".equals(data.optString("result")));
            players.add(player);
        }

        int y = TOP;
        this.drawStringWithShadow(matrices, this.textRenderer, about(data), LEFT, y, QueueStatus.COLOR_MUTED);
        y += TextTable.ROW_HEIGHT + SECTION_GAP;
        if (players.isEmpty()) {
            playerTable = null;
            this.drawStringWithShadow(matrices, this.textRenderer, "No players.", LEFT, y, QueueStatus.COLOR_MUTED);
            return;
        }

        String result = data.optString("result", "");
        playerTable = playerTable(result);
        y = playerTable.render(matrices, this.textRenderer, LEFT, y, this.width - 2 * LEFT, contentBottom(),
                mouseX, mouseY, true);
        for (int i = playerTable.firstShownRow(); i < playerTable.firstShownRow() + playerTable.shownRows(); i++) {
            PlayerHeads.draw(matrices, players.get(i).optString("uuid"), playerTable.cellX(PLAYER_COLUMN),
                    playerTable.rowY(i) - 1, FACE_SIZE);
        }
        RunModifier hovered = playerTable.hoveredModifier();

        y += SECTION_GAP;
        TextTable splits = splitTable();
        if (splits.isEmpty()) {
            if (y + TextTable.ROW_HEIGHT <= contentBottom()) {
                this.drawStringWithShadow(matrices, this.textRenderer, "No splits.", LEFT, y, QueueStatus.COLOR_MUTED);
            }
        } else {
            splits.render(matrices, this.textRenderer, LEFT, y, this.width - 2 * LEFT, contentBottom(),
                    mouseX, mouseY, false, scroll);
        }

        if (hovered != null) this.renderTooltip(matrices, RunModifiersScreen.details(this.textRenderer, hovered), mouseX, mouseY);
    }

    private static String about(JSONObject data) {
        List<String> parts = new ArrayList<>();
        String when = when(data.optString("createdAt", null));
        if (!when.isEmpty()) parts.add(when);
        String seedType = data.optString("seedType", "");
        if (!seedType.isEmpty()) parts.add(RunLabels.seedType(seedType));
        return String.join(" · ", parts);
    }

    static String when(String timestamp) {
        try {
            return OffsetDateTime.parse(timestamp).atZoneSameInstant(ZoneId.systemDefault()).format(WHEN);
        } catch (DateTimeParseException | NullPointerException e) {
            return "";
        }
    }

    private TextTable playerTable(String result) {
        boolean paced = false;
        for (JSONObject player : players) {
            double pace = player.optDouble("pace", 1);
            if (ranked && !Double.isNaN(pace) && Math.abs(pace - 1) > 1e-9) paced = true;
        }
        String me = TascadeClient.getInstance().getPlayerUuid();

        TextTable table = new TextTable();
        table.column("Player").indent(FACE_SIZE + 4);
        table.column("Result");
        table.column("Time").alignRight();
        if (paced) table.column("Pace").alignRight().optional();
        if (paced) table.column("Adjusted").alignRight().optional();
        if (ranked) table.column("Elo").alignRight();
        table.column("pp").alignRight();
        table.column("Modifiers");

        for (JSONObject player : players) {
            boolean mine = me != null && me.equals(player.optString("uuid"));
            List<TextTable.Cell> cells = new ArrayList<>();
            cells.add(cell(player.optString("username", "Unknown"), mine ? QueueStatus.COLOR_ACCENT : QueueStatus.COLOR_TEXT));
            cells.add(MatchHistoryScreen.result(result, player.optBoolean("isWinner"), ranked));
            cells.add(time(player, "rtaMs"));
            if (paced) {
                double pace = player.optDouble("pace", 1);
                cells.add(cell(Double.isNaN(pace) ? "" : RunModifier.format(pace), QueueStatus.COLOR_MUTED));
                cells.add(time(player, "adjustedRtaMs"));
            }
            if (ranked) {
                int elo = player.optInt("eloDelta");
                cells.add(elo == 0 ? cell("0", QueueStatus.COLOR_MUTED)
                        : cell((elo > 0 ? "+" : "") + elo, elo > 0 ? COLOR_WON : QueueStatus.COLOR_ERROR));
            }
            double pp = player.optDouble("pp", 0);
            cells.add(cell(pp > 0 ? RunLabels.pp(pp) : "", QueueStatus.COLOR_TEXT));
            List<RunModifier> modifiers = RunModifiers.fromJson(player.optJSONArray("modifiers"));
            JSONArray played = player.optJSONArray("modifiers");
            if (!modifiers.isEmpty()) {
                cells.add(TextTable.modifiers(modifiers));
            } else if (played == null || played.length() == 0) {
                cells.add(cell("No run modifiers.", QueueStatus.COLOR_MUTED));
            } else {
                cells.add(cell(names(played), QueueStatus.COLOR_TEXT));
            }
            table.row(cells.toArray(new TextTable.Cell[0]));
        }
        return table;
    }

    private static TextTable.Cell time(JSONObject player, String key) {
        return player.isNull(key) || !player.has(key)
                ? cell("-", QueueStatus.COLOR_MUTED)
                : cell(RunLabels.time(player.optLong(key)), QueueStatus.COLOR_TEXT);
    }

    private static String names(JSONArray modifiers) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < modifiers.length(); i++) {
            JSONObject modifier = modifiers.optJSONObject(i);
            if (modifier != null) names.add(modifier.optString("name", modifier.optString("id", "?")));
        }
        return String.join(", ", names);
    }

    private TextTable splitTable() {
        List<Map<String, Long>> times = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (JSONObject player : players) {
            Map<String, Long> mine = new LinkedHashMap<>();
            JSONArray splits = player.optJSONArray("splits");
            for (int i = 0; splits != null && i < splits.length(); i++) {
                JSONObject split = splits.optJSONObject(i);
                if (split == null || split.isNull("rta") || !split.has("rta")) continue;
                String name = split.optString("name", "");
                if (name.isEmpty()) continue;
                mine.putIfAbsent(name, split.optLong("rta"));
                if (!names.contains(name)) names.add(name);
            }
            times.add(mine);
        }
        List<String> ordered = new ArrayList<>();
        for (String name : RunRecorder.trackedSplits()) {
            if (names.contains(name)) ordered.add(name);
        }
        for (String name : names) {
            if (!ordered.contains(name)) ordered.add(name);
        }

        TextTable table = new TextTable();
        if (ordered.isEmpty()) return table;
        table.column("Split");
        for (JSONObject player : players) {
            table.column(players.size() > 1 ? player.optString("username", "Unknown") : "Time").alignRight();
        }
        for (String name : ordered) {
            long best = Long.MAX_VALUE;
            int reached = 0;
            for (Map<String, Long> mine : times) {
                Long rta = mine.get(name);
                if (rta != null) {
                    best = Math.min(best, rta);
                    reached++;
                }
            }
            TextTable.Cell[] row = new TextTable.Cell[players.size() + 1];
            row[0] = cell(RunLabels.split(name).getString(), QueueStatus.COLOR_MUTED);
            for (int i = 0; i < times.size(); i++) {
                Long rta = times.get(i).get(name);
                row[i + 1] = rta == null ? cell("-", QueueStatus.COLOR_MUTED)
                        : cell(RunLabels.time(rta), reached > 1 && rta == best ? COLOR_WON : QueueStatus.COLOR_TEXT);
            }
            table.row(row);
        }
        return table;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || playerTable == null || data() == null) return false;
        int row = playerTable.rowAt(mouseX, mouseY);
        if (row < 0 || row >= players.size()) return false;
        JSONObject player = players.get(row);
        String uuid = player.optString("uuid", "");
        if (uuid.isEmpty()) return false;
        this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        this.client.openScreen(new ProfileScreen(this, uuid, player.optString("username", null)));
        return true;
    }
}
