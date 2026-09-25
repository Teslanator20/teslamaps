package com.teslamaps.features;

// Ported from SkyHanni's features/inventory/ItemPickupLog.kt (inventory diff, [Sacks] hover, purse)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemPickupLog {

    private static final Pattern SHOP_SUFFIX = Pattern.compile(" x\\d+$");
    private static final Pattern SACK_CHANGE = Pattern.compile("([+-][\\d,]+) (.+?) \\((.+?)\\)");
    private static final Pattern PURSE = Pattern.compile("^(?:Purse|Piggy): ([\\d,]+(?:\\.\\d+)?)");
    private static final String COINS = "coins";
    private static final long WORLD_GRACE = 2000;

    private static final class Entry {
        final String name, id;
        final ItemStack icon;
        long amount, updated;
        Entry(String name, String id, ItemStack icon, long amount) {
            this.name = name; this.id = id; this.icon = icon; this.amount = amount;
            this.updated = System.currentTimeMillis();
        }
    }

    private record Row(String text, ItemStack icon) {}

    private static HudEditElement hud;
    private static Map<String, Long> counts = new HashMap<>();
    private static final Map<String, ItemStack> samples = new HashMap<>();
    private static final Map<String, Entry> added = new LinkedHashMap<>(), removed = new LinkedHashMap<>();
    private static List<Row> rows = List.of();
    private static int rowsWidth;
    private static boolean dirty;
    private static Object lastLevel;
    private static long worldAt;
    private static long purse = -1;
    private static int ticks;
    private static long lastTick;
    private static String lastHover;
    private static long lastHoverAt;

    private static final List<Row> SAMPLE = List.of(new Row("§a+64 §fEnchanted Carrot", null),
            new Row("§c-1 §fSuperboom TNT", null), new Row("§a+12,500 §6Coins", null));

    private ItemPickupLog() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("pickupLog", "Item Pickup Log").section("Inventory")
                .enabled(() -> cfg().itemPickupLog)
                .size(() -> Math.max(90, rowsWidth), () -> Math.max(3, rows.size()) * 10)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, rows.isEmpty() ? SAMPLE : rows, rows.isEmpty() ? width(mc, SAMPLE) : rowsWidth));
        Feature.of("ItemPickupLog").section("Inventory").when(() -> cfg().itemPickupLog)
                .tick(ItemPickupLog::tick)
                .hud("pickuplog", ItemPickupLog::render)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Item Pickup Log");
                    b.toggle("Item Pickup Log", () -> c.itemPickupLog, v -> c.itemPickupLog = v)
                            .desc("+N / -N list of what entered or left your inventory (/tmap gui)");
                    b.toggle("  Sacks", () -> c.pickupLogSacks, v -> c.pickupLogSacks = v)
                            .desc("Reads the hover of the [Sacks] message");
                    b.toggle("  Purse Coins", () -> c.pickupLogCoins, v -> c.pickupLogCoins = v);
                    b.toggle("  Compact (Net Change)", () -> c.pickupLogCompact, v -> c.pickupLogCompact = v)
                            .desc("Gain and loss of the same item become one line");
                    b.toggle("  Item Icons", () -> c.pickupLogIcons, v -> c.pickupLogIcons = v);
                    b.slider("  Expire After (s)", 2f, 60f, () -> (float) c.pickupLogExpire, v -> c.pickupLogExpire = Math.round(v), 0);
                    b.toggle("  Coin Value", () -> c.pickupLogValue, v -> c.pickupLogValue = v);
                    b.priceSource("    Price Source", () -> c.pickupLogPriceSource, v -> c.pickupLogPriceSource = v);
                });
    }

    private static String strip(String s) {
        return SHOP_SUFFIX.matcher(StringUtil.stripTrim(s)).replaceAll("");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            worldAt = now;
            purse = -1;
            added.clear();
            removed.clear();
            dirty = true;
        }
        if (!SkyblockUtils.isInSkyblock()) {
            if (!rows.isEmpty()) rows = List.of();
            return;
        }
        boolean grace = now - worldAt < WORLD_GRACE || now - lastTick > 1000;
        lastTick = now;
        ticks++;

        if (mc.screen == null && (ticks % 4 == 0 || grace)) {
            Map<String, Long> next = new HashMap<>();
            var inv = mc.player.getInventory();
            for (int i = 0; i < 36; i++) {
                if (i == 8) continue;
                ItemStack st = inv.getItem(i);
                if (st.isEmpty()) continue;
                String name = strip(st.getHoverName().getString());
                String key = ItemUtil.skyblockId(st) + "|" + name.toLowerCase(Locale.ROOT);
                next.merge(key, (long) st.getCount(), Long::sum);
                if (!samples.containsKey(key)) samples.put(key, st.copy());
            }
            if (!grace) {
                for (Map.Entry<String, Long> e : next.entrySet()) {
                    long d = e.getValue() - counts.getOrDefault(e.getKey(), 0L);
                    if (d != 0) change(e.getKey(), d);
                }
                for (Map.Entry<String, Long> e : counts.entrySet()) {
                    if (!next.containsKey(e.getKey())) change(e.getKey(), -e.getValue());
                }
            }
            counts = next;
            if (samples.size() > 500) samples.keySet().retainAll(counts.keySet());
        }

        if (cfg().pickupLogCoins && ticks % 10 == 0) {
            long p = readPurse();
            if (p >= 0) {
                if (purse >= 0 && !grace && p != purse) coins(p - purse);
                purse = p;
            }
        }

        long expire = Math.max(2, cfg().pickupLogExpire) * 1000L;
        if (added.values().removeIf(e -> now - e.updated > expire)) dirty = true;
        if (removed.values().removeIf(e -> now - e.updated > expire)) dirty = true;
        if (dirty) rebuild();
    }

    private static long readPurse() {
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            String t = ScoreboardUtils.cleanLine(line);
            if (!t.startsWith("Purse") && !t.startsWith("Piggy")) continue;
            Matcher m = PURSE.matcher(t);
            if (!m.find()) continue;
            try { return (long) Double.parseDouble(m.group(1).replace(",", "")); } catch (NumberFormatException e) { return -1; }
        }
        return -1;
    }

    private static void change(String key, long delta) {
        ItemStack s = samples.get(key);
        String name = s == null ? key.substring(key.indexOf('|') + 1) : s.getHoverName().getString();
        String id = key.substring(0, key.indexOf('|'));
        record(key, name, id.isEmpty() ? null : id, s, delta);
    }

    private static void coins(long delta) {
        record(COINS, "§6Coins", null, null, delta);
    }

    private static void record(String key, String name, String id, ItemStack icon, long delta) {
        Map<String, Entry> target = delta > 0 ? added : removed;
        Map<String, Entry> other = delta > 0 ? removed : added;
        long now = System.currentTimeMillis();
        Entry o = other.get(key);
        if (o != null) o.updated = now;
        Entry e = target.get(key);
        if (e != null) {
            e.amount += Math.abs(delta);
            e.updated = now;
        } else {
            target.put(key, new Entry(name, id, icon, Math.abs(delta)));
        }
        dirty = true;
    }

    /** From ChatMixin, server lines only. */
    public static void onChatComponent(Component message) {
        TeslaMapsConfig c = cfg();
        if (!c.itemPickupLog || !c.pickupLogSacks || !c.section("Inventory")) return;
        String plain = message.getString();
        if (!plain.contains("[Sacks]")) return;
        StringBuilder sb = new StringBuilder();
        collect(message, sb);
        String text = sb.toString();
        long now = System.currentTimeMillis();
        if (text.isBlank() || (text.equals(lastHover) && now - lastHoverAt < 500)) return;
        lastHover = text;
        lastHoverAt = now;
        for (String line : text.split("\n")) {
            Matcher m = SACK_CHANGE.matcher(StringUtil.stripTrim(line));
            while (m.find()) {
                long d;
                try { d = Long.parseLong(m.group(1).replace(",", "").replace("+", "")); } catch (NumberFormatException e) { continue; }
                String name = m.group(2).trim();
                if (d == 0 || name.contains("other items")) continue;
                String id = PriceManager.idForName(name);
                String key = (id == null ? "" : id) + "|" + name.toLowerCase(Locale.ROOT);
                record(key, name, id, samples.get(key), d);
            }
        }
    }

    private static void collect(Component c, StringBuilder out) {
        if (c.getStyle().getHoverEvent() instanceof HoverEvent.ShowText show) {
            String h = StringUtil.strip(show.value().getString());
            String t = h.trim();
            if (t.startsWith("Added") || t.startsWith("Removed")) out.append(h).append('\n');
        }
        for (Component s : c.getSiblings()) collect(s, out);
    }

    private static String fmt(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static String nameOf(Entry e) {
        return e.name.contains("§") ? e.name : "§f" + e.name;
    }

    private static void rebuild() {
        dirty = false;
        TeslaMapsConfig c = cfg();
        List<Row> out = new ArrayList<>();
        double value = 0;
        if (c.pickupLogCompact) {
            Map<String, Long> net = new LinkedHashMap<>();
            Map<String, Entry> ref = new HashMap<>();
            for (var e : added.entrySet()) { net.merge(e.getKey(), e.getValue().amount, Long::sum); ref.put(e.getKey(), e.getValue()); }
            for (var e : removed.entrySet()) { net.merge(e.getKey(), -e.getValue().amount, Long::sum); ref.putIfAbsent(e.getKey(), e.getValue()); }
            for (var e : net.entrySet()) {
                long n = e.getValue();
                if (n == 0) continue;
                Entry en = ref.get(e.getKey());
                out.add(new Row((n > 0 ? "§a+" : "§c-") + fmt(Math.abs(n)) + " " + nameOf(en), en.icon));
                value += value(e.getKey(), en, n);
            }
        } else {
            for (var e : added.entrySet()) {
                out.add(new Row("§a+" + fmt(e.getValue().amount) + " " + nameOf(e.getValue()), e.getValue().icon));
                value += value(e.getKey(), e.getValue(), e.getValue().amount);
                Entry r = removed.get(e.getKey());
                if (r != null) {
                    out.add(new Row("§c-" + fmt(r.amount) + " " + nameOf(r), r.icon));
                    value += value(e.getKey(), r, -r.amount);
                }
            }
            for (var e : removed.entrySet()) {
                if (added.containsKey(e.getKey())) continue;
                out.add(new Row("§c-" + fmt(e.getValue().amount) + " " + nameOf(e.getValue()), e.getValue().icon));
                value += value(e.getKey(), e.getValue(), -e.getValue().amount);
            }
        }
        if (c.pickupLogValue && !out.isEmpty() && value != 0) {
            out.add(0, new Row("§eValue: " + (value < 0 ? "§c-" : "§6") + ItemPrice.format(Math.abs(value)) + " coins", null));
        }
        rows = out;
        rowsWidth = width(Minecraft.getInstance(), out);
    }

    private static double value(String key, Entry e, long amount) {
        if (COINS.equals(key)) return amount;
        if (e.id == null) return 0;
        Double p = PriceManager.getPriceOrNull(e.id, cfg().pickupLogPriceSource);
        return p == null ? 0 : p * amount;
    }

    private static int width(Minecraft mc, List<Row> l) {
        int w = 0;
        int icon = cfg().pickupLogIcons ? 12 : 0;
        for (Row r : l) w = Math.max(w, mc.font.width(r.text) + (r.icon != null ? icon : 0));
        return w;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<Row> l = rows;
        if (l.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l, rowsWidth);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<Row> l, int w) {
        boolean icons = cfg().pickupLogIcons;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * 10 + 2);
        for (int i = 0; i < l.size(); i++) {
            Row r = l.get(i);
            int tx = 0;
            if (icons && r.icon != null) {
                pose.pushMatrix();
                pose.translate(0, i * 10 - 1);
                pose.scale(0.625f, 0.625f);
                g.item(r.icon, 0, 0);
                pose.popMatrix();
                tx = 12;
            }
            g.text(mc.font, r.text, tx, i * 10, 0xFFFFFFFF, true);
        }
        pose.popMatrix();
    }
}
