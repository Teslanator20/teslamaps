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
    private HudEditElement selected = null;
    private boolean wasMiddleDown = false;
    private long resetArmed = 0;
    private String flash = null;
    private long flashAt = 0;
    private TeslaButton resetAllBtn;

    private static final int GRIP = 5, SNAP = 4;
    private final java.util.Set<HudEditElement> multi = new java.util.LinkedHashSet<>();
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
        resetAllBtn = null;
        if (extras() && TeslaMapsConfig.get().hudEditReset) {
            resetAllBtn = addRenderableWidget(new TeslaButton(this.width - 110, 32, 100, 22, "Reset All", this::resetAll));
        }
        if (globalScaleShown()) {
            addRenderableWidget(new TeslaButton(10, 6, 20, 22, "§f-", () -> globalScale(-0.05f)));
            addRenderableWidget(new TeslaButton(34, 6, 20, 22, "§f+", () -> globalScale(0.05f)));
            addRenderableWidget(new TeslaButton(58, 6, 26, 22, "§f1x", () -> globalScale(0f)));
        }
        if (com.teslamaps.features.HudLayouts.enabled()) layoutButtons();
        ScreenFit.clamp(this); // keep every widget reachable at any gui scale
    }

    private static boolean extras() {
        return TeslaMapsConfig.get().hudEditExtras;
    }

    private static boolean globalScaleShown() {
        return extras() || HudEditElement.globalScale() != 1f;
    }

    private void flash(String msg) {
        flash = msg;
        flashAt = System.currentTimeMillis();
    }

    private void resetAll() {
        long now = System.currentTimeMillis();
        if (now - resetArmed > 3000) {
            resetArmed = now;
            flash("Click Reset All again to put every element back to its default");
            return;
        }
        resetArmed = 0;
        TeslaMapsConfig defaults = new TeslaMapsConfig();
        for (HudEditElement e : HudEditElement.all()) e.resetToDefault(defaults);
        TeslaMapsConfig.save();
        flash("Every element reset");
    }

    /** Selected element, else the one under the cursor. */
    private HudEditElement target() {
        if (selected != null && visible.contains(selected)) return selected;
        var w = minecraft.getWindow();
        double mx = minecraft.mouseHandler.xpos() * w.getGuiScaledWidth() / w.getScreenWidth();
        double my = minecraft.mouseHandler.ypos() * w.getGuiScaledHeight() / w.getScreenHeight();
        return at((int) mx, (int) my);
    }

    private void move(HudEditElement e, int dx, int dy) {
        e.bakeShift();
        e.setX(Math.max(0, Math.min(this.width - e.width(), e.x() + dx)));
        e.setY(Math.max(0, Math.min(this.height - e.height(), e.y() + dy)));
        e.recordAnchor();
        TeslaMapsConfig.save();
    }

    private void globalScale(float step) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        float v = step == 0f ? 1f : Math.round((HudEditElement.globalScale() + step) * 100f) / 100f;
        c.hudGlobalScale = Math.max(0.25f, Math.min(4f, v));
        TeslaMapsConfig.save();
    }

    private int copyTarget = 0;

    private void layoutButtons() {
        String[] names = com.teslamaps.features.HudLayouts.NAMES;
        int y = this.height - 28;
        TeslaButton[] target = new TeslaButton[1];
        target[0] = new TeslaButton(10, y, 120, 22, "§fCopy To: " + names[copyTarget], () -> {
            copyTarget = (copyTarget + 1) % names.length;
            target[0].setMessage(Component.literal("§fCopy To: " + names[copyTarget]));
        });
        addRenderableWidget(target[0]);
        addRenderableWidget(new TeslaButton(134, y, 50, 22, "§aCopy", () -> {
            com.teslamaps.features.HudLayouts.copyTo(names[copyTarget]);
            if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal(
                    "§b[TeslaMaps] §fHUD layout copied to §e" + names[copyTarget]));
        }));
        addRenderableWidget(new TeslaButton(188, y, 90, 22, "§cUse Default", com.teslamaps.features.HudLayouts::resetCurrent));
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int k = event.key();
        if (k == GLFW.GLFW_KEY_TAB) {
            showAll = !showAll;
            return true;
        }
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (extras() && dragging == null) {
            HudEditElement t = target();
            if (t != null && c.hudEditReset && k == GLFW.GLFW_KEY_R) {
                t.resetToDefault(new TeslaMapsConfig());
                TeslaMapsConfig.save();
                flash(t.label + " reset");
                return true;
            }
            if (t != null && c.hudEditNudge) {
                int step = minecraft.hasShiftDown() ? 10 : 1;
                switch (k) {
                    case GLFW.GLFW_KEY_LEFT -> { move(t, -step, 0); return true; }
                    case GLFW.GLFW_KEY_RIGHT -> { move(t, step, 0); return true; }
                    case GLFW.GLFW_KEY_UP -> { move(t, 0, -step); return true; }
                    case GLFW.GLFW_KEY_DOWN -> { move(t, 0, step); return true; }
                    case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> { t.nudgeScale(0.1f); TeslaMapsConfig.save(); return true; }
                    case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> { t.nudgeScale(-0.1f); TeslaMapsConfig.save(); return true; }
                    default -> {}
                }
            }
        }
        return super.keyPressed(event);
    }

    private void rightClick(HudEditElement e) {
        Boolean v = e.toggle();
        if (v == null) { flash(e.label + " has no single on/off switch - use the settings"); return; }
        TeslaMapsConfig.save();
        String sec = e.section();
        boolean secOff = sec != null && !TeslaMapsConfig.get().section(sec);
        flash(e.label + (v ? " §aenabled" : " §cdisabled") + (v && secOff ? " §7(\"" + sec + "\" is switched off)" : ""));
    }

    private void jump(HudEditElement e) {
        TeslaMapsConfig.save();
        MapConfigScreen.View view = MapConfigScreen.viewFor(e.section(), e.label);
        MapConfigScreen.jumpTo(e.section(), e.label);
        minecraft.setScreen(MapConfigScreen.open(view));
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
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        boolean extras = extras();
        boolean showOff = extras && cfg.hudEditShowDisabled;

        // TAB drops the situation check only, a switched off element stays hidden either way
        visible.clear();
        for (HudEditElement e : HudEditElement.all()) {
            if (e.shown(showAll) || (showOff && e.shownDisabled(showAll))) visible.add(e);
        }
        if (dragging != null && !visible.contains(dragging)) dragging = null;

        boolean helpers = cfg.hudEditHelpers;
        if (!helpers) {
            multi.clear();
            resizing = null;
        }
        multi.retainAll(visible);
        if (resizing != null && !visible.contains(resizing)) resizing = null;

        if (isMouseDown && !wasMouseDown) {
            HudEditElement hit = at(mouseX, mouseY);
            if (hit != null && helpers && cfg.hudEditMultiDrag && keyDown(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
                if (!multi.remove(hit)) multi.add(hit);
            } else if (hit != null && helpers && cfg.hudEditResize && onGrip(hit, mouseX, mouseY)) {
                selected = hit;
                hit.bakeShift();
                resizing = hit;
                resizeStartW = Math.max(1, hit.width());
                resizeStartScale = hit.scale();
                resizeStartMouse = mouseX;
            } else if (hit != null) {
                if (!multi.contains(hit)) multi.clear();
                selected = hit;
                hit.bakeShift();
                dragging = hit;
                dragOffsetX = mouseX - hit.x();
                dragOffsetY = mouseY - hit.y();
                groupStart.clear();
                for (HudEditElement e : multi) {
                    if (e == hit) continue;
                    e.bakeShift();
                    groupStart.put(e, new int[]{e.x(), e.y()});
                }
                dragStartX = hit.x();
                dragStartY = hit.y();
            } else if (helpers) {
                multi.clear();
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
        if (extras && cfg.hudEditJump) {
            boolean mid = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
            boolean midClicked = mid && !wasMiddleDown;
            wasMiddleDown = mid;
            HudEditElement hit = midClicked && dragging == null ? at(mouseX, mouseY) : null;
            if (hit != null) {
                jump(hit);
                return;
            }
        }

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
            resizing.resizeTo(resizeStartScale * (resizeStartW + (mouseX - resizeStartMouse)) / (float) resizeStartW);
        }

        context.fill(0, 0, this.width, this.height, 0xC0000000);
        if (helpers) drawHelpers(context);

        HudEditElement hovered = null;
        for (HudEditElement e : visible) {
            int bx = e.boxX(), by = e.boxY(), w = e.width(), h = e.height();
            if (e.hit(mouseX, mouseY)) hovered = e;
            if (showOff && !e.enabledNow()) {
                context.fill(bx, by, bx + w, by + h, 0x40FFFFFF);
                context.text(this.font, "§7" + e.label + " (off)", bx, by - 12, 0xFFFFFFFF);
                continue;
            }
            e.drawPreview(context, minecraft);
            boolean sel = multi.contains(e);
            if (e == dragging || e == resizing || sel || e.hit(mouseX, mouseY)) {
                int borderColor = e == dragging || e == resizing ? e.dragColor() : sel ? 0xFF55FFFF : 0xFF888888;
                context.fill(bx - 2, by - 2, bx + w + 2, by, borderColor);
                context.fill(bx - 2, by + h, bx + w + 2, by + h + 2, borderColor);
                context.fill(bx - 2, by, bx, by + h, borderColor);
                context.fill(bx + w, by, bx + w + 2, by + h, borderColor);
            }
            context.text(this.font, e.title(), bx, by - 12, e == selected && extras ? 0xFFFFFF55 : 0xFFFFFFFF);
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
        if (extras && dragging == null) {
            if (flash != null && System.currentTimeMillis() - flashAt < 2500) instructions = flash;
            else if (hovered != null) instructions = hovered.label + " | x " + hovered.x() + " y " + hovered.y()
                    + " | " + String.format("%.2fx", hovered.scale()) + keyHints();
        }
        int textWidth = this.font.width(instructions);
        com.teslamaps.render.gui.HudStyle.screenPanel(context,
                this.width / 2f - textWidth / 2f - 8, 8, textWidth + 16, 18, 9);
        context.centeredText(this.font, instructions, this.width / 2, 12, 0xFFE0E0E0);
        if (com.teslamaps.features.HudLayouts.enabled()) {
            String loc = com.teslamaps.features.HudLayouts.current();
            context.text(this.font, "Layout: §e" + loc + (com.teslamaps.features.HudLayouts.hasOwn(loc) ? "" : " §7(using Default)"),
                    10, this.height - 40, 0xFFFFFFFF);
        }

        if (resetAllBtn != null) resetAllBtn.setMessage(Component.literal(
                System.currentTimeMillis() - resetArmed < 3000 ? "§cConfirm Reset" : "Reset All"));
        if (globalScaleShown()) context.text(this.font, String.format("Global %.2fx", HudEditElement.globalScale()), 90, 13, 0xFFE0E0E0);

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

    private static String keyHints() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        StringBuilder sb = new StringBuilder();
        if (c.hudEditNudge) sb.append(" | Arrows move, +/- scale");
        if (c.hudEditReset) sb.append(" | R reset");
        if (c.hudEditShowDisabled && c.hudEditRightClickToggle) sb.append(" | Right-click on/off");
        if (c.hudEditJump) sb.append(" | Middle-click settings");
        return sb.toString();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && extras() && c.hudEditShowDisabled && c.hudEditRightClickToggle && dragging == null) {
            HudEditElement hit = at((int) event.x(), (int) event.y());
            if (hit != null) {
                rightClick(hit);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
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
        com.teslamaps.features.HudLayouts.editorClosed();
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
