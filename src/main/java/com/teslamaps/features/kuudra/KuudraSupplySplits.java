package com.teslamaps.features.kuudra;

// Ported from Odin's features/impl/nether/SupplyHelper.kt (supply time) and legitcatmod's SupplySplits (HUD list)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Who placed which supply how long after the run started. */
public final class KuudraSupplySplits {

    private static final Pattern RECOVERED = Pattern.compile("^(?:\\[[^]]*] )?(\\w{1,16}) recovered one of Elle's supplies! \\((\\d)/(\\d)\\)$");
    private static final List<String> SAMPLE = List.of("§6§lSupplies", "§f1. §bPlayerOne §7- §a0:21.4", "§f2. §bPlayerTwo §7- §a0:24.0");

    private static final List<String> lines = new ArrayList<>();
    private static HudEditElement hud;
    private static int linesW;

    private KuudraSupplySplits() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("kuudraSupplySplits", "Kuudra Supply Splits").section("Kuudra")
                .enabled(() -> cfg().kuudraSupplySplits)
                .size(() -> width(SAMPLE) + 8, () -> SAMPLE.size() * 10 + 6)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, width(SAMPLE)));
        Feature.of("KuudraSupplySplits").section("Kuudra")
                .when(() -> cfg().kuudraSupplySplits)
                .serverChat(KuudraSupplySplits::onChat)
                .hud("kuudrasupplysplits", KuudraSupplySplits::render);
    }

    /** Rows under Kuudra Splits. */
    static void configRows(com.teslamaps.screen.config.ConfigBuilder b) {
        TeslaMapsConfig c = cfg();
        b.toggle("  Supply Splits", () -> c.kuudraSupplySplits, v -> c.kuudraSupplySplits = v)
                .desc("Lists who placed each supply and how long after the run start. Own HUD, move it in /tmap gui.")
                .tags("supply", "splits", "recovered");
    }

    static void reset() {
        lines.clear();
    }

    private static void onChat(String raw) {
        if (KuudraContext.phase() != KuudraContext.SUPPLIES) return;
        Matcher m = RECOVERED.matcher(StringUtil.stripTrim(raw));
        if (!m.matches()) return;
        long start = KuudraContext.runStart();
        String time = start > 0 ? format(System.currentTimeMillis() - start) : "?";
        if (lines.isEmpty()) lines.add("§6§lSupplies");
        lines.add("§f" + m.group(2) + ". §b" + m.group(1) + " §7- §a" + time);
        linesW = width(lines);
    }

    private static String format(long ms) {
        long s = ms / 1000;
        long tenth = ms / 100 % 10;
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60 + "." + tenth;
    }

    private static int width(List<String> l) {
        int w = 0;
        for (String s : l) w = Math.max(w, Minecraft.getInstance().font.width(s));
        return w;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || lines.isEmpty() || !KuudraContext.inKuudra()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, linesW);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, 0, 0, w + 8, l.size() * 10 + 6);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 4, 4 + i * 10, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
