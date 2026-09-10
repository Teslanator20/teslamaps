package com.teslamaps.features.hunting;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shards the player has on order in the bazaar, so the overlay stops calling something the
 * cheapest buy right after it was bought. Ported from SkyHanni's BazaarOrderApi, trimmed to the
 * buy side and to shards.
 *
 * The order inventory is authoritative and replaces the stored state; the chat lines only bridge
 * the gap between two visits.
 */
public class BazaarOrders {

    private static final Pattern TITLE = Pattern.compile("(?:Your|Co-op) Bazaar Orders");
    private static final Pattern ORDER_NAME = Pattern.compile("(?<type>BUY|SELL) (?<name>.+)");
    private static final Pattern AMOUNT = Pattern.compile("(?:Order|Offer) amount: (?<amount>[\\d,]+)x");
    private static final Pattern FILLED = Pattern.compile("Filled: (?<filled>[\\d,.km]+)/[\\d,.km]+ (?<progress>\\S+)");
    private static final Pattern CLAIMABLE = Pattern.compile("You have (?<amount>[\\d,]+) items? to claim!");
    private static final Pattern OWNER = Pattern.compile("By: (?:\\[[^]]+] )?(?<name>\\S+)");

    private static final Pattern SETUP =
            Pattern.compile("\\[Bazaar] Buy Order Setup! (?<amount>[\\d,]+)x (?<item>.+) for [\\d,.]+ coins\\.");
    private static final Pattern CLAIMED =
            Pattern.compile("\\[Bazaar] Claimed (?<amount>[\\d,]+)x (?<item>.+) worth .* coins bought for .* each!");

    public static void tick() {
        if (!ShardData.loaded()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || mc.player == null) return;
        if (!TITLE.matcher(StringUtil.stripTrim(cs.getTitle().getString())).matches()) return;

        Map<String, Integer> orders = new HashMap<>();
        String ownName = mc.player.getName().getString();

        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            Matcher name = ORDER_NAME.matcher(StringUtil.stripTrim(stack.getHoverName().getString()));
            if (!name.matches() || !"BUY".equals(name.group("type"))) continue;
            ShardData.Shard shard = shardFor(name.group("name"));
            if (shard == null) continue;

            int amount = 0, filled = 0, claimable = 0;
            boolean full = false, own = true;
            for (String line : lore(stack)) {
                Matcher a = AMOUNT.matcher(line);
                if (a.matches()) { amount = parseInt(a.group("amount")); continue; }
                Matcher f = FILLED.matcher(line);
                if (f.matches()) {
                    filled = parseShort(f.group("filled"));
                    full = "100%!".equals(f.group("progress"));
                    continue;
                }
                Matcher c = CLAIMABLE.matcher(line);
                if (c.matches()) { claimable = parseInt(c.group("amount")); continue; }
                Matcher o = OWNER.matcher(line);
                if (o.matches()) own = o.group("name").equalsIgnoreCase(ownName);
            }
            if (!own || amount <= 0) continue;
            if (full) filled = amount;

            // claimed shards already sit in the hunting box, unclaimed ones do not
            int outstanding = Math.max(0, amount - (filled - claimable));
            if (outstanding > 0) orders.merge(shard.bazaarId(), outstanding, Integer::sum);
        }

        TeslaMapsConfig.get().shardOrders = orders;
    }

    public static void onChatMessage(String text) {
        if (!ShardData.loaded()) return;

        Matcher setup = SETUP.matcher(text);
        if (setup.matches()) {
            ShardData.Shard shard = shardFor(setup.group("item"));
            if (shard != null) {
                TeslaMapsConfig.get().shardOrders.merge(shard.bazaarId(), parseInt(setup.group("amount")), Integer::sum);
            }
            return;
        }

        Matcher claimed = CLAIMED.matcher(text);
        if (claimed.matches()) {
            ShardData.Shard shard = shardFor(claimed.group("item"));
            if (shard == null) return;
            Map<String, Integer> orders = TeslaMapsConfig.get().shardOrders;
            Integer open = orders.get(shard.bazaarId());
            if (open == null) return;
            int left = open - parseInt(claimed.group("amount"));
            if (left > 0) orders.put(shard.bazaarId(), left);
            else orders.remove(shard.bazaarId());
        }
    }

    public static int openBuy(ShardData.Shard shard) {
        if (shard == null) return 0;
        Integer amount = TeslaMapsConfig.get().shardOrders.get(shard.bazaarId());
        return amount == null ? 0 : amount;
    }

    /** Bazaar order lines carry the item name, so "Magma Slug Shard" not the shard id. */
    private static ShardData.Shard shardFor(String itemName) {
        return ShardData.byItemName(itemName);
    }

    private static List<String> lore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return List.of();
        List<String> out = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) out.add(StringUtil.stripTrim(line.getString()));
        return out;
    }

    private static int parseInt(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The filled line abbreviates and rounds: "1.2k", "34", "1.9m". */
    private static int parseShort(String s) {
        if (s == null || s.isEmpty()) return 0;
        String clean = s.replace(",", "").toLowerCase(Locale.ROOT);
        double factor = 1;
        if (clean.endsWith("k")) { factor = 1_000; clean = clean.substring(0, clean.length() - 1); }
        else if (clean.endsWith("m")) { factor = 1_000_000; clean = clean.substring(0, clean.length() - 1); }
        try {
            return (int) (Double.parseDouble(clean) * factor);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BazaarOrders() {}
}
