package com.teslamaps.safari;

// After critterMod's client/BazaarPrices + CritterConfig.ProfitConfig
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.hunting.ShardData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Coins the shards of a run are worth at the bazaar top of book. */
public final class SafariShardValue {

    public static final String[] MODES = {"Instant Sell", "Sell Offer", "Both"};
    private static final double TAX = 0.0125;

    private SafariShardValue() {}

    public static boolean on() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.safariShardValue && c.safariTracker;
    }

    static String id(String critter) {
        ShardData.load();
        ShardData.Shard s = ShardData.byDisplayName(critter);
        if (s != null && s.bazaarId() != null && !s.bazaarId().isEmpty()) return s.bazaarId();
        return PriceManager.idForName(critter + " Shard");
    }

    /** {instant sell, sell offer} for one shard, tax already taken off when asked. */
    static double[] unit(String critter) {
        String id = id(critter);
        Double is = PriceManager.bazaarBuyOrderOrNull(id), so = PriceManager.bazaarSellOfferOrNull(id);
        if (is == null) is = PriceManager.bazaarSellOrNull(id);
        double f = TeslaMapsConfig.get().safariShardTax ? 1 - TAX : 1;
        return new double[] {is == null ? 0 : is * f, so == null ? 0 : so * f};
    }

    /** {instant sell, sell offer} total. */
    public static double[] value(Map<String, Integer> shards) {
        double[] out = new double[2];
        if (shards == null) return out;
        PriceManager.ensureFresh();
        for (Map.Entry<String, Integer> e : shards.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            double[] u = unit(e.getKey());
            out[0] += u[0] * e.getValue();
            out[1] += u[1] * e.getValue();
        }
        return out;
    }

    private static String mode() {
        String m = TeslaMapsConfig.get().safariShardPrice;
        return m == null ? "Instant Sell" : m;
    }

    /** The one number the chosen mode stands for, instant sell when "Both". */
    public static double main(double[] v) {
        return mode().equals("Sell Offer") ? v[1] : v[0];
    }

    public static String format(double[] v) {
        return switch (mode()) {
            case "Sell Offer" -> "§6" + EstimatedValue.fmt(v[1]);
            case "Both" -> "§6" + EstimatedValue.fmt(v[0]) + " §8/ §6" + EstimatedValue.fmt(v[1]);
            default -> "§6" + EstimatedValue.fmt(v[0]);
        };
    }

    public static String perHour(double coins, long ms) {
        if (ms < 60_000) return "";
        return " §7(" + EstimatedValue.fmt(coins * 3_600_000d / ms) + "/h)";
    }

    /** The most valuable species, for /tmap safari prices. */
    public static List<String> priceLines() {
        PriceManager.ensureFresh();
        List<Object[]> list = new ArrayList<>();
        for (SafariData.Critter c : SafariData.ALL) {
            double[] u = unit(c.name());
            if (u[0] <= 0 && u[1] <= 0) continue;
            list.add(new Object[] {c.name(), u});
        }
        list.sort((a, b) -> Double.compare(main((double[]) b[1]), main((double[]) a[1])));
        List<String> out = new ArrayList<>();
        out.add("§b§lSafari shard prices §7(" + mode() + (TeslaMapsConfig.get().safariShardTax ? ", after tax" : "") + ")");
        if (list.isEmpty()) out.add(PriceManager.isLoaded() ? "§7No shard prices found" : "§7Prices still loading, try again");
        for (int i = 0; i < list.size() && i < 20; i++) {
            Object[] o = list.get(i);
            out.add("§f" + o[0] + "§8: " + format((double[]) o[1]));
        }
        return out;
    }
}
