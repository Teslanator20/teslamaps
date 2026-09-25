package com.teslamaps.features;

// Ported from SkyHanni's NoBreak.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

public final class NoBreak {

    private static Feature feature;

    private NoBreak() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("NoBreak").section("Garden").when(() -> cfg().noBreakRod || cfg().noBreakSprayonator)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("No Crop Break With Rod", () -> c.noBreakRod, v -> c.noBreakRod = v)
                            .desc("Left clicks on crops do nothing while you hold a fishing rod.")
                            .tags("no break", "rod", "fishing", "crop");
                    b.toggle("  With Sprayonator Too", () -> c.noBreakSprayonator, v -> c.noBreakSprayonator = v)
                            .desc("Same for the Sprayonator, works on its own as well.");
                });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
            if (!level.isClientSide() || feature == null || !feature.active() || !SkyblockUtils.isInGarden()) return InteractionResult.PASS;
            ItemStack held = player.getMainHandItem();
            boolean block = (cfg().noBreakRod && held.getItem() instanceof FishingRodItem)
                    || (cfg().noBreakSprayonator && sprayonator(held));
            if (block && FarmSpeed.isCrop(level.getBlockState(pos))) return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });
    }

    private static boolean sprayonator(ItemStack st) {
        if (st.isEmpty()) return false;
        return ItemUtil.skyblockId(st).contains("SPRAYONATOR")
                || st.getHoverName().getString().contains("Sprayonator");
    }
}
