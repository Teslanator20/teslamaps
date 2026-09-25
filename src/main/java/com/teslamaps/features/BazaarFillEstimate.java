package com.teslamaps.features;

// After Skysoft's BazaarTrackerFillEstimator / BazaarTrackerFillEstimatorMarket, fed by the public bazaar API
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guesses how far each bazaar order is filled from the public order book: the amount sitting at the
 * order's price shrinks as the queue ahead and then the order itself gets filled. Orders come from the
 * "Bazaar Orders" menu and the setup chat line. Two orders on the same product, side and price are
 * ambiguous and skipped.
 */
public final class BazaarFillEstimate {

    private static final Pattern SETUP = Pattern.compile("^\\[Bazaar] (Buy Order|Sell Offer) Setup! ([\\d,]+)x (.+) for ([\\d,.]+) coins\\.");
    private static final Pattern FILLED_CHAT = Pattern.compile("^\\[Bazaar] Your (Buy Order|Sell Offer) for ([\\d,]+)x (.+) was filled!");
    private static final Pattern NAME = Pattern.compile("^(BUY|SELL) (.+)$");
    private static final Pattern AMOUNT = Pattern.compile("^(?:Order|Offer) amount: ([\\d,]+)x");
    private static final Pattern PRICE = Pattern.compile("^Price per unit: ([\\d,.]+) coins");
    private static final Pattern FILLED = Pattern.compile("^Filled: ([\\d,.]+[kKmM]?)/");
    private static final double EPS = 0.0001;
    private static final int BOOK_ROWS = 30;
    private static final int LINE = 10;

    public static class Order {
        public boolean buy;
        public String name, id;
        public double price;
        public long amount, confirmed, createdAt;
        transient boolean init, ambiguous, gone;
        transient long refConfirmed, baselineAtPrice, queueAheadAtPrice, filledAtBaseline, estimated, queueAhead;

        long visible() {
            if (amount <= 0 || confirmed >= amount) return confirmed;
            return Math.min(Math.max(confirmed, estimated), amount - 1);
        }
    }

    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<List<Order>>() {}.getType();
    private static final ProfiledFile FILE = new ProfiledFile("bazaar_orders.json", null);

    private static List<Order> orders = new ArrayList<>();
    private static boolean loaded;
    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int linesW;
    private static int lastDepth = -1, ticks;
    private static Screen lastScreen;
    private static long openedMs;

    private BazaarFillEstimate() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("bazaarFill", "Bazaar Fill Estimate").section("Inventory")
                .enabled(() -> cfg().bazaarFillEstimate)
                .size(() -> Math.max(120, linesW), () -> Math.max(20, lines.size() * LINE))
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, lines.isEmpty()
                        ? List.of("§6§lBazaar Orders §8(estimate)", "§aBUY §f64x Enchanted Coal §7@ 15.6 §e~50%") : lines));
        Feature.of("BazaarFillEstimate").section("Inventory").when(() -> cfg().bazaarFillEstimate)
                .tick(BazaarFillEstimate::tick)
                .serverChat(BazaarFillEstimate::onChat)
                .hud("bazaarfill", BazaarFillEstimate::render);
        com.teslamaps.utils.SbProfile.onChange(() -> loaded = false);
    }

    private static void ensure() {
        if (loaded) return;
        loaded = true;
        orders = new ArrayList<>();
        Path p = FILE.path();
        if (p == null) return;
        try {
            if (Files.exists(p)) {
                List<Order> l = GSON.fromJson(Files.readString(p), TYPE);
                if (l != null) {
                    l.removeIf(o -> o == null || o.name == null || o.amount <= 0);
                    orders = l;
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load bazaar_orders.json", e);
        }
        changed();
    }

    private static void save() {
        Path p = FILE.path();
        if (p == null) return;
        String json = GSON.toJson(orders, TYPE);
        net.minecraft.util.Util.ioPool().execute(() -> {
            try {
                Files.createDirectories(p.getParent());
                Files.writeString(p, json);
            } catch (Exception e) {
                TeslaMaps.LOGGER.error("Failed to save bazaar_orders.json", e);
            }
        });
    }

    private static void changed() {
        Set<String> ids = new HashSet<>();
        for (Order o : orders) {
            if (o.id == null) o.id = idFor(o.name);
            if (o.id != null && o.confirmed < o.amount) ids.add(o.id);
        }
        PriceManager.watchDepth(ids);
        for (Order o : orders) {
            o.ambiguous = false;
            for (Order other : orders) {
                if (other != o && other.buy == o.buy && o.id != null && o.id.equals(other.id)
                        && Math.abs(other.price - o.price) <= EPS) o.ambiguous = true;
            }
        }
        rebuildLines();
    }

    private static String idFor(String name) {
        String id = PriceManager.idForName(name);
        return id != null ? id : com.teslamaps.utils.SkyblockItems.idForName(name);
    }

    private static void onChat(String text) {
        String l = StringUtil.stripTrim(text);
        if (!l.startsWith("[Bazaar]")) return;
        ensure();
        Matcher m = SETUP.matcher(l);
        if (m.find()) {
            Order o = new Order();
            o.buy = m.group(1).equals("Buy Order");
            o.amount = parseLong(m.group(2));
            o.name = m.group(3).trim();
            double total = parseDouble(m.group(4));
            if (o.amount <= 0 || total <= 0) return;
            o.price = Math.round(total / o.amount * 10) / 10.0;
            o.createdAt = System.currentTimeMillis();
            orders.add(o);
            changed();
            save();
            return;
        }
        Matcher f = FILLED_CHAT.matcher(l);
        if (f.find()) {
            boolean buy = f.group(1).equals("Buy Order");
            long amount = parseLong(f.group(2));
            String name = f.group(3).trim();
            for (Order o : orders) {
                if (o.buy == buy && o.amount == amount && o.name.equalsIgnoreCase(name) && o.confirmed < o.amount) {
                    o.confirmed = o.amount;
                    changed();
                    save();
                    return;
                }
            }
        }
    }

    private static void tick() {
        ensure();
        Minecraft mc = Minecraft.getInstance();
        readMenu(mc);
        if (ticks++ % 20 != 0) return;
        if (orders.isEmpty()) return;
        PriceManager.refreshDepth(60_000);
        int dv = PriceManager.depthVersion();
        if (dv == lastDepth) return;
        lastDepth = dv;
        estimate(mc);
    }

    private static void readMenu(Minecraft mc) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            lastScreen = null;
            return;
        }
        if (cs != lastScreen) {
            lastScreen = cs;
            openedMs = System.currentTimeMillis();
        }
        if (ticks % 10 != 0 || System.currentTimeMillis() - openedMs < 600) return;
        if (!StringUtil.stripTrim(cs.getTitle().getString()).endsWith("Bazaar Orders")) return;
        List<Order> seen = new ArrayList<>();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            Order o = parse(slot.getItem());
            if (o != null) seen.add(o);
        }
        if (seen.isEmpty() && orders.isEmpty()) return;
        boolean diff = seen.size() != orders.size();
        List<Order> merged = new ArrayList<>(seen.size());
        for (Order s : seen) {
            Order old = null;
            for (Order o : orders) {
                if (!merged.contains(o) && o.buy == s.buy && o.amount == s.amount && o.name.equalsIgnoreCase(s.name)
                        && Math.abs(o.price - s.price) < 0.05) { old = o; break; }
            }
            if (old == null) {
                s.createdAt = System.currentTimeMillis();
                merged.add(s);
                diff = true;
                continue;
            }
            if (old.confirmed != s.confirmed) diff = true;
            old.confirmed = Math.max(old.confirmed, s.confirmed);
            old.price = s.price;
            merged.add(old);
        }
        if (!diff) return;
        orders = merged;
        changed();
        save();
    }

    private static Order parse(ItemStack st) {
        if (st.isEmpty()) return null;
        Matcher nm = NAME.matcher(StringUtil.stripTrim(st.getHoverName().getString()));
        if (!nm.matches()) return null;
        Order o = new Order();
        o.buy = nm.group(1).equals("BUY");
        o.name = nm.group(2).trim();
        for (String raw : ItemUtil.loreLines(st)) {
            String l = raw.trim();
            Matcher a = AMOUNT.matcher(l);
            if (a.find()) { o.amount = parseLong(a.group(1)); continue; }
            Matcher p = PRICE.matcher(l);
            if (p.find()) { o.price = parseDouble(p.group(1)); continue; }
            Matcher f = FILLED.matcher(l);
            if (f.find()) o.confirmed = parseShort(f.group(1));
        }
        if (o.amount <= 0 || o.price <= 0) return null;
        o.confirmed = Math.min(o.confirmed, o.amount);
        return o;
    }

    private static void estimate(Minecraft mc) {
        long marketAt = PriceManager.bazaarUpdatedAt();
        boolean any = false;
        for (Order o : orders) {
            long before = o.visible();
            if (!update(o, marketAt)) continue;
            long after = o.visible();
            if (after > before && o.init) {
                any = true;
                if (cfg().bazaarFillEstimateAlert && mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7" + (o.buy ? "Buy Order" : "Sell Offer")
                            + " §f" + o.amount + "x " + o.name + " §7now ~§a" + pct(o) + "% §7filled §8(estimate)"));
                }
            }
        }
        if (any && cfg().bazaarFillEstimateSound) com.teslamaps.utils.LoudSound.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        rebuildLines();
    }

    /** False when nothing could be said about the order this round. */
    private static boolean update(Order o, long marketAt) {
        long remaining = o.amount - o.confirmed;
        if (remaining <= 0 || o.ambiguous || o.id == null || marketAt <= o.createdAt) return false;
        double[][] rows = PriceManager.depthOrNull(o.id, o.buy);
        if (rows == null) return false;
        long better = 0, same = 0, sameOrders = 0;
        for (double[] r : rows) {
            boolean isBetter = o.buy ? r[0] > o.price + EPS : r[0] < o.price - EPS;
            if (isBetter) better += (long) r[1];
            else if (Math.abs(r[0] - o.price) <= 0.05) {
                same += (long) r[1];
                sameOrders += (long) r[2];
            }
        }
        if (o.init && o.refConfirmed != o.confirmed) o.init = false;
        if (same <= 0 && sameOrders <= 0) {
            boolean visibleRange = rows.length < BOOK_ROWS || (rows.length > 0
                    && (o.buy ? rows[rows.length - 1][0] < o.price : rows[rows.length - 1][0] > o.price));
            if (!visibleRange) return false;
            boolean wasInit = o.init;
            o.gone = true;
            o.estimated = o.amount;
            o.queueAhead = 0;
            o.init = true;
            o.refConfirmed = o.confirmed;
            return wasInit;
        }
        o.gone = false;
        if (!o.init) {
            long inferred = same < remaining && sameOrders == 1 ? remaining - same : 0;
            o.filledAtBaseline = Math.min(o.amount, o.confirmed + inferred);
            o.baselineAtPrice = same;
            o.queueAheadAtPrice = Math.max(0, same - remaining);
            o.estimated = o.filledAtBaseline;
            o.refConfirmed = o.confirmed;
            o.queueAhead = better + o.queueAheadAtPrice;
            o.init = true;
            return false;
        }
        long filledSince = Math.max(0, o.baselineAtPrice - same - o.queueAheadAtPrice);
        o.estimated = Math.clamp(Math.max(o.estimated, o.filledAtBaseline + filledSince), o.confirmed, o.amount);
        o.queueAhead = better + Math.max(0, same - (o.amount - o.estimated));
        return true;
    }

    private static int pct(Order o) {
        return o.amount <= 0 ? 0 : (int) (o.visible() * 100 / o.amount);
    }

    private static void rebuildLines() {
        if (orders.isEmpty()) {
            lines = List.of();
            linesW = 0;
            return;
        }
        List<String> out = new ArrayList<>(orders.size() + 1);
        out.add("§6§lBazaar Orders §8(estimate)");
        for (Order o : orders) {
            String state;
            if (o.confirmed >= o.amount) state = "§a✔ filled";
            else if (o.ambiguous) state = "§8? same price twice";
            else if (!o.init) state = "§7" + pct(o) + "% §8…";
            else if (o.gone) state = "§a~99% §7(gone from book)";
            else state = "§e~" + pct(o) + "%" + (o.queueAhead > 0 ? " §8ahead " + EstimatedValue.shortFormat(o.queueAhead) : "");
            out.add((o.buy ? "§aBUY " : "§6SELL ") + "§f" + String.format(Locale.ROOT, "%,d", o.amount) + "x " + o.name
                    + " §7@ " + EstimatedValue.exact(o.price) + " " + state);
        }
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String s : out) w = Math.max(w, mc.font.width(s));
        linesW = w;
        lines = out;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || lines.isEmpty()) return;
        draw(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), lines);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l) {
        int w = 0;
        if (l == lines) w = linesW;
        else for (String s : l) w = Math.max(w, mc.font.width(s));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * LINE + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * LINE, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.replace(",", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s.replace(",", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static long parseShort(String s) {
        String t = s.replace(",", "");
        char u = Character.toLowerCase(t.charAt(t.length() - 1));
        double mul = u == 'k' ? 1e3 : u == 'm' ? 1e6 : 1;
        if (mul != 1) t = t.substring(0, t.length() - 1);
        try { return Math.round(Double.parseDouble(t) * mul); } catch (NumberFormatException e) { return 0; }
    }
}
