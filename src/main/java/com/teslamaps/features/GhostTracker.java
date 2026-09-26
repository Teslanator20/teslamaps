package com.teslamaps.features;

// Ported from SkyHanni's features/combat/ghosttracker/GhostTracker.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.Placeholders;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GhostTracker {

    private static final Pattern DROP = Pattern.compile("^RARE DROP! (?<item>.+?) \\(\\+(?<mf>[\\d,]+)%? ✯ Magic Find\\)");
    private static final Pattern COMBO_END = Pattern.compile("^Your Kill Combo has expired! You reached a (?<kill>[\\d,.]+) Kill Combo!");
    private static final String BAG_OF_CASH = "The ghost's death materialized 1,000,000 coins from the mists!";
    private static final Pattern BESTIARY = Pattern.compile("^\\s*Ghost (?:\\d+|[XVI]+): (?<kills>[\\d,.]+)/[\\d,.]+");
    private static final Pattern BESTIARY_MAX = Pattern.compile("^\\s*Ghost (?:\\d+|[XVI]+): MAX");
    private static final Pattern PURSE = Pattern.compile("^(?:Purse|Piggy): ([\\d,]+(?:\\.\\d+)?)");
    private static final Pattern XP = Pattern.compile("\\+([\\d,.]+) Combat \\(");
    private static final Map<String, String> DROPS = Map.of(
            "Sorrow", "SORROW", "Plasma", "PLASMA", "Volta", "VOLTA", "Ghostly Boots", "GHOST_BOOTS");
    private static final String KILLS = "GHOST_KILLS";
    private static final long MAX_BESTIARY = 100_000;

    private static Tracker tracker;
    private static boolean inMist, bestiaryFound;
    private static long purse = -1;
    private static long lastWarn;
    private static String lastBar = "", lastXpPart = "";
    private static int ticks;

    private GhostTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        tracker = Tracker.create("ghost", "§e§lGhost Profit Tracker")
                .priceSource(() -> cfg().ghostTrackerPriceSource).maxRows(15)
                .fixedValue(KILLS, 0).name(KILLS, "§7Kills");
        tracker.extra(GhostTracker::extra);
        HudEditElement.of("ghostTracker", "Ghost Tracker").section("Combat")
                .enabled(() -> cfg().ghostTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("GhostTracker").section("Combat").when(() -> cfg().ghostTracker)
                .tick(GhostTracker::tick)
                .serverChat(GhostTracker::onChat)
                .hud("ghosttracker", GhostTracker::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Ghosts");
                    b.toggle("Ghost Profit Tracker", () -> c.ghostTracker, v -> c.ghostTracker = v)
                            .desc("The Mist: Sorrow / Plasma / Volta / Ghostly Boots drops, coins, Bag of Cash, kills from the "
                                    + "Bestiary tab widget, ghosts since Sorrow, max kill combo, combat XP and average Magic Find. Move it in /tmap gui.")
                            .tags("ghost", "mist", "sorrow", "plasma", "volta", "tracker", "profit");
                    b.toggle("  Bestiary Widget Warning", () -> c.ghostTrackerWidgetWarn, v -> c.ghostTrackerWidgetWarn = v)
                            .desc("Chat hint (once a minute) when the Bestiary tab widget with Ghost is missing.");
                    b.priceSource("  Price Source", () -> c.ghostTrackerPriceSource, v -> c.ghostTrackerPriceSource = v);
                    b.trackerButtons(tracker);
                    b.button("  Reset Ghost Stats", () -> {
                        c.ghostsSinceSorrow = 0;
                        c.ghostMaxCombo = 0;
                        c.ghostCombatXp = 0;
                        c.ghostMfTotal = 0;
                        c.ghostMfDrops = 0;
                        TeslaMapsConfig.save();
                    });
                });
    }

    private static List<String> extra(Tracker.View v) {
        TeslaMapsConfig c = cfg();
        List<String> out = new ArrayList<>();
        out.add("§7Ghosts Since Sorrow: §e" + String.format(Locale.ROOT, "%,d", c.ghostsSinceSorrow));
        out.add("§7Max Kill Combo: §e" + String.format(Locale.ROOT, "%,d", c.ghostMaxCombo));
        out.add("§7Combat XP Gained: §e" + String.format(Locale.ROOT, "%,d", c.ghostCombatXp));
        double mf = c.ghostMfDrops == 0 ? 0 : (double) c.ghostMfTotal / c.ghostMfDrops;
        out.add("§7Average Magic Find: §b" + String.format(Locale.ROOT, "%.1f", mf));
        out.add("§7Bestiary Kills: §e" + (c.ghostBestiaryKills >= MAX_BESTIARY ? "MAX" : String.format(Locale.ROOT, "%,d", c.ghostBestiaryKills)));
        if (!bestiaryFound) out.add("§cBestiary widget missing (/widget)");
        return out;
    }

    private static void tick() {
        tracker.tick();
        if (++ticks % 20 == 0) {
            boolean was = inMist;
            inMist = false;
            for (String l : ScoreboardUtils.getScoreboardLines()) {
                if (ScoreboardUtils.cleanLine(l).contains("The Mist")) { inMist = true; break; }
            }
            if (!inMist) {
                purse = -1;
                return;
            }
            if (!was) purse = -1;
            readBestiary();
            readPurse();
            warn();
        }
        if (inMist) readXp();
    }

    private static void readBestiary() {
        TeslaMapsConfig c = cfg();
        bestiaryFound = false;
        for (String raw : TabListUtils.getTabListLines()) {
            String line = StringUtil.strip(raw);
            if (line.indexOf("Ghost") < 0) continue;
            if (BESTIARY_MAX.matcher(line).find()) {
                bestiaryFound = true;
                c.ghostBestiaryKills = MAX_BESTIARY;
                return;
            }
            Matcher m = BESTIARY.matcher(line);
            if (!m.find()) continue;
            bestiaryFound = true;
            long kills = parse(m.group("kills"));
            if (kills <= c.ghostBestiaryKills) return;
            long diff = kills - c.ghostBestiaryKills;
            c.ghostBestiaryKills = kills;
            if (diff > 50) return;
            tracker.add(KILLS, diff);
            c.ghostsSinceSorrow += diff;
            return;
        }
    }

    private static void readPurse() {
        long value = -1;
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            Matcher m = PURSE.matcher(ScoreboardUtils.cleanLine(raw));
            if (!m.find()) continue;
            value = parse(m.group(1));
            break;
        }
        if (value < 0) return;
        long gain = purse < 0 ? 0 : value - purse;
        purse = value;
        if (gain >= 200 && gain <= 15_000) tracker.addCoins(gain);
    }

    private static void readXp() {
        String bar = Placeholders.actionBar;
        if (bar == null || bar == lastBar) return;
        lastBar = bar;
        String s = StringUtil.strip(bar);
        Matcher m = XP.matcher(s);
        if (!m.find()) return;
        int end = s.indexOf(')', m.end());
        String part = end < 0 ? s.substring(m.start()) : s.substring(m.start(), end + 1);
        if (part.equals(lastXpPart)) return;
        lastXpPart = part;
        long gained = Math.round(parseDouble(m.group(1)));
        if (gained > 0 && gained <= 10_000) cfg().ghostCombatXp += gained;
    }

    private static void warn() {
        if (!cfg().ghostTrackerWidgetWarn || bestiaryFound) return;
        long now = System.currentTimeMillis();
        if (now - lastWarn < 60_000) return;
        lastWarn = now;
        Minecraft.getInstance().player.sendSystemMessage(Component.literal(
                "§b[TeslaMaps] §cGhost Bestiary not found in the tab list! Enable the Bestiary widget with /widget for the Ghost Tracker."));
    }

    private static void onChat(String raw) {
        if (!inMist) return;
        String t = StringUtil.stripTrim(raw);
        TeslaMapsConfig c = cfg();
        if (t.startsWith("RARE DROP!")) {
            Matcher m = DROP.matcher(t);
            if (!m.find()) return;
            String id = DROPS.get(m.group("item").trim());
            if (id == null) return;
            tracker.add(id, 1);
            c.ghostMfTotal += parse(m.group("mf"));
            c.ghostMfDrops++;
            if (id.equals("SORROW")) c.ghostsSinceSorrow = 0;
            return;
        }
        if (t.startsWith("Your Kill Combo")) {
            Matcher m = COMBO_END.matcher(t);
            if (m.find()) c.ghostMaxCombo = Math.max(c.ghostMaxCombo, parse(m.group("kill")));
            return;
        }
        if (t.equals(BAG_OF_CASH)) tracker.addCoins(1_000_000);
    }

    private static long parse(String s) {
        return Math.round(parseDouble(s));
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!inMist) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        TeslaMapsConfig c = cfg();
        tracker.draw(g, mc, c.ghostTrackerX, c.ghostTrackerY, c.ghostTrackerScale);
    }
}
