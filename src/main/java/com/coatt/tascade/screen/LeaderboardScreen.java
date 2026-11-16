package com.coatt.tascade.screen;

import com.coatt.tascade.hud.RunLabels;
import com.coatt.tascade.network.TascadeClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.function.Consumer;

import static com.coatt.tascade.screen.TextTable.cell;

@Environment(EnvType.CLIENT)
public class LeaderboardScreen extends BackendScreen {
    private static final int TOP_PLAYERS = 100;
    private static final int SORT_WIDTH = 90;
    private static final int FACE_SIZE = 8;
    private static final int PLAYER_COLUMN = 1;

    private boolean byElo = false;
    private TextTable table = null;
    private JSONArray entries = null;

    public LeaderboardScreen(Screen parent) {
        super(new LiteralText("Leaderboards"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addButton(new ButtonWidget(LEFT + BACK_WIDTH + BUTTON_GAP, buttonRowY(), SORT_WIDTH, 20,
                new LiteralText(sortLabel()), buttonWidget -> {
                    byElo = !byElo;
                    buttonWidget.setMessage(new LiteralText(sortLabel()));
                    scroll.toTop();
                    reload();
                }));
    }

    private String sortLabel() {
        return byElo ? "Sort: Elo" : "Sort: pp";
    }

    private String sortBy() {
        return byElo ? "elo" : "pp";
    }

    @Override
    protected void send(Consumer<JSONObject> onReply) {
        TascadeClient.getInstance().requestLeaderboardTop(sortBy(), TOP_PLAYERS, onReply);
    }

    @Override
    protected String cacheKey() {
        return "leaderboard:" + sortBy();
    }

    @Override
    protected void renderData(MatrixStack matrices, JSONObject data, int mouseX, int mouseY) {
        entries = data.optJSONArray("entries");
        if (entries == null || entries.length() == 0) {
            table = null;
            this.drawStringWithShadow(matrices, this.textRenderer, "No players yet.", LEFT, TOP, QueueStatus.COLOR_MUTED);
            return;
        }

        String me = TascadeClient.getInstance().getPlayerUuid();
        table = new TextTable();
        table.column("#").alignRight();
        table.column("Player").indent(FACE_SIZE + 4);
        TextTable.Column pp = table.column("pp").alignRight();
        TextTable.Column elo = table.column("Elo").alignRight();
        (byElo ? elo : pp).highlight();

        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) entry = new JSONObject();
            boolean mine = me != null && me.equals(entry.optString("uuid"));
            table.row(
                    cell(String.valueOf(entry.optInt("rank")), QueueStatus.COLOR_MUTED),
                    cell(entry.optString("username", "Unknown"), mine ? QueueStatus.COLOR_ACCENT : QueueStatus.COLOR_TEXT),
                    cell(RunLabels.pp(entry.optDouble("pp", 0)), byElo ? QueueStatus.COLOR_MUTED : QueueStatus.COLOR_TEXT),
                    cell(String.valueOf(entry.optInt("elo")), byElo ? QueueStatus.COLOR_TEXT : QueueStatus.COLOR_MUTED));
        }

        table.render(matrices, this.textRenderer, LEFT, TOP, this.width - 2 * LEFT, contentBottom(), mouseX, mouseY, true,
                scroll);
        for (int i = table.firstShownRow(); i < table.firstShownRow() + table.shownRows(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry != null) {
                PlayerHeads.draw(matrices, entry.optString("uuid"), table.cellX(PLAYER_COLUMN), table.rowY(i) - 1, FACE_SIZE);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || table == null || data() == null) return false;

        int row = table.rowAt(mouseX, mouseY);
        JSONObject entry = row >= 0 ? entries.optJSONObject(row) : null;
        if (entry == null) return false;
        this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        this.client.openScreen(new ProfileScreen(this, entry.optString("uuid"), entry.optString("username", null)));
        return true;
    }
}
