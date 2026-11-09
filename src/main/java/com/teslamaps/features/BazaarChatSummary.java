package com.teslamaps.features;

// Ported from HypixelSimpleChat's BazaarSummary.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.ChatComponentAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bazaar sells/buys within 5s collapse into one total line, per-item detail on hover. */
public final class BazaarChatSummary {

    private static final Pattern SOLD = Pattern.compile("^\\[Bazaar] Sold ([\\d,]+)x (.+) for ([\\d,.]+) coins!");
    private static final Pattern BOUGHT = Pattern.compile("^\\[Bazaar] Bought ([\\d,]+)x (.+) for ([\\d,.]+) coins!");
    private static final Pattern CLAIMED = Pattern.compile("^\\[Bazaar] Claimed ([\\d,.]+) coins from selling ([\\d,]+)x (.+) at ");
    private static final long WINDOW_MS = 5_000L;
    private static final int MAX_HOVER = 20;

    private static final class Item {
        long qty;
        double coins;
    }

    private static final Map<String, Item> batch = new LinkedHashMap<>();
    private static boolean sell = true;
    private static int orders;
    private static long lastAt;
    private static String shown;
    private static Component pendingOut;
    private static String pendingText;

    private BazaarChatSummary() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("BazaarChatSummary").section("Chat")
                .config("Chat", b -> {
                    b.label("Bazaar");
                    b.toggle("Bazaar Chat Summary", () -> cfg().bazaarChatSummary, v -> cfg().bazaarChatSummary = v)
                            .desc("Sold/Bought/Claimed lines within 5s become one \"Bz - 3,100 items · 3 sales · +2M\" line, hover lists each item")
                            .tags("bazaar", "compact", "sell inventory");
                    b.info("\"Submitting sell offer...\" is hidden by the Chat Filter's Bazaar rule");
                });
    }

    /** From ChatMixin on server lines. Never drops, may queue a rewrite. */
    public static void process(String text) {
        pendingOut = null;
        pendingText = null;
        if (!cfg().bazaarChatSummary) return;
        String t = StringUtil.stripTrim(text);
        if (!t.startsWith("[Bazaar] ")) return;
        String name;
        long qty;
        double coins;
        boolean isSell;
        Matcher m;
        if ((m = SOLD.matcher(t)).find()) {
            name = m.group(2); qty = num(m.group(1)); coins = dbl(m.group(3)); isSell = true;
        } else if ((m = BOUGHT.matcher(t)).find()) {
            name = m.group(2); qty = num(m.group(1)); coins = dbl(m.group(3)); isSell = false;
        } else if ((m = CLAIMED.matcher(t)).find()) {
            name = m.group(3); qty = num(m.group(2)); coins = dbl(m.group(1)); isSell = true;
        } else return;

        long now = System.currentTimeMillis();
        if (now - lastAt > WINDOW_MS || isSell != sell) reset();
        lastAt = now;
        sell = isSell;
        orders++;
        Item it = batch.computeIfAbsent(name, k -> new Item());
        it.qty += qty;
        it.coins += coins;

        // the first order keeps its own detailed line
        if (orders == 1) {
            shown = t;
            return;
        }
        removeShown();
        MutableComponent line = Component.literal(line());
        line.setStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(hover())));
        pendingOut = line;
        pendingText = text;
        shown = StringUtil.stripTrim(line.getString());
    }

    public static Component rewrite(Component message) {
        if (pendingOut == null) return message;
        Component out = pendingOut;
        boolean same = message.getString().equals(pendingText);
        pendingOut = null;
        pendingText = null;
        return same ? out : message;
    }

    private static void reset() {
        batch.clear();
        orders = 0;
        shown = null;
    }

    private static void removeShown() {
        if (shown == null) return;
        Minecraft mc = Minecraft.getInstance();
        var chat = mc.gui.hud.getChat();
        List<GuiMessage> all = ((ChatComponentAccessor) chat).teslamaps$getAllMessages();
        for (int i = 0; i < Math.min(15, all.size()); i++) {
            String s = ChatTimestamps.cut(StringUtil.stripTrim(all.get(i).content().getString()));
            if (!s.equals(shown)) continue;
            all.remove(i);
            ((ChatComponentAccessor) chat).teslamaps$refreshTrimmedMessages();
            break;
        }
        shown = null;
    }

    private static String line() {
        long count = 0;
        double total = 0;
        for (Item it : batch.values()) {
            count += it.qty;
            total += it.coins;
        }
        String head = sell ? "§6Bz §c-" : "§6Bz §a+";
        String kind = sell ? "sale" : "buy";
        return head + " §e" + group(count) + " item" + (count > 1 ? "s" : "") + " §8· §b" + orders + " " + kind
                + (orders > 1 ? "s" : "") + " §7· " + (sell ? "§a+" : "§c-") + EstimatedValue.fmt(total);
    }

    private static Component hover() {
        List<Map.Entry<String, Item>> list = new ArrayList<>(batch.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue().coins, a.getValue().coins));
        MutableComponent out = Component.empty();
        int n = Math.min(MAX_HOVER, list.size());
        for (int i = 0; i < n; i++) {
            Map.Entry<String, Item> e = list.get(i);
            if (i > 0) out.append("\n");
            out.append(Component.literal("§f" + group(e.getValue().qty) + "x " + e.getKey() + " §8· "
                    + (sell ? "§a+" : "§c-") + EstimatedValue.fmt(e.getValue().coins)));
        }
        if (list.size() > n) out.append(Component.literal("\n§8… and " + (list.size() - n) + " more"));
        return out;
    }

    private static String group(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static long num(String s) {
        try { return Long.parseLong(s.replace(",", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static double dbl(String s) {
        try { return Double.parseDouble(s.replace(",", "")); } catch (NumberFormatException e) { return 0; }
    }
}
