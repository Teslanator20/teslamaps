package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Drops mouse sensitivity while a farming tool or the Squeaky Mousemat is held,
 * so a straight lane stays straight.
 * <p>
 * The player's own value is stored the moment it is overridden and put back as
 * soon as the tool is away, the garden is left, or the world unloads — being
 * left stuck on zero sensitivity would be far worse than the feature is useful.
 */
public class FarmingSensitivity {

    private static final String MOUSEMAT = "squeaky mousemat";

    private static boolean applied = false;
    private static double savedSensitivity = 0.5;
    private static boolean active = false;
    private static boolean pendingChecked = false;

    /** Quitting while the override was live leaves the low value in options.txt — put it back. */
    private static void restorePending() {
        if (pendingChecked) return;
        pendingChecked = true;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (config.sensitivityBackup < 0) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.sensitivity().set(config.sensitivityBackup);
        mc.options.save();
        config.sensitivityBackup = -1;
        TeslaMapsConfig.save();
    }

    /**
     * True while looking around should be suppressed entirely. The vanilla
     * option bottoms out at {@code (0 * 0.6 + 0.2)^3}, so zero is never really
     * zero without stopping the turn itself.
     */
    public static boolean blocksTurning() {
        return active && TeslaMapsConfig.get().farmingSensitivityValue <= 0.001f;
    }

    public static void tick() {
        restorePending();
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig config = TeslaMapsConfig.get();

        if (!config.farmingSensitivity || mc.player == null || !SkyblockUtils.isInGarden()) {
            restore();
            return;
        }

        if (holdingFarmingItem(mc.player.getMainHandItem())) {
            active = true;
            apply(config.farmingSensitivityValue);
        } else {
            restore();
        }
    }

    private static boolean holdingFarmingItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        String name = stack.getHoverName().getString().toLowerCase();
        if (name.contains(MOUSEMAT)) return true;

        // Explicitly not the vacuum: aiming at pests needs the normal sensitivity.
        if (name.contains("vacuum")) return false;

        String id = ItemUtil.skyblockId(stack);
        if (id != null) {
            String lower = id.toLowerCase();
            if (lower.contains("vacuum")) return false;
            // Skyblock farming tools all carry these in their id.
            if (lower.contains("hoe") || lower.contains("dicer") || lower.contains("axe")
                    || lower.contains("chopper")) {
                return true;
            }
        }
        return name.contains("hoe") || name.contains("dicer") || name.contains("chopper");
    }

    private static void apply(double sensitivity) {
        if (applied) return;
        Minecraft mc = Minecraft.getInstance();
        savedSensitivity = mc.options.sensitivity().get();
        mc.options.sensitivity().set(sensitivity);
        applied = true;
        TeslaMapsConfig.get().sensitivityBackup = savedSensitivity;
        TeslaMapsConfig.save();
    }

    /** Puts the player's own sensitivity back. Safe to call at any time. */
    public static void restore() {
        restorePending();
        active = false;
        if (!applied) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.sensitivity().set(savedSensitivity);
        mc.options.save();
        applied = false;
        TeslaMapsConfig.get().sensitivityBackup = -1;
        TeslaMapsConfig.save();
    }
}
