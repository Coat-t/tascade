package com.coatt.tascade.screen;

import com.coatt.tascade.Main;
import com.coatt.tascade.hud.RunLabels;
import com.coatt.tascade.modifier.RunModifier;
import com.coatt.tascade.modifier.RunModifiers;
import com.coatt.tascade.network.TascadeClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.coatt.tascade.screen.TextTable.cell;

@Environment(EnvType.CLIENT)
public class ProfileScreen extends BackendScreen {
    private static final int TOP_PLAYS = 100;
    private static final int PLAYER_HEIGHT = 64;
    private static final int PLAYER_GAP = 10;
    private static final int COLOR_GOOD = 0x55FF55;

    private final String uuid;
    private final String knownName;
    private TextTable playsTable;
    private final List<JSONObject> shownPlays = new ArrayList<>();

    public ProfileScreen(Screen parent, String uuid, String name) {
        super(new LiteralText("Profile"), parent);
        this.uuid = uuid;
        this.knownName = name;
    }

    public ProfileScreen(Screen parent) {
        this(parent, null, null);
    }

    @Override
    protected void init() {
        super.init();
        this.addButton(new ButtonWidget(LEFT + BACK_WIDTH + BUTTON_GAP, buttonRowY(), 90, 20,
                new LiteralText("Match History"),
                buttonWidget -> this.client.openScreen(new MatchHistoryScreen(this, uuid, uuid != null ? name() : null))));
    }

    @Override
    protected void send(Consumer<JSONObject> onReply) {
        TascadeClient.getInstance().requestProfile(uuid, TOP_PLAYS, onReply);
    }

    @Override
    protected String cacheKey() {
        return "profile:" + (uuid != null ? uuid : "me");
    }

    @Override
    protected Text heading() {
        return new LiteralText(name());
    }

    private String name() {
        JSONObject data = data();
        JSONObject profile = data != null ? data.optJSONObject("profile") : null;
        if (profile != null) return profile.optString("username", "Unknown");
        if (knownName != null) return knownName;
        if (uuid == null && Main.nickname != null) return Main.nickname;
        return this.title.getString();
    }

    @Override
    protected void renderData(MatrixStack matrices, JSONObject data, int mouseX, int mouseY) {
        JSONObject profile = data.optJSONObject("profile");
        if (profile == null) {
            this.drawStringWithShadow(matrices, this.textRenderer, "No such player.", LEFT, TOP, QueueStatus.COLOR_MUTED);
            return;
        }

        PlayerModels.draw(profile.optString("uuid"), LEFT, TOP, PLAYER_HEIGHT, mouseX, mouseY);
        int statsX = LEFT + PLAYER_HEIGHT / 2 + PLAYER_GAP;
        int y = stats(data, profile).render(matrices, this.textRenderer, statsX, TOP,
                this.width - statsX - LEFT, contentBottom(), mouseX, mouseY, false);
        y = Math.max(y, TOP + PLAYER_HEIGHT);

        y += 10;
        if (y + TextTable.ROW_HEIGHT > contentBottom()) return;
        this.drawStringWithShadow(matrices, this.textRenderer, "Top plays", LEFT, y, QueueStatus.COLOR_ACCENT);
        y += TextTable.ROW_HEIGHT + 2;
        TextTable plays = topPlays(data.optJSONArray("plays"), shownPlays);
        playsTable = null;
        if (plays.isEmpty()) {
            this.drawStringWithShadow(matrices, this.textRenderer, "No completed runs yet.", LEFT, y, QueueStatus.COLOR_MUTED);
        } else {
            playsTable = plays;
            plays.render(matrices, this.textRenderer, LEFT, y, this.width - 2 * LEFT, contentBottom(),
                    mouseX, mouseY, true, scroll);
            RunModifier hovered = plays.hoveredModifier();
            if (hovered != null) this.renderTooltip(matrices, RunModifiersScreen.details(this.textRenderer, hovered), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || playsTable == null || data() == null) return false;
        int row = playsTable.rowAt(mouseX, mouseY);
        if (row < 0 || row >= shownPlays.size()) return false;
        JSONObject play = shownPlays.get(row);
        boolean ranked = "ranked".equals(play.optString("mode"));
        String id = play.optString(ranked ? "matchId" : "runId", "");
        if (id.isEmpty()) return false;
        this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        this.client.openScreen(new MatchDetailsScreen(this, ranked, id));
        return true;
    }

    private static TextTable stats(JSONObject data, JSONObject profile) {
        TextTable table = new TextTable();
        table.column("");
        table.column("").alignRight();
        table.column("");
        JSONObject ranks = data.optJSONObject("ranks");

        table.row(cell("Performance", QueueStatus.COLOR_MUTED),
                cell(RunLabels.pp(profile.optDouble("performancePoints", 0)), QueueStatus.COLOR_TEXT),
                cell(rank(ranks, "pp"), QueueStatus.COLOR_MUTED));
        table.row(cell("Elo", QueueStatus.COLOR_MUTED),
                cell(String.valueOf(profile.optInt("elo")), QueueStatus.COLOR_TEXT),
                cell(rank(ranks, "elo"), QueueStatus.COLOR_MUTED));

        // older backends send no stats
        JSONObject stats = data.optJSONObject("stats");
        if (stats == null) return table;

        JSONObject ranked = stats.optJSONObject("ranked");
        int played = ranked != null ? ranked.optInt("played") : 0;
        if (played > 0) {
            int wins = ranked.optInt("wins");
            table.row(cell("Ranked", QueueStatus.COLOR_MUTED),
                    cell(wins + "W " + ranked.optInt("losses") + "L " + ranked.optInt("draws") + "D", QueueStatus.COLOR_TEXT),
                    cell(Math.round(100.0 * wins / played) + "% won", QueueStatus.COLOR_MUTED));
        } else {
            table.row(cell("Ranked", QueueStatus.COLOR_MUTED), cell("-", QueueStatus.COLOR_MUTED),
                    cell("", QueueStatus.COLOR_MUTED));
        }

        JSONObject solo = stats.optJSONObject("solo");
        int completed = solo != null ? solo.optInt("completed") : 0;
        int attempts = solo != null ? solo.optInt("played") : 0;
        table.row(cell("Solo", QueueStatus.COLOR_MUTED),
                cell(completed + " / " + attempts, QueueStatus.COLOR_TEXT),
                cell("completed", QueueStatus.COLOR_MUTED));

        boolean hasBest = !stats.isNull("bestRtaMs");
        table.row(cell("Best time", QueueStatus.COLOR_MUTED),
                cell(hasBest ? RunLabels.time(stats.optLong("bestRtaMs")) : "-",
                        hasBest ? QueueStatus.COLOR_TEXT : QueueStatus.COLOR_MUTED),
                cell("", QueueStatus.COLOR_MUTED));
        return table;
    }

    private static String rank(JSONObject ranks, String key) {
        if (ranks == null || ranks.isNull(key)) return "";
        return "#" + ranks.optInt(key);
    }

    private static TextTable topPlays(JSONArray plays, List<JSONObject> shown) {
        shown.clear();
        TextTable table = new TextTable();
        table.column("#").alignRight();
        table.column("Mode");
        table.column("Modifiers").optional();
        table.column("Seed").optional();
        table.column("Time").alignRight();
        table.column("pp").alignRight().highlight();
        table.column("Weighted").alignRight().optional();
        table.column("").alignRight().optional();
        if (plays == null) return table;

        for (int i = 0; i < plays.length(); i++) {
            JSONObject play = plays.optJSONObject(i);
            if (play == null || play.optDouble("rawPP", 0) <= 0) continue;
            String seedType = play.optString("seedType", "");
            shown.add(play);
            table.row(
                    cell(String.valueOf(play.optInt("rank", i + 1)), QueueStatus.COLOR_MUTED),
                    cell("ranked".equals(play.optString("mode")) ? "Ranked" : "Solo", QueueStatus.COLOR_TEXT),
                    TextTable.modifiers(RunModifiers.fromJson(play.optJSONArray("modifiers"))),
                    cell(seedType.isEmpty() ? "" : RunLabels.seedType(seedType), QueueStatus.COLOR_MUTED),
                    cell(play.isNull("rtaMs") ? "-" : RunLabels.time(play.optLong("rtaMs")), QueueStatus.COLOR_TEXT),
                    cell(RunLabels.pp(play.optDouble("rawPP")), COLOR_GOOD),
                    cell(RunLabels.pp(play.optDouble("weightedPP")), QueueStatus.COLOR_TEXT),
                    cell(play.optInt("weightPercent") + "%", QueueStatus.COLOR_MUTED));
        }
        return table;
    }
}
