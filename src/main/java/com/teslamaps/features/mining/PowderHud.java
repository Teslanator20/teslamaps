package com.teslamaps.features.mining;

// After Skyblocker's PowderWidget (tab "Powders" lines, diff since the last change)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Mithril / Gemstone / Glacite powder from the tab list, with the last gain behind it. */
public final class PowderHud {

    private static final String[] PREFIX = {"Mithril: ", "Gemstone: ", "Glacite: "};
    private static final String[] LABEL = {"§2Mithril", "§dGemstone", "§bGlacite"};
    private static final String[] DIFF = {"§2", "§d", "§b"};
    private static final long INTERVAL = 2000;
    private static final List<String> SAMPLE = List.of("§3§lPowders", "§2Mithril: §f4,210,331 §2(+312)",
            "§dGemstone: §f9,876,543", "§bGlacite: §f1,234,567");

    private static final long[] last = {-1, -1, -1};
    private static final long[] diff = new long[3];
    private static final long[] changedAt = new long[3];
    private static List<String> lines = List.of();
    private static int width;
    private static int ticks;
    private static HudEditElement hud;

    private PowderHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("powderHud", "Powder HUD").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().powderHud)
                .size(() -> width(SAMPLE), () -> SAMPLE.size() * 10 + 6)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, width(SAMPLE)));
        Feature.of("PowderHud").section("Mining").when(() -> cfg().powderHud)
                .tick(PowderHud::tick)
                .hudHiddenInInventory("powderhud", PowderHud::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Powder");
                    b.toggle("Powder HUD", () -> c.powderHud, v -> c.powderHud = v)
                            .desc("Mithril, Gemstone and Glacite powder from the tab list, plus the last gain in brackets. Needs the Powders tab widget. Mining islands only.")
                            .tags("powder", "mithril", "gemstone", "glacite", "hud");
                    b.toggle("  Show Gain", () -> c.powderHudDiff, v -> c.powderHudDiff = v);
                });
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        if (!Mining.onMiningIsland()) {
            lines = List.of();
            return;
        }
        long now = System.currentTimeMillis();
        boolean[] seen = new boolean[3];
        for (String raw : TabListUtils.getTabListLines()) {
            String l = StringUtil.stripTrim(raw);
            for (int i = 0; i < 3; i++) {
                if (seen[i] || !l.startsWith(PREFIX[i])) continue;
                long v = parse(l.substring(PREFIX[i].length()));
                if (v < 0) continue;
                seen[i] = true;
                if (v != last[i]) {
                    diff[i] = last[i] < 0 ? 0 : v - last[i];
                    changedAt[i] = now;
                    last[i] = v;
                } else if (now - changedAt[i] > INTERVAL) {
                    diff[i] = 0;
                }
            }
        }
        List<String> out = new ArrayList<>();
        out.add("§3§lPowders");
        for (int i = 0; i < 3; i++) {
            if (!seen[i]) continue;
            String s = LABEL[i] + ": §f" + String.format(Locale.ROOT, "%,d", last[i]);
            if (cfg().powderHudDiff && diff[i] != 0) {
                s += " " + DIFF[i] + "(" + (diff[i] > 0 ? "+" : "") + String.format(Locale.ROOT, "%,d", diff[i]) + ")";
            }
            out.add(s);
        }
        lines = out.size() > 1 ? out : List.of();
        width = width(lines);
    }

    private static long parse(String s) {
        int end = 0;
        while (end < s.length() && (Character.isDigit(s.charAt(end)) || s.charAt(end) == ',')) end++;
        if (end == 0) return -1;
        try {
            return Long.parseLong(s.substring(0, end).replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> ls = lines;
        if (ls.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || com.teslamaps.utils.HudVisibility.suppressed()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), ls, width);
    }

    private static int width(List<String> ls) {
        int w = 0;
        for (String l : ls) w = Math.max(w, Minecraft.getInstance().font.width(l));
        return w + 8;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> ls, int w) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, ls.size() * 10 + 6);
        for (int i = 0; i < ls.size(); i++) g.text(mc.font, ls.get(i), 4, 4 + i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
