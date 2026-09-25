package com.teslamaps.features;

// Ported from SkyHanni's features/event/winter/JyrreTimer.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class JyrreTimer {

    private static final long DURATION = 60 * 60_000L;
    private static final String SAMPLE = "§aJyrre Boost: §b59m 12s";

    private static HudEditElement hud;
    private static String line;
    private static long lastTick;
    private static int saveTicks;

    private JyrreTimer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("jyrreTimer", "Jyrre Timer").section("Timers")
                .enabled(() -> cfg().jyrreTimer)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("JyrreTimer").section("Timers").when(() -> cfg().jyrreTimer)
                .tick(JyrreTimer::tick)
                .serverChat(JyrreTimer::onChat)
                .hud("jyrretimer", JyrreTimer::render)
                .config("Timers", b -> {
                    b.label("Refined Bottle of Jyrre");
                    b.toggle("Jyrre Timer", () -> cfg().jyrreTimer, v -> cfg().jyrreTimer = v)
                            .desc("60 min Intelligence boost left after drinking a Refined Bottle of Jyrre (/tmap gui)")
                            .tags("jyrre", "bottle", "intelligence");
                    b.toggle("  Show Inactive", () -> cfg().jyrreShowInactive, v -> cfg().jyrreShowInactive = v)
                            .desc("\"Inactive!\" instead of hiding when it ran out");
                });
    }

    private static void onChat(String text) {
        String s = StringUtil.stripTrim(text);
        if (s.startsWith("You consumed a Refined Bottle of Jyrre and gained") && s.endsWith("for 60m!")) {
            cfg().jyrreRemainingMs = DURATION;
            TeslaMapsConfig.save();
        }
    }

    // buff only ticks while you are in SkyBlock
    private static void tick() {
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        long dt = lastTick == 0 ? 0 : Math.min(1000, now - lastTick);
        lastTick = now;
        if (!SkyblockUtils.isInSkyblock()) { line = null; return; }
        if (c.jyrreRemainingMs > 0) {
            c.jyrreRemainingMs = Math.max(0, c.jyrreRemainingMs - dt);
            if (++saveTicks >= 1200 || c.jyrreRemainingMs == 0) { saveTicks = 0; TeslaMapsConfig.save(); }
        }
        long left = c.jyrreRemainingMs;
        if (left <= 0) { line = c.jyrreShowInactive ? "§aJyrre Boost: §cInactive!" : null; return; }
        long secs = left / 1000;
        line = "§aJyrre Boost: §b" + (secs >= 60 ? secs / 60 + "m " + secs % 60 + "s" : secs + "s");
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = line;
        if (l == null || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
