package com.teslamaps.features;

// Concept from SkyblockAddons' TileEntityEnderChestRendererHook
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;

public final class EndChestTint {

    private static boolean active;

    private EndChestTint() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("EndChestTint")
                .tickAlways(() -> active = cfg().endChestTint && cfg().section("The End") && SkyblockUtils.isInArea("The End"))
                .config("The End", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Ender Chests");
                    b.toggle("Color Ender Chests", () -> c.endChestTint, v -> c.endChestTint = v)
                            .desc("Tints ender chests on The End island in a color of your choice.");
                    b.color("  Chest Color", () -> c.endChestColor, v -> c.endChestColor = v);
                });
    }

    /** From EnderChestTintMixin, per chest per frame. */
    public static int tint(ChestRenderState state, int color) {
        if (!active || state.material != ChestRenderState.ChestMaterialType.ENDER_CHEST) return color;
        return TeslaMapsConfig.parseColor(cfg().endChestColor) | 0xFF000000;
    }
}
