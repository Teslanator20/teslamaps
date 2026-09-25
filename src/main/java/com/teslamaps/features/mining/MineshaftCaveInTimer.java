package com.teslamaps.features.mining;

// Ported from SkyHanni's features/mining/glacitemineshaft/MineshaftCaveInTimer.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.HudVisibility;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** "Entrance caves in: 0:42" from entering a Glacite Mineshaft, plus time inside and a cold-based estimate. */
public final class MineshaftCaveInTimer {

    private static final long CAVE_IN = 60_000L;
    private static final List<String> SAMPLE = List.of("§fEntrance caves in: §a0:42",
            "§fTime in mineshaft: §e0:18", "§fEstimated time left: §e4:10");

    private static HudEditElement hud;
    private static Level lastLevel;
    private static boolean inShaft;
    private static long start, firstColdAt;
    private static int lastCold = -1, coldGained, ticks;
    private static List<String> lines = List.of();
    private static int width;

    private MineshaftCaveInTimer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("caveInTimer", "Mineshaft Cave-in Timer").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().caveInTimer)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines),
                        () -> TextHud.height((lines.isEmpty() ? SAMPLE : lines).size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        TextHud.width(lines.isEmpty() ? SAMPLE : lines)));
        Feature.of("MineshaftCaveInTimer").section("Mining").when(() -> cfg().caveInTimer)
                .tick(MineshaftCaveInTimer::tick)
                .hud("caveintimer", MineshaftCaveInTimer::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Mineshaft Cave-in Timer");
                    b.toggle("Mineshaft Cave-in Timer", () -> c.caveInTimer, v -> c.caveInTimer = v)
                            .desc("Counts down the 60 s until a Glacite Mineshaft entrance caves in (green, yellow, red). Position in /tmap gui.")
                            .tags("mineshaft", "cave in", "entrance", "exit", "glacite", "timer");
                    b.toggle("  Time In Mineshaft", () -> c.caveInTimerShowTime, v -> c.caveInTimerShowTime = v);
                    b.toggle("  Estimated Time Left (Cold)", () -> c.caveInTimerEstimate, v -> c.caveInTimerEstimate = v)
                            .desc("How long until you freeze, from how fast your cold went up so far");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            inShaft = false;
            start = 0;
            lines = List.of();
        }
        if (++ticks % 5 != 0) return;
        boolean now = SkyblockUtils.getArea().toLowerCase(java.util.Locale.ROOT).contains("mineshaft");
        if (now && !inShaft) {
            start = System.currentTimeMillis();
            firstColdAt = 0;
            lastCold = -1;
            coldGained = 0;
        }
        inShaft = now;
        if (!inShaft || start == 0) {
            lines = List.of();
            return;
        }
        int cold = GlaciteColdOverlay.readCold();
        if (cold >= 0) {
            if (lastCold < 0) firstColdAt = System.currentTimeMillis();
            else if (cold > lastCold) coldGained += cold - lastCold;
            lastCold = cold;
        }
        build();
    }

    private static void build() {
        long passed = System.currentTimeMillis() - start;
        long left = CAVE_IN - passed;
        List<String> out = new ArrayList<>(3);
        String col = left > 30_000 ? "§a" : left > 10_000 ? "§e" : "§c";
        out.add("§fEntrance caves in: " + (left <= 0 ? "§cCaved in!" : col + time(left)));
        if (cfg().caveInTimerShowTime) out.add("§fTime in mineshaft: §e" + time(passed));
        if (cfg().caveInTimerEstimate) {
            String est = "§7Calculating...";
            long elapsed = firstColdAt == 0 ? 0 : System.currentTimeMillis() - firstColdAt;
            if (coldGained > 0 && elapsed > 0 && lastCold >= 0) {
                double perMs = coldGained / (double) elapsed;
                est = time((long) Math.max(0, (100 - lastCold) / perMs));
            }
            out.add("§fEstimated time left: §e" + est);
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (lines.isEmpty() || hud == null || HudVisibility.suppressed()) return;
        TextHud.draw(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
