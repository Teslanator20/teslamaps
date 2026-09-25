package com.teslamaps.features;

// Ported from SkyHanni's AnitaMedalProfit.kt and AnitaExtraFarmingFortune.kt
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.RepoCache;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnitaShop {

    private static final String REPO = "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/AnitaUpgradeCosts.json";
    private static final Pattern AMOUNT = Pattern.compile("^(.+?) x([\\d,]+)$");
    private static final Pattern AMOUNT_FIRST = Pattern.compile("^([\\d,]+) ([\\w' ]+)$");
    private static final Pattern FORTUNE = Pattern.compile("You have: \\+(\\d+). Farming Fortune");
    private static final Pattern TICKETS = Pattern.compile("Jacob's Ticket x([\\d,.]+)");

    private record Cost(int gold, int tickets) {}

    private static final EcoPanel panel = new EcoPanel(EcoPanel.Side.LEFT);
    private static volatile Map<Integer, Cost> levels = Map.of();
    private static boolean loading;
    private static long lastTry;
    private static Screen lastScreen;
    private static int ticks;
    private static Feature feature;

    private AnitaShop() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("AnitaShop").section("Garden")
                .when(() -> cfg().anitaMedalProfit || cfg().anitaExtraFortune)
                .tick(AnitaShop::tick)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Anita Shop");
                    b.toggle("Anita Medal Profit", () -> c.anitaMedalProfit, v -> c.anitaMedalProfit = v)
                            .desc("Table next to Anita's shop: profit per bronze medal for every item (gold = 8, silver = 2 bronze), after the extra material cost.")
                            .tags("medal", "jacob", "contest", "anita");
                    b.toggle("  Extra Farming Fortune Cost", () -> c.anitaExtraFortune, v -> c.anitaExtraFortune = v)
                            .desc("Tooltip of Extra Farming Fortune: gold medals and Jacob tickets left to max it, plus the current tier.");
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature == null || !feature.active() || !cfg().anitaExtraFortune) return;
            try { tooltip(stack, lines); } catch (RuntimeException ignored) {}
        });
    }

    private static boolean inAnita(Screen s) {
        return s instanceof AbstractContainerScreen<?> cs && StringUtil.stripTrim(cs.getTitle().getString()).equals("Anita");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!cfg().anitaMedalProfit || !inAnita(mc.screen)) {
            lastScreen = null;
            panel.clear();
            return;
        }
        if (lastScreen != mc.screen) {
            lastScreen = mc.screen;
            ticks = 0;
        }
        if (ticks++ % 10 != 0) return;
        PriceManager.ensureFresh();
        if (cfg().anitaExtraFortune && levels.isEmpty()) load();
        build((AbstractContainerScreen<?>) mc.screen);
    }

    private record Entry(EcoPanel.Row row, double perBronze) {}

    private static void build(AbstractContainerScreen<?> cs) {
        List<Entry> entries = new ArrayList<>();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            Entry e = read(slot.index, st);
            if (e != null) entries.add(e);
        }
        if (entries.isEmpty()) {
            panel.clear();
            return;
        }
        entries.sort((a, b) -> Double.compare(b.perBronze(), a.perBronze()));
        List<EcoPanel.Row> rows = new ArrayList<>(entries.size() + 1);
        rows.add(EcoPanel.Row.of("§eProfit per Bronze Medal"));
        for (Entry e : entries) rows.add(e.row());
        panel.set(rows);
    }

    private static Entry read(int slot, ItemStack st) {
        List<String> lore = ItemUtil.loreLines(st);
        boolean trade = false;
        int header = -1;
        for (int i = 0; i < lore.size(); i++) {
            String t = lore.get(i).trim();
            if (t.equals("Click to trade!")) trade = true;
            if (t.equals("Cost") || t.equals("Cost:")) header = i;
        }
        if (!trade || header < 0) return null;

        long bronze = 0;
        double extra = 0;
        List<String> mats = new ArrayList<>();
        for (int i = header + 1; i < lore.size(); i++) {
            String t = lore.get(i).trim();
            if (t.isEmpty()) break;
            String name = t;
            long amount = 1;
            Matcher m = AMOUNT.matcher(t), f = AMOUNT_FIRST.matcher(t);
            if (m.matches()) { name = m.group(1).trim(); amount = parse(m.group(2)); }
            else if (f.matches()) { name = f.group(2).trim(); amount = parse(f.group(1)); }
            String low = name.toLowerCase(Locale.ROOT);
            if (low.startsWith("gold medal")) { bronze += 8 * amount; continue; }
            if (low.startsWith("silver medal")) { bronze += 2 * amount; continue; }
            if (low.startsWith("bronze medal")) { bronze += amount; continue; }
            if (low.equals("coins") || low.endsWith(" coins")) { extra += amount; mats.add("§6" + t); continue; }
            String id = idFor(name);
            double p = id == null ? 0 : PriceManager.getPrice(id) * amount;
            extra += p;
            mats.add("§7" + t + (p > 0 ? " §6" + EstimatedValue.fmt(p) : ""));
        }
        if (bronze <= 0) return null;

        String display = st.getHoverName().getString();
        String plain = StringUtil.stripTrim(display);
        long count = 1;
        Matcher m = AMOUNT.matcher(plain);
        if (m.matches()) { plain = m.group(1).trim(); count = parse(m.group(2)); }
        String id = idFor(plain);
        double price = id != null && !id.equals("ENCHANTED_BOOK") ? PriceManager.getPrice(id) * count : 0;
        if (price <= 0) price = EstimatedValue.compute(st) * Math.max(1, st.getCount());
        if (price <= 0) return null;

        double profit = price - extra;
        double perBronze = profit / bronze;
        String col = perBronze > 0 ? "§6" : "§c";
        List<String> tip = new ArrayList<>();
        tip.add(display);
        tip.add("");
        tip.add("§7Sell price: §6" + EstimatedValue.fmt(price));
        tip.add("§7Additional cost: §6" + EstimatedValue.fmt(extra));
        tip.addAll(mats);
        tip.add("§7Profit per sell: §6" + EstimatedValue.fmt(profit));
        tip.add("");
        tip.add("§7Bronze medals required: §c" + bronze);
        tip.add("§7Profit per bronze medal: " + col + EstimatedValue.fmt(perBronze));
        String text = display + " §7- " + col + EstimatedValue.fmt(perBronze);
        return new Entry(new EcoPanel.Row(text, EcoPanel.tip(tip), null, slot), perBronze);
    }

    private static String idFor(String name) {
        String id = PriceManager.idForName(name);
        return id != null ? id : SkyblockItems.idForName(name);
    }

    private static long parse(String s) {
        try {
            return (long) Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static void render(GuiGraphicsExtractor g, int mx, int my) {
        if (feature == null || !feature.active() || panel.empty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != lastScreen || !(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        panel.render(g, cs, 0, mx, my);
    }

    public static boolean scroll(double mx, double my, double dy) {
        return feature != null && feature.active() && !panel.empty() && panel.scroll(mx, my, dy);
    }

    private static void tooltip(ItemStack stack, List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();
        if (!inAnita(mc.screen)) return;
        if (!StringUtil.stripTrim(stack.getHoverName().getString()).equals("Extra Farming Fortune")) return;
        Map<Integer, Cost> lv = levels;
        if (lv.isEmpty()) {
            load();
            return;
        }
        int fortune = 0;
        double realTickets = 0;
        for (String l : ItemUtil.loreLines(stack)) {
            String t = l.trim();
            Matcher f = FORTUNE.matcher(t);
            if (f.find()) fortune = Integer.parseInt(f.group(1));
            Matcher k = TICKETS.matcher(t);
            if (k.find()) {
                try { realTickets = Double.parseDouble(k.group(1).replace(",", "")); } catch (NumberFormatException ignored) {}
            }
        }
        int tier = fortune / 4;
        Cost next = lv.get(tier + 1);
        double factor = next != null && next.tickets() > 0 ? realTickets / next.tickets() : 0;
        int gold = 0, tickets = 0;
        for (Map.Entry<Integer, Cost> e : lv.entrySet()) {
            if (e.getKey() <= tier) continue;
            gold += e.getValue().gold();
            tickets += e.getValue().tickets();
        }
        tickets = (int) (factor * tickets);

        int idx = -1, fortuneLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            String s = StringUtil.stripTrim(lines.get(i).getString());
            if (s.equals("Click to trade!")) idx = i - 1;
            if (FORTUNE.matcher(s).find()) fortuneLine = i;
        }
        if (idx < 0) return;
        double price = tickets * PriceManager.getPrice("JACOBS_TICKET");
        lines.add(idx, Component.literal("  §7Price: §6" + EstimatedValue.fmt(price) + " coins"));
        lines.add(idx, Component.literal("§aJacob Tickets §8x" + String.format(Locale.ROOT, "%,d", tickets)));
        lines.add(idx, Component.literal("§6Gold medals: §8x" + gold));
        lines.add(idx, Component.literal("§7Cost to max out"));
        lines.add(idx, Component.literal(""));
        if (fortuneLine >= 0 && fortuneLine < idx) {
            lines.add(fortuneLine + 1, Component.literal("§7Current Tier: §e" + tier + "/" + lv.size()));
        }
    }

    private static void load() {
        long now = System.currentTimeMillis();
        if (loading || now - lastTry < 60_000L) return;
        lastTry = now;
        loading = true;
        CompletableFuture.runAsync(() -> {
            try {
                JsonElement json = RepoCache.fetch("AnitaUpgradeCosts", REPO, 20);
                JsonObject lp = json.getAsJsonObject().getAsJsonObject("level_price");
                Map<Integer, Cost> out = new TreeMap<>();
                for (var e : lp.entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    out.put(Integer.parseInt(e.getKey()), new Cost(o.get("gold_medals").getAsInt(), o.get("jacob_tickets").getAsInt()));
                }
                levels = out;
            } catch (Exception e) {
                com.teslamaps.TeslaMaps.LOGGER.warn("[AnitaShop] upgrade costs failed: {}", e.toString());
            } finally {
                loading = false;
            }
        });
    }
}
