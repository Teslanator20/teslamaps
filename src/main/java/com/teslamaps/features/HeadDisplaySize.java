package com.teslamaps.features;

// Ported from Skysoft's features/inventory/HeadDisplaySize.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2f;

public final class HeadDisplaySize {

    private static Feature feature;

    private HeadDisplaySize() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("HeadDisplaySize").section("Inventory").when(() -> cfg().headDisplaySize)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Head Icons");
                    b.toggle("Head Display Size", () -> c.headDisplaySize, v -> c.headDisplaySize = v)
                            .desc("Resizes player head icons in inventories and the hotbar");
                    b.slider("  Size (%)", 50f, 200f, () -> c.headDisplayPercent, v -> c.headDisplayPercent = v, 0);
                });
    }

    /** From HeadDisplaySizeMixin; the pose is a copy made for this one item. */
    public static Matrix3x2f scale(Matrix3x2f pose, ItemStack stack, int x, int y) {
        if (!cfg().headDisplaySize || stack.getItem() != Items.PLAYER_HEAD || feature == null || !feature.active()) return pose;
        float s = cfg().headDisplayPercent / 100f;
        if (s == 1f) return pose;
        return pose.translate(x + 8f, y + 8f).scale(s).translate(-x - 8f, -y - 8f);
    }
}
