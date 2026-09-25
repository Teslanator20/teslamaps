package com.teslamaps.features.mining;

// Ported from SkyHanni's CrystalNucleusApi.kt, CrystalNucleusTracker.kt and CrystalNucleusProfitPer.kt
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

/** Crystal Nucleus loot minus Jungle Key and Robot Parts / Precursor Apparatus, per run. */
public final class NucleusTracker {

    private static final String START = "CRYSTAL NUCLEUS LOOT BUNDLE";
    private static final Pattern END = Pattern.compile("^▬{64}$");
    private static final Pattern AMOUNT = Pattern.compile("^(?:\\+?([\\d,]+)x )?(.+?)(?: ?x([\\d,]+))?$");
    private static final Pattern BOOK = Pattern.compile("^Enchanted Book \\((.+) ([IVX]+)\\)$");
    private static final Pattern BAL = Pattern.compile("^(?:\\[[^]]+] )?(\\w+) has obtained \\[Lvl 1] Bal!$");
    private static final String[] PARTS = {"CONTROL_SWITCH", "ELECTRON_TRANSMITTER", "FTX_3070",
            "ROBOTRON_REFLECTOR", "SUPERLITE_MOTOR", "SYNTHETIC_HEART"};
    private static final String RUNS = "NUCLEUS_RUNS", KEY = "NUCLEUS_JUNGLE_KEY", PRECURSOR = "NUCLEUS_PRECURSOR";
    public static final String[] PROFESSOR = {"Robot Parts", "Precursor Apparatus"};

    private static Tracker tracker;
    private static HudEditElement hud;
    private static boolean inLoot, show;
    private static final Map<String, Long> loot = new LinkedHashMap<>();

    private NucleusTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        tracker = Tracker.create("crystal_nucleus", "§e§lCrystal Nucleus Profit")
                .priceSource(() -> cfg().nucleusPriceSource)
                .fixedValue(RUNS, 0).name(RUNS, "§eRuns Completed")
                .name(KEY, "§5Jungle Keys Used").name(PRECURSOR, "§9Robot Parts Used");
        hud = HudEditElement.of("nucleusTracker", "Crystal Nucleus Tracker").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().nucleusTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("NucleusTracker").section("Mining").when(() -> cfg().nucleusTracker)
                .tick(NucleusTracker::tick)
                .serverChat(NucleusTracker::onChat)
                .hud("nucleustracker", NucleusTracker::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Crystal Nucleus Profit Tracker", () -> c.nucleusTracker, v -> c.nucleusTracker = v)
                            .desc("Loot of every Crystal Nucleus run minus the Jungle Key and Robot Parts, plus completed runs. Shown in the Crystal Hollows (/tmap gui).")
                            .tags("nucleus", "crystal hollows", "jungle key", "robot parts", "profit", "tracker");
                    b.dropdown("  Professor Usage", PROFESSOR, () -> c.nucleusProfessor, v -> c.nucleusProfessor = v);
                    b.toggle("  Profit Chat Message", () -> c.nucleusChat, v -> c.nucleusChat = v)
                            .desc("\"Profit for Crystal Nucleus Run\" after each run, hover for the loot");
                    b.slider("    Hide Items Below (k coins)", 0f, 1000f,
                            () -> c.nucleusChatMinimum / 1000f, v -> c.nucleusChatMinimum = Math.round(v) * 1000, 0);
                    b.priceSource("  Price Source", () -> c.nucleusPriceSource, v -> c.nucleusPriceSource = v);
                    b.trackerButtons(tracker);
                });
    }

    private static boolean apparatus() {
        return PROFESSOR[1].equals(cfg().nucleusProfessor);
    }

    private static double price(String id) {
        Double p = PriceManager.getPriceOrNull(id, cfg().nucleusPriceSource);
        return p == null ? 0 : p;
    }

    private static double precursorCost() {
        if (apparatus()) return price("PRECURSOR_APPARATUS");
        double sum = 0;
        for (String p : PARTS) sum += price(p);
        return sum;
    }

    private static void tick() {
        show = SkyblockUtils.isInArea("Crystal Hollows");
        tracker.fixedValue(KEY, -price("JUNGLE_KEY"));
        tracker.fixedValue(PRECURSOR, -precursorCost());
        tracker.name(PRECURSOR, apparatus() ? "§5Precursor Apparatus Used" : "§9Robot Part Sets Used");
        tracker.tick();
    }

    private static void onChat(String text) {
        if (!show) return;
        String plain = StringUtil.strip(text);
        String t = plain.trim();
        if (t.startsWith(START)) {
            inLoot = true;
            loot.clear();
            return;
        }
        Matcher bal = BAL.matcher(t);
        if (bal.matches()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && bal.group(1).equalsIgnoreCase(mc.player.getName().getString())) {
                tracker.add(text.contains("§6Bal") ? "BAL;4" : "BAL;3", 1);
            }
            return;
        }
        if (!inLoot) return;
        if (END.matcher(t).matches()) {
            inLoot = false;
            finish();
            return;
        }
        if (!plain.startsWith("    ")) return;
        if (t.contains(" Powder") || t.isEmpty()) return;
        Matcher m = AMOUNT.matcher(t);
        if (!m.matches()) return;
        long amount = 1;
        String n = m.group(1) != null ? m.group(1) : m.group(3);
        if (n != null) {
            try { amount = Long.parseLong(n.replace(",", "")); } catch (NumberFormatException ignored) {}
        }
        String id = idFor(m.group(2).trim());
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
        tracker.add(RUNS, 1);
        tracker.add(KEY, 1);
        tracker.add(PRECURSOR, 1);
        double total = 0;
        List<Map.Entry<String, Double>> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : loot.entrySet()) {
            tracker.add(e.getKey(), e.getValue());
            double v = tracker.value(e.getKey(), e.getValue());
            total += v;
            rows.add(Map.entry(e.getKey(), v));
        }
        rows.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        double key = price("JUNGLE_KEY"), parts = precursorCost();
        total -= key + parts;

        Minecraft mc = Minecraft.getInstance();
        if (!cfg().nucleusChat || mc.player == null) { loot.clear(); return; }
        List<String> hover = new ArrayList<>();
        int hidden = 0;
        double hiddenSum = 0;
        for (Map.Entry<String, Double> r : rows) {
            if (r.getValue() < cfg().nucleusChatMinimum) {
                hidden++;
                hiddenSum += r.getValue();
                continue;
            }
            String name = SkyblockItems.coloredName(r.getKey());
            hover.add("§eFound §f" + loot.get(r.getKey()) + "x " + (name != null ? name : r.getKey()) + " §6" + ItemPrice.format(r.getValue()));
        }
        if (hidden > 0) hover.add("§7" + hidden + " cheap items are hidden §7(§6" + ItemPrice.format(hiddenSum) + "§7).");
        String msg = "Profit for Crystal Nucleus Run§e: " + (total < 0 ? "§c-" : "§6") + ItemPrice.format(Math.abs(total));
        hover.add("");
        hover.add("§cUsed §5Jungle Key§7: §c-" + ItemPrice.format(key));
        hover.add("§cUsed " + (apparatus() ? "§5Precursor Apparatus" : "§9Robot Parts") + "§7: §c-" + ItemPrice.format(parts));
        hover.add("");
        hover.add("§e" + msg);
        Component tip = Component.literal(String.join("\n", hover));
        mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §e" + msg)
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(tip))));
        loot.clear();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        tracker.draw(g, mc, hud.x(), hud.y(), hud.scale());
    }
}
