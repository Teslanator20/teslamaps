package com.teslamaps.features;

// After SkyHanni's OutsideSackValue
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Value of everything in the sacks, insta-sell to insta-buy, shown outside the sack menus. */
public final class SackValueHud {

    private static final int LINE = 10;
    private static final int EXPANDED_ROWS = 20;

    private static HudEditElement hud;
    private static String header = "";
    private static int headerW, perSackW;
    private static boolean expanded;
    private static int lastChanges = -1, lastPrices = -1, ticks;
    private static int boxX, boxY, boxW, boxH;

    private SackValueHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("sackValueHud", "Sack Value").section("Inventory")
                .enabled(() -> cfg().sackValueHud)
                .size(() -> Math.max(120, headerW), () -> LINE)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s,
                        List.of("§eSacks: §61.2M§7-§61.5M §7(12,345 items)"), 160));
        Feature.of("SackValueHud").section("Inventory").when(() -> cfg().sackValueHud)
                .tick(SackValueHud::tick)
                .hudForeground("sackvaluehud", SackValueHud::render);
    }

    private static void tick() {
        if (ticks++ % 20 != 0) return;
        int ch = SackApi.changes(), pv = PriceManager.version();
        if (ch == lastChanges && pv == lastPrices && ticks % 100 != 1) return;
        lastChanges = ch;
        lastPrices = pv;
        PriceManager.ensureFresh();
        build(Minecraft.getInstance());
    }

    private static void build(Minecraft mc) {
        Map<String, Long> amounts = SackApi.amounts();
        Map<String, String> sackOf = SackApi.sackNames();
        Map<String, double[]> sacks = new HashMap<>();
        double min = 0, max = 0;
        long items = 0;
        for (Map.Entry<String, Long> e : amounts.entrySet()) {
            long amount = e.getValue();
            if (amount <= 0) continue;
            String id = PriceManager.idForName(e.getKey());
            if (id == null || !PriceManager.hasBazaar(id)) continue;
            Double sell = PriceManager.bazaarSellOrNull(id), buy = PriceManager.bazaarBuyOrNull(id);
            double lo = (sell == null ? 0 : sell) * amount, hi = (buy == null ? 0 : buy) * amount;
            min += lo;
            max += hi;
            items += amount;
            String sack = sackOf.getOrDefault(e.getKey(), "Other");
            double[] s = sacks.computeIfAbsent(sack, k -> new double[3]);
            s[0] += lo;
            s[1] += hi;
            s[2] += amount;
        }
        header = amounts.isEmpty() && !SackApi.enabled()
                ? "§eSacks: §cturn on Live Sack Contents"
                : "§eSacks: " + range(min, max) + " §7(" + String.format(java.util.Locale.ROOT, "%,d", items) + " items)"
                + (PriceManager.isLoaded() ? "" : " §7(loading prices…)");
        List<Map.Entry<String, double[]>> sorted = new ArrayList<>(sacks.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1]));
        List<String> lines = new ArrayList<>(sorted.size());
        for (Map.Entry<String, double[]> e : sorted) {
            lines.add("§f" + StringUtil.titleCase(e.getKey()) + " " + range(e.getValue()[0], e.getValue()[1]));
        }
        if (lines.isEmpty()) lines.add("§cNo items in sacks, open each sack once");
        headerW = mc.font.width(header);
        int w = 0;
        for (String s : lines) w = Math.max(w, mc.font.width(s));
        perSackW = w;

        List<String> ex = new ArrayList<>(Math.min(lines.size(), EXPANDED_ROWS) + 2);
        ex.add(header);
        for (int i = 0; i < lines.size() && i < EXPANDED_ROWS; i++) ex.add(lines.get(i));
        if (lines.size() > EXPANDED_ROWS) ex.add("§7… " + (lines.size() - EXPANDED_ROWS) + " more");
        List<Component> tip = new ArrayList<>(lines.size() + 3);
        tip.add(Component.literal(header));
        tip.add(Component.empty());
        for (String l : lines) tip.add(Component.literal(l));
        tip.add(Component.literal("§eLeft click: all sacks, right click: /sacks"));
        headerLines = List.of(header);
        expandedLines = ex;
        tipLines = tip;
    }

    private static List<String> headerLines = List.of(), expandedLines = List.of();
    private static List<Component> tipLines = List.of();

    private static String range(double lo, double hi) {
        return "§6" + EstimatedValue.fmt(lo) + "§7-§6" + EstimatedValue.fmt(hi);
    }

    private static boolean inSackMenu(Minecraft mc) {
        return mc.gui.screen() instanceof AbstractContainerScreen<?> cs
                && StringUtil.stripTrim(cs.getTitle().getString()).contains("Sack");
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (hud == null || header.isEmpty() || inSackMenu(mc)) return;
        if (cfg().sackValueHudInSkyblockOnly && !com.teslamaps.utils.SkyblockUtils.inSkyblockCached()) return;
        List<String> lines = expanded ? expandedLines : headerLines;
        int w = expanded ? Math.max(headerW, perSackW) : headerW;
        int x = hud.x(), y = hud.y();
        float s = hud.scale();
        draw(g, mc, x, y, s, lines, w);
        boxX = x;
        boxY = y;
        boxW = Math.round((w + 4) * s);
        boxH = Math.round(lines.size() * LINE * s);

        if (!HudForeground.active() || expanded) return;
        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        if (inside(mx, my)) g.setComponentTooltipForNextFrame(mc.font, tipLines, (int) mx, (int) my);
    }

    private static boolean inside(double mx, double my) {
        return mx >= boxX - 2 && mx <= boxX + boxW && my >= boxY - 2 && my <= boxY + boxH;
    }

    /** From HandledScreenClickMixin, inventory only: left toggles the list, right opens /sacks. */
    public static boolean handleClick(double mx, double my, int button) {
        if (!cfg().sackValueHud || !cfg().section("Inventory") || !HudForeground.active() || header.isEmpty()) return false;
        if (button != 0 && button != 1 || !inside(mx, my)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.containerMenu.getCarried().isEmpty()) return false;
        if (button == 0) expanded = !expanded;
        else if (mc.getConnection() != null) mc.getConnection().sendCommand("sacks");
        return true;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * LINE + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * LINE, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
