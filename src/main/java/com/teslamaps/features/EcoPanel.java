package com.teslamaps.features;

import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/** Text panel beside a container: rows built in the tick, hover tips, slot highlight, click actions. */
public final class EcoPanel {

    public record Row(String text, List<Component> tip, Runnable click, int slot) {
        public static Row of(String text) { return new Row(text, null, null, -1); }
    }

    public enum Side { LEFT, RIGHT }

    private static final int LINE = 10;

    private volatile List<Row> rows = List.of();
    private int width;
    private final Side side;
    private int maxRows = 60;
    private int scroll;
    private int drawX, drawY, drawN;

    public EcoPanel(Side side) {
        this.side = side;
    }

    public EcoPanel maxRows(int n) { this.maxRows = n; return this; }

    public void set(List<Row> r) {
        var font = Minecraft.getInstance().font;
        int w = 0;
        for (Row row : r) w = Math.max(w, font.width(row.text()));
        width = w;
        rows = r;
        if (scroll > Math.max(0, r.size() - 1)) scroll = 0;
    }

    public void clear() {
        if (!rows.isEmpty()) rows = List.of();
        scroll = 0;
    }

    public boolean empty() { return rows.isEmpty(); }

    public static List<Component> tip(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String s : lines) out.add(Component.literal(s));
        return out;
    }

    /** Draws beside the container, {@code yOffset} below its top. Returns the height drawn. */
    public int render(GuiGraphicsExtractor g, AbstractContainerScreen<?> cs, int yOffset, int mx, int my) {
        List<Row> r = rows;
        if (r.isEmpty()) return 0;
        Minecraft mc = Minecraft.getInstance();
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int pad = HudStyle.padding();
        int x = side == Side.LEFT ? Math.max(pad + 2, acc.getX() - 8 - width) : acc.getX() + acc.getImageWidth() + 8;
        int y = Math.max(pad + 2, acc.getY() + yOffset);
        int fit = Math.max(3, (mc.getWindow().getGuiScaledHeight() - y - pad) / LINE);
        int n = Math.min(Math.min(r.size(), fit), maxRows);
        int start = r.size() > n ? Math.min(scroll, r.size() - n) : 0;
        HudStyle.panel(g, x - pad, y - pad, width + pad * 2, n * LINE + pad * 2);
        for (int i = 0; i < n; i++) g.text(mc.font, r.get(start + i).text(), x, y + i * LINE, 0xFFFFFFFF, true);
        drawX = x;
        drawY = y;
        drawN = n;

        int idx = rowAt(mx, my);
        if (idx >= 0) {
            Row row = r.get(idx);
            int ry = y + (idx - start) * LINE;
            if (row.click() != null) g.fill(x - 1, ry - 1, x + width + 1, ry + LINE - 1, 0x30FFFFFF);
            if (row.slot() >= 0 && row.slot() < cs.getMenu().slots.size()) {
                Slot s = cs.getMenu().slots.get(row.slot());
                int sx = acc.getX() + s.x, sy = acc.getY() + s.y;
                g.fill(sx, sy, sx + 16, sy + 16, 0x80FFAA00);
            }
            if (row.tip() != null && !row.tip().isEmpty()) g.setComponentTooltipForNextFrame(mc.font, row.tip(), mx, my);
        }
        return n * LINE + pad * 2 + 4;
    }

    private int rowAt(double mx, double my) {
        List<Row> r = rows;
        if (r.isEmpty() || drawN == 0 || mx < drawX - 1 || mx > drawX + width + 1 || my < drawY - 1) return -1;
        int i = (int) ((my - drawY + 1) / LINE);
        if (i < 0 || i >= drawN) return -1;
        int start = r.size() > drawN ? Math.min(scroll, r.size() - drawN) : 0;
        int idx = start + i;
        return idx < r.size() ? idx : -1;
    }

    public boolean click(double mx, double my, int button) {
        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        int idx = rowAt(mx, my);
        if (idx < 0) return false;
        Row row = rows.get(idx);
        if (row.click() == null) return false;
        row.click().run();
        return true;
    }

    public boolean scroll(double mx, double my, double dy) {
        List<Row> r = rows;
        if (r.size() <= drawN || rowAt(mx, my) < 0) return false;
        scroll = Math.max(0, Math.min(r.size() - drawN, scroll - (int) Math.signum(dy) * 3));
        return true;
    }

    /** Explicit click: /bz for bazaar items, /ahs otherwise. */
    public static void shop(String id, String name, long amount) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) return;
        String plain = com.teslamaps.utils.StringUtil.stripTrim(name);
        if (id != null && com.teslamaps.features.croesus.PriceManager.hasBazaar(id)) {
            mc.getConnection().sendCommand("bz " + plain);
            if (amount > 0) mc.keyboardHandler.setClipboard(String.valueOf(amount));
        } else {
            mc.getConnection().sendCommand("ahs " + plain);
        }
    }
}
