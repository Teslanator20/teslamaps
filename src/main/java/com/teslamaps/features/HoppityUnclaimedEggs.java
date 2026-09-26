package com.teslamaps.features;

// After SkyHanni's HoppityEggDisplayManager.kt + HoppityEggType.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HoppityUnclaimedEggs {

    private record Meal(String name, String color, int hour, boolean alt) {}

    private static final Meal[] MEALS = {
            new Meal("Breakfast", "§6", 7, false), new Meal("Lunch", "§9", 14, false), new Meal("Dinner", "§a", 21, false),
            new Meal("Brunch", "§6", 7, true), new Meal("Déjeuner", "§9", 14, true), new Meal("Supper", "§a", 21, true)};
    private static final long EPOCH = 1560275700000L, HOUR = 50_000L, DAY = 1_200_000L, MONTH = DAY * 31, YEAR = MONTH * 12;
    private static final Pattern FOUND = Pattern.compile("^HOPPITY'S HUNT You found a Chocolate ([\\wé]+) Egg");
    private static final Pattern COLLECTED = Pattern.compile("^You have already collected this Chocolate ([\\wé]+) Egg");
    private static final String NONE_LEFT = "There are no hidden Chocolate Rabbit Eggs nearby! Try again later!";
    private static final String[] WARPS = {"nest", "hub", "island", "home", "park", "castle"};
    private static final List<String> SAMPLE = List.of("§bUnclaimed Eggs:", "§7 - §6Breakfast: §aReady", "§7 - §9Lunch: §92m 13s",
            "§7 - §aDinner: §a8m 40s", "§7Locations: §e9§7/§a15");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, timer;
    private static boolean allReady;
    private static long lastWarn;

    private HoppityUnclaimedEggs() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("hoppityUnclaimed", "Hoppity Unclaimed Eggs").section("Events")
                .enabled(() -> cfg().hoppityUnclaimedEggs)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("HoppityUnclaimedEggs").section("Events").when(() -> cfg().hoppityUnclaimedEggs)
                .tick(HoppityUnclaimedEggs::tick)
                .serverChat(HoppityUnclaimedEggs::onChat)
                .hudHiddenInInventory("hoppityunclaimed", HoppityUnclaimedEggs::render)
                .config("Events", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Hoppity Unclaimed Eggs", () -> c.hoppityUnclaimedEggs, v -> c.hoppityUnclaimedEggs = v)
                            .desc("During Hoppity's Hunt: all six meal eggs with the time until they spawn or 'Ready', plus the egg spots found on this island (/tmap gui).");
                    b.toggle("  Warn When All Six Are Ready", () -> c.hoppityUnclaimedWarn, v -> c.hoppityUnclaimedWarn = v);
                    b.dropdown("  Warp Destination", WARPS, () -> c.hoppityUnclaimedWarp, v -> c.hoppityUnclaimedWarp = v)
                            .desc("Target of the clickable /warp in the warning.");
                    b.toggle("  Show While Busy", () -> c.hoppityUnclaimedWhileBusy, v -> c.hoppityUnclaimedWhileBusy = v)
                            .desc("Also in Dungeons, Kuudra, the Rift and outside SkyBlock.");
                });
    }

    private static long absDay(long now) { return (now - EPOCH) / DAY; }

    private static int monthOf(long day) { return (int) ((day % 372) / 31) + 1; }

    private static boolean alt(long day) {
        int m = monthOf(day), d = (int) (day % 31) + 1;
        return (m % 2 == 1) == (d % 2 == 0);
    }

    private static boolean eventDay(long day) { return monthOf(day) <= 3; }

    static boolean eventActive() { return eventDay(absDay(System.currentTimeMillis())); }

    private static long spawnAt(long day, Meal m) { return EPOCH + day * DAY + m.hour * HOUR; }

    private static long lastSpawn(Meal m, long now) {
        long today = absDay(now);
        for (long d = today; d >= today - 2; d--) {
            if (alt(d) != m.alt) continue;
            long t = spawnAt(d, m);
            if (t <= now) return eventDay(d) ? t : -1;
        }
        return -1;
    }

    private static long nextSpawn(Meal m, long now) {
        long today = absDay(now);
        for (long d = today; d <= today + 2; d++) {
            if (alt(d) != m.alt) continue;
            long t = spawnAt(d, m);
            if (t > now) return eventDay(d) ? t : -1;
        }
        return -1;
    }

    /** Latest spawn of either meal on this texture (Breakfast/Brunch …), -1 outside the event. */
    static long lastSpawnFor(String base, long now) {
        long best = -1;
        for (Meal m : MEALS) if (m.hour == hourOf(base)) best = Math.max(best, lastSpawn(m, now));
        return best;
    }

    static long nextSpawnFor(String base, long now) {
        long best = -1;
        for (Meal m : MEALS) {
            if (m.hour != hourOf(base)) continue;
            long t = nextSpawn(m, now);
            if (t > 0 && (best < 0 || t < best)) best = t;
        }
        return best;
    }

    /** The meal an egg with this texture is right now: Brunch on alternate days etc. */
    static String currentMeal(String base, long now) {
        long best = -1;
        String name = base;
        for (Meal m : MEALS) {
            if (m.hour != hourOf(base)) continue;
            long t = lastSpawn(m, now);
            if (t > best) { best = t; name = m.name; }
        }
        return name;
    }

    private static int hourOf(String base) {
        return switch (base) { case "Breakfast" -> 7; case "Lunch" -> 14; default -> 21; };
    }

    static String fmtLeft(long ms) { return fmt(ms); }

    private static boolean claimed(Meal m, long now) {
        long last = lastSpawn(m, now);
        if (last < 0) return true;
        Long c = cfg().hoppityMealClaimed.get(m.name);
        return c != null && c >= last;
    }

    private static void claim(String meal) {
        for (Meal m : MEALS) {
            if (!m.name.equals(meal)) continue;
            cfg().hoppityMealClaimed.put(m.name, System.currentTimeMillis());
            TeslaMapsConfig.save();
            timer = 19;
        }
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.contains("Chocolate")) return;
        Matcher m = FOUND.matcher(t);
        if (m.find()) { claim(m.group(1)); return; }
        m = COLLECTED.matcher(t);
        if (m.find()) { claim(m.group(1)); return; }
        if (t.equals(NONE_LEFT)) {
            for (Meal meal : MEALS) claim(meal.name);
        }
    }

    private static boolean busy() {
        if (!SkyblockUtils.isInSkyblock()) return true;
        if (DungeonManager.isInDungeon() || com.teslamaps.features.kuudra.Kuudra.inKuudra()) return true;
        return SkyblockUtils.getArea().contains("Rift");
    }

    private static void tick() {
        if (++timer % 20 != 0) return;
        long now = System.currentTimeMillis();
        if (!eventActive() || (!cfg().hoppityUnclaimedWhileBusy && busy())) {
            lines = List.of();
            allReady = false;
            return;
        }
        long eventEnd = EPOCH + ((now - EPOCH) / YEAR) * YEAR + 3 * MONTH;
        List<String> out = new ArrayList<>();
        out.add("§bUnclaimed Eggs:");
        int ready = 0;
        for (Meal m : MEALS) {
            boolean c = claimed(m, now);
            long next = nextSpawn(m, now);
            if (c && next < 0) continue;
            String name = (c ? "§7§m" : m.color) + m.name + ":" + m.color;
            String time;
            if (!c) {
                ready++;
                time = "§aReady";
            } else {
                time = m.color + fmt(next - now);
            }
            if (next < 0 && !c) time += " §c(ends " + fmt(eventEnd - now) + ")";
            out.add("§7 - " + name + " " + time);
        }
        String area = SkyblockUtils.getArea();
        List<String> spots = area.isEmpty() ? null : cfg().hoppityUniqueEggs.get(area);
        if (spots != null && !spots.isEmpty()) {
            int n = Math.min(15, spots.size());
            double p = n / 15.0;
            String col = p < 0.3 ? "§c" : p < 0.6 ? "§6" : p < 0.9 ? "§e" : "§a";
            out.add("§7Locations: " + col + n + "§7/§a15");
        }
        lines = out.size() > 1 ? out : List.of();
        width = TextHud.width(lines);

        boolean all = ready == MEALS.length;
        if (all && cfg().hoppityUnclaimedWarn && (!allReady || now - lastWarn > 300_000)) warn(now);
        allReady = all;
    }

    private static void warn(long now) {
        lastWarn = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String cmd = "/warp " + cfg().hoppityUnclaimedWarp;
        mc.gui.setTimes(0, 50, 10);
        mc.gui.setTitle(Component.literal("§d§lAll Hoppity Eggs Ready!"));
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §dAll six Hoppity eggs are ready! §eClick to " + cmd)
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(cmd))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to run " + cmd)))));
        com.teslamaps.utils.LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f);
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) return (s / 3600) + "h " + (s % 3600 / 60) + "m";
        if (s >= 60) return (s / 60) + "m " + (s % 60) + "s";
        return s + "s";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty() || hud == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
