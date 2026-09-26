package com.teslamaps.features.foraging;

// After SkyHanni's ForagingTracker.kt / ForagingTrackerLegacy.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tree gift loot and profit, per tree type (Fig / Mangrove / Helix) or all together. */
public final class ForagingTracker {

    public static final String[] SHOW = {"All", "Fig", "Mangrove", "Helix"};
    private static final Pattern SEPARATOR = Pattern.compile("▬{64}");
    private static final Pattern BONUS_HEADER = Pattern.compile(" *BONUS GIFT");
    private static final Pattern CONTRIBUTION = Pattern.compile(" *You helped cut (?<pct>[\\d.]+)% of the (?<type>.*) Tree\\.");
    private static final Pattern REWARDS = Pattern.compile(" *\\+[\\d,]+ rewards gained!.*");
    private static final Pattern BONUS_REWARD = Pattern.compile(" *(?<item>.*) \\([\\d.]+%\\)");
    private static final Pattern HOVER_AMOUNT = Pattern.compile("(?<item>\\S.*?)\\s+x(?:0-)?(?<amount>[\\d,]+)");
    private static final Pattern HOVER_CHANCE = Pattern.compile("(?<item>\\S.*?)\\s+\\([\\d.]+%\\)");
    private static final Pattern BOOK = Pattern.compile("Enchanted Book \\((?<book>.+) (?<tier>[IVXL]+)\\)");
    private static final Pattern SACK_ADD = Pattern.compile("\\+([\\d,]+) ((?:Enchanted )?(?:Fig|Mangrove|Helix) Log) \\(");
    private static final String TREES = "FT_TREES", WHOLE = "FT_TREE_PERCENT", FXP = "FT_FORAGING_XP", HXP = "FT_HOTF_XP", WHISPERS = "FT_WHISPERS";

    private static Tracker all;
    private static final Tracker[] byType = new Tracker[3];
    private static boolean inBlock, bonus, show, header;
    private static int type = -1;
    private static long lastAxe;

    private ForagingTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        all = setup(Tracker.create("foraging", "§a§lForaging Tracker"));
        for (int i = 0; i < 3; i++) byType[i] = setup(Tracker.create("foraging_" + SHOW[i + 1].toLowerCase(Locale.ROOT), "§a§l" + SHOW[i + 1] + " Foraging Tracker"));
        HudEditElement.of("foragingTracker", "Foraging Tracker").ctx(HudEditElement.HudCtx.FORAGING).section("Foraging")
                .enabled(() -> cfg().foragingTracker)
                .size(() -> Math.max(80, shown().size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, shown().size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> shown().draw(g, mc, x, y, s));
        Feature.of("ForagingTracker").section("Foraging").when(() -> cfg().foragingTracker)
                .tick(ForagingTracker::tick)
                .hud("foragingtracker", ForagingTracker::render)
                .config("Foraging", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Foraging Tracker");
                    b.toggle("Foraging Tracker", () -> c.foragingTracker, v -> c.foragingTracker = v)
                            .desc("Tree gift loot, XP, whispers and logs from sacks with profit per gift and per hour. Move it in /tmap gui.")
                            .tags("tree gift", "fig", "mangrove", "helix", "profit", "tracker");
                    b.dropdown("  Show Tree Type", SHOW, () -> c.foragingTrackerShow, v -> c.foragingTrackerShow = v);
                    b.toggle("  Only With Axe", () -> c.foragingTrackerAxeOnly, v -> c.foragingTrackerAxeOnly = v)
                            .desc("Shown while holding an axe and 10 s after");
                    b.priceSource("  Price Source", () -> c.foragingTrackerPriceSource, v -> c.foragingTrackerPriceSource = v);
                    b.buttonRow(new String[]{"Reset Session", "Reset Total", "Session / Total"},
                            new Runnable[]{() -> each(Tracker::resetSession), () -> each(Tracker::resetTotal), () -> each(Tracker::toggleView)});
                });
    }

    private static Tracker setup(Tracker t) {
        t.priceSource(() -> cfg().foragingTrackerPriceSource)
                .fixedValue(TREES, 0).name(TREES, "§eTrees Felled")
                .fixedValue(WHOLE, 0).name(WHOLE, "§7Tree % Cut")
                .fixedValue(FXP, 0).name(FXP, "§3Foraging Experience")
                .fixedValue(HXP, 0).name(HXP, "§aHOTF Experience")
                .fixedValue(WHISPERS, 0).name(WHISPERS, "§bForest Whispers")
                .extra(v -> {
                    List<String> out = new ArrayList<>();
                    long trees = t.count(TREES, v);
                    if (trees <= 0) return out;
                    out.add(String.format(Locale.ROOT, "§eWhole Trees: §f%.2f", t.count(WHOLE, v) / 100.0));
                    out.add("§eProfit/Gift: §6" + ItemPrice.format(t.profit(v) / trees));
                    return out;
                });
        return t;
    }

    private static void each(java.util.function.Consumer<Tracker> f) {
        f.accept(all);
        for (Tracker t : byType) f.accept(t);
    }

    private static Tracker shown() {
        String s = cfg().foragingTrackerShow;
        for (int i = 0; i < 3; i++) if (SHOW[i + 1].equals(s)) return byType[i];
        return all;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean island = Foraging.onForagingIsland();
        if (island && holdingAxe(mc)) lastAxe = System.currentTimeMillis();
        show = island && (!cfg().foragingTrackerAxeOnly || System.currentTimeMillis() - lastAxe < 10_000);
        each(Tracker::tick);
    }

    private static boolean holdingAxe(Minecraft mc) {
        if (mc.player == null) return false;
        String id = ItemUtil.skyblockId(mc.player.getMainHandItem());
        if (id.isEmpty()) return false;
        return "AXE".equals(SkyblockItems.category(id)) || id.endsWith("_AXE") || id.contains("CHOPPER");
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || com.teslamaps.utils.HudVisibility.suppressed()) return;
        TeslaMapsConfig c = cfg();
        shown().draw(g, mc, c.foragingTrackerX, c.foragingTrackerY, c.foragingTrackerScale);
    }

    private static void add(String key, long amount) {
        if (key == null || amount <= 0) return;
        all.add(key, amount);
        if (type >= 0) byType[type].add(key, amount);
    }

    /** From ForagingChat, every server line on the foraging islands. */
    public static void onChat(Component message, String clean) {
        if (!cfg().foragingTracker || !cfg().section("Foraging") || !Foraging.onForagingIsland()) return;
        String t = clean.stripTrailing();
        if (t.contains("[Sacks]")) {
            sacks(message);
            return;
        }
        if (SEPARATOR.matcher(t.trim()).matches()) {
            inBlock = !inBlock;
            bonus = false;
            header = false;
            return;
        }
        if (!inBlock) return;
        if (!header) {
            if (t.trim().equals("TREE GIFT")) header = true;
            else if (!t.isBlank()) inBlock = false;
            return;
        }
        if (BONUS_HEADER.matcher(t).matches()) {
            bonus = true;
            return;
        }
        Matcher m = CONTRIBUTION.matcher(t);
        if (m.matches()) {
            type = -1;
            for (int i = 0; i < 3; i++) if (SHOW[i + 1].equalsIgnoreCase(m.group("type").trim())) type = i;
            add(TREES, 1);
            try {
                add(WHOLE, Math.round(Double.parseDouble(m.group("pct"))));
            } catch (NumberFormatException ignored) {}
            return;
        }
        if (REWARDS.matcher(t).matches()) {
            String hover = hoverText(message);
            if (hover != null) for (String line : hover.split("\n")) hoverLine(StringUtil.stripTrim(line));
            return;
        }
        if (bonus) {
            m = BONUS_REWARD.matcher(t);
            if (m.matches()) add(idFor(m.group("item").trim()), 1);
        }
    }

    private static void hoverLine(String line) {
        Matcher m = HOVER_AMOUNT.matcher(line);
        String item;
        long amount;
        if (m.matches()) {
            item = m.group("item");
            try {
                amount = Long.parseLong(m.group("amount").replace(",", ""));
            } catch (NumberFormatException e) {
                return;
            }
        } else {
            m = HOVER_CHANCE.matcher(line);
            if (!m.matches()) return;
            item = m.group("item");
            amount = 1;
        }
        switch (item) {
            case "HOTF Experience" -> add(HXP, amount);
            case "Foraging Experience" -> add(FXP, amount);
            case "Forest Whispers" -> add(WHISPERS, amount);
            default -> add(idFor(item), amount);
        }
    }

    private static void sacks(Component message) {
        List<String> added = new ArrayList<>();
        collect(message, added);
        for (String h : added) {
            Matcher m = SACK_ADD.matcher(StringUtil.strip(h));
            while (m.find()) {
                try {
                    String name = m.group(2);
                    int logType = -1;
                    for (int i = 0; i < 3; i++) if (name.contains(SHOW[i + 1])) logType = i;
                    String id = (name.startsWith("Enchanted ") ? "ENCHANTED_" : "") + SHOW[logType + 1].toUpperCase(Locale.ROOT) + "_LOG";
                    long n = Long.parseLong(m.group(1).replace(",", ""));
                    all.add(id, n);
                    byType[logType].add(id, n);
                } catch (RuntimeException ignored) {}
            }
        }
    }

    private static void collect(Component c, List<String> out) {
        if (c.getStyle().getHoverEvent() instanceof HoverEvent.ShowText show) {
            String h = show.value().getString();
            if (StringUtil.stripTrim(h).startsWith("Added")) out.add(h);
        }
        for (Component s : c.getSiblings()) collect(s, out);
    }

    private static String hoverText(Component c) {
        if (c.getStyle().getHoverEvent() instanceof HoverEvent.ShowText show) return show.value().getString();
        for (Component s : c.getSiblings()) {
            String h = hoverText(s);
            if (h != null) return h;
        }
        return null;
    }

    private static String idFor(String name) {
        Matcher b = BOOK.matcher(name);
        if (b.matches()) return "ENCHANTMENT_" + b.group("book").toUpperCase(Locale.ROOT).replace(' ', '_') + "_" + roman(b.group("tier"));
        String id = PriceManager.idForName(name);
        if (id == null) id = SkyblockItems.idForName(name);
        return id != null ? id : name;
    }

    private static int roman(String r) {
        int total = 0, prev = 0;
        for (int i = r.length() - 1; i >= 0; i--) {
            int v = switch (r.charAt(i)) { case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50; default -> 0; };
            total += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return total;
    }
}
