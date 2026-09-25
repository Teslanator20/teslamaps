package com.teslamaps.features.customize;

// After SkyOcean's modifyIndicator (ItemModifier.kt)
import com.teslamaps.config.TeslaMapsConfig;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Marks items whose name, dye, trim or model was changed with /tmap customize. */
public final class ModifyIndicator {

    public static final String[] MODES = {"Off", "Prefix", "Suffix", "Lore"};
    private static final Component ICON = Component.literal("✎").withStyle(s -> s.withColor(0x55FFFF));
    private static final Component LORE = Component.literal("Modified by TeslaMaps").withStyle(s -> s.withColor(0x555555));

    private ModifyIndicator() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            String mode = TeslaMapsConfig.get().modifyIndicator;
            if (mode == null || "Off".equals(mode) || lines.isEmpty() || !Customize.active() || !modified(stack)) return;
            switch (mode) {
                case "Prefix" -> lines.set(0, Component.empty().append(ICON).append(" ").append(lines.get(0)));
                case "Suffix" -> lines.set(0, Component.empty().append(lines.get(0)).append(" ").append(ICON));
                case "Lore" -> lines.add(1, LORE);
                default -> {}
            }
        });
    }

    private static boolean modified(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String key = Customize.uuid(stack);
        if (key == null || key.isEmpty()) return false;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.customItemNames.containsKey(key)
                || c.customArmorTrims.containsKey(key)
                || c.customDyeColors.containsKey(key)
                || c.customAnimatedDyes.containsKey(key)
                || c.customHelmetTextures.containsKey(key)
                || c.customAnimatedHelmetTextures.containsKey(key)
                || c.customGlint.containsKey(key)
                || c.customGlintColor.containsKey(key)
                || c.customItemModel.containsKey(key)
                || c.customArmorModel.containsKey(key);
    }
}
