package com.teslamaps.features;

// Ported from Skysoft's features/misc/DroppedItemScaling.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.world.item.ItemStack;

public final class DroppedItemScaling {

    private static boolean active;

    private DroppedItemScaling() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DroppedItemScaling")
                .tickAlways(() -> active = cfg().droppedItemScaling && cfg().section("Render") && SkyblockUtils.isInSkyblock())
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Dropped Items");
                    b.toggle("Dropped Item Scaling", () -> c.droppedItemScaling, v -> c.droppedItemScaling = v)
                            .desc("Draws dropped SkyBlock items bigger or smaller by rarity. 100% = vanilla size.");
                    b.slider("  Common %", 25f, 400f, () -> c.droppedScaleCommon, v -> c.droppedScaleCommon = step(v), 0);
                    b.slider("  Uncommon %", 25f, 400f, () -> c.droppedScaleUncommon, v -> c.droppedScaleUncommon = step(v), 0);
                    b.slider("  Rare %", 25f, 400f, () -> c.droppedScaleRare, v -> c.droppedScaleRare = step(v), 0);
                    b.slider("  Epic %", 25f, 400f, () -> c.droppedScaleEpic, v -> c.droppedScaleEpic = step(v), 0);
                    b.slider("  Legendary %", 25f, 400f, () -> c.droppedScaleLegendary, v -> c.droppedScaleLegendary = step(v), 0);
                    b.slider("  Mythic %", 25f, 400f, () -> c.droppedScaleMythic, v -> c.droppedScaleMythic = step(v), 0);
                    b.slider("  Divine %", 25f, 400f, () -> c.droppedScaleDivine, v -> c.droppedScaleDivine = step(v), 0);
                    b.slider("  Special %", 25f, 400f, () -> c.droppedScaleSpecial, v -> c.droppedScaleSpecial = step(v), 0);
                });
    }

    private static float step(float v) {
        return Math.round(v / 5f) * 5f;
    }

    /** From ItemEntityScaleMixin, per dropped item per frame. */
    public static float scaleFor(ItemStack stack) {
        if (!active) return 1f;
        TeslaMapsConfig c = cfg();
        float pct = switch (RarityBackground.rarityRank(stack)) {
            case 0 -> c.droppedScaleCommon;
            case 1 -> c.droppedScaleUncommon;
            case 2 -> c.droppedScaleRare;
            case 3 -> c.droppedScaleEpic;
            case 4 -> c.droppedScaleLegendary;
            case 5 -> c.droppedScaleMythic;
            case 6 -> c.droppedScaleDivine;
            case 7, 8 -> c.droppedScaleSpecial;
            default -> 100f;
        };
        return pct / 100f;
    }
}
