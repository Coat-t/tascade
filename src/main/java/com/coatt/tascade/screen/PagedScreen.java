package com.coatt.tascade.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.json.JSONObject;

@Environment(EnvType.CLIENT)
public abstract class PagedScreen extends BackendScreen {
    protected static final int PAGE_SIZE = 10;
    private static final int OPTION_WIDTH = 90;
    private static final int ARROW_WIDTH = 20;
    private static final int PAGE_LABEL_WIDTH = 50;

    protected int page = 1;
    private ButtonWidget previousButton;
    private ButtonWidget nextButton;

    protected PagedScreen(Text title, Screen parent) {
        super(title, parent);
    }

    protected abstract String optionLabel();

    protected abstract void nextOption();

    @Override
    protected void init() {
        super.init();
        int x = LEFT + BACK_WIDTH + BUTTON_GAP;
        int y = buttonRowY();
        this.addButton(new ButtonWidget(x, y, OPTION_WIDTH, 20, new LiteralText(optionLabel()), buttonWidget -> {
            nextOption();
            buttonWidget.setMessage(new LiteralText(optionLabel()));
            turnTo(1);
        }));
        x += OPTION_WIDTH + BUTTON_GAP;
        previousButton = this.addButton(new ButtonWidget(x, y, ARROW_WIDTH, 20, new LiteralText("<"),
                buttonWidget -> turnTo(page - 1)));
        x += ARROW_WIDTH + PAGE_LABEL_WIDTH;
        nextButton = this.addButton(new ButtonWidget(x, y, ARROW_WIDTH, 20, new LiteralText(">"),
                buttonWidget -> turnTo(page + 1)));
        updateArrows();
    }

    private void turnTo(int page) {
        this.page = page;
        reload();
        updateArrows();
    }

    @Override
    public void tick() {
        super.tick();
        updateArrows();
    }

    private void updateArrows() {
        previousButton.active = page > 1;
        nextButton.active = page < totalPages();
    }

    private int totalPages() {
        JSONObject data = data();
        return data != null ? data.optInt("totalPages", 1) : page;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        String label = data() != null ? page + " / " + totalPages() : String.valueOf(page);
        int center = previousButton.x + ARROW_WIDTH + PAGE_LABEL_WIDTH / 2;
        this.drawCenteredString(matrices, this.textRenderer, label, center, buttonRowY() + 6, QueueStatus.COLOR_TEXT);
    }
}
