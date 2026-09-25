package com.teslamaps.features.visitors;

// After SkyHanni's GardenVisitorTooltip + VisitorPriceCalculator, Devonian's VisitorProfitDisplay
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VisitorOffer {

    private static Feature feature;
    private static final Set<String> notified = new HashSet<>();
    private static Visitors.Visitor lastOpen;

    private VisitorOffer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("VisitorOffer").section("Garden").when(() -> cfg().visitorOfferInfo)
                .tick(VisitorOffer::tick)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Visitor Offer Prices", () -> c.visitorOfferInfo, v -> c.visitorOfferInfo = v)
                            .desc("Accept Offer tooltip: price of every wanted item and reward, coins paid per copper and per garden xp, copper worth (Green Thumb I / 1500) and the profit or loss of the offer.")
                            .tags("visitor", "copper", "profit", "price");
                    b.toggle("  Coins Per Copper / Garden XP", () -> c.visitorOfferCopperRate, v -> c.visitorOfferCopperRate = v);
                    b.toggle("  Profit Summary", () -> c.visitorOfferProfit, v -> c.visitorOfferProfit = v);
                    b.toggle("  Rare Reward In Chat", () -> c.visitorRareRewardChat, v -> c.visitorRareRewardChat = v);
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature == null || !feature.active()) return;
            Visitors.Visitor v = Visitors.open();
            if (v == null || !v.read) return;
            if (!StringUtil.stripTrim(stack.getHoverName().getString()).equals("Accept Offer")) return;
            enrich(v, lines);
        });
    }

    private static void tick() {
        Visitors.Visitor v = Visitors.open();
        if (v == lastOpen) return;
        lastOpen = v;
        if (v == null || !v.read || !cfg().visitorRareRewardChat || !notified.add(v.name)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        for (String r : Visitors.rareRewards(v)) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fFound visitor reward §9" + r + " §ffrom " + v.colored + "§f!"));
        }
    }

    private static void enrich(Visitors.Visitor v, List<Component> lines) {
        for (int i = 1; i < lines.size(); i++) {
            String t = StringUtil.stripTrim(lines.get(i).getString());
            if (t.isEmpty()) continue;
            Double p = v.linePrice.get(t);
            if (p != null && p > 0) {
                lines.set(i, lines.get(i).copy().append(Component.literal(" §7(§6" + EstimatedValue.fmt(p) + "§7)")));
                continue;
            }
            if (!cfg().visitorOfferCopperRate || !t.startsWith("+")) continue;
            if (t.contains(" Copper") && v.copper > 0) {
                lines.set(i, lines.get(i).copy().append(Component.literal(
                        " §7(paying §6" + EstimatedValue.fmt(v.pricePerCopper()) + " §7per)")));
            } else if (t.endsWith("Garden Experience") && v.gardenXp > 0) {
                lines.set(i, lines.get(i).copy().append(Component.literal(
                        " §7(paying §6" + EstimatedValue.fmt(v.cost / v.gardenXp) + " §7per)")));
            }
        }
        if (!cfg().visitorOfferProfit) return;
        lines.add(Component.empty());
        lines.add(Component.literal("§7Items cost: §6" + EstimatedValue.fmt(v.cost)));
        if (v.copper > 0) {
            lines.add(Component.literal("§7Copper worth: §6" + EstimatedValue.fmt(v.copperValue)
                    + " §8(" + EstimatedValue.fmt(Visitors.copperCoins()) + " each)"));
        }
        if (v.rewardValue > 0) lines.add(Component.literal("§7Reward items: §6" + EstimatedValue.fmt(v.rewardValue)));
        double profit = -v.loss();
        lines.add(Component.literal("§7Profit: " + (profit >= 0 ? "§a+" : "§c-") + EstimatedValue.fmt(Math.abs(profit))));
    }
}
