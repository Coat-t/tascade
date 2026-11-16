package com.coatt.tascade.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;

@Environment(EnvType.CLIENT)
public final class TableScroll {
    static final int BAR_WIDTH = 4;
    static final int BAR_GAP = 6;
    private static final int ROWS_PER_NOTCH = 3;
    private static final int MIN_THUMB_HEIGHT = 8;
    private static final int TRACK_COLOR = 0x30FFFFFF;
    private static final int THUMB_COLOR = 0x80FFFFFF;
    private static final int THUMB_ACTIVE_COLOR = 0xC0FFFFFF;

    private int firstRow = 0;

    private boolean hasBar = false;
    private int barX;
    private int barTop;
    private int barBottom;
    private int rows;
    private int shownRows;
    private int grabbedAt = -1;

    int firstRow(int rows, int shownRows) {
        firstRow = Math.max(0, Math.min(firstRow, rows - shownRows));
        return firstRow;
    }

    public void toTop() {
        firstRow = 0;
    }

    void renderBar(MatrixStack matrices, int x, int top, int bottom, int rows, int shownRows, int mouseX, int mouseY) {
        this.rows = rows;
        this.shownRows = shownRows;
        hasBar = rows > shownRows;
        if (!hasBar) return;
        barX = x;
        barTop = top;
        barBottom = bottom;

        DrawableHelper.fill(matrices, barX, barTop, barX + BAR_WIDTH, barBottom, TRACK_COLOR);
        int thumbTop = thumbTop();
        boolean active = grabbedAt >= 0 || (onBar(mouseX, mouseY) && mouseY >= thumbTop && mouseY < thumbTop + thumbHeight());
        DrawableHelper.fill(matrices, barX, thumbTop, barX + BAR_WIDTH, thumbTop + thumbHeight(),
                active ? THUMB_ACTIVE_COLOR : THUMB_COLOR);
    }

    public boolean mouseScrolled(double amount) {
        if (!hasBar) return false;
        firstRow -= (int) Math.round(amount * ROWS_PER_NOTCH);
        firstRow(rows, shownRows);
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !hasBar || !onBar(mouseX, mouseY)) return false;
        int thumbTop = thumbTop();
        boolean onThumb = mouseY >= thumbTop && mouseY < thumbTop + thumbHeight();
        grabbedAt = onThumb ? (int) mouseY - thumbTop : thumbHeight() / 2;
        mouseDragged(mouseY);
        return true;
    }

    public boolean mouseDragged(double mouseY) {
        if (grabbedAt < 0 || !hasBar) return false;
        int travel = barBottom - barTop - thumbHeight();
        double along = travel > 0 ? (mouseY - grabbedAt - barTop) / travel : 0;
        firstRow = (int) Math.round(along * (rows - shownRows));
        firstRow(rows, shownRows);
        return true;
    }

    public boolean mouseReleased() {
        boolean held = grabbedAt >= 0;
        grabbedAt = -1;
        return held;
    }

    private boolean onBar(double mouseX, double mouseY) {
        return mouseX >= barX && mouseX < barX + BAR_WIDTH && mouseY >= barTop && mouseY < barBottom;
    }

    private int thumbHeight() {
        return Math.max(MIN_THUMB_HEIGHT, (barBottom - barTop) * shownRows / rows);
    }

    private int thumbTop() {
        int travel = barBottom - barTop - thumbHeight();
        return barTop + travel * firstRow / (rows - shownRows);
    }
}
