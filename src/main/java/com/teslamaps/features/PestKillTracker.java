package com.teslamaps.features;

// Ported from SkyHanni's PestProfitTracker.kt and Devonian's PestKillsTracker.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Kills per pest species, their drops with rates, kill coins and spray costs, session and total. */
public final class PestKillTracker {

    public static final String[] PESTS = {"Beetle", "Cricket", "Earthworm", "Field Mouse", "Fly", "Locust", "Mite",
            "Mosquito", "Moth", "Rat", "Slug", "Firefly", "Dragonfly", "Praying Mantis"};
    private static final String[] SPRAYS = {"Compost", "Plant Matter", "Dung", "Honey Jar", "Tasty Cheese", "Jelly", "Moondew"};

    private static final Pattern KILL = Pattern.compile("^You received ([\\d,]+)x (.+) for killing an? (.+)!$");
    private static final Pattern RARE = Pattern.compile("^(?:RARE|PET) DROP! (.+?)(?: x\\d+)?(?: \\(.*\\))?$");
    private static final Pattern SPRAY = Pattern.compile("^SPRAYONATOR! You sprayed Plot - .+ with (?:(\\d+) )?(.+)!$");
    private static final Pattern PURSE = Pattern.compile("^(?:Purse|Piggy): ([\\d,]+(?:\\.\\d+)?)");

    private static Tracker tracker;
    private static final Map<String, Long> rare = new LinkedHashMap<>();
    private static String lastPest;
    private static long lastKill;
    private static long purse = -1;
    private static int ticks;

    private PestKillTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        tracker = Tracker.create("pests", "§a§lPest Tracker")
                .priceSource(() -> cfg().pestKillTrackerPriceSource)
                .name(Tracker.COINS, "§6Pest Kill Coins")
                .extra(PestKillTracker::extraLines);
        for (String p : PESTS) tracker.fixedValue(killKey(p), 0).name(killKey(p), "§e" + p + " Kills");
        for (String s : SPRAYS) tracker.name(sprayKey(s), "§c" + s + " Sprays");
        HudEditElement.of("pestKillTracker", "Pest Tracker").ctx(HudEditElement.HudCtx.GARDEN).section("Garden")
                .enabled(() -> cfg().pestKillTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("PestKillTracker").section("Garden").when(() -> cfg().pestKillTracker)
                .tick(PestKillTracker::tick)
                .serverChat(PestKillTracker::onChat)
                .hudForeground("pestkilltracker", PestKillTracker::render);
    }

    public static Tracker tracker() {
        return tracker;
    }

    private static String killKey(String pest) {
        return "PEST_KILL_" + pest.toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private static String sprayKey(String spray) {
        return "PEST_SPRAY_" + spray.toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private static String sprayItem(String spray) {
        return spray.equals("Tasty Cheese") ? "CHEESE_FUEL" : spray.toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        for (String s : SPRAYS) {
            Double p = c.pestKillTrackerSprays ? PriceManager.getPriceOrNull(sprayItem(s), c.pestKillTrackerPriceSource) : null;
            tracker.fixedValue(sprayKey(s), p == null ? 0 : -p);
        }
        tracker.tick();
        if (++ticks < 10) return;
        ticks = 0;
        readPurse();
    }

    private static void readPurse() {
        if (!SkyblockUtils.isInGarden()) { purse = -1; return; }
        long value = -1;
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            Matcher m = PURSE.matcher(ScoreboardUtils.cleanLine(raw));
            if (!m.find()) continue;
            try { value = (long) Double.parseDouble(m.group(1).replace(",", "")); } catch (NumberFormatException ignored) {}
            break;
        }
        if (value < 0) return;
        long gain = purse < 0 ? 0 : value - purse;
        purse = value;
        if (!cfg().pestKillTrackerCoins || gain <= 0 || gain > 1_000_000) return;
        if (System.currentTimeMillis() - lastKill < 2500) tracker.addCoins(gain);
    }

    private static void onChat(String raw) {
        if (!SkyblockUtils.isInGarden()) return;
        String text = StringUtil.stripTrim(raw);
        Matcher m = KILL.matcher(text);
        if (m.matches()) {
            long amount = Long.parseLong(m.group(1).replace(",", ""));
            String item = m.group(2).trim();
            String pest = m.group(3).trim();
            if (counts(item, pest)) {
                tracker.add(killKey(pest), 1);
                lastPest = pest;
                lastKill = System.currentTimeMillis();
            }
            addItem(item, amount);
            return;
        }
        if (text.startsWith("RARE DROP!") || text.startsWith("PET DROP!")) {
            if (System.currentTimeMillis() - lastKill > 3000 || lastPest == null) return;
            m = RARE.matcher(text);
            if (!m.matches()) return;
            String item = m.group(1).trim();
            addItem(item, 1);
            rare.merge(lastPest + "|" + item, 1L, Long::sum);
            return;
        }
        if (text.startsWith("SPRAYONATOR! You sprayed")) {
            m = SPRAY.matcher(text);
            if (!m.matches()) return;
            String spray = m.group(2).trim();
            for (String s : SPRAYS) {
                if (!s.equalsIgnoreCase(spray)) continue;
                tracker.add(sprayKey(s), m.group(1) == null ? 1 : Long.parseLong(m.group(1)));
                return;
            }
        }
    }

    /** Same rule as FarmProfit: one line per kill carries the count. */
    private static boolean counts(String item, String pest) {
        return switch (pest) {
            case "Field Mouse" -> item.equals("Dung");
            case "Lunar Moth" -> item.equals("Enchanted Sunflower");
            default -> !item.equals("Overclocker 3000");
        };
    }

    private static void addItem(String name, long amount) {
        String id = SkyblockItems.idForName(name);
        if (id != null) {
            tracker.add(id, amount);
            return;
        }
        String key = "PEST_ITEM_" + name.toUpperCase(Locale.ROOT).replace(' ', '_');
        tracker.name(key, "§f" + name).fixedValue(key, BazaarPrices.known(name) ? BazaarPrices.priceOf(name) : 0);
        tracker.add(key, amount);
    }

    private static List<String> extraLines(Tracker.View view) {
        List<String> out = new ArrayList<>();
        long kills = 0;
        for (String p : PESTS) kills += tracker.count(killKey(p), view);
        out.add("§7Pests killed: §e" + String.format(Locale.ROOT, "%,d", kills));
        if (cfg().pestKillTrackerRates && view == Tracker.View.SESSION) {
            for (Map.Entry<String, Long> e : rare.entrySet()) {
                int cut = e.getKey().indexOf('|');
                String pest = e.getKey().substring(0, cut);
                long of = tracker.count(killKey(pest), view);
                if (of <= 0) continue;
                out.add(String.format(Locale.ROOT, "§7%s §8(%s)§7: §e%d§7/§e%d §7(%.2f%%)",
                        e.getKey().substring(cut + 1), pest, e.getValue(), of, e.getValue() * 100.0 / of));
            }
        }
        return out;
    }

    /** From ChatMixin: the kill drop lines, when hidden. */
    public static boolean hideDrop(String raw) {
        TeslaMapsConfig c = cfg();
        if (!c.pestKillTracker || !c.pestKillTrackerHideDropChat) return false;
        if (raw.indexOf("for killing") < 0 || !SkyblockUtils.isInGarden()) return false;
        return KILL.matcher(StringUtil.stripTrim(raw)).matches();
    }

    public static void resetRates() {
        rare.clear();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        TeslaMapsConfig c = cfg();
        if (!SkyblockUtils.isInGarden()) return;
        tracker.draw(g, Minecraft.getInstance(), c.pestKillTrackerX, c.pestKillTrackerY, c.pestKillTrackerScale);
    }
}
