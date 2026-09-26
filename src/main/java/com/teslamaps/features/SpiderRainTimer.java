package com.teslamaps.features;

// Concept from Skytils' rainTimer
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Spider's Den rain: the Rain:/Thunder: line when Hypixel shows one, else how long the world has been raining. */
public final class SpiderRainTimer {

    private static final Pattern LINE = Pattern.compile("^(?:[^\\w]+\\s*)?(Rain|Thunder|Thunderstorm): (?<time>[0-9dhms: ]+)$");
    private static final String SAMPLE = "§9Rain: §f4m 20s";

    private static String text;
    private static long rainStart;
    private static int ticks;
    private static HudEditElement hud;

    private SpiderRainTimer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("spiderRainTimer", "Spider's Den Rain Timer").section("Combat")
                .enabled(() -> cfg().spiderRainTimer)
                .size(() -> width(SAMPLE), () -> 16)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, text == null ? SAMPLE : text));
        Feature.of("SpiderRainTimer").section("Combat").when(() -> cfg().spiderRainTimer)
                .tick(SpiderRainTimer::tick)
                .hudHiddenInInventory("spiderraintimer", SpiderRainTimer::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Spider's Den");
                    b.toggle("Spider's Den Rain Timer", () -> c.spiderRainTimer, v -> c.spiderRainTimer = v)
                            .desc("Spider's Den only: rain time left from the tab list / scoreboard, or how long it has been raining. Position in /tmap gui")
                            .tags("rain", "spider", "den", "tarantula", "broodmother", "timer");
                });
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInArea("Spider's Den") || mc.level == null) {
            text = null;
            rainStart = 0;
            return;
        }
        String found = find(TabListUtils.getTabListLines());
        if (found == null) found = find(ScoreboardUtils.getScoreboardLines());
        boolean raining = mc.level.isRaining();
        if (!raining) rainStart = 0;
        else if (rainStart == 0) rainStart = System.currentTimeMillis();
        if (found != null) text = found;
        else if (raining) text = "§9Raining: §f" + fmt((System.currentTimeMillis() - rainStart) / 1000);
        else text = "§7No rain";
    }

    private static String find(java.util.List<String> lines) {
        for (String line : lines) {
            Matcher m = LINE.matcher(StringUtil.stripTrim(line));
            if (m.matches()) return (m.group(1).startsWith("Thunder") ? "§eThunder: §f" : "§9Rain: §f") + m.group("time").trim();
        }
        return null;
    }

    private static String fmt(long s) {
        return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }

    private static int width(String s) {
        return Minecraft.getInstance().font.width(s) + 8;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String t = text;
        if (hud == null || t == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), t);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String s) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, width(s), 16);
        g.text(mc.font, s, 4, 4, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
