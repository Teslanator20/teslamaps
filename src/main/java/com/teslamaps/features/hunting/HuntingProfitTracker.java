package com.teslamaps.features.hunting;

// After SkyHanni's HuntingProfitTracker.kt (gain patterns from AttributeShardsData.kt)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shards caught while hunting, priced on the bazaar, with profit per hour. */
public final class HuntingProfitTracker {

    private static final Pattern[] GAINS = {
            Pattern.compile("You caught(?: an?)?(?: x(?<amount>\\d+))? (?<name>.+) Shards?!"),
            Pattern.compile(".*CATCH! You caught(?: an?)? (?<name>.+) Shard(?: x(?<amount>\\d+))?!"),
            Pattern.compile("LOOT SHARE!? You received (?:an?|(?<amount>\\d+)x?) (?<name>.+) Shards? (?:for assisting .*|from .*)!"),
            Pattern.compile("CHARM! You charmed the .+ and received (?<amount>\\d+) (?<name>.+) Shards?!"),
            Pattern.compile("CAPTURE! You (?:caught an?|found) .+ and (?:gained|as a reward (?:he|she|they) gave you) (?:an?|(?<amount>\\d+)x) (?<name>.+) Shard!"),
    };
    private static final String CAUGHT = "HPT_CAUGHT";

    private static Tracker tracker;
    private static long lastCatch, lastTool;
    private static boolean show;

    private HuntingProfitTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        tracker = Tracker.create("hunting", "§e§lHunting Profit Tracker")
                .priceSource(() -> cfg().huntingProfitPriceSource)
                .fixedValue(CAUGHT, 0).name(CAUGHT, "§7Mobs Caught");
        tracker.extra(v -> {
            List<String> out = new ArrayList<>();
            long caught = tracker.count(CAUGHT, v);
            if (caught <= 0) return out;
            long shards = 0;
            for (String k : tracker.keys()) if (!k.equals(CAUGHT)) shards += tracker.count(k, v);
            out.add("§7Shards: §e" + shards + String.format(Locale.ROOT, " §7(%.2f per catch)", shards / (double) caught));
            if (shards > 0) out.add("§eProfit/Shard: §6" + ItemPrice.format(tracker.profit(v) / shards));
            return out;
        });
        HudEditElement.of("huntingProfitTracker", "Hunting Profit Tracker").section("Shards")
                .enabled(() -> cfg().huntingProfitTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("HuntingProfitTracker").section("Shards").when(() -> cfg().huntingProfitTracker)
                .tick(HuntingProfitTracker::tick)
                .serverChat(HuntingProfitTracker::onChat)
                .hud("huntingprofit", HuntingProfitTracker::render);
    }

    /** Rows for the Shards category, right under Hunting Box Value. */
    public static void config(com.teslamaps.screen.config.ConfigBuilder b, TeslaMapsConfig c) {
        b.toggle("Hunting Profit Tracker", () -> c.huntingProfitTracker, v -> c.huntingProfitTracker = v)
                .desc("Shards caught, looted or charmed with their bazaar value, shards per catch and profit per hour. "
                        + "Shown with a hunting tool in hand or 10 s after a catch. Move it in /tmap gui.")
                .tags("shard", "hunting", "lasso", "net", "profit", "tracker");
        b.toggle("  Always Show", () -> c.huntingProfitAlways, v -> c.huntingProfitAlways = v);
        b.priceSource("  Price Source", () -> c.huntingProfitPriceSource, v -> c.huntingProfitPriceSource = v);
        if (tracker != null) b.trackerButtons(tracker);
    }

    private static void tick() {
        ShardData.load();
        long now = System.currentTimeMillis();
        if (holdingTool()) lastTool = now;
        show = com.teslamaps.utils.SkyblockUtils.isInSkyblock()
                && (cfg().huntingProfitAlways || now - lastTool < 10_000 || now - lastCatch < 10_000);
        tracker.tick();
    }

    private static boolean holdingTool() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String id = ItemUtil.skyblockId(mc.player.getMainHandItem());
        if (id.isEmpty()) return false;
        String cat = SkyblockItems.category(id);
        return cat.equals("LASSO") || cat.equals("FISHING_NET") || id.contains("LASSO") || id.contains("_NET")
                || id.contains("BLACK_HOLE") || id.contains("HUNTING");
    }

    private static void onChat(String raw) {
        if (!ShardData.loaded()) return;
        String text = StringUtil.stripTrim(raw);
        if (!text.contains("Shard")) return;
        for (Pattern p : GAINS) {
            Matcher m = p.matcher(text);
            if (!m.matches()) continue;
            ShardData.Shard shard = ShardData.byDisplayName(m.group("name"));
            if (shard == null || shard.bazaarId() == null) return;
            int amount = m.group("amount") == null ? 1 : Integer.parseInt(m.group("amount"));
            tracker.name(shard.bazaarId(), "§f" + shard.displayName() + " Shard");
            tracker.add(shard.bazaarId(), amount);
            tracker.add(CAUGHT, 1);
            lastCatch = System.currentTimeMillis();
            return;
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || com.teslamaps.utils.HudVisibility.suppressed()) return;
        TeslaMapsConfig c = cfg();
        tracker.draw(g, mc, c.huntingProfitTrackerX, c.huntingProfitTrackerY, c.huntingProfitTrackerScale);
    }
}
