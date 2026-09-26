package com.teslamaps.features;

// Ported from SkyHanni's WhispersPerHotfPerk.kt, PowderPerHotmPerk.kt, CurrencyPerHotxPerk.kt (costs from HotmData/HotfData)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HotxPerkCurrency {

    private static final int MITHRIL = 0, GEMSTONE = 1, GLACITE = 2, WHISPERS = 3;
    private static final String[] CUR_NAME = {"Mithril Powder", "Gemstone Powder", "Glacite Powder", "Forest Whispers"};
    private static final String[] CUR_COLOR = {"§2", "§d", "§b", "§3"};
    private static final String[] CUR_LABEL = {"Powder", "Powder", "Powder", "Whispers"};

    /** max level, currency, offset, exponent; exponent 0 = Daily Wishes (200 + 18 * level). */
    private record Perk(int max, int currency, double offset, double exp) {
        long cost(int level) {
            if (exp == 0) return (long) (200.0 + level * 18.0);
            return (long) Math.pow(level + offset, exp);
        }

        long spent(int level) {
            long sum = 0;
            for (int l = 1; l < level; l++) sum += cost(l);
            return sum;
        }
    }

    private static final Map<String, Perk> HOTM = new HashMap<>();
    private static final Map<String, Perk> HOTF = new HashMap<>();

    static {
        m("Mining Speed", 50, MITHRIL, 3.0); m("Mining Fortune", 50, MITHRIL, 3.05);
        m("Titanium Insanium", 50, MITHRIL, 3.1); m("Luck of the Cave", 45, MITHRIL, 3.07);
        m("Efficient Miner", 100, MITHRIL, 2.6); m("Quick Forge", 20, MITHRIL, 3.2);
        m("Old-School", 20, GEMSTONE, 4.0); m("Professional", 140, GEMSTONE, 2.3);
        m("Mole", 200, GEMSTONE, 2.17883); m("Gem Lover", 20, GEMSTONE, 4.0);
        m("Seasoned Mineman", 100, GEMSTONE, 2.3); m("Fortunate Mineman", 50, GEMSTONE, 3.2);
        m("Blockhead", 20, GEMSTONE, 4.0); m("Keep It Cool", 50, GEMSTONE, 3.07);
        m("Lonesome Miner", 45, GEMSTONE, 3.07); m("Great Explorer", 20, GEMSTONE, 4.0);
        m("Powder Buff", 50, GEMSTONE, 3.2); m("Speedy Mineman", 50, GEMSTONE, 3.2);
        m("Subterranean Fisher", 40, GEMSTONE, 3.07);
        m("No Stone Unturned", 50, GLACITE, 3.05); m("Strong Arm", 100, GLACITE, 2.3);
        m("Steady Hand", 100, GLACITE, 2.6); m("Warm Heart", 50, GLACITE, 3.1);
        m("Surveyor", 20, GLACITE, 4.0); m("Metal Head", 20, GLACITE, 4.0);
        m("Rags to Riches", 50, GLACITE, 3.05); m("Eager Adventurer", 100, GLACITE, 2.3);
        m("Crystalline", 50, GLACITE, 3.3); m("Gifts from the Departed", 100, GLACITE, 2.45);
        HOTM.put("Mining Master", new Perk(10, GLACITE, 7.0, 5.0));
        m("Dead Man's Chest", 50, GLACITE, 3.2); m("Vanguard Seeker", 50, GLACITE, 3.1);

        f("Sweep", 50, 3.0); f("Foraging Fortune", 50, 3.105); f("Strength Boost", 50, 3.1);
        f("Speed Boost", 50, 3.1); f("Luck of the Forest", 40, 3.07); f("250 Gifts", 40, 3.07);
        f("Deep Waters", 50, 2.9); f("Efficient Forager", 100, 2.6); f("Collector", 50, 2.9);
        f("Forest Strength", 50, 3.4); f("Hunter's Luck", 50, 3.2); f("Galatea's Might", 50, 3.2);
        f("Essence Fortune", 50, 3.2); f("Forest Speed", 50, 3.4); f("Half Empty", 25, 4.1);
        f("Ricochet", 10, 5.5); f("Half Full", 25, 4.1);
        HOTF.put("Daily Wishes", new Perk(100, WHISPERS, 0, 0));
    }

    private static void m(String name, int max, int cur, double exp) { HOTM.put(name, new Perk(max, cur, 1.0, exp)); }

    private static void f(String name, int max, double exp) { HOTF.put(name, new Perk(max, WHISPERS, 1.0, exp)); }

    private static final Pattern LEVEL = Pattern.compile("Level (\\d+).*");
    private static final Pattern[] HEART = {
            Pattern.compile("Mithril Powder: ([\\d,]+)"), Pattern.compile("Gemstone Powder: ([\\d,]+)"),
            Pattern.compile("Glacite Powder: ([\\d,]+)"), Pattern.compile("Forest Whispers: ([\\d,]+)")};

    private static final class Built {
        Perk perk;
        int level;
        Component spent, for10, toMax, have;
        long haveAmount = -1;
    }

    private static final Map<ItemStack, Built> CACHE = new WeakHashMap<>();
    private static final long[] current = {-1, -1, -1, -1};
    private static Feature feature;
    private static Screen lastScreen;
    private static int tree; // 0 none, 1 HotM, 2 HotF
    private static int ticks;

    private HotxPerkCurrency() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("HotxPerkCurrency").when(() -> cfg().hotxCurrencyPerPerk)
                .tick(HotxPerkCurrency::tick)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Powder / Whispers Per Perk", () -> c.hotxCurrencyPerPerk, v -> c.hotxCurrencyPerPerk = v)
                            .desc("HotM and HotF perk tooltips: powder or whispers spent, cost of the next 10 levels, what you have.");
                    b.toggle("  Spent So Far", () -> c.hotxCurrencySpent, v -> c.hotxCurrencySpent = v);
                    b.toggle("  Next 10 Levels (Shift)", () -> c.hotxCurrencyFor10, v -> c.hotxCurrencyFor10 = v);
                    b.toggle("  Current Amount", () -> c.hotxCurrencyCurrent, v -> c.hotxCurrencyCurrent = v);
                    b.toggle("  Rest Until Max", () -> c.hotxCurrencyToMax, v -> c.hotxCurrencyToMax = v)
                            .desc("Powder or whispers still needed to max the perk, shown when it is more than the next 10 levels");
                });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (tree == 0 || feature == null || !feature.active() || lines.size() < 2) return;
            com.teslamaps.debug.Perf.begin("HotxPerkCurrency");
            try {
                onTooltip(stack, lines);
            } catch (Exception e) {
                com.teslamaps.TeslaMaps.debug("[HotxPerkCurrency] {}", e.toString());
            } finally {
                com.teslamaps.debug.Perf.end();
            }
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != lastScreen) {
            lastScreen = mc.screen;
            tree = 0;
            if (mc.screen instanceof AbstractContainerScreen<?> cs) {
                String t = StringUtil.stripTrim(cs.getTitle().getString());
                if (t.contains("Heart of the Mountain")) tree = cfg().section("Mining") ? 1 : 0;
                else if (t.contains("Heart of the Forest")) tree = cfg().section("Foraging") ? 2 : 0;
            }
            ticks = 9;
        }
        if (tree == 0 || ++ticks % 10 != 0) return;
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        String heart = tree == 1 ? "Heart of the Mountain" : "Heart of the Forest";
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty() || !StringUtil.stripTrim(st.getHoverName().getString()).equals(heart)) continue;
            for (String line : ItemUtil.loreLines(st)) {
                String l = line.trim();
                for (int i = 0; i < HEART.length; i++) {
                    Matcher mt = HEART[i].matcher(l);
                    if (mt.find()) current[i] = parse(mt.group(1));
                }
            }
            break;
        }
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != lastScreen) return;
        Built b = CACHE.get(stack);
        if (b == null) {
            b = build(stack);
            if (CACHE.size() > 256) CACHE.clear();
            CACHE.put(stack, b);
        }
        if (b.perk == null) return;
        TeslaMapsConfig c = cfg();
        Perk p = b.perk;

        if (c.hotxCurrencySpent) lines.add(Math.min(2, lines.size()), b.spent);
        if (b.level >= p.max) return;
        int costIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (StringUtil.stripTrim(lines.get(i).getString()).equals("Cost")) { costIdx = i; break; }
        }
        if (costIdx < 0) return;
        if (c.hotxCurrencyToMax && b.toMax != null) lines.add(Math.min(costIdx + 2, lines.size()), b.toMax);
        if (c.hotxCurrencyFor10 && mc.hasShiftDown()) lines.add(Math.min(costIdx + 2, lines.size()), b.for10);
        long have = current[p.currency];
        if (c.hotxCurrencyCurrent && have >= 0) {
            if (b.haveAmount != have) {
                b.haveAmount = have;
                b.have = Component.literal(CUR_COLOR[p.currency] + fmt(have) + " " + CUR_NAME[p.currency]);
            }
            int at = Math.min(costIdx + 2, lines.size());
            lines.add(at, Component.literal(" "));
            lines.add(at + 1, Component.literal("§7You have"));
            lines.add(at + 2, b.have);
        }
    }

    private static Built build(ItemStack stack) {
        Built b = new Built();
        String name = StringUtil.stripTrim(stack.getHoverName().getString());
        Perk p = (tree == 1 ? HOTM : HOTF).get(name);
        if (p == null) return b;
        List<String> lore = ItemUtil.loreLines(stack);
        int level = -1;
        for (String l : lore) {
            Matcher m = LEVEL.matcher(l.trim());
            if (m.matches()) { level = Integer.parseInt(m.group(1)); break; }
        }
        if (level < 1) return b;
        level = Math.min(level, p.max);
        b.perk = p;
        b.level = level;
        String label = CUR_LABEL[p.currency];
        long spent = p.spent(level), maxSpent = p.spent(p.max);
        if (level >= p.max) {
            b.spent = Component.literal("§7" + label + " spent: §e" + fmt(maxSpent) + " §7(§aMax level§7)");
        } else {
            double pct = maxSpent == 0 ? 0 : spent * 100.0 / maxSpent;
            b.spent = Component.literal("§7" + label + " spent: §e" + fmt(spent) + "§7/§e" + fmt(maxSpent)
                    + String.format(Locale.ROOT, "§7 (§e%.2f%%§7)", pct));
        }
        int target = Math.min(level + 10, p.max);
        int n = target - level;
        b.for10 = Component.literal("§7" + label + " for " + n + (n == 1 ? " level " : " levels ")
                + "§e" + fmt(p.spent(target) - spent));
        if (p.max - level > 10) {
            b.toMax = Component.literal("§7" + label + " until max (" + (p.max - level) + " levels) §e" + fmt(maxSpent - spent));
        }
        return b;
    }

    private static String fmt(long v) {
        return String.format(Locale.US, "%,d", v);
    }
}
