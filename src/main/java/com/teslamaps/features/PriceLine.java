package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The plain market price of an item on its tooltip: instant buy/sell for anything the bazaar
 * carries, lowest BIN for everything else. Independent of the estimated value, which only has
 * something to say about gear carrying upgrades.
 */
public class PriceLine {

    public static List<String> lines(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.itemValueShowBin) return List.of();

        String id = ItemUtil.skyblockId(stack);
        if (id.isEmpty()) id = PriceManager.idForName(stack.getHoverName().getString());
        if (id == null || id.isEmpty()) return List.of();

        List<String> out = new ArrayList<>(2);

        Double buy = PriceManager.bazaarBuyOrNull(id);
        Double sell = PriceManager.bazaarSellOrNull(id);
        if (buy != null || sell != null) {
            if (sell != null && sell > 0) out.add(line("Bazaar Insta-Sell", sell));
            if (buy != null && buy > 0) out.add(line("Bazaar Insta-Buy", buy));
            return out;
        }

        Double bin = PriceManager.lowestBinOrNull(id);
        if (bin == null || bin <= 0) return List.of();
        out.add(line("Lowest BIN", bin));
        return out;
    }

    private static String line(String label, double unit) {
        return "§e§l" + label + ": §6§l" + EstimatedValue.exact(unit) + " coins";
    }

    private PriceLine() {}
}
