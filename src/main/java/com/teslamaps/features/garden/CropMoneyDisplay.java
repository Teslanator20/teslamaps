package com.teslamaps.features.garden;

// Ported from SkyHanni's CropMoneyDisplay.kt (no armor drops); crop speed = latest fortune x base drops x BPS
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.FarmProfit;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.features.pets.PetTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class CropMoneyDisplay {

    private static final String[] SEEDS = {"SEEDS", "ENCHANTED_SEEDS", "BOX_OF_SEEDS"};
    private static final List<String> SAMPLE = List.of("§7Money per Hour §7(§eInstant Sell§7)",
            "§7#1 §eCactus§7: §61.52M", "§7#2 §7Sugar Cane§7: §61.38M", "§7#3 §7Nether Wart§7: §61.21M");

    private static final Map<GardenCrop, Boolean> bountiful = new EnumMap<>(GardenCrop.class);
    private static List<String> lines = List.of();
    private static int width;
    private static int ticks;
    private static GardenCrop lastInHand;
    private static HudEditElement hud;

    private record Row(GardenCrop crop, double[] coins, double best) {}

    private CropMoneyDisplay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("cropMoneyHud", "Money per Hour").ctx(HudEditElement.HudCtx.GARDEN).section("Garden")
                .enabled(() -> cfg().cropMoneyHud)
                .size(() -> TextHud.width(SAMPLE), () -> TextHud.height(SAMPLE.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("CropMoneyDisplay").section("Garden").when(() -> cfg().cropMoneyHud)
                .tick(CropMoneyDisplay::tick)
                .hudHiddenInInventory("cropmoney", CropMoneyDisplay::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Money per Hour");
                    b.toggle("Money per Hour per Crop", () -> c.cropMoneyHud, v -> c.cropMoneyHud = v)
                            .desc("Coins per hour every crop would make with your own measured speed (latest fortune and BPS per crop), best first, current crop yellow. Farm a crop once to add it. Position in /tmap gui.")
                            .tags("money", "coins", "profit", "crop", "per hour");
                    b.slider("  Crops Listed", 1f, 13f, () -> (float) c.cropMoneyRows, v -> c.cropMoneyRows = Math.round(v), 0);
                    b.toggle("  Always Show Current Crop", () -> c.cropMoneyShowCurrent, v -> c.cropMoneyShowCurrent = v);
                    b.toggle("  Show Without Farming Tool", () -> c.cropMoneyAlwaysOn, v -> c.cropMoneyAlwaysOn = v);
                    b.toggle("  Price: Instant Sell", () -> c.cropMoneyInstantSell, v -> c.cropMoneyInstantSell = v);
                    b.toggle("  Price: Sell Offer", () -> c.cropMoneySellOffer, v -> c.cropMoneySellOffer = v);
                    b.toggle("  Price: NPC", () -> c.cropMoneyNpc, v -> c.cropMoneyNpc = v);
                    b.toggle("  Count Bountiful", () -> c.cropMoneyBountiful, v -> c.cropMoneyBountiful = v)
                            .desc("Bountiful tools add 0.2 coins per crop");
                    b.toggle("  Count Mooshroom Cow", () -> c.cropMoneyMooshroom, v -> c.cropMoneyMooshroom = v)
                            .desc("Mushrooms from the Mooshroom Cow pet perk while farming other crops");
                    b.toggle("  Merge Seeds Into Wheat", () -> c.cropMoneyMergeSeeds, v -> c.cropMoneyMergeSeeds = v);
                });
    }

    private static int[] formats(TeslaMapsConfig c) {
        int n = (c.cropMoneyInstantSell ? 1 : 0) + (c.cropMoneySellOffer ? 1 : 0) + (c.cropMoneyNpc ? 1 : 0);
        if (n == 0) return new int[]{0};
        int[] out = new int[n];
        int i = 0;
        if (c.cropMoneyInstantSell) out[i++] = 0;
        if (c.cropMoneySellOffer) out[i++] = 1;
        if (c.cropMoneyNpc) out[i] = 2;
        return out;
    }

    private static final String[] FORMAT_NAMES = {"Instant Sell", "Sell Offer", "NPC"};

    private static Double price(String id, int format) {
        if (id == null) return null;
        return switch (format) {
            case 0 -> PriceManager.bazaarSellOrNull(id);
            case 1 -> PriceManager.bazaarBuyOrNull(id);
            default -> SkyblockItems.hasNpcPrice(id) ? SkyblockItems.npcPrice(id) : null;
        };
    }

    /** Best coins per hour over the item tiers, -1 when nothing is priced. */
    private static double best(String[] ids, double perSecond, int format) {
        double best = -1;
        for (int i = 0; i < ids.length; i++) {
            if (format == 2 ? i != 0 : GardenCrop.ITEM_AMOUNTS[i] < 10) continue;
            Double p = price(ids[i], format);
            if (p == null || p <= 0) continue;
            best = Math.max(best, p * perSecond * 3600 / GardenCrop.ITEM_AMOUNTS[i]);
        }
        return best;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        GardenCrop inHand = GardenCropData.inHand();
        boolean changed = inHand != lastInHand;
        lastInHand = inHand;
        if (!changed && ++ticks % 100 != 0) return;
        if (inHand != null) {
            CustomData d = mc.player.getMainHandItem().get(DataComponents.CUSTOM_DATA);
            if (d != null) bountiful.put(inHand, "bountiful".equals(d.copyTag().getStringOr("modifier", "")));
        }
        TeslaMapsConfig c = cfg();
        if (!SkyblockUtils.isInGarden() || inHand == null && !c.cropMoneyAlwaysOn) {
            if (!lines.isEmpty()) lines = List.of();
            return;
        }
        PriceManager.ensureFresh();
        int[] formats = formats(c);

        double extra = 0;
        if (c.cropMoneyMooshroom && inHand != null && inHand != GardenCrop.MUSHROOM
                && "MOOSHROOM_COW".equals(PetTracker.internalName())) {
            Double bps = GardenCropData.latestBps(inHand);
            Double red = PriceManager.bazaarSellOrNull("ENCHANTED_RED_MUSHROOM"), brown = PriceManager.bazaarSellOrNull("ENCHANTED_BROWN_MUSHROOM");
            if (bps != null && red != null && brown != null) {
                double mushroom = (red / 160 + brown / 160) / 2;
                extra = bps * inHand.multiplier() * mushroom * Math.max(0, PetTracker.level()) / 100.0 * 3600;
            }
        }

        List<Row> rows = new ArrayList<>();
        for (GardenCrop crop : GardenCrop.ALL) {
            Double cps = GardenCropData.cropsPerSecond(crop);
            if (cps == null) continue;
            double bps = GardenCropData.latestBps(crop);
            double speed = cps - (crop.replenish ? crop.multiplier() * bps : 0);
            double seedSpeed = crop == GardenCrop.WHEAT ? cps * 1.5 - bps : 0;
            double bounty = c.cropMoneyBountiful && Boolean.TRUE.equals(bountiful.get(crop)) ? speed * 3600 * 0.2 : 0;
            double[] coins = new double[formats.length];
            double top = -1;
            boolean any = false;
            for (int f = 0; f < formats.length; f++) {
                double v = best(crop.items, speed, formats[f]);
                if (crop == GardenCrop.WHEAT && c.cropMoneyMergeSeeds) {
                    double s = best(SEEDS, seedSpeed, formats[f]);
                    if (s > 0) v = Math.max(v, 0) + s;
                }
                if (v < 0) continue;
                any = true;
                coins[f] = v + bounty + extra;
                top = Math.max(top, coins[f]);
            }
            if (any) rows.add(new Row(crop, coins, top));
        }
        rows.sort((a, b) -> Double.compare(b.best, a.best));

        List<String> out = new ArrayList<>();
        StringBuilder title = new StringBuilder("§7Money per Hour §7(");
        for (int f = 0; f < formats.length; f++) {
            if (f > 0) title.append("§7/");
            title.append("§e").append(FORMAT_NAMES[formats[f]]);
        }
        out.add(title.append("§7)").toString());
        if (rows.isEmpty()) out.add("§cFarm crops to add them to this list!");
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            boolean current = r.crop == inHand;
            if (i >= c.cropMoneyRows && !(current && c.cropMoneyShowCurrent)) continue;
            StringBuilder line = new StringBuilder("§7#").append(i + 1).append(' ')
                    .append(current ? "§e" : "§7").append(r.crop.cropName).append("§7: ");
            for (int f = 0; f < r.coins.length; f++) {
                if (f > 0) line.append("§7/");
                line.append("§6").append(FarmProfit.shortCoins(r.coins[f]));
            }
            out.add(line.toString());
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || lines.isEmpty() || hud == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
