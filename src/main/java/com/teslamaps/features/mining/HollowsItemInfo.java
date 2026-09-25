package com.teslamaps.features.mining;

// After SkyblockExtras' Divan chamber count and Prehistoric Egg "Blocks Walked" tooltip lines
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Locale;

/** Tooltip lines: unlocked Gemstone Chambers on Divan's armor, blocks walked on a Prehistoric Egg. */
public final class HollowsItemInfo {

    private static Feature feature;

    private HollowsItemInfo() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("HollowsItemInfo").section("Mining").when(() -> cfg().hollowsItemInfo)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Hollows Item Info", () -> c.hollowsItemInfo, v -> c.hollowsItemInfo = v)
                            .desc("Extra tooltip lines for Crystal Hollows items")
                            .tags("divan", "gemstone chamber", "prehistoric egg", "blocks walked", "tooltip");
                    b.toggle("  Divan Gemstone Chambers", () -> c.hollowsItemChambers, v -> c.hollowsItemChambers = v)
                            .desc("Unlocked gemstone chambers (x/5) on Divan's armor");
                    b.toggle("  Prehistoric Egg Blocks Walked", () -> c.hollowsItemEggBlocks, v -> c.hollowsItemEggBlocks = v);
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!lines.isEmpty() && feature.active()) tooltip(stack, lines);
        });
    }

    private static void tooltip(ItemStack stack, List<Component> lines) {
        String id = ItemUtil.skyblockId(stack);
        if (id.isEmpty()) return;
        boolean egg = id.equals("PREHISTORIC_EGG");
        boolean divan = id.startsWith("DIVAN_") && (id.endsWith("HELMET") || id.endsWith("CHESTPLATE")
                || id.endsWith("LEGGINGS") || id.endsWith("BOOTS"));
        if (!(egg && cfg().hollowsItemEggBlocks) && !(divan && cfg().hollowsItemChambers)) return;
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return;
        CompoundTag tag = d.copyTag();
        if (egg) {
            int walked = tag.getIntOr("blocks_walked", 0);
            lines.add(Math.min(lines.size(), 1), Component.literal("§dBlocks Walked: §3" + String.format(Locale.ROOT, "%,d", walked)));
            return;
        }
        int unlocked = tag.getCompoundOrEmpty("gems").getListOrEmpty("unlocked_slots").size();
        lines.add(Math.min(lines.size(), 1), Component.literal("§a● §d" + unlocked + "§d/§65 §5Chambers"));
    }
}
