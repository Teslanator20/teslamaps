package com.teslamaps.features;

// Ported from Skyblocker's BitsHelper and SkyHanni's SkyMartCopperPrice
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.debug.Perf;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Coins per bit (Community Shop) and coins per copper (SkyMart), best deal highlighted. */
public final class BitsShopHelper {

    private static final Pattern BITS = Pattern.compile("^([\\d,]+) Bits$");
    private static final Pattern COPPER = Pattern.compile("^([\\d,]+) Copper$");
    private static final Pattern ITEM_COST = Pattern.compile("^(.+?)(?: x([\\d,]+))?$");

    // quick sellers after Skyblocker (300+ daily sales)
    private static final Set<String> GOOD_SELLING = Set.of("KAT_FLOWER", "KAT_BOUQUET", "HEAT_CORE",
            "ULTIMATE_CARROT_CANDY_UPGRADE", "TALISMAN_ENRICHMENT_SWAPPER", "GOD_POTION_2", "KISMET_FEATHER",
            "MATRIARCH_PARFUM", "AUTOPET_RULES_2");

    // category buttons: item id -> bits, the button shows its best item
    private static final Map<String, Map<String, Integer>> CATEGORIES = Map.of(
            "Kat Items", Map.of("KAT_FLOWER", 500, "KAT_BOUQUET", 2500),
            "Upgrade Components", Map.of("HEAT_CORE", 3000, "HYPER_CATALYST_UPGRADE", 300,
                    "ULTIMATE_CARROT_CANDY_UPGRADE", 8000, "COLOSSAL_EXP_BOTTLE_UPGRADE", 1200,
                    "JUMBO_BACKPACK_UPGRADE", 4000, "MINION_STORAGE_EXPANDER", 1500),
            "Sacks", Map.of("POCKET_SACK_IN_A_SACK", 8000, "LARGE_DUNGEON_SACK", 14000, "RUNE_SACK", 14000,
                    "FLOWER_SACK", 14000, "DWARVEN_MINES_SACK", 14000, "CRYSTAL_HOLLOWS_SACK", 14000),
            "Abiphone Supershop", Map.of("TRIO_CONTACTS_ADDON", 6450, "ABICASE_SUMSUNG_1", 15000,
                    "ABICASE_SUMSUNG_2", 25000, "ABICASE_REZAR", 26000, "ABICASE_BLUE_AQUA", 17000,
                    "ABICASE_BLUE_BLUE", 17000, "ABICASE_BLUE_GREEN", 17000, "ABICASE_BLUE_RED", 17000,
                    "ABICASE_BLUE_YELLOW", 17000),
            "Dyes", Map.of("DYE_PURE_WHITE", 250000, "DYE_PURE_BLACK", 250000),
            "Stacking Enchants", Map.of("ENCHANTMENT_HECATOMB_1", 6000, "ENCHANTMENT_EXPERTISE_1", 4000,
                    "ENCHANTMENT_COMPACT_1", 4000, "ENCHANTMENT_CULTIVATING_1", 4000,
                    "ENCHANTMENT_CHAMPION_1", 4000, "ENCHANTMENT_TOXOPHILITE_1", 4000),
            "Enrichments", Map.ofEntries(Map.entry("TALISMAN_ENRICHMENT_SWAPPER", 200),
                    Map.entry("TALISMAN_ENRICHMENT_WALK_SPEED", 5000), Map.entry("TALISMAN_ENRICHMENT_INTELLIGENCE", 5000),
                    Map.entry("TALISMAN_ENRICHMENT_CRITICAL_DAMAGE", 5000), Map.entry("TALISMAN_ENRICHMENT_CRITICAL_CHANCE", 5000),
                    Map.entry("TALISMAN_ENRICHMENT_STRENGTH", 5000), Map.entry("TALISMAN_ENRICHMENT_DEFENSE", 5000),
                    Map.entry("TALISMAN_ENRICHMENT_HEALTH", 5000), Map.entry("TALISMAN_ENRICHMENT_MAGIC_FIND", 5000),
                    Map.entry("TALISMAN_ENRICHMENT_FEROCITY", 5000), Map.entry("TALISMAN_ENRICHMENT_SEA_CREATURE_CHANCE", 5000),
                    Map.entry("TALISMAN_ENRICHMENT_ATTACK_SPEED", 5000)));

    private enum Shop { NONE, BITS, COPPER }

    private static Feature feature;
    private static Screen lastScreen;
    private static Shop shop = Shop.NONE;
    private static int ticks;
    private static final Map<ItemStack, List<String>> tips = new IdentityHashMap<>();
    private static int bestAll = -1, bestSelling = -1;

    private BitsShopHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("BitsShopHelper")
                .when(() -> cfg().bitsShopHelper)
                .tick(BitsShopHelper::tick)
                .config("Tooltips & Value", b -> {
                    b.label("Bits / Copper Shops");
                    b.toggle("Bits & Copper Shop Helper", () -> cfg().bitsShopHelper, v -> cfg().bitsShopHelper = v)
                            .desc("Coins per bit / copper on every shop item, categories name their best item")
                            .tags("bits", "copper", "community shop", "skymart", "coins per bit");
                    b.toggle("  Community Shop (Bits)", () -> cfg().bitsShopBits, v -> cfg().bitsShopBits = v);
                    b.toggle("  SkyMart (Copper)", () -> cfg().bitsShopCopper, v -> cfg().bitsShopCopper = v);
                    b.toggle("  Highlight Best Deal", () -> cfg().bitsShopHighlight, v -> cfg().bitsShopHighlight = v)
                            .desc("Yellow = best coins per bit/copper, green = best among items that sell fast");
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (shop == Shop.NONE || feature == null || !feature.active() || lines.isEmpty()) return;
            List<String> t = tips.get(stack);
            if (t == null) return;
            for (String s : t) lines.add(Component.literal(s));
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) {
            if (lastScreen != null) {
                lastScreen = null;
                shop = Shop.NONE;
                tips.clear();
                bestAll = bestSelling = -1;
            }
            return;
        }
        if (cs != lastScreen) {
            lastScreen = cs;
            ticks = 0;
            String title = StringUtil.stripTrim(cs.getTitle().getString());
            if (cfg().bitsShopBits && (title.contains("Community Shop") || title.contains("Bits Shop"))) shop = Shop.BITS;
            else if (cfg().bitsShopCopper && title.startsWith("SkyMart")) shop = Shop.COPPER;
            else shop = Shop.NONE;
            tips.clear();
            bestAll = bestSelling = -1;
        }
        if (shop == Shop.NONE || ticks++ % 10 != 0) return;
        PriceManager.ensureFresh();
        build(cs);
    }

    private static void build(AbstractContainerScreen<?> cs) {
        tips.clear();
        double topAll = 0, topSelling = 0;
        int all = -1, selling = -1;
        int source = EstimatedValue.priceSource();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(stack);
            String bestId = null;
            double per;
            if (shop == Shop.BITS && lore.contains("Click to browse!")) {
                String cat = StringUtil.stripTrim(stack.getHoverName().getString());
                Map<String, Integer> items = CATEGORIES.get(cat);
                if (items == null) continue;
                double best = 0, bestFast = 0;
                String fastId = null;
                for (Map.Entry<String, Integer> e : items.entrySet()) {
                    Double p = PriceManager.getPriceOrNull(e.getKey(), source);
                    if (p == null || p <= 0) continue;
                    double v = p / e.getValue();
                    if (v > best) { best = v; bestId = e.getKey(); }
                    if (GOOD_SELLING.contains(e.getKey()) && v > bestFast) { bestFast = v; fastId = e.getKey(); }
                }
                if (bestId == null) continue;
                per = best;
                List<String> t = new ArrayList<>(2);
                t.add("§bBits Cost: §3" + EstimatedValue.exact(best) + " Coins per bit");
                t.add("§3From " + name(bestId));
                if (fastId != null && !fastId.equals(bestId)) {
                    t.add("§aFast seller: " + name(fastId) + " §7(" + EstimatedValue.exact(bestFast) + "/bit)");
                }
                tips.put(stack, t);
                if (fastId != null && bestFast > topSelling) { topSelling = bestFast; selling = slot.index; }
            } else {
                long cost = currency(lore, shop == Shop.BITS ? BITS : COPPER);
                if (cost <= 0) continue;
                double price = EstimatedValue.compute(stack) * stack.getCount();
                if (price <= 0) {
                    String id = ItemUtil.skyblockId(stack);
                    if (id.isEmpty()) id = PriceManager.idForName(stack.getHoverName().getString());
                    Double p = id == null ? null : PriceManager.getPriceOrNull(id, source);
                    if (p != null) price = p * stack.getCount();
                }
                if (price <= 0) continue;
                double extra = shop == Shop.COPPER ? otherCosts(lore, source) : 0;
                per = (price - extra) / cost;
                bestId = ItemUtil.skyblockId(stack);
                List<String> t = new ArrayList<>(3);
                if (shop == Shop.BITS) {
                    t.add("§bBits Cost: §3" + EstimatedValue.exact(per) + " Coins per bit");
                } else {
                    if (extra > 0) t.add("§7Additional cost: §6" + EstimatedValue.fmt(extra));
                    t.add("§cCopper: §6" + EstimatedValue.exact(per) + " Coins per copper");
                }
                tips.put(stack, t);
                if (GOOD_SELLING.contains(bestId) && per > topSelling) { topSelling = per; selling = slot.index; }
            }
            if (per > topAll) { topAll = per; all = slot.index; }
        }
        bestAll = all;
        bestSelling = selling;
    }

    private static String name(String id) {
        String n = com.teslamaps.utils.SkyblockItems.name(id);
        return n == null || n.isEmpty() ? id : n;
    }

    /** Amount of the "N Bits" / "N Copper" line under "Cost", 0 without one. */
    private static long currency(List<String> lore, Pattern p) {
        for (int i = 0; i < lore.size(); i++) {
            String l = lore.get(i).trim();
            if (l.startsWith("Cost ")) l = l.substring(5).trim();
            Matcher m = p.matcher(l);
            if (m.matches()) {
                try { return Long.parseLong(m.group(1).replace(",", "")); } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    /** Items besides copper listed under "Cost". */
    private static double otherCosts(List<String> lore, int source) {
        double sum = 0;
        boolean in = false;
        for (String raw : lore) {
            String l = raw.trim();
            if (l.equals("Cost")) { in = true; continue; }
            if (!in) continue;
            if (l.isEmpty()) break;
            if (COPPER.matcher(l).matches()) continue;
            Matcher m = ITEM_COST.matcher(l);
            if (!m.matches()) continue;
            String id = PriceManager.idForName(m.group(1));
            if (id == null) continue;
            Double p = PriceManager.getPriceOrNull(id, source);
            if (p == null) continue;
            int amount = m.group(2) == null ? 1 : Integer.parseInt(m.group(2).replace(",", ""));
            sum += p * amount;
        }
        return sum;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (shop == Shop.NONE || !cfg().bitsShopHighlight || feature == null || !feature.active()) return;
        if (bestAll < 0 && bestSelling < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || cs != lastScreen) return;
        Perf.begin("BitsShopHelper.render");
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Slot> slots = cs.getMenu().slots;
        if (bestAll >= 0 && bestAll != bestSelling && bestAll < slots.size()) mark(ctx, acc, slots.get(bestAll), 0xFFFF55);
        if (bestSelling >= 0 && bestSelling < slots.size()) mark(ctx, acc, slots.get(bestSelling), 0x55FF55);
        Perf.end();
    }

    private static void mark(GuiGraphicsExtractor ctx, HandledScreenAccessor acc, Slot slot, int rgb) {
        int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
        int line = 0xFF000000 | rgb;
        ctx.fill(sx, sy, sx + 16, sy + 16, 0x66000000 | rgb);
        ctx.fill(sx, sy, sx + 16, sy + 1, line);
        ctx.fill(sx, sy + 15, sx + 16, sy + 16, line);
        ctx.fill(sx, sy, sx + 1, sy + 16, line);
        ctx.fill(sx + 15, sy, sx + 16, sy + 16, line);
    }
}
