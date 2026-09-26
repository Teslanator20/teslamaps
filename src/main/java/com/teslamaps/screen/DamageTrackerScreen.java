package com.teslamaps.screen;

import com.teslamaps.features.DamageTracker;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class DamageTrackerScreen extends Screen {
    private static final int PANEL_W = 340, ROW_H = 14;
    private static final int[] COL = {16, 100, 150, 220, 280};

    private final Screen parent;
    private int panelX, panelY, panelH;
    private final long[] count = new long[DamageTracker.Type.values().length];
    private final long[] sum = new long[count.length];
    private final long[] max = new long[count.length];
    private long totalCount, totalSum, totalMax;

    public DamageTrackerScreen(Screen parent) {
        super(Component.literal("Damage Tracker"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        compute();
        panelH = 60 + (count.length + 1) * ROW_H + 40;
        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;
        addRenderableWidget(new TeslaButton(panelX + 16, panelY + panelH - 30, 90, 20, "§fReset", () -> {
            DamageTracker.reset();
            compute();
        }));
        addRenderableWidget(new TeslaButton(panelX + PANEL_W - 106, panelY + panelH - 30, 90, 20, "§fDone", this::onClose));
        ScreenFit.clamp(this);
    }

    private void compute() {
        java.util.Arrays.fill(count, 0);
        java.util.Arrays.fill(sum, 0);
        java.util.Arrays.fill(max, 0);
        totalCount = totalSum = totalMax = 0;
        for (DamageTracker.Hit h : DamageTracker.HITS) {
            int i = h.type().ordinal();
            count[i]++;
            sum[i] += h.damage();
            max[i] = Math.max(max[i], h.damage());
            totalCount++;
            totalSum += h.damage();
            totalMax = Math.max(totalMax, h.damage());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, panelH, 8);
        ctx.text(font, "§f§lDamage Tracker", panelX + 16, panelY + 14, 0xFFFFFFFF);
        ctx.text(font, "§8last " + totalCount + " hits (max 1000)", panelX + 16, panelY + 28, 0xFFFFFFFF);
        int y = panelY + 46;
        row(ctx, y, "§7Type", "§7Hits", "§7Total", "§7Max", "§7Avg");
        y += ROW_H;
        DamageTracker.Type[] types = DamageTracker.Type.values();
        for (int i = 0; i < types.length; i++, y += ROW_H) {
            row(ctx, y, types[i].label, String.valueOf(count[i]), fmt(sum[i]), fmt(max[i]),
                    count[i] == 0 ? "-" : fmt(sum[i] / count[i]));
        }
        row(ctx, y, "§fAll", String.valueOf(totalCount), fmt(totalSum), fmt(totalMax),
                totalCount == 0 ? "-" : fmt(totalSum / totalCount));
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void row(GuiGraphicsExtractor ctx, int y, String... cells) {
        for (int i = 0; i < cells.length; i++) ctx.text(font, cells[i], panelX + COL[i], y, 0xFFFFFFFF);
    }

    private static String fmt(long v) {
        if (v >= 1_000_000_000L) return String.format(Locale.ROOT, "%.2fB", v / 1e9);
        if (v >= 1_000_000L) return String.format(Locale.ROOT, "%.2fM", v / 1e6);
        return String.format(Locale.ROOT, "%,d", v);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
