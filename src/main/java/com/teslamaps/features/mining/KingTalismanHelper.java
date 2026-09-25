package com.teslamaps.features.mining;

// Ported from SkyHanni's KingTalismanHelper.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayList;
import java.util.List;

/** Which of the seven rotating Dwarven kings you already talked to, and when the missing ones are up. */
public final class KingTalismanHelper {

    private static final String[] KINGS = {"Brammor", "Emkam", "Redros", "Erren", "Thormyr", "Emmor", "Grandan"};
    private static final long SB_EPOCH = 1560275700000L, DAY_MS = 1_200_000L;
    private static final double KX = 129.6, KY = 196.5, KZ = 194.1;

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int linesWidth, ticks;
    private static boolean show, checkedScreen;

    private KingTalismanHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("kingTalisman", "King Talisman Helper").section("Mining")
                .enabled(() -> cfg().kingTalismanHelper)
                .size(() -> Math.max(100, linesWidth), () -> Math.max(10, lines.size() * 10))
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, lines.isEmpty()
                        ? List.of("§cNext missing king: §7Emkam §7(§bin 12m 3s§7)") : lines));
        Feature.of("KingTalismanHelper").section("Mining").when(() -> cfg().kingTalismanHelper)
                .tick(KingTalismanHelper::tick)
                .serverChat(KingTalismanHelper::onChat)
                .hud("kingtalisman", KingTalismanHelper::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("King Talisman Helper", () -> c.kingTalismanHelper, v -> c.kingTalismanHelper = v)
                            .desc("Tracks which of the 7 Dwarven kings you talked to (open Commissions at the king) and when the missing ones are up. Next to the king all 7 are listed, elsewhere only the next missing one (/tmap gui).")
                            .tags("king", "talisman", "dwarven", "royal palace", "commissions");
                    b.toggle("  Show Outside Dwarven Mines", () -> c.kingTalismanOutsideMines, v -> c.kingTalismanOutsideMines = v);
                    b.button("  Reset Kings", () -> { c.kingsTalkedTo.clear(); TeslaMapsConfig.save(); });
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean inMines = SkyblockUtils.isInArea("Dwarven Mines");
        show = SkyblockUtils.isInSkyblock() && (inMines || cfg().kingTalismanOutsideMines);
        if (!show) return;
        boolean near = inMines && mc.player.distanceToSqr(KX, KY, KZ) < 100;
        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            if (!checkedScreen && near && StringUtil.stripTrim(cs.getTitle().getString()).equals("Commissions")) {
                checkedScreen = true;
                String king = KINGS[currentIndex()];
                if (!cfg().kingsTalkedTo.contains(king)) {
                    cfg().kingsTalkedTo.add(king);
                    TeslaMapsConfig.save();
                    ticks = 19;
                }
            }
        } else {
            checkedScreen = false;
        }
        if (++ticks % 20 != 0) return;
        lines = build(near);
        int w = 0;
        for (String s : lines) w = Math.max(w, mc.font.width(s));
        linesWidth = w;
    }

    private static void onChat(String text) {
        if (!StringUtil.strip(text).trim().equals("You have received a King Talisman!")) return;
        if (!SkyblockUtils.isInArea("Dwarven Mines")) return;
        cfg().kingsTalkedTo.clear();
        cfg().kingsTalkedTo.addAll(List.of(KINGS));
        TeslaMapsConfig.save();
        ticks = 19;
    }

    private static long day() {
        return Math.floorDiv(System.currentTimeMillis() - SB_EPOCH, DAY_MS);
    }

    private static int currentIndex() {
        long day = day();
        int dayOfMonth = (int) (day % 31) + 1;
        return (dayOfMonth + 5) % KINGS.length;
    }

    /** Millis until each king's next turn, like SkyHanni (the current king sits near a full cycle). */
    private static long[] untilTimes() {
        long now = System.currentTimeMillis();
        long start = SB_EPOCH + (day() - currentIndex()) * DAY_MS;
        long cycle = DAY_MS * KINGS.length;
        long[] out = new long[KINGS.length];
        for (int i = 0; i < KINGS.length; i++) {
            long t = start + DAY_MS * i - now;
            while (t < 0) t += cycle;
            out[i] = t;
        }
        return out;
    }

    private static List<String> build(boolean near) {
        List<String> talked = cfg().kingsTalkedTo;
        int missingCount = 0;
        for (String k : KINGS) if (!talked.contains(k)) missingCount++;
        if (missingCount == 0) return near ? List.of("§eAll Kings found.") : List.of();

        long[] until = untilTimes();
        int current = currentIndex();
        List<String> all = new ArrayList<>();
        String far = null;
        for (int i = 0; i < KINGS.length; i++) {
            boolean missing = !talked.contains(KINGS[i]);
            boolean cur = i == current;
            String time = cur ? "§7(§b" + format(until[i] - DAY_MS * (KINGS.length - 1)) + " remaining§7)"
                    : "§7(§bin " + format(until[i]) + "§7)";
            if (missing && cur) far = "§cNext missing king: §7" + KINGS[i] + " §eNow " + time;
            all.add("§7" + (cur ? "§6King " : "") + KINGS[i] + (missing ? " §cMissing " + time : " §aDone"));
        }
        if (near) return all;
        if (far == null) {
            int best = -1;
            for (int i = 0; i < KINGS.length; i++) {
                if (talked.contains(KINGS[i])) continue;
                if (best < 0 || until[i] < until[best]) best = i;
            }
            far = "§cNext missing king: §7" + KINGS[best] + " §7(§bin " + format(until[best]) + "§7)";
        }
        return List.of(far);
    }

    private static String format(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) return s / 3600 + "h " + s / 60 % 60 + "m";
        if (s >= 60) return s / 60 + "m " + s % 60 + "s";
        return s + "s";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show || hud == null || lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), lines);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l) {
        int w = 0;
        if (l == lines) w = linesWidth;
        else for (String s : l) w = Math.max(w, mc.font.width(s));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * 10 + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * 10, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

}
