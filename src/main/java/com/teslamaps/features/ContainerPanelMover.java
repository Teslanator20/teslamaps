package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Alt + drag moves the panels drawn next to container menus (Croesus profit, shard overlay).
 * Stored as an offset from their default spot and kept on screen. Alt + right click resets.
 */
public final class ContainerPanelMover {

    public static final int CROESUS = 0, SHARD = 1;
    private static final int COUNT = 2;
    private static final long VISIBLE_MS = 200;

    private static final int[][] bounds = new int[COUNT][4];
    private static final long[] drawnAt = new long[COUNT];
    private static int dragging = -1, grabX, grabY, startDX, startDY;

    private ContainerPanelMover() {}

    public static boolean on() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.containerPanelDrag && c.hudDragInInventory;
    }

    private static int dx(int id) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return id == CROESUS ? c.croesusPanelDX : c.shardPanelDX;
    }

    private static int dy(int id) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return id == CROESUS ? c.croesusPanelDY : c.shardPanelDY;
    }

    private static void set(int id, int x, int y) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (id == CROESUS) { c.croesusPanelDX = x; c.croesusPanelDY = y; }
        else { c.shardPanelDX = x; c.shardPanelDY = y; }
    }

    /** Outer left edge after the offset, kept on screen. Unchanged while the option is off. */
    public static int x(int id, int x, int w) {
        if (!on()) return x;
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        return Math.clamp(x + dx(id), 0, Math.max(0, sw - w));
    }

    public static int y(int id, int y, int h) {
        if (!on()) return y;
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return Math.clamp(y + dy(id), 0, Math.max(0, sh - h));
    }

    /** Outer rect as drawn this frame. */
    public static void record(int id, int x, int y, int w, int h) {
        int[] b = bounds[id];
        b[0] = x; b[1] = y; b[2] = w; b[3] = h;
        drawnAt[id] = System.currentTimeMillis();
    }

    private static int at(double mx, double my) {
        long now = System.currentTimeMillis();
        for (int i = COUNT - 1; i >= 0; i--) {
            if (now - drawnAt[i] > VISIBLE_MS) continue;
            int[] b = bounds[i];
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) return i;
        }
        return -1;
    }

    /** From the container mouseClicked HEAD. */
    public static boolean click(double mx, double my, int button) {
        if (!on() || !Minecraft.getInstance().hasAltDown()) return false;
        int id = at(mx, my);
        if (id < 0) return false;
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
            set(id, 0, 0);
            TeslaMapsConfig.save();
            return true;
        }
        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        dragging = id;
        grabX = (int) mx;
        grabY = (int) my;
        startDX = dx(id);
        startDY = dy(id);
        return true;
    }

    /** From ContainerOverlays after the panels: follows a held drag and outlines the hovered panel. */
    public static void render(GuiGraphicsExtractor ctx, int mx, int my) {
        if (!on()) {
            dragging = -1;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (dragging >= 0) {
            boolean down = com.teslamaps.utils.MouseState.left();
            if (!down) {
                dragging = -1;
                TeslaMapsConfig.save();
            } else {
                set(dragging, startDX + mx - grabX, startDY + my - grabY);
            }
        }
        if (dragging < 0 && !mc.hasAltDown()) return;
        int id = dragging >= 0 ? dragging : at(mx, my);
        if (id < 0) return;
        int[] b = bounds[id];
        int col = 0xFFFFFF55;
        ctx.fill(b[0], b[1], b[0] + b[2], b[1] + 1, col);
        ctx.fill(b[0], b[1] + b[3] - 1, b[0] + b[2], b[1] + b[3], col);
        ctx.fill(b[0], b[1], b[0] + 1, b[1] + b[3], col);
        ctx.fill(b[0] + b[2] - 1, b[1], b[0] + b[2], b[1] + b[3], col);
    }
}
