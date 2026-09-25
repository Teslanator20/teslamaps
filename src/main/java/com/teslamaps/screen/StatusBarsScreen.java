package com.teslamaps.screen;

// After Skyblocker's StatusBarsConfigScreen / EditBarWidget, for our health + mana bars
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.PlayerStats;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** /tmap bars: move, resize and restyle the status bars. */
public class StatusBarsScreen extends Screen {

    public static final String[] TEXT_MODES = {"Off", "Left", "Right", "Above", "Center"};
    private static final String[] COLORS = {"E53935", "1E88E5", "43A047", "FDD835", "FB8C00", "8E24AA", "00ACC1", "EEEEEE"};
    private static final String[] COLOR_NAMES = {"Red", "Blue", "Green", "Yellow", "Orange", "Purple", "Cyan", "White"};
    private static final int EDGE = 4, ROW_H = 12, MENU_W = 120;

    private final Screen parent;
    private boolean dragHealth;
    private HudEditElement dragging;
    private boolean resizing;
    private int grabX, grabY;
    private Boolean menuFor; // true = health, false = mana, null = closed
    private int menuX, menuY;

    public StatusBarsScreen(Screen parent) {
        super(Component.literal("Status Bars"));
        this.parent = parent;
    }

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    @Override
    protected void init() {
        addRenderableWidget(new TeslaButton(this.width - 110, 6, 100, 22, "§fDone", this::onClose));
        addRenderableWidget(new TeslaButton(this.width - 220, 6, 100, 22, "§cReset", this::reset));
        ScreenFit.clamp(this);
    }

    private void reset() {
        TeslaMapsConfig c = cfg();
        c.stHealthBarX = 100; c.stHealthBarY = 240; c.stHealthBarScale = 1.0f;
        c.stManaBarX = 100; c.stManaBarY = 250; c.stManaBarScale = 1.0f;
        c.stHealthBarW = -1; c.stManaBarW = -1;
        c.stHealthBarColor = "E53935"; c.stManaBarColor = "1E88E5";
        c.stHealthBarText = "Off"; c.stManaBarText = "Off";
        c.stHealthBarMax = true; c.stManaBarMax = true;
        c.stHealthBarOver = true; c.stManaBarOver = true;
        c.stHealthBarReverse = false; c.stManaBarReverse = false;
        menuFor = null;
        TeslaMapsConfig.save();
    }

    private static HudEditElement el(boolean health) {
        return health ? PlayerStats.healthBar : PlayerStats.manaBar;
    }

    private static boolean on(boolean health) {
        return health ? cfg().statHealthBar : cfg().statManaBar;
    }

    private static int barW(boolean health) {
        return (int) (PlayerStats.barWidth(health) * el(health).scale());
    }

    private static int barH(boolean health) {
        return Math.max(4, (int) (5 * el(health).scale()));
    }

    private static boolean over(boolean health, double mx, double my) {
        HudEditElement e = el(health);
        return mx >= e.x() - 2 && mx <= e.x() + barW(health) + 2 && my >= e.y() - 3 && my <= e.y() + barH(health) + 3;
    }

    private static boolean overEdge(boolean health, double mx, double my) {
        return over(health, mx, my) && mx >= el(health).x() + barW(health) - EDGE;
    }

    private List<String> menu(boolean h) {
        TeslaMapsConfig c = cfg();
        String color = h ? c.stHealthBarColor : c.stManaBarColor;
        String name = "Custom";
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i].equalsIgnoreCase(color)) name = COLOR_NAMES[i];
        return List.of(
                "Enabled: " + (on(h) ? "§aon" : "§coff"),
                "Color: §f" + name,
                "Text: §f" + (h ? c.stHealthBarText : c.stManaBarText),
                "Show Max: " + ((h ? c.stHealthBarMax : c.stManaBarMax) ? "§aon" : "§coff"),
                "Overflow: " + ((h ? c.stHealthBarOver : c.stManaBarOver) ? "§aon" : "§coff"),
                "Direction: §f" + ((h ? c.stHealthBarReverse : c.stManaBarReverse) ? "right to left" : "left to right"));
    }

    private void apply(boolean h, int row) {
        TeslaMapsConfig c = cfg();
        switch (row) {
            case 0 -> { if (h) c.statHealthBar = !c.statHealthBar; else c.statManaBar = !c.statManaBar; }
            case 1 -> {
                String cur = h ? c.stHealthBarColor : c.stManaBarColor;
                int i = 0;
                for (int k = 0; k < COLORS.length; k++) if (COLORS[k].equalsIgnoreCase(cur)) i = k + 1;
                String next = COLORS[i % COLORS.length];
                if (h) c.stHealthBarColor = next; else c.stManaBarColor = next;
            }
            case 2 -> {
                String cur = h ? c.stHealthBarText : c.stManaBarText;
                int i = 0;
                for (int k = 0; k < TEXT_MODES.length; k++) if (TEXT_MODES[k].equals(cur)) i = k + 1;
                String next = TEXT_MODES[i % TEXT_MODES.length];
                if (h) c.stHealthBarText = next; else c.stManaBarText = next;
            }
            case 3 -> { if (h) c.stHealthBarMax = !c.stHealthBarMax; else c.stManaBarMax = !c.stManaBarMax; }
            case 4 -> { if (h) c.stHealthBarOver = !c.stHealthBarOver; else c.stManaBarOver = !c.stManaBarOver; }
            case 5 -> { if (h) c.stHealthBarReverse = !c.stHealthBarReverse; else c.stManaBarReverse = !c.stManaBarReverse; }
            default -> {}
        }
        TeslaMapsConfig.save();
    }

    private int menuRow(double mx, double my) {
        if (menuFor == null) return -1;
        if (mx < menuX || mx > menuX + MENU_W || my < menuY + 14) return -1;
        int row = (int) ((my - menuY - 14) / ROW_H);
        return row < 6 ? row : -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (menuFor != null) {
            int row = menuRow(mx, my);
            if (row >= 0) {
                apply(menuFor, row);
                return true;
            }
            boolean inMenu = mx >= menuX && mx <= menuX + MENU_W && my >= menuY && my <= menuY + 14 + 6 * ROW_H;
            menuFor = null;
            if (inMenu) return true;
        }
        for (boolean h : new boolean[]{false, true}) {
            if (!over(h, mx, my)) continue;
            if (event.button() == 1) {
                menuFor = h;
                menuX = (int) Math.min(mx, this.width - MENU_W - 4);
                menuY = (int) Math.min(my, this.height - 14 - 6 * ROW_H - 4);
                return true;
            }
            if (event.button() == 0) {
                el(h).bakeShift();
                dragging = el(h);
                dragHealth = h;
                resizing = overEdge(h, mx, my);
                grabX = (int) mx - dragging.x();
                grabY = (int) my - dragging.y();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(event, dx, dy);
        double mx = event.x(), my = event.y();
        if (resizing) {
            int w = (int) Math.round((mx - dragging.x()) / dragging.scale());
            w = Math.max(20, Math.min(400, w));
            if (dragHealth) cfg().stHealthBarW = w; else cfg().stManaBarW = w;
        } else {
            dragging.setX(Math.max(0, Math.min(this.width - barW(dragHealth), (int) mx - grabX)));
            dragging.setY(Math.max(0, Math.min(this.height - barH(dragHealth), (int) my - grabY)));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging.recordAnchor();
            dragging = null;
            TeslaMapsConfig.save();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        for (boolean b : new boolean[]{false, true}) {
            if (over(b, mx, my)) {
                el(b).scroll(v);
                TeslaMapsConfig.save();
                return true;
            }
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0xC0000000);
        for (boolean h : new boolean[]{true, false}) {
            HudEditElement e = el(h);
            PlayerStats.drawPreview(g, h, e.x(), e.y(), e.scale());
            int bw = barW(h), bh = barH(h);
            if (!on(h)) g.fill(e.x() - 1, e.y() - 1, e.x() + bw + 1, e.y() + bh + 1, 0xA0000000);
            boolean hover = over(h, mouseX, mouseY) || (dragging == e);
            if (hover) {
                int col = dragging == e ? 0xFFFF5555 : 0xFF888888;
                g.fill(e.x() - 2, e.y() - 2, e.x() + bw + 2, e.y() - 1, col);
                g.fill(e.x() - 2, e.y() + bh + 1, e.x() + bw + 2, e.y() + bh + 2, col);
                if (overEdge(h, mouseX, mouseY) || (dragging == e && resizing))
                    g.fill(e.x() + bw - 1, e.y() - 3, e.x() + bw + 1, e.y() + bh + 3, 0xFFFFFF55);
            }
            g.text(this.font, (h ? "Health" : "Mana") + (on(h) ? "" : " §8(off)"), e.x(), e.y() - 12, 0xFFFFFFFF);
        }

        String hint = "Drag to move | drag the right edge for width | scroll to scale | right-click for options";
        int tw = this.font.width(hint);
        HudStyle.screenPanel(g, this.width / 2f - tw / 2f - 8, 34, tw + 16, 18, 9);
        g.centeredText(this.font, hint, this.width / 2, 39, 0xFFE0E0E0);
        if (!cfg().playerStatHuds) {
            String warn = "§cPlayer Stat HUDs is off (Interface > Player Stats)";
            g.centeredText(this.font, warn, this.width / 2, 58, 0xFFFFFFFF);
        }

        if (menuFor != null) {
            List<String> rows = menu(menuFor);
            int mh = 14 + rows.size() * ROW_H + 2;
            HudStyle.screenPanel(g, menuX, menuY, MENU_W, mh, 6);
            g.text(this.font, menuFor ? "§lHealth Bar" : "§lMana Bar", menuX + 5, menuY + 4, 0xFFFFFFFF);
            int hoverRow = menuRow(mouseX, mouseY);
            for (int i = 0; i < rows.size(); i++) {
                int ry = menuY + 14 + i * ROW_H;
                if (i == hoverRow) g.fill(menuX + 2, ry, menuX + MENU_W - 2, ry + ROW_H, 0x40FFFFFF);
                g.text(this.font, rows.get(i), menuX + 5, ry + 2, 0xFFBBBBBB);
            }
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        if (parent != null) minecraft.setScreen(parent);
        else super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
