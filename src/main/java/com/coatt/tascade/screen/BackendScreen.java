package com.coatt.tascade.screen;

import com.coatt.tascade.network.TascadeClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.json.JSONObject;

import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public abstract class BackendScreen extends TascadeScreen {
    protected static final int LEFT = 14;
    protected static final int TOP = 36;
    protected static final int BUTTON_GAP = 6;
    protected static final int BACK_WIDTH = 80;
    private static final int BOTTOM_PADDING = 10;

    protected final Screen parent;
    protected final TableScroll scroll = new TableScroll();
    private final BackendReply reply = new BackendReply();
    private ButtonWidget retryButton;

    protected BackendScreen(Text title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected abstract void send(Consumer<JSONObject> onReply);

    protected abstract void renderData(MatrixStack matrices, JSONObject data, int mouseX, int mouseY);

    protected String cacheKey() {
        return null;
    }

    protected boolean unchanging() {
        return false;
    }

    protected Text heading() {
        return this.title;
    }

    @Override
    protected void init() {
        this.addButton(new ButtonWidget(LEFT, buttonRowY(), BACK_WIDTH, 20, new LiteralText("Back"),
                buttonWidget -> onClose()));
        retryButton = this.addButton(new ButtonWidget(LEFT, TOP + 14, BACK_WIDTH, 20, new LiteralText("Retry"),
                buttonWidget -> {
                    TascadeClient.getInstance().connect();
                    reload();
                }));
        retryButton.visible = false;
        // init runs again on resize, only the first one fetches
        if (!reply.requested()) reload();
    }

    protected final void reload() {
        Consumer<JSONObject> answer = reply.request();
        String key = cacheKey();
        BackendCache.Answer cached = key != null ? BackendCache.get(key) : null;
        if (cached != null) {
            answer.accept(cached.data);
            if (cached.isFresh() || unchanging()) return;
        }
        int clears = BackendCache.clears();
        send(fresh -> {
            boolean failed = fresh.has("error");
            if (key != null && !failed) BackendCache.put(key, fresh, clears);
            // a failure doesn't replace data already shown
            if (!failed || cached == null) answer.accept(fresh);
        });
    }

    protected final JSONObject data() {
        return reply.data();
    }

    protected final int buttonRowY() {
        return this.height - 20 - BOTTOM_PADDING;
    }

    protected final int contentBottom() {
        return buttonRowY() - 8;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scroll.mouseScrolled(amount) || super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button) || scroll.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return scroll.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return scroll.mouseReleased() | super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (this.client != null) {
            this.client.openScreen(this.parent);
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        this.drawTextWithShadow(matrices, this.textRenderer, heading(), LEFT, 14, QueueStatus.COLOR_ACCENT);

        String failure = reply.failure();
        retryButton.visible = failure != null;
        JSONObject data = data();
        if (data != null) {
            renderData(matrices, data, mouseX, mouseY);
        } else if (failure != null) {
            this.drawStringWithShadow(matrices, this.textRenderer, failure, LEFT, TOP, QueueStatus.COLOR_ERROR);
        } else {
            this.drawStringWithShadow(matrices, this.textRenderer, "Loading...", LEFT, TOP, QueueStatus.COLOR_MUTED);
        }

        super.render(matrices, mouseX, mouseY, delta);
    }
}
