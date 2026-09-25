package com.teslamaps.features;

// Ported from Skyblocker's SackMessagePrice and SkyOcean's SackNotification
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SackItems;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** [Sacks] line: coin value in the hover, and a chat ping for picked items. */
public final class SackMessages {

    private static final Pattern CHANGE = Pattern.compile("([+-][\\d,]+) (.+?) \\((.+?)\\)");

    private static String lastAdded;
    private static long lastAddedMs;

    private SackMessages() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SackMessagePrices").section("Chat").when(() -> cfg().sackMessagePrices)
                .config("Chat", b -> {
                    b.label("More Chat");
                    b.toggle("Sack Message Prices", () -> cfg().sackMessagePrices, v -> cfg().sackMessagePrices = v)
                            .desc("Adds NPC sell, Bazaar buy and Bazaar sell value of the items to the [Sacks] hover");
                });
        Feature.of("SackNotify").section("Chat").when(() -> cfg().sackNotify)
                .config("Chat", b -> {
                    b.toggle("Sack Notifications", () -> cfg().sackNotify, v -> cfg().sackNotify = v)
                            .desc("Chat line when one of the picked items lands in your sacks, also while [Sacks] messages are hidden");
                    b.searchList("  Notify Items", SackMessages::options,
                            n -> cfg().sackNotifyItems.stream().anyMatch(s -> s.equalsIgnoreCase(n)),
                            n -> { if (!cfg().sackNotifyItems.removeIf(s -> s.equalsIgnoreCase(n))) cfg().sackNotifyItems.add(n); });
                });
    }

    private static List<String> options() {
        Set<String> out = new LinkedHashSet<>(cfg().sackNotifyItems);
        out.add("Glossy Gemstone");
        out.addAll(new java.util.TreeSet<>(SackItems.displayNames()));
        return new ArrayList<>(out);
    }

    private static boolean pricesOn() {
        return cfg().sackMessagePrices && cfg().section("Chat");
    }

    /** From ChatMixin, server lines, before the chat filter. */
    public static void onChat(Component message, String text) {
        if (!cfg().sackNotify || !cfg().section("Chat") || !text.contains("[Sacks]")) return;
        StringBuilder added = new StringBuilder();
        collectAdded(message, added);
        if (added.isEmpty()) return;
        String h = added.toString();
        long now = System.currentTimeMillis();
        if (h.equals(lastAdded) && now - lastAddedMs < 500) return;
        lastAdded = h;
        lastAddedMs = now;

        Map<String, Long> hits = new LinkedHashMap<>();
        List<String> wanted = cfg().sackNotifyItems;
        for (Map.Entry<String, Long> e : parse(h).entrySet()) {
            if (e.getValue() <= 0) continue;
            for (String w : wanted) {
                if (w != null && w.equalsIgnoreCase(e.getKey())) {
                    hits.merge(e.getKey(), e.getValue(), Long::sum);
                    break;
                }
            }
        }
        if (hits.isEmpty()) return;
        StringBuilder sb = new StringBuilder("§b[TeslaMaps] §7Sack Notification §8| ");
        boolean first = true;
        for (Map.Entry<String, Long> e : hits.entrySet()) {
            if (!first) sb.append("§7, ");
            first = false;
            sb.append("§a").append(e.getKey()).append(" §fx").append(e.getValue());
        }
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal(sb.toString()));
    }

    private static void collectAdded(Component c, StringBuilder out) {
        if (c.getStyle().getHoverEvent() instanceof HoverEvent.ShowText show) {
            String h = StringUtil.strip(show.value().getString());
            if (h.trim().startsWith("Added")) out.append(h).append('\n');
        }
        for (Component s : c.getSiblings()) collectAdded(s, out);
    }

    private static Map<String, Long> parse(String hover) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String line : hover.split("\n")) {
            Matcher m = CHANGE.matcher(line.trim());
            while (m.find()) {
                try {
                    out.merge(m.group(2).trim(), Long.parseLong(m.group(1).replace(",", "").replace("+", "")), Long::sum);
                } catch (NumberFormatException ignored) {}
            }
        }
        return out;
    }

    /** Right before the line is stored; adds the price block to every Added/Removed hover. */
    public static Component rewrite(Component message) {
        if (!pricesOn() || !message.getString().contains("[Sacks]")) return message;
        try {
            return withPrices(message);
        } catch (RuntimeException e) {
            return message;
        }
    }

    private static MutableComponent withPrices(Component c) {
        MutableComponent copy = c.plainCopy();
        Style st = c.getStyle();
        if (st.getHoverEvent() instanceof HoverEvent.ShowText show) {
            String h = StringUtil.strip(show.value().getString());
            String t = h.trim();
            if (t.startsWith("Added") || t.startsWith("Removed")) {
                String block = priceBlock(h);
                if (block != null) {
                    st = st.withHoverEvent(new HoverEvent.ShowText(Component.empty().append(show.value())
                            .append(Component.literal(block))));
                }
            }
        }
        copy.setStyle(st);
        for (Component s : c.getSiblings()) copy.append(withPrices(s));
        return copy;
    }

    private static String priceBlock(String hover) {
        Map<String, Long> items = parse(hover);
        if (items.isEmpty()) return null;
        double npc = 0, buy = 0, sell = 0;
        for (Map.Entry<String, Long> e : items.entrySet()) {
            String id = PriceManager.idForName(e.getKey());
            if (id == null) continue;
            long n = Math.abs(e.getValue());
            if (SkyblockItems.hasNpcPrice(id)) npc += SkyblockItems.npcPrice(id) * n;
            Double b = PriceManager.bazaarBuyOrNull(id);
            if (b != null) buy += b * n;
            Double s = PriceManager.bazaarSellOrNull(id);
            if (s != null) sell += s * n;
        }
        return "\n§8§m                              \n"
                + "§eNPC Sell Price: " + coins(npc) + "\n"
                + "§6Bazaar Buy Price: " + coins(buy) + "\n"
                + "§6Bazaar Sell Price: " + coins(sell);
    }

    private static String coins(double v) {
        return v > 0 ? "§6" + String.format(Locale.ROOT, "%,.0f", v) + " coins" : "§cNo data";
    }
}
