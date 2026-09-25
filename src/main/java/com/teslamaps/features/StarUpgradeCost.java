package com.teslamaps.features;

// Ported from NEU's ItemPriceInformation (essence upgrade line)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SbRepo;
import com.teslamaps.utils.SkyblockItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Map;

public class StarUpgradeCost {

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature feature = Feature.of("StarUpgradeCost")
                .when(() -> cfg().starUpgradeCost);
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature.active()) onTooltip(stack, lines);
        });
    }

    private static ItemStack lastStack;
    private static int lastKey;
    private static List<Component> lastOut = List.of();

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        if (stack.isEmpty()) return;
        int key = (cfg().starUpgradeItems ? 1 : 0) | (Minecraft.getInstance().hasControlDown() ? 2 : 0) | (SbRepo.loaded() ? 4 : 0);
        if (stack != lastStack || key != lastKey) {
            lastStack = stack;
            lastKey = key;
            List<Component> out = new java.util.ArrayList<>();
            build(stack, out);
            lastOut = out;
        }
        lines.addAll(lastOut);
    }

    private static void build(ItemStack stack, List<Component> lines) {
        String id = ItemUtil.skyblockId(stack);
        if (id.isEmpty()) return;
        SbRepo.load();
        Map<Integer, SbRepo.EssenceTier> tiers = SbRepo.essencePrices(id);
        if (tiers == null || tiers.isEmpty()) return;

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        int stars;
        if (tag.contains("upgrade_level")) stars = tag.getIntOr("upgrade_level", 0);
        else if (tag.contains("dungeon_item_level")) stars = tag.getIntOr("dungeon_item_level", 0);
        else stars = -1;

        String type = tiers.values().iterator().next().essenceType();
        String essence = " " + (type == null ? "" : type) + " Essence";

        if (stars < 0) {
            int dungeonize = SbRepo.dungeonizeCost(id);
            if (dungeonize > 0 && !ItemUtil.loreContains(stack, "DUNGEON ")) {
                lines.add(Component.literal("§e§lDungeonize Cost: §6§l" + String.format("%,d", dungeonize) + essence));
                return;
            }
            stars = 0;
        }

        SbRepo.EssenceTier next = tiers.get(stars + 1);
        if (next == null) return;
        lines.add(Component.literal("§e§lUpgrade to " + starString(stars + 1) + "§e§l: §6§l"
                + String.format("%,d", next.essenceAmount()) + essence));

        if (next.coins() <= 0 && next.items().isEmpty()) return;
        if (!cfg().starUpgradeItems && !Minecraft.getInstance().hasControlDown()) {
            lines.add(Component.literal("§8[CTRL to show required items]"));
            return;
        }
        lines.add(Component.literal("§e§lRequired Items:"));
        if (next.coins() > 0) lines.add(Component.literal("  - §6" + String.format("%,d", next.coins()) + " Coins"));
        for (Map.Entry<String, Integer> item : next.items().entrySet()) {
            String name = SkyblockItems.coloredName(item.getKey());
            lines.add(Component.literal("  - " + (name == null ? "§f" + item.getKey() : name) + " §8x" + item.getValue()));
        }
    }

    private static String starString(int stars) {
        if (stars <= 5) return "§6" + "✪".repeat(stars);
        if (stars <= 10) return "§d" + "✪".repeat(stars - 5) + "§6" + "✪".repeat(10 - stars);
        return "§6✪" + stars;
    }
}
