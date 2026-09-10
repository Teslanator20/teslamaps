package com.teslamaps.features.hunting;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.features.croesus.PriceManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Chat version of the shard overlay, for /tmap shards. */
public class ShardReport {

    private record Missing(String name, int amount, double price, int tier) {}

    public static List<String> toMax(int limit) {
        ShardData.load();
        PriceManager.ensureFresh();
        List<String> out = new ArrayList<>();
        if (!ShardData.loaded()) {
            out.add("§cShard data is still loading, try again in a moment.");
            return out;
        }

        TeslaMapsConfig config = TeslaMapsConfig.get();
        List<Missing> missing = new ArrayList<>();
        double total = 0;
        int maxed = 0, known = 0;

        for (ShardData.Shard shard : ShardData.all()) {
            if (!ShardData.isConsumable(shard)) continue;
            if (config.shardsSyphoned.containsKey(shard.bazaarId())) known++;

            int[] tiers = ShardData.tiers(shard, ShardData.syphoned(shard));
            if (tiers[0] >= 10) { maxed++; continue; }

            int covered = (config.shardIncludeBox ? ShardData.inBox(shard) : 0)
                    + (config.shardIncludeOrders ? BazaarOrders.openBuy(shard) : 0)
                    + ShardData.inInventory(shard);
            int need = Math.max(0, tiers[2] - covered);
            if (need <= 0) continue;
            Double per = PriceManager.bazaarBuyOrNull(shard.bazaarId());
            double price = (per == null ? 0 : per) * need;
            total += price;
            missing.add(new Missing(shard.displayName(), need, price, tiers[0]));
        }
        missing.sort(Comparator.comparingDouble(Missing::price));

        int max = ShardData.maxShards();
        out.add("§6§lShards to max §7- §a" + maxed + "§7/§a" + max + " §7maxed");
        if (known < max) out.add("§cNo data for " + (max - known) + " shards - open /attributemenu once");
        out.add("§7Total: §6" + EstimatedValue.fmt(total) + " §7(bazaar instant buy)");

        int shown = Math.min(limit, missing.size());
        for (int i = 0; i < shown; i++) {
            Missing m = missing.get(i);
            out.add(" §7" + (i + 1) + ". §f" + m.name + " §8t" + m.tier
                    + " §7- §a" + m.amount + "x §7= §6" + EstimatedValue.fmt(m.price));
        }
        if (missing.size() > shown) out.add("§8… and " + (missing.size() - shown) + " more");
        if (missing.isEmpty()) out.add("§aNothing left to buy.");
        return out;
    }

    private ShardReport() {}
}
