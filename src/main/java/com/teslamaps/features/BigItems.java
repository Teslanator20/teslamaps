package com.teslamaps.features;

// Concept from SkyblockExtras' "Big Items" / "Larger Heads" / "Scale with Rarity"
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BigItems {

    private BigItems() {}

    public static void register() {
        Feature.of("BigItems").section("Inventory")
                .config("Inventory", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Big Items");
                    b.toggle("Big Items", () -> c.bigItems, v -> c.bigItems = v)
                            .desc("Draws item icons in container slots bigger.");
                    b.slider("  Size", 1f, 1.6f, () -> c.bigItemsScale, v -> c.bigItemsScale = v, 2);
                    b.toggle("  Heads Only", () -> c.bigItemsHeadsOnly, v -> c.bigItemsHeadsOnly = v)
                            .desc("Only player heads get bigger.");
                    b.toggle("  Scale With Rarity", () -> c.bigItemsRarity, v -> c.bigItemsRarity = v)
                            .desc("Rarer items get more of the extra size, commons stay small.");
                });
    }

    /** Slot icon scale, 1 = unchanged. Called per slot from BigItemsMixin. */
    public static float scale(ItemStack stack) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.bigItems || stack.isEmpty() || !c.section("Inventory")) return 1f;
        if (c.bigItemsHeadsOnly && !stack.is(Items.PLAYER_HEAD)) return 1f;
        float extra = c.bigItemsScale - 1f;
        if (extra <= 0f) return 1f;
        if (c.bigItemsRarity) {
            int rank = RarityBackground.rarityRank(stack);
            if (rank < 0) return 1f;
            extra *= (rank + 1) / 9f;
        }
        return 1f + extra;
    }
}
