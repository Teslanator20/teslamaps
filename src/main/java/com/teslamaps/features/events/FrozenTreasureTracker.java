package com.teslamaps.features.events;

// Ported from SkyHanni's FrozenTreasureTracker.kt + FrozenTreasure.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.FishingHelpers;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.LineOrder;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Locale;

public final class FrozenTreasureTracker {

    private record Treasure(String key, String name, String display, int amount, int ice) {}

    private static final Treasure[] TREASURES = {
            new Treasure("WHITE_GIFT", "White Gift", "§fWhite Gift", 1, 0),
            new Treasure("GREEN_GIFT", "Green Gift", "§aGreen Gift", 1, 0),
            new Treasure("RED_GIFT", "Red Gift", "§cRed Gift", 1, 0),
            new Treasure("PACKED_ICE", "Packed Ice", "§fPacked Ice", 32, 9),
            new Treasure("ENCHANTED_ICE", "Enchanted Ice", "§aEnchanted Ice", 9, 160),
            new Treasure("ENCHANTED_PACKED_ICE", "Enchanted Packed Ice", "§9Enchanted Packed Ice", 1, 25600),
            new Treasure("ICE_BAIT", "Ice Bait", "§aIce Bait", 16, 0),
            new Treasure("GLOWY_CHUM_BAIT", "Glowy Chum Bait", "§aGlowy Chum Bait", 16, 0),
            new Treasure("GLACIAL_FRAGMENT", "Glacial Fragment", "§5Glacial Fragment", 1, 0),
            new Treasure("GLACIAL_TALISMAN", "Glacial Talisman", "§fGlacial Talisman", 1, 0),
            new Treasure("FROZEN_BAIT", "Frozen Bait", "§9Frozen Bait", 1, 0),
            new Treasure("EINARY_RED_HOODIE", "Einary's Red Hoodie", "§cEinary's Red Hoodie", 1, 0),
    };
    private static final String PREFIX = "FROZEN TREASURE! You found ";
    private static final String COMPACT = "COMPACT! You found an Enchanted Ice!";
    private static final String ID = "frozenTreasure";
    private static final String[] KEYS = {"title", "mined", "ice", "iceHour", "compact", "items"};
    private static final String[] LABELS = {"Title", "Treasures Mined", "Total Ice", "Ice/hr", "Compact Procs", "Treasures"};
    private static final List<String> SAMPLE = List.of("§e§lFrozen Treasure Tracker", "§66 Treasures Mined",
            "§3320k Total Ice", "§31.2M Ice/hr", "§82 Compact Procs", "§b3 §fPacked Ice");

    private static Tracker stats;
    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width;
    private static boolean inWorkshop, inCave;
    private static int ticks;

    private FrozenTreasureTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        LineOrder.define(ID, KEYS, LABELS);
        stats = Tracker.create("frozen_treasure", "Frozen Treasure").fixedValue("mined", 0).fixedValue("compact", 0);
        for (Treasure t : TREASURES) stats.fixedValue(t.key, 0);
        hud = HudEditElement.of("frozenTreasureTracker", "Frozen Treasure Tracker").section("Events")
                .enabled(() -> cfg().frozenTreasureTracker)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width,
                        () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("FrozenTreasureTracker").section("Events").when(() -> cfg().frozenTreasureTracker)
                .tick(FrozenTreasureTracker::tick)
                .serverChat(FrozenTreasureTracker::onChat)
                .hudHiddenInInventory("frozentreasuretracker", FrozenTreasureTracker::render)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Frozen Treasure Tracker", () -> c.frozenTreasureTracker, v -> c.frozenTreasureTracker = v)
                            .desc("Counts Frozen Treasure finds (gifts, ice, baits, Glacial Fragments ...) with total ice, ice/hr "
                                    + "and Compact procs. Move it in /tmap gui.")
                            .tags("frozen", "treasure", "jerry", "winter", "ice", "glacial");
                    b.toggle("  Only In Glacial Cave", () -> c.frozenTreasureOnlyCave, v -> c.frozenTreasureOnlyCave = v);
                    b.toggle("  Show As Drops", () -> c.frozenTreasureAsDrops, v -> c.frozenTreasureAsDrops = v)
                            .desc("Multiply each treasure by its drop amount (32 Packed Ice per find, ...).");
                    b.toggle("  Hide Chat Messages", () -> c.frozenTreasureHideChat, v -> c.frozenTreasureHideChat = v);
                    b.lineOrder("  Lines", ID);
                    b.buttonRow(new String[]{"Reset Session", "Reset Total", "Session / Total"},
                            new Runnable[]{stats::resetSession, stats::resetTotal, stats::toggleView});
                });
    }

    private static Treasure treasure(String t) {
        if (!t.startsWith(PREFIX) || !t.endsWith("!")) return null;
        String name = t.substring(PREFIX.length(), t.length() - 1);
        for (Treasure tr : TREASURES) if (tr.name.equals(name)) return tr;
        return null;
    }

    private static void onChat(String raw) {
        if (!inWorkshop) return;
        String t = StringUtil.stripTrim(raw);
        if (t.equals(COMPACT)) {
            stats.add("compact", 1);
            return;
        }
        Treasure tr = treasure(t);
        if (tr == null) return;
        stats.add("mined", 1);
        stats.add(tr.key, 1);
    }

    /** From ChatMixin: true hides the line. */
    public static boolean hide(String raw) {
        TeslaMapsConfig c = cfg();
        if (!c.frozenTreasureTracker || !c.frozenTreasureHideChat || !inWorkshop || !c.section("Events")) return false;
        String t = StringUtil.stripTrim(raw);
        return t.equals(COMPACT) || treasure(t) != null;
    }

    private static void tick() {
        stats.tick();
        if (++ticks % 20 != 0) return;
        inWorkshop = SkyblockUtils.isInArea("Jerry's Workshop");
        inCave = inWorkshop && FishingHelpers.subArea().equals("Glacial Cave");
        build();
    }

    private static void build() {
        TeslaMapsConfig c = cfg();
        if (!inWorkshop || (c.frozenTreasureOnlyCave && !inCave)) {
            lines = List.of();
            return;
        }
        Tracker.View v = stats.view();
        long ice = stats.count("compact", v) * 160L;
        for (Treasure t : TREASURES) ice += stats.count(t.key, v) * t.amount * (long) t.ice;
        LineOrder.Lines l = LineOrder.lines(ID);
        l.key("title").add("§e§lFrozen Treasure Tracker §7(" + (v == Tracker.View.SESSION ? "Session" : v.name().charAt(0) + v.name().substring(1).toLowerCase(Locale.ROOT)) + ")");
        l.key("mined").add("§6" + fmt(stats.count("mined", v)) + " Treasures Mined");
        l.key("ice").add("§3" + fmt(ice) + " Total Ice");
        if (v == Tracker.View.SESSION) {
            long ms = stats.activeMillis();
            l.key("iceHour").add("§3" + fmt(ms < 60_000 ? 0 : (long) (ice * 3_600_000.0 / ms)) + " Ice/hr");
        }
        l.key("compact").add("§8" + fmt(stats.count("compact", v)) + " Compact Procs");
        l.key("items");
        for (Treasure t : TREASURES) {
            long n = stats.count(t.key, v);
            if (n == 0) continue;
            l.add("§b" + fmt(n * (c.frozenTreasureAsDrops ? t.amount : 1)) + " " + t.display);
        }
        lines = l.build();
        width = TextHud.width(lines);
    }

    private static String fmt(long n) {
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        if (n >= 10_000) return String.format(Locale.ROOT, "%.1fk", n / 1_000.0);
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (lines.isEmpty() || hud == null || mc.options.hideGui) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
