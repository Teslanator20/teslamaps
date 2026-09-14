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
    /** isInSkyblock() costs a regex plus a scoreboard scan, and the mixins ask per component read. */
    private static boolean onSkyblock = false;
    private static int ticks = 0;

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
        onSkyblock = anyCustomization && SkyblockUtils.isInSkyblock();
    }

    /** True while at least one item anywhere carries a customization. */
    public static boolean active() {
        return anyCustomization && onSkyblock;
    }

    public static boolean hasAnyName() {
        return anyName && onSkyblock;
    }

    public static String uuid(ItemStack stack) {
        return ((CustomizableStack) (Object) stack).teslamaps$uuid();
    }

    /** Drops every customization of one item. */
    public static void reset(String uuid) {
        if (uuid == null || uuid.isEmpty()) return;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        config.customItemNames.remove(uuid);
        config.customArmorTrims.remove(uuid);
        config.customDyeColors.remove(uuid);
        config.customAnimatedDyes.remove(uuid);
        config.customHelmetTextures.remove(uuid);
        config.customAnimatedHelmetTextures.remove(uuid);
        config.customGlint.remove(uuid);
        config.customItemModel.remove(uuid);
        config.customArmorModel.remove(uuid);
        refresh();
    }

    /** Drops every customization of every item. */
    public static void resetAll() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        config.customItemNames.clear();
        config.customArmorTrims.clear();
        config.customDyeColors.clear();
        config.customAnimatedDyes.clear();
        config.customHelmetTextures.clear();
        config.customAnimatedHelmetTextures.clear();
        config.customGlint.clear();
        config.customItemModel.clear();
        config.customArmorModel.clear();
        refresh();
    }

    public static void tick() {
        CustomAnimatedHelmetTextures.tick();
        // nothing here changes inside a second, and the check is not cheap
        if (++ticks % 20 == 0) onSkyblock = anyCustomization && SkyblockUtils.isInSkyblock();
    }

    /** Once per frame, so a dye shared by several pieces only interpolates once. */
    public static void frame() {
        CustomArmorAnimatedDyes.newFrame();
    }
}
