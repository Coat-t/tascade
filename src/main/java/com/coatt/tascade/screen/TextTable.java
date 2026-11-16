package com.coatt.tascade.screen;

import com.coatt.tascade.modifier.RunModifier;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class TextTable {
    public static final int ROW_HEIGHT = 12;
    private static final int COLUMN_GAP = 12;
    private static final int HOVER_COLOR = 0x30FFFFFF;
    private static final int ICON_SIZE = 10;
    private static final int ICON_GAP = 1;

    private final List<Column> columns = new ArrayList<>();
    private final List<Cell[]> rows = new ArrayList<>();

    private int left = 0;
    private int right = 0;
    private int firstRowY = 0;
    private int firstShownRow = 0;
    private int shownRows = 0;
    private RunModifier hoveredModifier = null;

    public static final class Column {
        private final String header;
        private boolean alignRight = false;
        private boolean optional = false;
        private boolean highlighted = false;
        private int indent = 0;
        private int x = 0;
        private int width = 0;
        private boolean shown = true;

        private Column(String header) {
            this.header = header;
        }

        public Column alignRight() {
            alignRight = true;
            return this;
        }

        public Column optional() {
            optional = true;
            return this;
        }

        public Column highlight() {
            highlighted = true;
            return this;
        }

        public Column indent(int pixels) {
            indent = pixels;
            return this;
        }
    }

    public static final class Cell {
        final String text;
        final int color;
        final List<RunModifier> modifiers;

        private Cell(String text, int color, List<RunModifier> modifiers) {
            this.text = text != null ? text : "";
            this.color = color;
            this.modifiers = modifiers;
        }

        private int width(TextRenderer textRenderer) {
            if (modifiers.isEmpty()) return textRenderer.getWidth(text);
            return modifiers.size() * (ICON_SIZE + ICON_GAP) - ICON_GAP;
        }
    }

    public static Cell cell(String text, int color) {
        return new Cell(text, color, Collections.emptyList());
    }

    public static Cell modifiers(List<RunModifier> modifiers) {
        return new Cell("", 0, new ArrayList<>(modifiers));
    }

    public Column column(String header) {
        Column column = new Column(header);
        columns.add(column);
        return column;
    }

    public void row(Cell... cells) {
        rows.add(cells);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public int render(MatrixStack matrices, TextRenderer textRenderer, int x, int y, int maxWidth, int maxY,
            int mouseX, int mouseY, boolean hover) {
        return render(matrices, textRenderer, x, y, maxWidth, maxY, mouseX, mouseY, hover, null);
    }

    public int render(MatrixStack matrices, TextRenderer textRenderer, int x, int y, int maxWidth, int maxY,
            int mouseX, int mouseY, boolean hover, TableScroll scroll) {
        int fit = Math.max(0, (maxY - y - (hasHeader() ? ROW_HEIGHT : 0)) / ROW_HEIGHT);
        boolean scrolls = scroll != null && rows.size() > fit;
        layout(textRenderer, x, scrolls ? maxWidth - TableScroll.BAR_GAP - TableScroll.BAR_WIDTH : maxWidth);

        if (hasHeader()) {
            for (Column column : columns) {
                if (!column.shown) continue;
                int color = column.highlighted ? QueueStatus.COLOR_TEXT : QueueStatus.COLOR_MUTED;
                draw(matrices, textRenderer, column, column.header, y, color);
            }
            y += ROW_HEIGHT;
        }

        firstRowY = y;
        hoveredModifier = null;
        firstShownRow = scroll != null ? scroll.firstRow(rows.size(), fit) : 0;
        shownRows = Math.min(fit, rows.size() - firstShownRow);
        for (Cell[] row : rows.subList(firstShownRow, firstShownRow + shownRows)) {
            if (hover && rowAt(mouseX, mouseY, y)) {
                DrawableHelper.fill(matrices, left - 2, y - 2, right + 2, y + ROW_HEIGHT - 2, HOVER_COLOR);
            }
            for (int i = 0; i < columns.size() && i < row.length; i++) {
                Column column = columns.get(i);
                if (!column.shown) continue;
                if (row[i].modifiers.isEmpty()) {
                    draw(matrices, textRenderer, column, row[i].text, y, row[i].color);
                } else {
                    drawIcons(textRenderer, column, row[i], y, mouseX, mouseY);
                }
            }
            y += ROW_HEIGHT;
        }
        if (scroll != null) {
            scroll.renderBar(matrices, right + TableScroll.BAR_GAP, firstRowY - 2, y - 2, rows.size(), fit,
                    mouseX, mouseY);
        }
        return y;
    }

    public RunModifier hoveredModifier() {
        return hoveredModifier;
    }

    private void drawIcons(TextRenderer textRenderer, Column column, Cell cell, int y, int mouseX, int mouseY) {
        int x = column.x + column.indent;
        if (column.alignRight) x = column.x + column.width - cell.width(textRenderer);
        int top = y - 1;
        MinecraftClient client = MinecraftClient.getInstance();
        float scale = ICON_SIZE / 16f;
        for (RunModifier modifier : cell.modifiers) {
            // items use RenderSystem's matrix, not the MatrixStack
            RenderSystem.pushMatrix();
            RenderSystem.translatef(x, top, 0);
            RenderSystem.scalef(scale, scale, 1f);
            client.getItemRenderer().renderInGui(modifier.icon(), 0, 0);
            RenderSystem.popMatrix();
            if (mouseX >= x && mouseX < x + ICON_SIZE && mouseY >= top && mouseY < top + ICON_SIZE) {
                hoveredModifier = modifier;
            }
            x += ICON_SIZE + ICON_GAP;
        }
    }

    public int rowAt(double mouseX, double mouseY) {
        for (int i = 0; i < shownRows; i++) {
            if (rowAt(mouseX, mouseY, firstRowY + i * ROW_HEIGHT)) return firstShownRow + i;
        }
        return -1;
    }

    public int cellX(int column) {
        return columns.get(column).x;
    }

    public int firstShownRow() {
        return firstShownRow;
    }

    public int shownRows() {
        return shownRows;
    }

    public int rowY(int row) {
        return firstRowY + (row - firstShownRow) * ROW_HEIGHT;
    }

    private boolean rowAt(double mouseX, double mouseY, int rowY) {
        return mouseX >= left - 2 && mouseX < right + 2 && mouseY >= rowY - 2 && mouseY < rowY + ROW_HEIGHT - 2;
    }

    private boolean hasHeader() {
        for (Column column : columns) {
            if (!column.header.isEmpty()) return true;
        }
        return false;
    }

    private void layout(TextRenderer textRenderer, int x, int maxWidth) {
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            int width = textRenderer.getWidth(column.header);
            for (Cell[] row : rows) {
                if (i < row.length) width = Math.max(width, row[i].width(textRenderer));
            }
            column.width = column.indent + width;
            column.shown = true;
        }
        for (int i = columns.size() - 1; i >= 0 && totalWidth() > maxWidth; i--) {
            if (columns.get(i).optional) columns.get(i).shown = false;
        }

        left = x;
        for (Column column : columns) {
            if (!column.shown) continue;
            column.x = x;
            x += column.width + COLUMN_GAP;
        }
        right = x - COLUMN_GAP;
    }

    private int totalWidth() {
        int width = -COLUMN_GAP;
        for (Column column : columns) {
            if (column.shown) width += column.width + COLUMN_GAP;
        }
        return width;
    }

    private static void draw(MatrixStack matrices, TextRenderer textRenderer, Column column, String text,
            int y, int color) {
        int x = column.x + column.indent;
        if (column.alignRight) x = column.x + column.width - textRenderer.getWidth(text);
        textRenderer.drawWithShadow(matrices, text, x, y, color);
    }
}
