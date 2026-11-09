package com.teslamaps.features.events;

// After NEU's CalendarOverlay.java and Skyblocker's EventNotifications.java, times from the SkyBlock calendar
import com.teslamaps.api.ElectionApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.JacobContests;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UpcomingEvents {

    private record Ev(String name, long start, long end, String extra, boolean remind) {}

    private static final long EPOCH = 1560275700000L, YEAR = 446_400_000L, MONTH = YEAR / 12, DAY = 1_200_000L,
            HOUR_SB = 50_000L, HOUR = 3_600_000L, MIN = 60_000L;
    private static final String[] ZOO_PETS = {"Lion", "Monkey", "Elephant", "Giraffe", "Blue Whale", "Tiger"};
    private static final List<String> SAMPLE = List.of("§6Upcoming Events", "§5Dark Auction§8: §f12m",
            "§eJacob's Contest§8: §f32m", "§bCult of the Fallen Star§8: §f1h 5m", "§6Spooky Festival§8: §f2d 3h");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, timer;
    private static final Map<String, Long> reminded = new HashMap<>();

    private UpcomingEvents() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("upcomingEvents", "Upcoming Events").section("Events")
                .enabled(() -> cfg().upcomingEvents)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("UpcomingEvents").section("Events")
                .when(() -> cfg().upcomingEvents || cfg().upcomingEventsReminder)
                .tickAlways(UpcomingEvents::tick)
                .hudHiddenInInventory("upcomingevents", UpcomingEvents::render)
                .config("Events", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Upcoming Events HUD", () -> c.upcomingEvents, v -> c.upcomingEvents = v)
                            .desc("The next SkyBlock events with a countdown: Dark Auction, Jacob's Contest with crops, Cult of the Fallen Star, Hoppity's Hunt, Traveling Zoo with its pet, Spooky Festival, Season of Jerry, New Year, election, Fishing Festival (Marina). Move it in /tmap gui")
                            .tags("calendar", "event", "countdown", "upcoming", "zoo", "spooky", "jerry");
                    b.slider("  Events Shown", 1f, 12f, () -> (float) c.upcomingEventsCount, v -> c.upcomingEventsCount = Math.round(v), 0);
                    b.toggle("Event Reminder", () -> c.upcomingEventsReminder, v -> c.upcomingEventsReminder = v)
                            .desc("Title, chat line and sound before an event starts. Dark Auction and Jacob have their own alerts in Contest Timers");
                    b.slider("  Minutes Before", 1f, 30f, () -> (float) c.upcomingEventsLeadMin, v -> c.upcomingEventsLeadMin = Math.round(v), 0);
                });
    }

    private static int year(long t) { return (int) Math.floorDiv(t - EPOCH, YEAR) + 1; }

    private static String zooPet(int year, int month) {
        return ZOO_PETS[Math.floorMod(year * 2 + (month < 6 ? 0 : 1), ZOO_PETS.length)];
    }

    // yearly event: month 0-11, first day 1-31, length in days; the running or next occurrence
    private static Ev yearly(String name, long now, int month, int day, int days, String color, boolean remind) {
        long yearStart = EPOCH + Math.floorDiv(now - EPOCH, YEAR) * YEAR;
        long start = yearStart + month * MONTH + (day - 1) * DAY;
        long end = start + days * DAY;
        if (end <= now) { start += YEAR; end += YEAR; }
        return new Ev(color + name, start, end, null, remind);
    }

    private static List<Ev> compute(long now) {
        List<Ev> out = new ArrayList<>();
        long da = Math.floorDiv(now - 55 * MIN, HOUR) * HOUR + 55 * MIN;
        if (now - da >= 5 * MIN) da += HOUR;
        out.add(new Ev("§5Dark Auction", da, da + 5 * MIN, null, false));

        long jacob = Math.floorDiv(now - 15 * MIN, HOUR) * HOUR + 15 * MIN;
        if (now - jacob >= 20 * MIN) jacob += HOUR;
        out.add(new Ev("§eJacob's Contest", jacob, jacob + 20 * MIN, JacobContests.cropsFor(jacob), false));

        // Cult of the Fallen Star: days 7, 14, 21, 28 of every month, 6 SkyBlock hours
        long monthStart = EPOCH + Math.floorDiv(now - EPOCH, MONTH) * MONTH;
        for (int m = 0; m < 2; m++) {
            boolean done = false;
            for (int d = 7; d <= 28; d += 7) {
                long s = monthStart + m * MONTH + (d - 1) * DAY;
                if (s + 6 * HOUR_SB > now) {
                    out.add(new Ev("§bCult of the Fallen Star", s, s + 6 * HOUR_SB, null, true));
                    done = true;
                    break;
                }
            }
            if (done) break;
        }

        out.add(yearly("Hoppity's Hunt", now, 0, 1, 93, "§d", true));
        Ev zoo1 = yearly("Traveling Zoo", now, 3, 1, 3, "§a", true);
        Ev zoo2 = yearly("Traveling Zoo", now, 9, 1, 3, "§a", true);
        Ev zoo = zoo1.start < zoo2.start ? zoo1 : zoo2;
        int zm = (int) (Math.floorMod(zoo.start - EPOCH, YEAR) / MONTH);
        out.add(new Ev(zoo.name, zoo.start, zoo.end, "§6" + zooPet(year(zoo.start), zm), true));
        out.add(yearly("Spooky Festival", now, 7, 29, 3, "§6", true));
        out.add(yearly("Season of Jerry", now, 11, 24, 3, "§f", true));
        out.add(yearly("New Year Celebration", now, 11, 29, 3, "§b", true));
        out.add(yearly("Election Closes", now, 2, 27, 0, "§e", false));

        String mayor = ElectionApi.mayor();
        if (mayor != null && mayor.contains("Marina") || ElectionApi.hasPerk("Fishing Festival")) {
            long s = monthStart;
            if (s + 3 * DAY <= now) s += MONTH;
            out.add(new Ev("§9Fishing Festival", s, s + 3 * DAY, null, true));
        }
        out.sort((a, b) -> Long.compare(Math.max(a.start, now), Math.max(b.start, now)));
        return out;
    }

    private static void tick() {
        if (++timer % 20 != 0) return;
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        List<Ev> evs = compute(now);

        if (c.upcomingEvents) {
            List<String> out = new ArrayList<>();
            out.add("§6Upcoming Events");
            int n = Math.max(1, c.upcomingEventsCount);
            for (int i = 0; i < evs.size() && i < n; i++) {
                Ev e = evs.get(i);
                String time = e.start <= now ? "§aLIVE §f" + fmt(e.end - now) : "§f" + fmt(e.start - now);
                out.add(e.name + "§8: " + time);
                if (e.extra != null) out.add(" §7" + e.extra);
            }
            lines = out;
            width = TextHud.width(out);
        } else {
            lines = List.of();
        }

        if (c.upcomingEventsReminder && SkyblockUtils.isInSkyblock()) {
            long lead = Math.max(1, c.upcomingEventsLeadMin) * MIN;
            for (Ev e : evs) {
                if (!e.remind || e.start <= now || e.start - now > lead) continue;
                Long last = reminded.get(e.name);
                if (last != null && last == e.start) continue;
                reminded.put(e.name, e.start);
                remind(e, e.start - now);
            }
        }
    }

    private static void remind(Ev e, long left) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String t = fmt(left);
        mc.player.sendSystemMessage(Component.literal("§e[TeslaMaps] " + e.name + " §estarts in §f" + t
                + (e.extra != null ? " §7(" + e.extra + "§7)" : "")));
        mc.gui.hud.setTimes(0, 50, 10);
        mc.gui.hud.setTitle(Component.literal(e.name));
        mc.gui.hud.setSubtitle(Component.literal("§estarts in §f" + t));
        LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.2f);
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 86400) return (s / 86400) + "d " + (s % 86400 / 3600) + "h";
        if (s >= 3600) return (s / 3600) + "h " + (s % 3600 / 60) + "m";
        if (s >= 60) return (s / 60) + "m " + (s % 60) + "s";
        return s + "s";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || lines.isEmpty() || hud == null || !cfg().upcomingEvents) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
