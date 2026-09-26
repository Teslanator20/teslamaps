package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings as free columns, one per category (FrankyAddons' ModMenuScreen idea): drag by the header,
 * scroll while dragging to scale, right-click the header to fold. Rows are the classic screen's entries.
 */
public class ColumnConfigScreen extends MapConfigScreen {
    private static final int COL_W = 190, HEAD = 18, PAD = 4;
    private static final float MIN_SCALE = 0.5f, MAX_SCALE = 2.0f;

    private final List<String> order = new ArrayList<>();
    private final List<TeslaButton> buttons = new ArrayList<>();
    private final Map<String, Integer> bodyScroll = new HashMap<>();
    private final Map<String, float[]> velocity = new HashMap<>();
    private String dragging, pressCol;
    private float dragDx, dragDy, lastMx, lastMy, velX, velY;
    private long descStart;
    private MapConfigScreen.SettingsEntry descEntry;
    private String descFor;
    private List<net.minecraft.util.FormattedCharSequence> descLines;

    @Override
    protected void init() {
        String jump = jumpCategory;
        jumpCategory = jumpQuery = null;
        super.init();
        if (searchField != null) {
            removeWidget(searchField);
            searchField = null;
        }
        order.removeIf(c -> !categories.containsKey(c));
        for (String c : categories.keySet()) if (!order.contains(c)) order.add(c);
        int n = 0;
        int perRow = Math.max(1, (width - 20) / (COL_W + 8));
        for (String c : order) {
            if (!TeslaMapsConfig.get().configColumns.containsKey(c)) {
                int col = n % perRow, row = n / perRow;
                TeslaMapsConfig.get().configColumns.put(c, new float[]{10 + col * (COL_W + 8), 10 + row * (HEAD + 6), 1f, 1f});
            }
            n++;
        }
        if (jump != null && categories.containsKey(jump)) {
            state(jump)[3] = 0f;
            toFront(jump);
        }
        int by = height - 26;
        buttons.clear();
        btn(new TeslaButton(width - 70, by, 60, 20, "Done", this::onClose));
        btn(new TeslaButton(width - 176, by, 100, 20, "Classic Layout", () -> {
            TeslaMapsConfig.get().configColumnLayout = false;
            TeslaMapsConfig.save();
            minecraft.setScreen(MapConfigScreen.open(view));
        }));
        btn(new TeslaButton(width - 262, by, 80, 20, "Reset Layout", () -> {
            TeslaMapsConfig.get().configColumns.clear();
            TeslaMapsConfig.save();
            rebuildWidgets();
        }));
        ScreenFit.clamp(this);
    }

    private void btn(TeslaButton b) {
        buttons.add(addRenderableWidget(b));
    }

    private float[] state(String cat) {
        return TeslaMapsConfig.get().configColumns.computeIfAbsent(cat, k -> new float[]{10, 10, 1f, 1f});
    }

    private List<SettingsEntry> rows(String cat) {
        List<SettingsEntry> all = categories.get(cat);
        List<SettingsEntry> out = new ArrayList<>();
        if (all == null || all.isEmpty()) return out;
        if (all.get(0) instanceof ToggleEntry master && master.getLabel().equals("Enable " + cat) && !master.get()) {
            out.add(master);
            return out;
        }
        int ord = 0;
        boolean hidden = false;
        LabelEntry group = null;
        for (SettingsEntry e : visibleChildren(all)) {
            if (e instanceof LabelEntry le) {
                le.reposition(12, COL_W - 16);
                le.collapseKey = collapsePrefix() + cat + "#" + (ord++);
                le.childCount = 0;
                group = le;
                hidden = GROUP_COLLAPSED.contains(le.collapseKey);
                out.add(e);
            } else {
                if (group != null) group.childCount++;
                if (!hidden) out.add(e);
            }
        }
        return out;
    }

    private int bodyHeight(String cat, List<SettingsEntry> rows, float[] s) {
        int total = 0;
        for (SettingsEntry e : rows) total += e.getHeight();
        int room = (int) ((height - s[1]) / s[2]) - HEAD - PAD - 2;
        return Math.max(40, Math.min(total, room));
    }

    private boolean overHeader(String cat, double mx, double my) {
        float[] s = state(cat);
        return mx >= s[0] && mx < s[0] + COL_W * s[2] && my >= s[1] && my < s[1] + HEAD * s[2];
    }

    private boolean overColumn(String cat, double mx, double my) {
        float[] s = state(cat);
        float h = HEAD;
        if (s[3] < 0.5f) h += bodyHeight(cat, rows(cat), s) + PAD;
        return mx >= s[0] && mx < s[0] + COL_W * s[2] && my >= s[1] && my < s[1] + h * s[2];
    }

    private String topAt(double mx, double my) {
        for (int i = order.size() - 1; i >= 0; i--) if (overColumn(order.get(i), mx, my)) return order.get(i);
        return null;
    }

    private boolean overWidget(double mx, double my) {
        for (var c : children()) if (c.isMouseOver(mx, my)) return true;
        return false;
    }

    private void toFront(String cat) {
        order.remove(cat);
        order.add(cat);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        long win = minecraft.getWindow().handle();
        boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rightDown = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean clicked = down && !wasMouseDown;
        if (clicked && com.teslamaps.features.QuickModSwitch.covers(this, mouseX, mouseY)) clicked = false;
        boolean rightClicked = rightDown && !wasRightDown;
        wasRightDown = rightDown;
        boolean onWidget = overWidget(mouseX, mouseY);
        String top = onWidget ? null : topAt(mouseX, mouseY);

        if (clicked) pressCol = top;
        if (!down) pressCol = null;
        if (clicked && top != null && overHeader(top, mouseX, mouseY)) {
            float[] s = state(top);
            dragging = top;
            dragDx = mouseX - s[0];
            dragDy = mouseY - s[1];
            velocity.remove(top);
            toFront(top);
            clicked = false;
        } else if (clicked && top != null) {
            toFront(top);
        }
        if (rightClicked && top != null && overHeader(top, mouseX, mouseY)) {
            float[] s = state(top);
            s[3] = s[3] < 0.5f ? 1f : 0f;
            GuiSounds.fold(s[3] < 0.5f);
            TeslaMapsConfig.save();
            rightClicked = false;
        }
        if (dragging != null) {
            float[] s = state(dragging);
            if (down) {
                s[0] = Math.max(0, Math.min(width - 20, mouseX - dragDx));
                s[1] = Math.max(0, Math.min(height - 12, mouseY - dragDy));
                velX = velX * 0.5f + (mouseX - lastMx) * 0.5f;
                velY = velY * 0.5f + (mouseY - lastMy) * 0.5f;
            } else {
                if (TeslaMapsConfig.get().configColumnFling && Math.abs(velX) + Math.abs(velY) > 2) {
                    velocity.put(dragging, new float[]{velX, velY});
                }
                dragging = null;
                TeslaMapsConfig.save();
            }
        }
        lastMx = mouseX;
        lastMy = mouseY;
        fling();

        g.fill(0, 0, width, height, GuiStyle.background());
        clickHandled = false;
        rightClickedNow = rightClicked;
        SettingsEntry hover = null;
        for (String cat : order) {
            boolean holding = down && cat.equals(pressCol);
            boolean active = dragging == null && (holding || (pressCol == null && cat.equals(top)));
            SettingsEntry h = drawColumn(g, cat, mouseX, mouseY, active && clicked, active && down, active);
            if (h != null) hover = h;
        }
        if (clicked && !clickHandled) editingList = null;
        wasMouseDown = down;

        String hint = "Drag headers | Scroll while dragging to scale | Right-click header to fold";
        if (view == View.NEW) {
            g.text(font, "TeslaMaps — New Features", 10, height - 32, 0xFFFFAA00);
        }
        g.text(font, hint, 10, height - 20, GuiStyle.textFaint());
        tooltip(g, hover, mouseX, mouseY, down);
        GuiSounds.endFrame();
        for (TeslaButton b : buttons) b.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void tooltip(GuiGraphicsExtractor g, SettingsEntry hover, int mx, int my, boolean down) {
        String desc = hover != null ? hover.description() : null;
        if (desc == null || hover != descEntry) {
            descEntry = desc == null ? null : hover;
            descStart = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - descStart >= 500 && !down) {
            if (!desc.equals(descFor)) {
                descFor = desc;
                descLines = font.split(Component.literal(desc), 220);
            }
            g.setTooltipForNextFrame(font, descLines, mx, my);
        }
    }

    private void fling() {
        if (velocity.isEmpty()) return;
        boolean stopped = false;
        for (var it = velocity.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            float[] s = state(en.getKey()), v = en.getValue();
            float w = COL_W * s[2], h = HEAD * s[2];
            s[0] += v[0];
            s[1] += v[1];
            if (s[0] < 0) { s[0] = 0; v[0] = -v[0]; }
            if (s[0] + w > width) { s[0] = width - w; v[0] = -v[0]; }
            if (s[1] < 0) { s[1] = 0; v[1] = -v[1]; }
            if (s[1] + h > height) { s[1] = height - h; v[1] = -v[1]; }
            v[0] *= 0.94f;
            v[1] *= 0.94f;
            if (Math.abs(v[0]) + Math.abs(v[1]) < 0.3f) { it.remove(); stopped = true; }
        }
        if (stopped) TeslaMapsConfig.save();
    }

    /** @return the hovered row, for its description. */
    private SettingsEntry drawColumn(GuiGraphicsExtractor g, String cat, int mouseX, int mouseY,
                                     boolean clicked, boolean down, boolean active) {
        float[] s = state(cat);
        float sc = s[2];
        int lmx = active ? (int) ((mouseX - s[0]) / sc) : -10000;
        int lmy = active ? (int) ((mouseY - s[1]) / sc) : -10000;
        boolean open = s[3] < 0.5f;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(s[0], s[1]);
        pose.scale(sc, sc);

        boolean headHover = lmx >= 0 && lmx < COL_W && lmy >= 0 && lmy < HEAD;
        if (headHover) GuiSounds.hover("col:" + cat);
        GuiStyle.panel(g, 0, 0, COL_W, HEAD, headHover || cat.equals(dragging));
        int caret = headHover ? 0xFFFFFFFF : GuiStyle.textDim();
        if (open) for (int i = 0; i < 4; i++) g.fill(6 + i, 7 + i, 13 - i, 8 + i, caret);
        else for (int i = 0; i < 4; i++) g.fill(7 + i, 5 + i, 8 + i, 12 - i, caret);
        g.text(font, fitLabel(cat, 18, COL_W - 4), 18, 5, cat.equals(dragging) ? GuiStyle.accent() : 0xFFFFFFFF);

        SettingsEntry hover = null;
        if (open) {
            List<SettingsEntry> rows = rows(cat);
            int bodyH = bodyHeight(cat, rows, s);
            int total = 0;
            for (SettingsEntry e : rows) total += e.getHeight();
            int max = Math.max(0, total - bodyH);
            int scroll = Math.min(bodyScroll.getOrDefault(cat, 0), max);
            bodyScroll.put(cat, scroll);
            int top = HEAD + 2;
            g.fill(0, top, COL_W, top + bodyH + PAD, GuiStyle.sidebar());
            boolean inBody = lmx >= 0 && lmx < COL_W && lmy >= top && lmy < top + bodyH;
            int ex = inBody || down ? lmx : -10000, ey = inBody || down ? lmy : -10000;
            g.enableScissor(0, top, COL_W, top + bodyH);
            int y = top - scroll;
            for (SettingsEntry e : rows) {
                if (!(e instanceof LabelEntry)) e.reposition(4, COL_W - 10);
                int eh = e.getHeight();
                if (y + eh > top - 40 && y < top + bodyH + 40) {
                    e.render(g, this, y, ex, ey, clicked && inBody && !clickHandled, down);
                }
                if (inBody && ey >= y && ey < y + Math.min(eh, ROW_HEIGHT + 4)) hover = e;
                y += eh;
            }
            g.disableScissor();
            if (max > 0) {
                int thumb = Math.max(12, bodyH * bodyH / total);
                int ty = top + (int) ((bodyH - thumb) * (scroll / (float) max));
                g.fill(COL_W - 3, ty, COL_W - 1, ty + thumb, GuiStyle.scrollThumb());
            }
        }
        pose.popMatrix();
        return hover;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (dragging != null) {
            float[] s = state(dragging);
            s[2] = Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.round((s[2] + (v > 0 ? 0.1f : -0.1f)) * 10f) / 10f));
            return true;
        }
        String top = topAt(mx, my);
        if (top == null || state(top)[3] >= 0.5f) return true;
        float[] s = state(top);
        int lmx = (int) ((mx - s[0]) / s[2]), lmy = (int) ((my - s[1]) / s[2]);
        int y = HEAD + 2 - bodyScroll.getOrDefault(top, 0);
        for (SettingsEntry e : rows(top)) {
            int eh = e.getHeight();
            if (lmy >= y && lmy < y + eh) {
                if (e.scrollAt(lmx, lmy, y, v)) return true;
                break;
            }
            y += eh;
        }
        bodyScroll.put(top, (int) Math.max(0, bodyScroll.getOrDefault(top, 0) - v * 15));
        return true;
    }
}
