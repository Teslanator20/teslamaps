package com.teslamaps.features;

// After SkyHanni's LoreCostUtils
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the "Cost" block of shop / trade lore (plain lines). */
public final class LoreCosts {

    public record Cost(String name, long amount, String raw, String id, boolean coins) {}

    private static final Pattern HEADER = Pattern.compile("^(?:Donation )?Costs?(?: to unlock)?(?::(?: (.+))?)?$");
    private static final Pattern TRADE = Pattern.compile("^(?:Left )?Click to (?:trade|unlock|level up|buy|donate)!$|^You can't afford this upgrade!$");
    private static final Pattern AMOUNT = Pattern.compile("^(.+?) x([\\d,]+)$");
    private static final Pattern AMOUNT_FIRST = Pattern.compile("^([\\d,.]+) ([\\w' ]+)$");

    private LoreCosts() {}

    public static boolean hasTradeLine(List<String> lore) {
        for (String l : lore) if (TRADE.matcher(l.trim()).matches()) return true;
        return false;
    }

    public static boolean isHeader(String plain) {
        return HEADER.matcher(plain.trim()).matches();
    }

    public static List<Cost> read(List<String> lore) {
        int header = -1;
        for (int i = 0; i < lore.size(); i++) if (isHeader(lore.get(i))) header = i;
        if (header < 0) return List.of();
        Matcher h = HEADER.matcher(lore.get(header).trim());
        if (h.matches() && h.group(1) != null) {
            Cost c = line(h.group(1));
            return c == null ? List.of() : List.of(c);
        }
        List<Cost> out = new ArrayList<>();
        for (int i = header + 1; i < lore.size(); i++) {
            String raw = lore.get(i);
            if (raw.isBlank()) break;
            Cost c = line(raw);
            if (c != null) out.add(c);
        }
        return out;
    }

    private static Cost line(String raw) {
        String t = raw.trim();
        String name = t;
        long amount = 1;
        Matcher m = AMOUNT.matcher(t);
        Matcher f = AMOUNT_FIRST.matcher(t);
        try {
            if (m.matches()) {
                name = m.group(1).trim();
                amount = Long.parseLong(m.group(2).replace(",", ""));
            } else if (f.matches()) {
                name = f.group(2).trim();
                String num = f.group(1).replace(",", "");
                amount = num.contains(".") ? Math.round(Double.parseDouble(num)) : Long.parseLong(num);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        if (name.isEmpty()) return null;
        boolean coins = name.equals("Coins") || name.equals("Coin");
        String id = coins ? "SKYBLOCK_COIN" : PriceManager.idForName(name);
        return new Cost(name, amount, raw, id, coins);
    }

    public static int source() { return EstimatedValue.priceSource(); }

    /** Coin value of one cost line, 0 when unknown (bits, copper, gems ...). */
    public static double price(Cost c) {
        if (c.coins()) return c.amount();
        if (c.id() == null) return 0;
        Double p = PriceManager.getPriceOrNull(c.id(), EstimatedValue.priceSource());
        return p == null ? 0 : p * c.amount();
    }

    /** Amount in the player inventory, -1 when it can't be counted there. */
    public static long owned(Cost c) {
        if (c.coins()) return purse();
        if (c.id() == null || c.id().startsWith("ESSENCE_")) return -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        long n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (c.id().equals(ItemUtil.skyblockId(s))) n += s.getCount();
        }
        return n;
    }

    public static long purse() {
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            String line = ScoreboardUtils.cleanLine(raw);
            if (!line.startsWith("Purse:") && !line.startsWith("Piggy:")) continue;
            String num = line.substring(6).trim();
            int end = 0;
            while (end < num.length() && (Character.isDigit(num.charAt(end)) || num.charAt(end) == ',' || num.charAt(end) == '.')) end++;
            if (end == 0) return -1;
            try {
                return (long) Double.parseDouble(num.substring(0, end).replace(",", ""));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}
