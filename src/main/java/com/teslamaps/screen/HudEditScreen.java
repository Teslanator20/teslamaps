package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.HudEditElement;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** /tmap gui. Every element comes from {@link HudEditElement#all()}. */
public class HudEditScreen extends Screen {
    private final Screen parent;
    private boolean wasMouseDown = false;
    private boolean showAll = false;

    /** Elements drawn this frame, in draw order. Anything not in here cannot be hovered or dragged either. */
    private final List<HudEditElement> visible = new ArrayList<>();
    private HudEditElement dragging = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private static final int GRIP = 5, SNAP = 4;
    private final java.util.Set<HudEditElement> selected = new java.util.LinkedHashSet<>();
    private final java.util.Map<HudEditElement, int[]> groupStart = new java.util.HashMap<>();
    private int dragStartX, dragStartY;
    private HudEditElement resizing = null;
    private int resizeStartW, resizeStartMouse;
    private float resizeStartScale;
    private int snappedX = Integer.MIN_VALUE, snappedY = Integer.MIN_VALUE;

    public HudEditScreen(Screen parent) {
        super(Component.literal("Edit HUD Positions"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(new TeslaButton(this.width - 110, 6, 100, 22, "§fDone", this::onClose));
        ScreenFit.clamp(this); // keep every widget reachable at any gui scale
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_TAB) {
            showAll = !showAll;
            return true;
        }
        return super.keyPressed(event);
    }

    /** Topmost (last drawn) element under the cursor. */
    private HudEditElement at(int mouseX, int mouseY) {
        for (int i = visible.size() - 1; i >= 0; i--) {
            if (visible.get(i).hit(mouseX, mouseY)) return visible.get(i);
        }
        return null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        // TAB drops the situation check only, a switched off element stays hidden either way
        visible.clear();
        for (HudEditElement e : HudEditElement.all()) {
            if (e.shown(showAll)) visible.add(e);
        }
        if (dragging != null && !visible.contains(dragging)) dragging = null;

        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        boolean helpers = cfg.hudEditHelpers;
        if (!helpers) {
            selected.clear();
            resizing = null;
        }
        selected.retainAll(visible);
        if (resizing != null && !visible.contains(resizing)) resizing = null;

        if (isMouseDown && !wasMouseDown) {
            HudEditElement hit = at(mouseX, mouseY);
            if (hit != null && helpers && cfg.hudEditMultiDrag && keyDown(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
                if (!selected.remove(hit)) selected.add(hit);
            } else if (hit != null && helpers && cfg.hudEditResize && onGrip(hit, mouseX, mouseY)) {
                hit.bakeShift();
                resizing = hit;
                resizeStartW = Math.max(1, hit.width());
                resizeStartScale = hit.scale();
                resizeStartMouse = mouseX;
            } else if (hit != null) {
                if (!selected.contains(hit)) selected.clear();
                hit.bakeShift();
                dragging = hit;
                dragOffsetX = mouseX - hit.x();
                dragOffsetY = mouseY - hit.y();
                groupStart.clear();
                for (HudEditElement e : selected) {
                    if (e == hit) continue;
                    e.bakeShift();
                    groupStart.put(e, new int[]{e.x(), e.y()});
                }
                dragStartX = hit.x();
                dragStartY = hit.y();
            } else if (helpers) {
                selected.clear();
            }
        }
        if (!isMouseDown && wasMouseDown && (dragging != null || resizing != null)) {
            if (dragging != null) dragging.recordAnchor();
            for (HudEditElement e : groupStart.keySet()) e.recordAnchor();
            if (resizing != null) resizing.recordAnchor();
            dragging = null;
            resizing = null;
            groupStart.clear();
            TeslaMapsConfig.save();
        }
        wasMouseDown = isMouseDown;

        snappedX = snappedY = Integer.MIN_VALUE;
        if (dragging != null) {
            int w = dragging.width(), h = dragging.height();
            int nx = mouseX - dragOffsetX, ny = mouseY - dragOffsetY;
            if (helpers && !keyDown(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT)) {
                int offX = dragging.boxX() - dragging.x(), offY = dragging.boxY() - dragging.y();
                nx = snapX(nx + offX, w) - offX;
                ny = snapY(ny + offY, h) - offY;
            }
            dragging.setX(Math.max(0, Math.min(this.width - w, nx)));
            dragging.setY(Math.max(0, Math.min(this.height - h, ny)));
            int dx = dragging.x() - dragStartX, dy = dragging.y() - dragStartY;
            for (var en : groupStart.entrySet()) {
                HudEditElement e = en.getKey();
                e.setX(Math.max(0, Math.min(this.width - e.width(), en.getValue()[0] + dx)));
                e.setY(Math.max(0, Math.min(this.height - e.height(), en.getValue()[1] + dy)));
            }
        }
        if (resizing != null) {
            resizing.setScale(resizeStartScale * (resizeStartW + (mouseX - resizeStartMouse)) / (float) resizeStartW);
        }

        context.fill(0, 0, this.width, this.height, 0xC0000000);
        if (helpers) drawHelpers(context);

        for (HudEditElement e : visible) {
            e.drawPreview(context, minecraft);
            int bx = e.boxX(), by = e.boxY(), w = e.width(), h = e.height();
            boolean sel = selected.contains(e);
            if (e == dragging || e == resizing || sel || e.hit(mouseX, mouseY)) {
                int borderColor = e == dragging || e == resizing ? e.dragColor() : sel ? 0xFF55FFFF : 0xFF888888;
                context.fill(bx - 2, by - 2, bx + w + 2, by, borderColor);
                context.fill(bx - 2, by + h, bx + w + 2, by + h + 2, borderColor);
                context.fill(bx - 2, by, bx, by + h, borderColor);
                context.fill(bx + w, by, bx + w + 2, by + h, borderColor);
            }
            context.text(this.font, e.title(), bx, by - 12, 0xFFFFFFFF);
            if (helpers && cfg.hudEditResize && (e == resizing || e.hit(mouseX, mouseY))) {
                context.fill(bx + w - GRIP, by + h - GRIP, bx + w, by + h, 0xFFFFFFFF);
            }
        }
        if (helpers && dragging != null) {
            String pos = dragging.boxX() + ", " + dragging.boxY();
            int pw = this.font.width(pos);
            int px = Math.min(this.width - pw - 6, dragging.boxX() + dragging.width() + 6);
            int py = Math.max(2, dragging.boxY());
            context.fill(px - 3, py - 2, px + pw + 3, py + 10, 0xC0000000);
            context.text(this.font, pos, px, py, 0xFFFFFF55);
        }

        String instructions = dragging != null
                ? (helpers ? "Release to place | Alt = no snap" : "Release to place")
                : (showAll
                        ? "Showing every element | TAB for current situation only"
                        : helpers ? "Drag to move | Scroll or corner to resize | Ctrl+Click to select several | TAB shows all"
                                  : "Drag to move | Scroll to resize | TAB shows all elements");
        int textWidth = this.font.width(instructions);
        com.teslamaps.render.gui.HudStyle.screenPanel(context,
                this.width / 2f - textWidth / 2f - 8, 8, textWidth + 16, 18, 9);
        context.centeredText(this.font, instructions, this.width / 2, 12, 0xFFE0E0E0);

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private boolean keyDown(int a, int b) {
        long win = minecraft.getWindow().handle();
        return GLFW.glfwGetKey(win, a) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(win, b) == GLFW.GLFW_PRESS;
    }

    private static boolean onGrip(HudEditElement e, int mx, int my) {
        int bx = e.boxX() + e.width(), by = e.boxY() + e.height();
        return mx >= bx - GRIP - 1 && mx <= bx && my >= by - GRIP - 1 && my <= by;
    }

    /** Other panels' edges and the screen centre within SNAP, otherwise the grid. */
    private int snapX(int x, int w) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        int best = x, dist = SNAP + 1;
        boolean centre = false;
        if (c.hudEditSnapEdges) {
            for (HudEditElement o : visible) {
                if (o == dragging || groupStart.containsKey(o)) continue;
                int l = o.boxX(), r = l + o.width();
                for (int cand : new int[]{r, l - w, l, r - w}) {
                    int d = Math.abs(x - cand);
                    if (d < dist) { dist = d; best = cand; }
                }
            }
        }
        if (c.hudEditGuides) {
            int cand = this.width / 2 - w / 2, d = Math.abs(x - cand);
            if (d < dist) { dist = d; best = cand; centre = true; }
        }
        if (dist <= SNAP) {
            if (centre) snappedX = this.width / 2;
            return best;
        }
        int g = c.hudEditGrid;
        return g > 0 ? Math.round(x / (float) g) * g : x;
    }

    private int snapY(int y, int h) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        int best = y, dist = SNAP + 1;
        boolean centre = false;
        if (c.hudEditSnapEdges) {
            for (HudEditElement o : visible) {
                if (o == dragging || groupStart.containsKey(o)) continue;
                int t = o.boxY(), b = t + o.height();
                for (int cand : new int[]{b, t - h, t, b - h}) {
                    int d = Math.abs(y - cand);
                    if (d < dist) { dist = d; best = cand; }
                }
            }
        }
        if (c.hudEditGuides) {
            int cand = this.height / 2 - h / 2, d = Math.abs(y - cand);
            if (d < dist) { dist = d; best = cand; centre = true; }
        }
        if (dist <= SNAP) {
            if (centre) snappedY = this.height / 2;
            return best;
        }
        int g = c.hudEditGrid;
        return g > 0 ? Math.round(y / (float) g) * g : y;
    }

    private void drawHelpers(GuiGraphicsExtractor ctx) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        int g = c.hudEditGrid;
        if (g >= 4) {
            for (int x = g; x < this.width; x += g) ctx.fill(x, 0, x + 1, this.height, 0x14FFFFFF);
            for (int y = g; y < this.height; y += g) ctx.fill(0, y, this.width, y + 1, 0x14FFFFFF);
        }
        if (!c.hudEditGuides || dragging == null) return;
        for (int i = 1; i <= 3; i++) {
            int x = this.width * i / 4, y = this.height * i / 4;
            int col = i == 2 ? 0x30FFFFFF : 0x18FFFFFF;
            ctx.fill(x, 0, x + 1, this.height, col);
            ctx.fill(0, y, this.width, y + 1, col);
        }
        if (snappedX != Integer.MIN_VALUE) ctx.fill(snappedX, 0, snappedX + 1, this.height, 0xFF55FFFF);
        if (snappedY != Integer.MIN_VALUE) ctx.fill(0, snappedY, this.width, snappedY + 1, 0xFF55FFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        HudEditElement hit = at((int) mouseX, (int) mouseY);
        if (hit != null) {
            hit.scroll(verticalAmount);
            TeslaMapsConfig.save();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        if (parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
