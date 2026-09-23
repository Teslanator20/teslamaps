package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tints auction browser slots by what the listing costs against the estimated item
 * value - green when it sells for less than it is worth, red when it does not.
 */
public class AuctionPriceCompare {

    private static final Pattern PRICE = Pattern.compile("(?:Buy it now|Starting bid|Top bid): ([\\d,]+) coins");

    /** ratio is NaN for a slot with no usable price, so it is not recomputed every pass. */
    private record Deal(ItemStack stack, long diff, double ratio) {}

    private static final Map<Slot, Deal> deals = new IdentityHashMap<>();
    private static Screen lastScreen;
    private static boolean inAuctions;
    private static int ticks;
    private static long bestDiff, worstDiff;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!TeslaMapsConfig.get().auctionPriceCompare || !(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            reset();
            return;
        }

        if (lastScreen != mc.screen) {
            lastScreen = mc.screen;
            deals.clear();
            ticks = 0;
            inAuctions = isAuctionTitle(cs.getTitle().getString());
        }
        if (!inAuctions) return;
        if (ticks++ % 3 != 0) return;

        PriceManager.ensureFresh();
        // a full page is ~45 items and one value calculation is not cheap, so the
        // page fills in over a few passes instead of spiking a single tick
        int budget = 12;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                deals.remove(slot);
                continue;
            }
            Deal known = deals.get(slot);
            if (known != null && ItemStack.matches(known.stack(), stack)) continue;
            deals.put(slot, compute(stack));
            if (--budget <= 0) break;
        }
        // page-relative range for the gradient, after SkyHanni's AuctionHousePriceComparison
        long best = 0, worst = 0;
        for (Deal d : deals.values()) {
            if (Double.isNaN(d.ratio())) continue;
            if (d.diff() > best) best = d.diff();
            if (d.diff() < worst) worst = d.diff();
        }
        bestDiff = best;
        worstDiff = worst;
    }

    public static boolean isAuctionTitle(String rawTitle) {
        String title = StringUtil.stripTrim(rawTitle);
        return title.startsWith("Auctions") || title.startsWith("Cosmetics Browser");
    }

    private static void reset() {
        if (!deals.isEmpty()) deals.clear();
        lastScreen = null;
        inAuctions = false;
    }

    private static Deal compute(ItemStack stack) {
        ItemStack copy = stack.copy();
        long price = listingPrice(stack);
        if (price <= 0) return new Deal(copy, 0, Double.NaN);

        Double value = EstimatedValue.totalPrice(stack, false);
        if (value == null || value <= 0) return new Deal(copy, 0, Double.NaN);

        long diff = Math.round(value - price);
        return new Deal(copy, diff, diff / value);
    }

    private static long listingPrice(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return 0;
        for (Component line : lore.lines()) {
            Matcher m = PRICE.matcher(StringUtil.stripTrim(line.getString()));
            if (!m.find()) continue;
            try {
                return Long.parseLong(m.group(1).replace(",", ""));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /** Called from SlotBackgrounds, so the tint sits behind the item icon. */
    public static void drawHighlight(GuiGraphicsExtractor ctx, Slot slot) {
        if (!inAuctions || deals.isEmpty()) return;
        if (!TeslaMapsConfig.get().auctionPriceCompare) return;

        Deal deal = deals.get(slot);
        if (deal == null || Double.isNaN(deal.ratio())) return;
        int color = color(deal);
        if (color == 0) return;
        ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
    }

    private static int color(Deal deal) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        double ratio = deal.ratio();
        double min = Math.max(0.01, c.auctionCompareThreshold / 100.0);
        int alpha = (int) (Math.max(0.05f, Math.min(1f, c.auctionCompareOpacity)) * 255) << 24;
        int veryGood = rgb(c.colorAuctionVeryGood), good = rgb(c.colorAuctionGood);
        int bad = rgb(c.colorAuctionBad), veryBad = rgb(c.colorAuctionVeryBad);

        if (c.auctionCompareGradient) {
            if (ratio >= min) {
                double t = bestDiff > 0 ? (double) deal.diff() / bestDiff : 1;
                return alpha | lerp(good, veryGood, t);
            }
            if (c.auctionCompareOnlyGood || ratio > -min) return 0;
            double t = worstDiff < 0 ? (double) deal.diff() / worstDiff : 1;
            return alpha | lerp(bad, veryBad, t);
        }

        if (ratio >= min * 3) return alpha | veryGood;
        if (ratio >= min) return alpha | good;
        if (c.auctionCompareOnlyGood) return 0;
        if (ratio <= -min * 3) return alpha | veryBad;
        if (ratio <= -min) return alpha | bad;
        return 0;
    }

    private static int rgb(String hex) {
        return TeslaMapsConfig.parseColor(hex) & 0x00FFFFFF;
    }

    private static int lerp(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 0xFF) + t * (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)));
        int g = (int) Math.round(((a >> 8) & 0xFF) + t * (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)));
        int bl = (int) Math.round((a & 0xFF) + t * ((b & 0xFF) - (a & 0xFF)));
        return (r << 16) | (g << 8) | bl;
    }

    /** Extra tooltip lines for the hovered auction, empty when it is not one. */
    public static List<String> lines(ItemStack stack) {
        if (!inAuctions || deals.isEmpty()) return List.of();
        if (!TeslaMapsConfig.get().auctionPriceCompare) return List.of();

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return List.of();
        Slot slot = ((HandledScreenAccessor) cs).getFocusedSlot();
        if (slot == null || slot.getItem() != stack) return List.of();

        Deal deal = deals.get(slot);
        if (deal == null || Double.isNaN(deal.ratio())) return List.of();

        List<String> out = new ArrayList<>(3);
        out.add("");
        if (deal.diff() >= 0) {
            out.add("§aThis item is §6" + EstimatedValue.exact(deal.diff()) + " coins §acheaper");
            out.add("§athan the estimated item value!");
        } else {
            out.add("§cThis item is §6" + EstimatedValue.exact(-deal.diff()) + " coins §cmore");
            out.add("§cexpensive than the estimated item value!");
        }
        return out;
    }

    private AuctionPriceCompare() {}
}
