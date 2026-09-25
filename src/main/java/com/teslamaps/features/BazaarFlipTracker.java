package com.teslamaps.features;

// After Skysoft's BazaarFlipAccounting + BazaarSessionState: FIFO cost basis from the bazaar chat lines
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.debug.Perf;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Invested coins (open buy orders + unsold bought lots) and flip profit, session or total. */
public final class BazaarFlipTracker {

    private static final Pattern BUY_SETUP = Pattern.compile("^\\[Bazaar] Buy Order Setup! ([\\d,]+)x (.+) for ([\\d,.]+) coins\\.$");
    private static final Pattern FLIPPED = Pattern.compile("^\\[Bazaar] Order Flipped! ([\\d,]+)x (.+) for ([\\d,.]+) coins of total expected profit\\.$");
    private static final Pattern BUY_CANCEL = Pattern.compile("^(?:\\[Bazaar] )?Cancelled! Refunded ([\\d,.]+) coins from cancelling Buy Order!$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUY_CLAIM = Pattern.compile("^\\[Bazaar] Claimed ([\\d,]+)x (.+) worth ([\\d,.]+) coins bought for ([\\d,.]+) each!$");
    private static final Pattern SELL_CLAIM = Pattern.compile("^\\[Bazaar] Claimed ([\\d,.]+) coins from selling ([\\d,]+)x (.+) at ([\\d,.]+) each!$");
    private static final Pattern INSTA_BUY = Pattern.compile("^\\[Bazaar] Bought ([\\d,]+)x (.+) for ([\\d,.]+) coins!$");
    private static final Pattern INSTA_SELL = Pattern.compile("^\\[Bazaar] Sold ([\\d,]+)x (.+) for ([\\d,.]+) coins!$");

    private static final class Order { String item; long amount; double unit; }
    private static final class Lot { String item; long amount; double unit; }
    private static final class Data {
        List<Order> orders = new ArrayList<>();
        List<Lot> lots = new ArrayList<>();
        double totalProfit;
    }

    private static final ProfiledFile FILE = new ProfiledFile("bazaar_flips.json", null);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] SAMPLE = {"§6§lBazaar Flips", "§7Invested: §61.2M", "§7Profit (Session): §a+340k"};

    private static Data data = new Data();
    private static boolean loaded, dirty;
    private static double sessionProfit;
    private static List<String> lines = List.of();
    private static int ticks;
    private static HudEditElement hud;

    private BazaarFlipTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("bazaarFlip", "Bazaar Flips").section("Inventory")
                .enabled(() -> cfg().bazaarFlipTracker)
                .size(() -> width(List.of(SAMPLE)), () -> SAMPLE.length * (Minecraft.getInstance().font.lineHeight + 1))
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, List.of(SAMPLE)));
        Feature.of("BazaarFlipTracker").section("Inventory")
                .when(() -> cfg().bazaarFlipTracker)
                .tick(BazaarFlipTracker::tick)
                .serverChat(BazaarFlipTracker::onChat)
                .hud("bazaarflip", (g, d) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.options.hideGui || lines.isEmpty() || !SkyblockUtils.isInSkyblock()) return;
                    draw(g, mc, hud.x(), hud.y(), hud.scale(), lines);
                })
                .config("Inventory", b -> {
                    b.label("Bazaar Flips");
                    b.toggle("Bazaar Flip Tracker", () -> cfg().bazaarFlipTracker, v -> cfg().bazaarFlipTracker = v)
                            .desc("HUD: coins in open buy orders + bought, unsold items, and profit from sells against their FIFO buy price")
                            .tags("bazaar", "flip", "profit", "invested");
                    b.toggle("  Show Total Instead Of Session", () -> cfg().bazaarFlipShowTotal, v -> cfg().bazaarFlipShowTotal = v);
                    b.toggle("  Count Instant Buys/Sells", () -> cfg().bazaarFlipInstant, v -> cfg().bazaarFlipInstant = v);
                    b.buttonRow(new String[]{"Reset Session", "Reset Total"}, new Runnable[]{
                            () -> sessionProfit = 0,
                            () -> { load(); data = new Data(); sessionProfit = 0; dirty = true; save(); }});
                    b.info("Position in /tmap gui. Crafting between buy and sell is not followed.");
                });
    }

    private static void onChat(String text) {
        String t = StringUtil.stripTrim(text);
        if (!t.startsWith("[Bazaar]") && !t.startsWith("Cancelled!")) return;
        load();
        Matcher m;
        if ((m = BUY_SETUP.matcher(t)).matches()) {
            Order o = new Order();
            o.item = m.group(2);
            o.amount = num(m.group(1));
            o.unit = o.amount > 0 ? dbl(m.group(3)) / o.amount : 0;
            if (o.amount > 0) data.orders.add(o);
        } else if ((m = BUY_CLAIM.matcher(t)).matches()) {
            long amount = num(m.group(1));
            takeOrder(m.group(2), amount);
            addLot(m.group(2), amount, dbl(m.group(4)));
        } else if ((m = FLIPPED.matcher(t)).matches()) {
            long amount = num(m.group(1));
            double unit = takeOrder(m.group(2), amount);
            if (unit > 0) addLot(m.group(2), amount, unit);
        } else if ((m = BUY_CANCEL.matcher(t)).matches()) {
            cancelOrder(dbl(m.group(1)));
        } else if ((m = SELL_CLAIM.matcher(t)).matches()) {
            sell(m.group(3), num(m.group(2)), dbl(m.group(1)));
        } else if (cfg().bazaarFlipInstant && (m = INSTA_BUY.matcher(t)).matches()) {
            long amount = num(m.group(1));
            if (amount > 0) addLot(m.group(2), amount, dbl(m.group(3)) / amount);
        } else if (cfg().bazaarFlipInstant && (m = INSTA_SELL.matcher(t)).matches()) {
            sell(m.group(2), num(m.group(1)), dbl(m.group(3)));
        } else return;
        dirty = true;
        ticks = 0;
    }

    /** Removes filled amount from the oldest open buy order of the item, returns its unit price. */
    private static double takeOrder(String item, long amount) {
        long left = amount;
        double unit = 0;
        Iterator<Order> it = data.orders.iterator();
        while (it.hasNext() && left > 0) {
            Order o = it.next();
            if (!o.item.equalsIgnoreCase(item)) continue;
            if (unit == 0) unit = o.unit;
            long used = Math.min(left, o.amount);
            o.amount -= used;
            left -= used;
            if (o.amount <= 0) it.remove();
        }
        return unit;
    }

    /** The refund names no item: take the open order whose remaining value fits it best. */
    private static void cancelOrder(double refund) {
        Order best = null;
        double bestDiff = Double.MAX_VALUE;
        for (Order o : data.orders) {
            double diff = Math.abs(o.amount * o.unit - refund);
            if (diff < bestDiff) { bestDiff = diff; best = o; }
        }
        if (best != null) data.orders.remove(best);
    }

    private static void addLot(String item, long amount, double unit) {
        if (amount <= 0 || unit <= 0) return;
        Lot l = new Lot();
        l.item = item;
        l.amount = amount;
        l.unit = unit;
        data.lots.add(l);
    }

    /** FIFO against the bought lots; the part without a known buy price adds nothing. */
    private static void sell(String item, long amount, double revenue) {
        if (amount <= 0) return;
        long left = amount, known = 0;
        double cost = 0;
        Iterator<Lot> it = data.lots.iterator();
        while (it.hasNext() && left > 0) {
            Lot l = it.next();
            if (!l.item.equalsIgnoreCase(item)) continue;
            long used = Math.min(left, l.amount);
            cost += used * l.unit;
            known += used;
            l.amount -= used;
            left -= used;
            if (l.amount <= 0) it.remove();
        }
        if (known == 0) return;
        double profit = revenue * ((double) known / amount) - cost;
        sessionProfit += profit;
        data.totalProfit += profit;
    }

    private static void tick() {
        load();
        if (ticks++ % 20 != 0) return;
        if (dirty) save();
        double invested = 0;
        for (Order o : data.orders) invested += o.amount * o.unit;
        for (Lot l : data.lots) invested += l.amount * l.unit;
        boolean total = cfg().bazaarFlipShowTotal;
        double profit = total ? data.totalProfit : sessionProfit;
        List<String> out = new ArrayList<>(3);
        out.add("§6§lBazaar Flips");
        out.add("§7Invested: §6" + EstimatedValue.fmt(invested));
        out.add("§7Profit (" + (total ? "Total" : "Session") + "): " + (profit >= 0 ? "§a+" : "§c-") + EstimatedValue.fmt(Math.abs(profit)));
        lines = out;
    }

    private static int width(List<String> l) {
        int w = 0;
        for (String s : l) w = Math.max(w, Minecraft.getInstance().font.width(s));
        return w;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l) {
        Perf.begin("BazaarFlipTracker.hud");
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int h = l.size() * (mc.font.lineHeight + 1);
        com.teslamaps.render.gui.TeslaGui.backdropRect(g, -4, -4, width(l) + 8, h + 4);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * (mc.font.lineHeight + 1), 0xFFFFFFFF);
        pose.popMatrix();
        Perf.end();
    }

    private static void load() {
        if (loaded && !FILE.stale()) return;
        loaded = true;
        dirty = false;
        data = new Data();
        try {
            Path p = FILE.path();
            if (p != null && Files.exists(p)) {
                Data d = GSON.fromJson(Files.readString(p), Data.class);
                if (d != null) {
                    if (d.orders == null) d.orders = new ArrayList<>();
                    if (d.lots == null) d.lots = new ArrayList<>();
                    data = d;
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[BazaarFlips] load failed: {}", e.toString());
        }
    }

    private static void save() {
        dirty = false;
        try {
            Path p = FILE.path();
            if (p == null) return;
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(data));
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[BazaarFlips] save failed: {}", e.toString());
        }
    }

    private static long num(String s) {
        try { return Long.parseLong(s.replace(",", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static double dbl(String s) {
        try { return Double.parseDouble(s.replace(",", "")); } catch (NumberFormatException e) { return 0; }
    }
}
