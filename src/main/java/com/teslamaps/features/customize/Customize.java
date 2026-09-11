package com.teslamaps.features.customize;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.injected.CustomizableStack;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.world.item.ItemStack;

/**
 * Entry point for the per-item customization from {@code /tmap customize}.
 *
 * <p>The mixins that apply it sit in very hot paths, so every one of them asks here first
 * and the answer is a plain flag that only changes when the screen writes to the config.
 */
public class Customize {
    private static boolean anyCustomization = false;
    private static boolean anyName = false;

    public static void init() {
        CustomHelmetTextures.init();
        CustomAnimatedHelmetTextures.init();
        RepoDyeColors.init();
        refresh();
    }

    /** Called after every write from the customize screen and once on config load. */
    public static void refresh() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        anyName = !config.customItemNames.isEmpty();
        anyCustomization = anyName
                || !config.customArmorTrims.isEmpty()
                || !config.customDyeColors.isEmpty()
                || !config.customAnimatedDyes.isEmpty()
                || !config.customHelmetTextures.isEmpty()
                || !config.customAnimatedHelmetTextures.isEmpty()
                || !config.customGlint.isEmpty()
                || !config.customItemModel.isEmpty()
                || !config.customArmorModel.isEmpty();
    }

    /** True while at least one item anywhere carries a customization. */
    public static boolean active() {
        return anyCustomization && SkyblockUtils.isInSkyblock();
    }

    public static boolean hasAnyName() {
        return anyName && SkyblockUtils.isInSkyblock();
    }

    public static String uuid(ItemStack stack) {
        return ((CustomizableStack) (Object) stack).teslamaps$uuid();
    }

    public static void tick() {
        CustomAnimatedHelmetTextures.tick();
    }

    /** Once per frame, so a dye shared by several pieces only interpolates once. */
    public static void frame() {
        CustomArmorAnimatedDyes.newFrame();
    }
}
