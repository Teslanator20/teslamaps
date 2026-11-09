package com.teslamaps.features;

// Slim port of NotEnoughUpdates' overlays/TimersOverlay.java (todo overlay)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.features.qol.QolStore;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DailyTimers {

    private static final long HOUR = 3_600_000L, DAY = 24 * HOUR, EST_OFFSET = 5 * HOUR;
    private static final String[] SHOW = {"Always", "Ready / Expiring"};
    private static final List<String> SAMPLE = List.of("§3Cookie Buff: §a3d 4h", "§3God Potion: §e5h 12m",
            "§3Puzzler: §aReady!", "§3Fetchur: §7in 7h 3m");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, timer;
    private static volatile String footer;
    private static String parsedFooter;

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
                            .desc("Todo HUD: buffs running out and daily tasks that reset. Read from chat and the tab list. Position in /tmap gui.");
                    b.dropdown("  Show", SHOW, () -> c.dailyTimersShow, v -> c.dailyTimersShow = v);
                    b.toggle("  Booster Cookie", () -> c.dailyTimersCookie, v -> c.dailyTimersCookie = v);
                    b.toggle("  God Potion", () -> c.dailyTimersGodPot, v -> c.dailyTimersGodPot = v);
                    b.toggle("  Century Cakes", () -> c.dailyTimersCakes, v -> c.dailyTimersCakes = v);
                    b.toggle("  Puzzler", () -> c.dailyTimersPuzzler, v -> c.dailyTimersPuzzler = v);
                    b.toggle("  Fetchur", () -> c.dailyTimersFetchur, v -> c.dailyTimersFetchur = v);
                    b.toggle("  Daily Mithril / Gemstone Powder", () -> c.dailyTimersPowder, v -> c.dailyTimersPowder = v);
                    b.toggle("  Free Rift Infusion", () -> c.dailyTimersRift, v -> c.dailyTimersRift = v);
                    b.toggle("  NPC Buy Limit", () -> c.dailyTimersShopLimit, v -> c.dailyTimersShopLimit = v);
                });
    }

    /** Tab footer packet, main thread. */
    public static void onTabFooter(String text) {
        footer = text;
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

    private static void readBuffs(Map<String, Long> m, long now) {
        String f = footer;
        boolean changed = false;
        if (f != null && !f.equals(parsedFooter)) {
            parsedFooter = f;
            String[] rows = f.split("\n");
            boolean effects = false, god = false;
            for (int i = 0; i < rows.length; i++) {
                String r = StringUtil.stripTrim(rows[i]);
                if (r.equals("Cookie Buff") && i + 1 < rows.length) {
                    String next = StringUtil.stripTrim(rows[i + 1]);
                    long ms = next.startsWith("Not active") ? 0 : QolStore.parseDuration(next.toLowerCase(Locale.ROOT));
                    if (ms > 0 || next.startsWith("Not active")) changed |= set(m, "cookieEnd", now + ms);
                } else if (r.equals("Active Effects")) {
                    effects = true;
                } else if (r.startsWith("You have a God Potion active!")) {
                    long ms = QolStore.parseDuration(r.substring(29).toLowerCase(Locale.ROOT));
                    if (ms > 0) {
                        changed |= set(m, "godPotEnd", now + ms);
                        god = true;
                    }
                }
            }
            if (effects && !god && !com.teslamaps.dungeon.DungeonManager.isInDungeon()) changed |= set(m, "godPotEnd", 0L);
        }
        for (String raw : TabListUtils.getTabListLines()) {
            String l = StringUtil.stripTrim(raw);
            if (!l.startsWith("God Potion:")) continue;
            long ms = QolStore.parseDuration(l.substring(11).toLowerCase(Locale.ROOT));
            if (ms > 0) changed |= set(m, "godPotEnd", now + ms);
            break;
        }
        if (changed) QolStore.save();
    }

    // durations tick down every second; only a real jump is worth a file write
    private static boolean set(Map<String, Long> m, String key, long end) {
        Long old = m.get(key);
        if (old != null && (end == 0 ? old == 0 : old != 0 && Math.abs(old - end) < 90_000)) return false;
        m.put(key, end);
        return true;
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
        readBuffs(m, now);
        boolean dueOnly = SHOW[1].equals(c.dailyTimersShow);
        long estReset = (now - EST_OFFSET) / DAY * DAY + EST_OFFSET;
        long utcReset = now / DAY * DAY;

        List<String> out = new ArrayList<>();
        if (c.dailyTimersCookie) buff(out, dueOnly, "Cookie Buff", m.get("cookieEnd"), now);
        if (c.dailyTimersGodPot) buff(out, dueOnly, "God Potion", m.get("godPotEnd"), now);
        if (c.dailyTimersCakes) {
            Map<String, Long> cakes = QolStore.get().cakes;
            Long soonest = null;
            for (Long t : cakes.values()) if (soonest == null || t < soonest) soonest = t;
            buff(out, dueOnly, "Cakes", soonest == null ? null : soonest, now);
        }
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

    private static void buff(List<String> out, boolean dueOnly, String label, Long end, long now) {
        if (end == null) {
            if (!dueOnly) out.add("§3" + label + ": §8unknown");
            return;
        }
        long left = end - now;
        if (left <= 0) {
            out.add("§3" + label + ": §cExpired!");
            return;
        }
        if (dueOnly && left >= HOUR) return;
        String col = left < HOUR ? "§c" : left < 12 * HOUR ? "§e" : "§a";
        out.add("§3" + label + ": " + col + QolStore.formatDuration(left));
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || lines.isEmpty()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
