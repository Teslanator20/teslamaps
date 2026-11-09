package com.teslamaps.screen;

import com.teslamaps.features.EstimatedValue;
import com.teslamaps.features.TradeHistory;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /tmap tradehistory - saved trades grouped by day, with a search. */
public class TradeHistoryScreen extends Screen {

    private static final int LINE_H = 11, MARGIN = 10;
    private static String lastQuery = "";

    private final Screen parent;
    private EditBox field;
    private List<String> lines = new ArrayList<>();
    private int scroll, visible;
    private int panelX, panelY, panelW, panelH, listY;
    private boolean confirmClear;

    public TradeHistoryScreen(Screen parent) {
        super(Component.literal("Trade History"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(width - MARGIN * 2, 420);
        panelH = Math.min(height - MARGIN * 2, 300);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        listY = panelY + 56;
        visible = Math.max(1, (panelH - 56 - 12) / LINE_H);

        field = new EditBox(font, panelX + 12, panelY + 32, panelW - 24, 16, Component.literal("Search"));
        field.setHint(Component.literal("Player or item..."));
        field.setMaxLength(64);
        field.setResponder(q -> {
            lastQuery = q;
            scroll = 0;
            rebuild();
        });
        field.setValue(lastQuery);
        addRenderableWidget(field);
        setInitialFocus(field);
        addRenderableWidget(new TeslaButton(panelX + panelW - 12 - 70, panelY + 8, 70, 16, "§cClear All", () -> {
            if (!confirmClear) {
                confirmClear = true;
                return;
            }
            confirmClear = false;
            TradeHistory.clear();
            rebuild();
        }));
        rebuild();
        ScreenFit.clamp(this);
    }

    private void rebuild() {
        String q = lastQuery.toLowerCase(Locale.ROOT).trim();
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        SimpleDateFormat time = new SimpleDateFormat("HH:mm", Locale.ROOT);
        Map<String, List<TradeHistory.Trade>> byDay = new LinkedHashMap<>();
        for (TradeHistory.Trade t : TradeHistory.all()) {
            if (!q.isEmpty() && !matches(t, q)) continue;
            byDay.computeIfAbsent(day.format(new Date(t.time)), k -> new ArrayList<>()).add(t);
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, List<TradeHistory.Trade>> e : byDay.entrySet()) {
            double balance = 0;
            for (TradeHistory.Trade t : e.getValue()) balance += t.profit();
            out.add("§e§l" + e.getKey() + " §7(" + e.getValue().size() + " trades, balance " + signed(balance) + "§7)");
            for (TradeHistory.Trade t : e.getValue()) {
                out.add(" §7" + time.format(new Date(t.time)) + " §fwith §b" + t.partner + " §7profit " + signed(t.profit()));
                out.add("   §cYou Gave §7(§6" + EstimatedValue.fmt(t.gaveValue()) + "§7):");
                items(out, t.gave);
                out.add("   §aYou Received §7(§6" + EstimatedValue.fmt(t.receivedValue()) + "§7):");
                items(out, t.received);
            }
            out.add("");
        }
        if (out.isEmpty()) out.add(TradeHistory.all().isEmpty() ? "§7No trades saved yet." : "§7Nothing matches.");
        lines = out;
    }

    private static void items(List<String> out, List<TradeHistory.Item> items) {
        if (items.isEmpty()) out.add("     §8nothing");
        for (TradeHistory.Item i : items) {
            out.add("     §f" + i.name + (i.count > 1 && !i.coins ? " §8x" + i.count : "")
                    + (i.value > 0 && !i.coins ? " §7(§6" + EstimatedValue.fmt(i.value) + "§7)" : ""));
        }
    }

    private static boolean matches(TradeHistory.Trade t, String q) {
        if (t.partner.toLowerCase(Locale.ROOT).contains(q)) return true;
        for (TradeHistory.Item i : t.gave) if (i.name != null && i.name.toLowerCase(Locale.ROOT).contains(q)) return true;
        for (TradeHistory.Item i : t.received) if (i.name != null && i.name.toLowerCase(Locale.ROOT).contains(q)) return true;
        return false;
    }

    private static String signed(double v) {
        return (v >= 0 ? "§a+" : "§c-") + EstimatedValue.fmt(Math.abs(v));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, lines.size() - visible);
        scroll = Math.clamp(scroll - (int) Math.signum(sy) * 3, 0, max);
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, panelW, panelH, 8);
        ctx.text(font, "§f§lTrade History", panelX + 12, panelY + 12, 0xFFFFFFFF);
        if (confirmClear) ctx.text(font, "§cClick again to delete all", panelX + panelW - 12 - 70 - 8 - font.width("Click again to delete all"), panelY + 12, 0xFFFFFFFF);
        for (int i = 0; i < visible; i++) {
            int idx = scroll + i;
            if (idx >= lines.size()) break;
            ctx.text(font, lines.get(idx), panelX + 12, listY + i * LINE_H, 0xFFFFFFFF);
        }
        if (lines.size() > visible) {
            String pos = "§8" + (scroll + 1) + "-" + Math.min(lines.size(), scroll + visible) + "/" + lines.size();
            ctx.text(font, pos, panelX + panelW - 12 - font.width(pos), panelY + panelH - 12, 0xFF808080);
        }
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
