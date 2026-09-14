package com.teslamaps.profileviewer.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Self measuring layout pieces, ported from schrumbo's pv. Every element reports its own size and
 * containers derive theirs from the children, so nothing can overlap by accident any more.
 */
public abstract class Ui {
    public abstract int width();

    public abstract int height();

    public abstract void render(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY);

    public enum H {START, CENTER, END}

    public enum V {TOP, CENTER, BOTTOM}

    /** Something that paints a panel background, e.g. NeuStyle::slimBox. */
    public interface Painter {
        void paint(GuiGraphicsExtractor ctx, int x, int y, int w, int h);
    }

    // hover results of the current frame, drained by the page
    public static ItemStack hoveredStack = ItemStack.EMPTY;
    public static List<Component> hoveredLines = null;

    public static void resetHover() {
        hoveredStack = ItemStack.EMPTY;
        hoveredLines = null;
    }

    protected static Font font() {
        return Minecraft.getInstance().font;
    }

    protected static boolean over(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // ---- containers ------------------------------------------------------------------------

    public static class Spacer extends Ui {
        private final int w;
        private final int h;

        public Spacer(int w, int h) {
            this.w = w;
            this.h = h;
        }

        public int width() {
            return w;
        }

        public int height() {
            return h;
        }

        public void render(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY) {
        }
    }

    public static class Box extends Ui {
        private final int w;
        private final int h;
        private final int color;

        public Box(int w, int h, int color) {
            this.w = w;
            this.h = h;
            this.color = color;
        }

        public int width() {
            return w;
        }

        public int height() {
            return h;
        }

        public void render(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY) {
            ctx.fill(x, y, x + w, y + h, color);
        }
    }

    /** Fixed size, optional painted background, one child placed by alignment. */
    public static class Frame extends Ui {
        private final int w;
        private final int h;
        private final Ui child;
        private final Painter background;
        private final H hAlign;
        private final V vAlign;

        public Frame(int w, int h, Ui child, Painter background, H hAlign, V vAlign) {
            this.w = w;
            this.h = h;
            this.child = child;
            this.background = background;
            this.hAlign = hAlign;
            this.vAlign = vAlign;
        }

        public int width() {
            return w;
        }

        public int height() {
            return h;
        }

        public void render(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY) {
            if (background != null) background.paint(ctx, x, y, w, h);
            int cx = switch (hAlign) {
                case START -> x;
                case CENTER -> x + (w - child.width()) / 2;
                case END -> x + w - child.width();
            };
            int cy = switch (vAlign) {
                case TOP -> y;
                case CENTER -> y + (h - child.height()) / 2;
                case BOTTOM -> y + h - child.height();
            };
            child.render(ctx, cx, cy, mouseX, mouseY);
        }
    }

    public static class Row extends Ui {
        private final List<Ui> children;
        private final int spacing;
        private final V align;

        public Row(List<Ui> children, int spacing, V align) {
            this.children = children;
            this.spacing = spacing;
            this.align = align;
        }

        public int width() {
            int total = 0;
            for (Ui child : children) total += child.width();
            return total + spacing * Math.max(0, children.size() - 1);
        }

        public int height() {
            int max = 0;
            for (Ui child : children) max = Math.max(max, child.height());
            return max;
        }

        public void render(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY) {
            int cx = x;
            int h = height();
            for (Ui child : children) {
                int cy = switch (align) {
                    case TOP -> y;
                    case CENTER -> y + (h - child.height()) / 2;
                    case BOTTOM -> y + h - child.height();
                };
                child.render(ctx, cx, cy, mouseX, mouseY);
                cx += child.width() + spacing;
            }
        }
    }

    public static class Column extends Ui {
        private final List<Ui> children;
        private final int spacing;
        private final H align;

        public Column(List<Ui> children, int spacing, H align) {
            this.children = children;
            this.spacing = spacing;
            this.align = align;
        }

        public int width() {
            int max = 0;
            for (Ui child : children) max = Math.max(max, child.width());
            return max;
        }

        public int height() {
            int total = 0;
            for (Ui child : children) total += child.height();
            return total + spacing * Math.max(0, children.size() - 1);
        }

        public void render(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY) {
            int cy = y;
            int w = width();
            for (Ui child : children) {
                int cx = switch (align) {
                    case START -> x;
                    case CENTER -> x + (w - child.width()) / 2;
                    case END -> x + w - child.width();
                };
                child.render(ctx, cx, cy, mouseX, mouseY);
                cy += child.height() + spacing;
            }
        }
    }

    /** Shrinks one child on its own, so a tall block does not force the whole page to scale down. */
    public static class Scaled extends Ui {
        private final Ui child;
        private final float factor;

        public Scaled(Ui child, float factor) {
            this.child = child;
            this.factor = factor;
        }

        public int width() {
            return (int) Math.ceil(child.width() * factor);
        }

        public int height() {
            return (int) Math.ceil(child.height() * factor);
        }

        public void render(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY) {
            ctx.pose().pushMatrix();
            ctx.pose().translate(x, y);
            ctx.pose().scale(factor, factor);
            child.render(ctx, 0, 0, (int) ((mouseX - x) / factor), (int) ((mouseY - y) / factor));
            ctx.pose().popMatrix();
        }
    }

    /** Pins one child left and one right inside a fixed width. */
    public static class SpaceBetween extends Ui {
        private final int w;
        private final Ui left;
        private final Ui right;

        public SpaceBetween(int w, Ui left, Ui right) {
            this.w = w;
            this.left = left;
            this.right = right;
        }

        public int width() {
            return w;
        }

        public int height() {
            return Math.max(left.height(), right.height());
        }

        public void render(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY) {
            int h = height();
            // the right side is the value and always wins; a long label gets cut instead of overlapping
            boolean collides = left.width() + right.width() + 4 > w;
            if (collides) {
                ctx.enableScissor(x, y, x + Math.max(0, w - right.width() - 4), y + h);
            }
            left.render(ctx, x, y + (h - left.height()) / 2, mouseX, mouseY);
            if (collides) ctx.disableScissor();
            right.render(ctx, x + w - right.width(), y + (h - right.height()) / 2, mouseX, mouseY);
        }
    }

    // ---- leaves ----------------------------------------------------------------------------

    public static final float SUBTITLE = 1.5f;
    public static final float NORMAL = 1f;
    public static final float SMALL = 0.75f;

    public static class Label extends Ui {
        private final String text;
        private final int color;
        private final float scale;

        public Label(String text, int color) {
            this(text, color, NORMAL);
        }

        public Label(String text, int color, float scale) {
            this.text = text;
            this.color = color;
            this.scale = scale;
        }

        public int width() {
            return (int) Math.ceil(font().width(text) * scale);
        }

        public int height() {
            return (int) Math.ceil(font().lineHeight * scale);
        }

        public void render(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY) {
            if (scale == NORMAL) {
                ctx.text(font(), text, x, y, color);
                return;
            }
            ctx.pose().pushMatrix();
            ctx.pose().translate(x, y);
            ctx.pose().scale(scale, scale);
            ctx.text(font(), text, 0, 0, color);
            ctx.pose().popMatrix();
        }
    }

    /** Progress bar drawn by whatever painter the style hands in. */
    public static class Bar extends Ui {
        public interface Draw {
            void draw(GuiGraphicsExtractor ctx, int x, int y, int w, double progress);
        }

        private final int w;
        private final int h;
        private final double progress;
        private final Draw draw;

        public Bar(int w, int h, double progress, Draw draw) {
            this.w = w;
            this.h = h;
            this.progress = progress;
            this.draw = draw;
        }

        public int width() {
            return w;
        }

        public int height() {
            return h;
        }

        public void render(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY) {
            draw.draw(ctx, x, y, w, progress);
        }
    }

    /** An item icon; hovering parks its stack for the screen to show the vanilla tooltip. */
    public static class Icon extends Ui {
        private final ItemStack stack;
        private final int size;
        private final boolean tooltip;
        private final String corner;

        public Icon(ItemStack stack, int size, boolean tooltip, String corner) {
            this.stack = stack;
            this.size = size;
            this.tooltip = tooltip;
            this.corner = corner;
        }

        public int width() {
            return size;
        }

        public int height() {
            return size;
        }

        public void render(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY) {
            if (!stack.isEmpty()) {
                if (size == 16) {
                    com.teslamaps.features.RarityBackground.draw(ctx, x, y, stack);
                    ctx.item(stack, x, y);
                } else {
                    float factor = size / 16f;
                    ctx.pose().pushMatrix();
                    ctx.pose().translate(x, y);
                    ctx.pose().scale(factor, factor);
                    com.teslamaps.features.RarityBackground.draw(ctx, 0, 0, stack);
                    ctx.item(stack, 0, 0);
                    ctx.pose().popMatrix();
                }
            }
            if (corner != null) {
                Font font = font();
                float s = 0.75f;
                ctx.pose().pushMatrix();
                ctx.pose().translate(x + size - font.width(corner) * s - 1, y + size - font.lineHeight * s);
                ctx.pose().scale(s, s);
                ctx.text(font, corner, 0, 0, 0xFFFFFFFF);
                ctx.pose().popMatrix();
            }
            if (tooltip && !stack.isEmpty() && over(mouseX, mouseY, x, y, size, size)) {
                hoveredStack = stack;
            }
        }
    }

    /** Wraps a child with a multi line legacy formatted tooltip. */
    public static class Tip extends Ui {
        private final Ui child;
        private final List<String> lines;

        public Tip(Ui child, List<String> lines) {
            this.child = child;
            this.lines = lines;
        }

        public int width() {
            return child.width();
        }

        public int height() {
            return child.height();
        }

        public void render(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY) {
            child.render(ctx, x, y, mouseX, mouseY);
            if (over(mouseX, mouseY, x, y, width(), height())) {
                List<Component> parsed = new ArrayList<>(lines.size());
                for (String line : lines) {
                    parsed.add(com.teslamaps.profileviewer.data.LegacyText.parse(line));
                }
                hoveredLines = parsed;
            }
        }
    }

    // ---- shorthand -------------------------------------------------------------------------

    public static Ui row(int spacing, V align, Ui... children) {
        return new Row(Arrays.asList(children), spacing, align);
    }

    public static Ui row(int spacing, V align, List<Ui> children) {
        return new Row(children, spacing, align);
    }

    public static Ui col(int spacing, Ui... children) {
        return new Column(Arrays.asList(children), spacing, H.START);
    }

    public static Ui col(int spacing, List<Ui> children) {
        return new Column(children, spacing, H.START);
    }

    public static Ui text(String text, int color) {
        return new Label(text, color);
    }

    public static Ui gap(int height) {
        return new Spacer(0, height);
    }
}
