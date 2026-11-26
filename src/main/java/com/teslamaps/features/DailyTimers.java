package com.teslamaps.features;

// Slim port of NotEnoughUpdates' overlays/TimersOverlay.java (todo overlay)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.features.qol.QolStore;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DailyTimers {

    private static final long HOUR = 3_600_000L, DAY = 24 * HOUR, EST_OFFSET = 5 * HOUR;
    private static final String[] SHOW = {"Always", "Ready / Expiring"};
    private static final List<String> SAMPLE = List.of("§3Puzzler: §aReady!", "§3Fetchur: §7in 7h 3m",
            "§3Free Rift Infusion: §7in 2h 10m");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, timer;

    private DailyTimers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("dailyTimers", "Daily Timers").section("Timers")
                .enabled(() -> cfg().dailyTimers)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines), () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> {
                    List<String> l = lines.isEmpty() ? SAMPLE : lines;
                    TextHud.draw(g, mc, x, y, s, l, TextHud.width(l));
                });
        Feature.of("DailyTimers").section("Timers").when(() -> cfg().dailyTimers)
                .tick(DailyTimers::tick)
                .serverChat(DailyTimers::onChat)
                .hudHiddenInInventory("dailytimers", DailyTimers::render)
                .config("Timers", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Daily Timers");
                    b.toggle("Daily Timers", () -> c.dailyTimers, v -> { c.dailyTimers = v; timer = 19; })
                            .desc("Todo HUD: daily tasks that reset. Read from chat. Cookie / God Potion: Effect Timers, cakes: Century Cake Timer. Position in /tmap gui.");
                    b.dropdown("  Show", SHOW, () -> c.dailyTimersShow, v -> c.dailyTimersShow = v);
                    b.toggle("  Puzzler", () -> c.dailyTimersPuzzler, v -> c.dailyTimersPuzzler = v);
                    b.toggle("  Fetchur", () -> c.dailyTimersFetchur, v -> c.dailyTimersFetchur = v);
                    b.toggle("  Daily Mithril / Gemstone Powder", () -> c.dailyTimersPowder, v -> c.dailyTimersPowder = v);
                    b.toggle("  Free Rift Infusion", () -> c.dailyTimersRift, v -> c.dailyTimersRift = v);
                    b.toggle("  NPC Buy Limit", () -> c.dailyTimersShopLimit, v -> c.dailyTimersShopLimit = v);
                });
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        String key = null;
        if (t.startsWith("Puzzler gave you ") && t.endsWith(" for solving the puzzle!")) key = "puzzler";
        else if (t.equals("[NPC] Fetchur: thanks thats probably what i needed")
                || t.equals("[NPC] Fetchur: come back another time, maybe tmrw")) key = "fetchur";
        else if (t.contains("Mithril Powder from mining your first Mithril Ore of the day!")) key = "mithril";
        else if (t.contains("Gemstone Powder from mining your first Gemstone of the day!")) key = "gemstone";
        else if (t.startsWith("You may only buy up to ") && t.endsWith(" of this item each day!")) key = "shop";
        else if (t.equals("INFUSED! Used one of your free Rift charges!")) key = "rift";
        if (key == null) return;
        QolStore.get().dailyTimers.put(key, System.currentTimeMillis());
        QolStore.save();
        timer = 19;
    }

    private static void tick() {
        if (++timer % 20 != 0) return;
        if (!SkyblockUtils.isInSkyblock()) {
            lines = List.of();
            return;
        }
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        Map<String, Long> m = QolStore.get().dailyTimers;
        boolean dueOnly = SHOW[1].equals(c.dailyTimersShow);
        long estReset = (now - EST_OFFSET) / DAY * DAY + EST_OFFSET;
        long utcReset = now / DAY * DAY;

        List<String> out = new ArrayList<>();
        if (c.dailyTimersPuzzler) task(out, dueOnly, "Puzzler", m.getOrDefault("puzzler", 0L) + DAY, now);
        if (c.dailyTimersFetchur) task(out, dueOnly, "Fetchur", resetAfter(m.getOrDefault("fetchur", 0L), estReset), now);
        if (c.dailyTimersPowder) {
            task(out, dueOnly, "Mithril Powder", resetAfter(m.getOrDefault("mithril", 0L), utcReset), now);
            task(out, dueOnly, "Gemstone Powder", resetAfter(m.getOrDefault("gemstone", 0L), utcReset), now);
        }
        if (c.dailyTimersRift) task(out, dueOnly, "Free Rift Infusion", m.getOrDefault("rift", 0L) + 4 * HOUR, now);
        if (c.dailyTimersShopLimit && !dueOnly) {
            long at = m.getOrDefault("shop", 0L);
            if (at >= utcReset) out.add("§3NPC Buy Limit: §chit §7(resets in " + QolStore.formatDuration(utcReset + DAY - now) + ")");
        }
        lines = out;
        width = TextHud.width(out);
    }

    /** Next reset after a completion, 0 when it was before the current window. */
    private static long resetAfter(long doneAt, long lastReset) {
        return doneAt >= lastReset ? lastReset + DAY : 0;
    }

    private static void task(List<String> out, boolean dueOnly, String label, long readyAt, long now) {
        if (readyAt <= now) {
            out.add("§3" + label + ": §aReady!");
        } else if (!dueOnly) {
            out.add("§3" + label + ": §7in " + QolStore.formatDuration(readyAt - now));
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || lines.isEmpty()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
