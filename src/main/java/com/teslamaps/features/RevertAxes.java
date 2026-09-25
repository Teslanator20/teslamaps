package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.customize.Customize;
import com.teslamaps.injected.CustomizableStack;
import com.teslamaps.registry.Feature;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Ragnarock/Daedalus render as a golden axe, Axe of the Shredded as a diamond axe (1.8 look). */
public final class RevertAxes {

    private static final Identifier GOLDEN_AXE = Identifier.withDefaultNamespace("golden_axe");
    private static final Identifier DIAMOND_AXE = Identifier.withDefaultNamespace("diamond_axe");
    private static Feature feature;

    private RevertAxes() {}

    public static void register() {
        feature = Feature.of("RevertAxes").section("Inventory").when(() -> TeslaMapsConfig.get().revertAxes);
    }

    public static Object model(ItemStack stack, Object original) {
        if (feature == null || !TeslaMapsConfig.get().revertAxes || stack.isEmpty() || !feature.active()) return original;
        CustomizableStack cs = (CustomizableStack) (Object) stack;
        Identifier axe = switch (cs.teslamaps$skyblockId()) {
            case "RAGNAROCK_AXE", "DAEDALUS_AXE" -> GOLDEN_AXE;
            case "AXE_OF_THE_SHREDDED" -> DIAMOND_AXE;
            default -> null;
        };
        if (axe == null) return original;
        if (Customize.active() && TeslaMapsConfig.get().customItemModel.containsKey(cs.teslamaps$uuid())) return original;
        return axe;
    }
}
