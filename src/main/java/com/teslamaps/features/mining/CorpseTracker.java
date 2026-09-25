package com.teslamaps.features.mining;

// After SkyHanni's CorpseLoot.kt, CorpseTracker.kt and ProfitPerMineshaftCorpse.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Glacite Mineshaft corpse loot minus the keys, one bucket per corpse type plus all. */
public final class CorpseTracker {

    private static final Pattern START = Pattern.compile("^([A-Z]+) CORPSE LOOT!$");
    private static final Pattern END = Pattern.compile("^▬{64}$");
    private static final Pattern AMOUNT = Pattern.compile("^(?:\\+?([\\d,]+)x )?(.+?)(?: ?x([\\d,]+))?$");
    private static final Pattern BOOK = Pattern.compile("^Enchanted Book \\((.+) ([IVX]+)\\)$");
    private static final String RESOURCEFUL = "LUCKY! Your Resourceful perk saved your key from being consumed!";
    public static final String[] SHOW = {"All", "Lapis", "Tungsten", "Umber", "Vanguard"};
    // type -> key item, display
    private static final String[][] TYPES = {
            {"LAPIS", null, "§9Lapis"},
            {"TUNGSTEN", "TUNGSTEN_KEY", "§7Tungsten"},
            {"UMBER", "UMBER_KEY", "§6Umber"},
            {"VANGUARD", "SKELETON_KEY", "§fVanguard"}};
    private static final String CORPSES = "CORPSES_LOOTED", POWDER = "GLACITE_POWDER";

    private static Tracker all;
    private static final Tracker[] byType = new Tracker[TYPES.length];
    private static HudEditElement hud;
    private static boolean show, inLoot, keySaved;
    private static int type = -1;
    private static final Map<String, Long> loot = new LinkedHashMap<>();

    private CorpseTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        all = setup(Tracker.create("corpse", "§b§lCorpse Profit"));
        for (int i = 0; i < TYPES.length; i++) {
            byType[i] = setup(Tracker.create("corpse_" + TYPES[i][0].toLowerCase(Locale.ROOT), "§b§l" + StringUtil.strip(TYPES[i][2]) + " Corpse Profit"));
        }
        hud = HudEditElement.of("corpseTracker", "Corpse Profit Tracker").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().corpseTracker)
                .size(() -> Math.max(80, shown().size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, shown().size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> shown().draw(g, mc, x, y, s));
        Feature.of("CorpseTracker").section("Mining").when(() -> cfg().corpseTracker)
                .tick(CorpseTracker::tick)
                .serverChat(CorpseTracker::onChat)
                .hud("corpsetracker", CorpseTracker::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Corpse Profit Tracker", () -> c.corpseTracker, v -> c.corpseTracker = v)
                            .desc("Glacite Mineshaft corpse loot minus the keys used, per corpse type or all together. "
                                    + "Fix a key count with /tmap tracker corpse edit <key> <amount>. Move it in /tmap gui.")
                            .tags("corpse", "mineshaft", "glacite", "tungsten key", "umber key", "skeleton key", "profit", "tracker");
                    b.dropdown("  Show Corpse Type", SHOW, () -> c.corpseTrackerShow, v -> c.corpseTrackerShow = v);
                    b.toggle("  Profit Per Corpse Message", () -> c.corpseTrackerChat, v -> c.corpseTrackerChat = v)
                            .desc("\"Profit for Umber Corpse\" after each loot, hover for the items");
                    b.priceSource("  Price Source", () -> c.corpseTrackerPriceSource, v -> c.corpseTrackerPriceSource = v);
                    b.buttonRow(new String[]{"Reset Session", "Reset Total", "Session / Total"},
                            new Runnable[]{() -> eachTracker(Tracker::resetSession), () -> eachTracker(Tracker::resetTotal),
                                    () -> eachTracker(Tracker::toggleView)});
                });
    }

    private static Tracker setup(Tracker t) {
        t.priceSource(() -> cfg().corpseTrackerPriceSource)
                .fixedValue(CORPSES, 0).name(CORPSES, "§eCorpses Looted")
                .fixedValue(POWDER, 0).name(POWDER, "§bGlacite Powder");
        for (String[] ty : TYPES) if (ty[1] != null) t.name(keyRow(ty[1]), ty[2] + " Keys Used");
        return t;
    }

    private static void eachTracker(java.util.function.Consumer<Tracker> f) {
        f.accept(all);
        for (Tracker t : byType) f.accept(t);
    }

    private static String keyRow(String key) {
        return "CORPSE_" + key;
    }

    private static Tracker shown() {
        String s = cfg().corpseTrackerShow;
        for (int i = 0; i < TYPES.length; i++) if (SHOW[i + 1].equals(s)) return byType[i];
        return all;
    }

    private static double price(String id) {
        Double p = PriceManager.getPriceOrNull(id, cfg().corpseTrackerPriceSource);
        return p == null ? 0 : p;
    }

    private static void tick() {
        show = Mining.onMiningIsland();
        for (String[] ty : TYPES) {
            if (ty[1] == null) continue;
            double p = -price(ty[1]);
            all.fixedValue(keyRow(ty[1]), p);
            for (Tracker t : byType) t.fixedValue(keyRow(ty[1]), p);
        }
        all.tick();
        for (Tracker t : byType) t.tick();
    }

    private static void onChat(String text) {
        if (!SkyblockUtils.isInArea("Mineshaft")) return;
        String plain = StringUtil.strip(text);
        String t = plain.trim();
        if (t.equals(RESOURCEFUL)) {
            keySaved = true;
            return;
        }
        Matcher s = START.matcher(t);
        if (s.matches()) {
            type = -1;
            for (int i = 0; i < TYPES.length; i++) if (TYPES[i][0].equals(s.group(1))) type = i;
            inLoot = type >= 0;
            loot.clear();
            return;
        }
        if (!inLoot) return;
        if (END.matcher(t).matches()) {
            inLoot = false;
            finish();
            return;
        }
        if (!plain.startsWith("    ") || t.isEmpty()) return;
        // gem symbol in front of gemstones
        if (!Character.isLetterOrDigit(t.charAt(0)) && t.indexOf(' ') > 0) t = t.substring(t.indexOf(' ') + 1);
        Matcher m = AMOUNT.matcher(t);
        if (!m.matches()) return;
        long amount = 1;
        String n = m.group(1) != null ? m.group(1) : m.group(3);
        if (n != null) {
            try { amount = Long.parseLong(n.replace(",", "")); } catch (NumberFormatException ignored) {}
        }
        String name = m.group(2).trim();
        String id = name.equals("Glacite Powder") ? POWDER : idFor(name);
        if (id != null) loot.merge(id, amount, Long::sum);
    }

    private static String idFor(String name) {
        Matcher b = BOOK.matcher(name);
        if (b.matches()) {
            return "ENCHANTMENT_" + b.group(1).toUpperCase(Locale.ROOT).replace(' ', '_') + "_" + roman(b.group(2));
        }
        String id = PriceManager.idForName(name);
        return id != null ? id : SkyblockItems.idForName(name);
    }

    private static int roman(String r) {
        int total = 0, prev = 0;
        for (int i = r.length() - 1; i >= 0; i--) {
            int v = switch (r.charAt(i)) { case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; default -> 0; };
            total += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return total;
    }

    private static void finish() {
        String[] ty = TYPES[type];
        Tracker bucket = byType[type];
        boolean keyUsed = ty[1] != null && !keySaved;
        keySaved = false;
        for (Tracker t : new Tracker[]{all, bucket}) {
            t.add(CORPSES, 1);
            if (keyUsed) t.add(keyRow(ty[1]), 1);
        }
        double total = 0;
        List<Map.Entry<String, Double>> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : loot.entrySet()) {
            all.add(e.getKey(), e.getValue());
            bucket.add(e.getKey(), e.getValue());
            if (e.getKey().equals(POWDER)) continue;
            double v = all.value(e.getKey(), e.getValue());
            total += v;
            rows.add(Map.entry(e.getKey(), v));
        }
        rows.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        double key = keyUsed ? price(ty[1]) : 0;
        total -= key;

        Minecraft mc = Minecraft.getInstance();
        if (!cfg().corpseTrackerChat || mc.player == null) { loot.clear(); return; }
        List<String> hover = new ArrayList<>();
        for (Map.Entry<String, Double> r : rows) {
            String name = SkyblockItems.coloredName(r.getKey());
            hover.add("§eFound §f" + loot.get(r.getKey()) + "x " + (name != null ? name : r.getKey()) + " §6" + ItemPrice.format(r.getValue()));
        }
        if (keyUsed) {
            String keyName = SkyblockItems.coloredName(ty[1]);
            hover.add("§cCost: " + (keyName != null ? keyName : ty[1]) + " §7(§c-" + ItemPrice.format(key) + "§7)");
        }
        String msg = "Profit for " + ty[2] + " Corpse§e: " + (total < 0 ? "§c-" : "§6") + ItemPrice.format(Math.abs(total));
        hover.add("");
        hover.add("§e" + msg);
        Component tip = Component.literal(String.join("\n", hover));
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §e" + msg)
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(tip))));
        loot.clear();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        shown().draw(g, mc, hud.x(), hud.y(), hud.scale());
    }
}
