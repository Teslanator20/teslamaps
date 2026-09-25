package com.teslamaps.features.mining;

// After SkyHanni's PowderTracker.kt / PowderChestReward.kt and Skyblocker's PowderMiningTracker.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Treasure chest loot in the Crystal Hollows: chests, powder, gemstones, parts, eggs, per hour and profit. */
public final class PowderGrindTracker {

    private static final Pattern REWARD = Pattern.compile("^ {4}(.+?)(?: x([\\d,]+))?$");
    private static final Pattern GEM = Pattern.compile("^(Rough|Flawed|Fine|Flawless|Perfect) (\\w+) Gemstone$");
    private static final String CHESTS = "POWDER_CHESTS";
    private static final String[] POWDERS = {"MITHRIL_POWDER", "GEMSTONE_POWDER", "GLACITE_POWDER"};
    private static final String[] POWDER_NAMES = {"§2Mithril Powder", "§dGemstone Powder", "§bGlacite Powder"};
    private static final Map<String, String> IDS = new HashMap<>();

    static {
        IDS.put("Mithril Powder", "MITHRIL_POWDER");
        IDS.put("Gemstone Powder", "GEMSTONE_POWDER");
        IDS.put("Glacite Powder", "GLACITE_POWDER");
        IDS.put("Pickonimbus 2000", "PICKONIMBUS");
        IDS.put("Gold Essence", "ESSENCE_GOLD");
        IDS.put("Diamond Essence", "ESSENCE_DIAMOND");
        IDS.put("Goblin Egg", "GOBLIN_EGG");
        IDS.put("Green Goblin Egg", "GOBLIN_EGG_GREEN");
        IDS.put("Blue Goblin Egg", "GOBLIN_EGG_BLUE");
        IDS.put("Red Goblin Egg", "GOBLIN_EGG_RED");
        IDS.put("Yellow Goblin Egg", "GOBLIN_EGG_YELLOW");
        IDS.put("FTX 3070", "FTX_3070");
        IDS.put("Electron Transmitter", "ELECTRON_TRANSMITTER");
        IDS.put("Robotron Reflector", "ROBOTRON_REFLECTOR");
        IDS.put("Superlite Motor", "SUPERLITE_MOTOR");
        IDS.put("Control Switch", "CONTROL_SWITCH");
        IDS.put("Synthetic Heart", "SYNTHETIC_HEART");
        IDS.put("Wishing Compass", "WISHING_COMPASS");
        IDS.put("Sludge Juice", "SLUDGE_JUICE");
        IDS.put("Ascension Rope", "ASCENSION_ROPE");
        IDS.put("Treasurite", "TREASURITE");
        IDS.put("Jungle Heart", "JUNGLE_HEART");
        IDS.put("Yoggie", "YOGGIE");
        IDS.put("Prehistoric Egg", "PREHISTORIC_EGG");
        IDS.put("Oil Barrel", "OIL_BARREL");
    }

    private static Tracker tracker;
    private static HudEditElement hud;
    private static boolean inChest, show;
    private static long lastChest;

    private PowderGrindTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        tracker = Tracker.create("powder_mining", "§b§lPowder Tracker")
                .priceSource(() -> cfg().powderTrackerPriceSource)
                .fixedValue(CHESTS, 0).name(CHESTS, "§6Treasure Chests")
                .extra(PowderGrindTracker::perHour);
        for (int i = 0; i < POWDERS.length; i++) tracker.fixedValue(POWDERS[i], 0).name(POWDERS[i], POWDER_NAMES[i]);
        hud = HudEditElement.of("powderTracker", "Powder Tracker").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().powderTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("PowderGrindTracker").section("Mining").when(() -> cfg().powderTracker)
                .tick(PowderGrindTracker::tick)
                .serverChat(PowderGrindTracker::onChat)
                .hud("powdertracker", PowderGrindTracker::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Powder Tracker", () -> c.powderTracker, v -> c.powderTracker = v)
                            .desc("Crystal Hollows treasure chests: chests and powder per hour, gemstones, robot parts, goblin eggs and the rest, with profit. /tmap tracker powder_mining reset | view")
                            .tags("powder", "tracker", "treasure chest", "gemstone", "crystal hollows", "profit");
                    b.toggle("  Only While Grinding", () -> c.powderTrackerOnlyGrinding, v -> c.powderTrackerOnlyGrinding = v)
                            .desc("Hide the tracker when no chest was opened in the last minute");
                    b.toggle("  Count Natural Loot Chests", () -> c.powderTrackerNatural, v -> c.powderTrackerNatural = v)
                            .desc("Also count \"LOOT CHEST COLLECTED\" chests, not only lockpicked ones");
                    b.priceSource("  Price Source", () -> c.powderTrackerPriceSource, v -> c.powderTrackerPriceSource = v);
                    b.trackerButtons(tracker);
                });
    }

    private static List<String> perHour(Tracker.View view) {
        if (view != Tracker.View.SESSION) return List.of();
        double hours = tracker.activeMillis() / 3_600_000.0;
        if (hours < 1 / 60.0) return List.of();
        List<String> out = new ArrayList<>();
        out.add("§6Chests/h: §f" + String.format(Locale.ROOT, "%,d", Math.round(tracker.count(CHESTS, view) / hours)));
        for (int i = 0; i < POWDERS.length; i++) {
            long n = tracker.count(POWDERS[i], view);
            if (n > 0) out.add(POWDER_NAMES[i] + "/h: §f" + String.format(Locale.ROOT, "%,d", Math.round(n / hours)));
        }
        return out;
    }

    private static void tick() {
        show = SkyblockUtils.isInArea("Crystal Hollows")
                && (!cfg().powderTrackerOnlyGrinding || System.currentTimeMillis() - lastChest < 60_000);
        tracker.tick();
    }

    private static void onChat(String text) {
        if (!SkyblockUtils.isInArea("Crystal Hollows")) return;
        String plain = StringUtil.strip(text);
        String t = plain.trim();
        if (!inChest) {
            if (t.equals("CHEST LOCKPICKED") || (cfg().powderTrackerNatural && t.equals("LOOT CHEST COLLECTED"))) {
                inChest = true;
                lastChest = System.currentTimeMillis();
                tracker.add(CHESTS, 1);
            }
            return;
        }
        if (t.startsWith("▬▬▬▬▬▬▬▬▬▬")) {
            inChest = false;
            return;
        }
        Matcher m = REWARD.matcher(plain);
        if (!m.matches()) return;
        String name = m.group(1).trim();
        if (!name.isEmpty() && !Character.isLetterOrDigit(name.charAt(0))) {
            int sp = name.indexOf(' ');
            if (sp < 0) return;
            name = name.substring(sp + 1).trim();
        }
        long amount = 1;
        if (m.group(2) != null) {
            try { amount = Long.parseLong(m.group(2).replace(",", "")); } catch (NumberFormatException ignored) {}
        }
        String id = idFor(name);
        if (id != null) tracker.add(id, amount);
    }

    private static String idFor(String name) {
        String id = IDS.get(name);
        if (id != null) return id;
        Matcher g = GEM.matcher(name);
        if (g.matches()) return (g.group(1) + "_" + g.group(2) + "_GEM").toUpperCase(Locale.ROOT);
        if (name.equals("REWARDS") || name.contains("CHEST")) return null;
        id = PriceManager.idForName(name);
        return id != null ? id : SkyblockItems.idForName(name);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        tracker.draw(g, mc, hud.x(), hud.y(), hud.scale());
    }
}
