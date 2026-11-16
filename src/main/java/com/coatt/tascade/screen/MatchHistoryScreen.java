package com.coatt.tascade.screen;

import com.coatt.tascade.hud.RunLabels;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.coatt.tascade.screen.TextTable.cell;

@Environment(EnvType.CLIENT)
public class MatchHistoryScreen extends PagedScreen {
    private enum Filter {
        ALL("all", "Show: All"),
        RANKED("ranked", "Show: Ranked"),
        SOLO("solo", "Show: Solo");

        final String mode;
        final String label;

        Filter(String mode, String label) {
            this.mode = mode;
            this.label = label;
        }
    }

    private static final int COLOR_WON = 0x55FF55;

    private final String uuid;
    private Filter filter = Filter.ALL;
    private TextTable table;
    private JSONArray entries;

    public MatchHistoryScreen(Screen parent, String uuid, String name) {
        super(new LiteralText(name != null ? "Match History · " + name : "Match History"), parent);
        this.uuid = uuid;
    }

    public MatchHistoryScreen(Screen parent) {
        this(parent, null, null);
    }

    @Override
    protected String optionLabel() {
        return filter.label;
    }

    @Override
    protected void nextOption() {
        filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
    }

    @Override
    protected void send(Consumer<JSONObject> onReply) {
        TascadeClient.getInstance().requestHistory(uuid, filter.mode, page, PAGE_SIZE, onReply);
    }

    @Override
    protected String cacheKey() {
        return "history:" + (uuid != null ? uuid : "me") + ":" + filter.mode + ":" + page;
    }

    @Override
    protected void renderData(MatrixStack matrices, JSONObject data, int mouseX, int mouseY) {
        entries = data.optJSONArray("entries");
        table = null;
        if (entries == null || entries.length() == 0) {
            String empty = page > 1 ? "Nothing more to show." : filter == Filter.ALL ? "No runs yet." : "None yet.";
            this.drawStringWithShadow(matrices, this.textRenderer, empty, LEFT, TOP, QueueStatus.COLOR_MUTED);
            return;
        }

        boolean versus = filter != Filter.SOLO;
        table = new TextTable();
        table.column("When");
        if (filter == Filter.ALL) table.column("Mode");
        table.column("Result");
        if (versus) table.column("Opponent");
        table.column("Seed").optional();
        table.column("Time").alignRight();
        if (versus) table.column("Elo").alignRight();
        table.column("pp").alignRight();
        table.column("Modifiers").optional();

        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            table.row(row(entry != null ? entry : new JSONObject(), versus));
        }
        table.render(matrices, this.textRenderer, LEFT, TOP, this.width - 2 * LEFT, contentBottom(), mouseX, mouseY, true);
        RunModifier hovered = table.hoveredModifier();
        if (hovered != null) this.renderTooltip(matrices, RunModifiersScreen.details(this.textRenderer, hovered), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || table == null || entries == null || data() == null) return false;
        int row = table.rowAt(mouseX, mouseY);
        JSONObject entry = row >= 0 ? entries.optJSONObject(row) : null;
        String id = entry != null ? entry.optString("id", "") : "";
        if (id.isEmpty()) return false;
        this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        this.client.openScreen(new MatchDetailsScreen(this, "ranked".equals(entry.optString("mode")), id));
        return true;
    }

    private TextTable.Cell[] row(JSONObject entry, boolean versus) {
        boolean ranked = "ranked".equals(entry.optString("mode"));
        JSONObject opponent = entry.optJSONObject("opponent");
        String seedType = entry.optString("seedType", "");
        int eloDelta = entry.optInt("eloDelta");
        double pp = entry.optDouble("pp", 0);

        List<TextTable.Cell> cells = new ArrayList<>();
        cells.add(cell(RunLabels.ago(entry.optString("createdAt", null)), QueueStatus.COLOR_MUTED));
        if (filter == Filter.ALL) cells.add(cell(ranked ? "Ranked" : "Solo", QueueStatus.COLOR_TEXT));
        cells.add(result(entry.optString("result", ""), entry.optBoolean("isWinner"), ranked));
        if (versus) {
            cells.add(cell(opponent != null ? opponent.optString("username", "Unknown") : "", QueueStatus.COLOR_TEXT));
        }
        cells.add(cell(seedType.isEmpty() ? "" : RunLabels.seedType(seedType), QueueStatus.COLOR_MUTED));
        cells.add(entry.isNull("rtaMs")
                ? cell("-", QueueStatus.COLOR_MUTED)
                : cell(RunLabels.time(entry.optLong("rtaMs")), QueueStatus.COLOR_TEXT));
        if (versus) {
            cells.add(!ranked || eloDelta == 0
                    ? cell(ranked ? "0" : "", QueueStatus.COLOR_MUTED)
                    : cell((eloDelta > 0 ? "+" : "") + eloDelta, eloDelta > 0 ? COLOR_WON : QueueStatus.COLOR_ERROR));
        }
        cells.add(cell(pp > 0 ? RunLabels.pp(pp) : "", QueueStatus.COLOR_TEXT));
        cells.add(TextTable.modifiers(RunModifiers.fromJson(entry.optJSONArray("modifiers"))));
        return cells.toArray(new TextTable.Cell[0]);
    }

    static TextTable.Cell result(String result, boolean won, boolean ranked) {
        if (ranked) {
            switch (result) {
                case "COMPLETE":
                    return won ? cell("Won", COLOR_WON) : cell("Lost", QueueStatus.COLOR_ERROR);
                case "FORFEIT":
                    return won ? cell("Won", COLOR_WON) : cell("Forfeited", QueueStatus.COLOR_ERROR);
                case "DRAW":
                    return cell("Draw", QueueStatus.COLOR_TEXT);
                default:
                    return cell("Canceled", QueueStatus.COLOR_MUTED);
            }
        }
        switch (result) {
            case "COMPLETE":
                return cell("Completed", COLOR_WON);
            case "FORFEIT":
                return cell("Abandoned", QueueStatus.COLOR_MUTED);
            default:
                return cell("Canceled", QueueStatus.COLOR_MUTED);
        }
    }
}
