package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.CraftCost;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The plain market price of an item on its tooltip: instant buy/sell for anything the bazaar
 * carries, lowest BIN for everything else. Independent of the estimated value, which only has
 * something to say about gear carrying upgrades. Optional extra lines after Skysoft's
 * PriceTooltips; the "total (each)" column layout after Skyblocker's getCoinsMessage.
 */
public class PriceLine {

    private record Row(String label, double unit) {}

    private static ItemStack lastStack;
    private static int lastKey;
    private static long lastAt;
    private static List<String> lastLines = List.of();

    public static List<String> lines(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.itemValueShowBin) return List.of();
        if (c.priceLineRequireKey && !com.teslamaps.utils.KeyNames.isDown(c.priceLineKey)) return List.of();

        boolean shift = Minecraft.getInstance().hasShiftDown();
        int key = (shift ? 1 : 0) * 31 + stack.getCount() + (int) PriceManager.bazaarUpdatedAt();
        long now = System.currentTimeMillis();
        if (stack == lastStack && key == lastKey && now - lastAt < 1000) return lastLines;

        List<String> out = build(stack, c, shift);
        lastStack = stack;
        lastKey = key;
        lastAt = now;
        lastLines = out;
        return out;
    }

    private static List<String> build(ItemStack stack, TeslaMapsConfig c, boolean shift) {
        String id = ItemUtil.skyblockId(stack);
        if (id.isEmpty()) id = PriceManager.idForName(stack.getHoverName().getString());
        if (id == null || id.isEmpty()) return List.of();

        List<Row> rows = new ArrayList<>(4);
        Double buy = PriceManager.bazaarBuyOrNull(id);
        Double sell = PriceManager.bazaarSellOrNull(id);
        boolean onBazaar = buy != null || sell != null;
        if (onBazaar && !c.priceLineHideInstant) {
            if (sell != null && sell > 0) rows.add(new Row("Bazaar Insta-Sell", sell));
            if (buy != null && buy > 0) rows.add(new Row("Bazaar Insta-Buy", buy));
        }
        if (!onBazaar) {
            Double bin = PriceManager.lowestBinOrNull(id);
            if (bin != null && bin > 0) rows.add(new Row("Lowest BIN", bin));
        }
        if (c.priceLineOrders) {
            Double order = PriceManager.bazaarBuyOrderOrNull(id);
            Double offer = PriceManager.bazaarSellOfferOrNull(id);
            if (order != null && order > 0) rows.add(new Row("Bazaar Buy Order", order));
            if (offer != null && offer > 0) rows.add(new Row("Bazaar Sell Offer", offer));
        }
        if (c.priceLineNpc && com.teslamaps.utils.SkyblockItems.hasNpcPrice(id)) {
            double npc = com.teslamaps.utils.SkyblockItems.npcPrice(id);
            if (npc > 0) rows.add(new Row("NPC Sell Price", npc));
        }
        if (c.priceLineCraft) {
            if (c.priceLineCraftCheapest) {
                Double craft = CraftCost.cheapestOrNull(id, PriceManager.SOURCE_BAZAAR_BUY);
                if (craft != null && craft > 0) rows.add(new Row("Crafting Price", craft));
            } else {
                Double craft = CraftCost.costOrNull(id, PriceManager.SOURCE_BAZAAR_BUY);
                if (craft != null && craft > 0) rows.add(new Row("Raw Craft Cost", craft));
            }
        }
        String volume = c.priceLineVolume && onBazaar ? volume(id) : null;
        if (rows.isEmpty() && volume == null) return List.of();

        List<String> out;
        if (c.priceLineEach) out = new ArrayList<>(columns(rows, stack.getCount()));
        else {
            int count = shift ? 1 : stack.getCount();
            out = new ArrayList<>(rows.size() + 1);
            for (Row r : rows) out.add(line(r.label(), r.unit(), count));
        }
        if (volume != null) out.add(volume);
        return out;
    }

    private static String volume(String id) {
        Long buys = PriceManager.bazaarWeekBuysOrNull(id), sells = PriceManager.bazaarWeekSellsOrNull(id);
        if (buys == null && sells == null) return null;
        return "§7Weekly Volume: §a" + EstimatedValue.shortFormat(buys == null ? 0 : buys) + " §7insta-buys, §c"
                + EstimatedValue.shortFormat(sells == null ? 0 : sells) + " §7insta-sells";
    }

    private static String line(String label, double unit, int count) {
        String amount = count > 1 ? " §7(" + count + "x)" : "";
        return "§e§l" + label + amount + ": §6§l" + EstimatedValue.exact(unit * count) + " coins";
    }

    /** "Label:   total coins (each each)", values right-aligned by padding with spaces. */
    private static List<String> columns(List<Row> rows, int count) {
        var font = Minecraft.getInstance().font;
        int space = Math.max(1, font.width(" "));
        List<String> labels = new ArrayList<>(rows.size());
        List<String> values = new ArrayList<>(rows.size());
        int widest = 0;
        for (Row r : rows) {
            String label = "§e" + r.label() + ":";
            String value = count > 1
                    ? "§6" + EstimatedValue.exact(r.unit() * count) + " coins §7(" + EstimatedValue.exact(r.unit()) + " each)"
                    : "§6" + EstimatedValue.exact(r.unit()) + " coins";
            labels.add(label);
            values.add(value);
            widest = Math.max(widest, font.width(label) + font.width(value));
        }
        int target = widest + space * 2;
        List<String> out = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            int gap = target - font.width(labels.get(i)) - font.width(values.get(i));
            out.add(labels.get(i) + " ".repeat(Math.max(1, Math.round(gap / (float) space))) + values.get(i));
        }
        return out;
    }

    private PriceLine() {}
}
