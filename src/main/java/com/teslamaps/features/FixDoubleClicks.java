package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public class FixDoubleClicks {

    private static final Set<String> BLAZE_DAGGERS = Set.of(
            "BURSTFIRE_DAGGER",
            "BURSTMAW_DAGGER",
            "FIREDUST_DAGGER",
            "HEARTFIRE_DAGGER",
            "HEARTMAW_DAGGER",
            "MAWDUST_DAGGER"
    );

    // SkyHanni-REPO constants/Items.json: lava_fishing_rods + water_fishing_rods
    private static final Set<String> FISHING_RODS = Set.of(
            "STARTER_LAVA_ROD",
            "INFERNO_ROD",
            "MAGMA_ROD",
            "HELLFIRE_ROD",
            "POLISHED_TOPAZ_ROD",
            "FISHING_ROD",
            "CHALLENGE_ROD",
            "CHAMP_ROD",
            "LEGEND_ROD",
            "ROD_OF_THE_SEA",
            "GIANT_FISHING_ROD",
            "BINGO_ROD",
            "FISHING_ROD-16"
    );

    /**
     * IMPORTANT: This is intentionally written as a packet cancellation because
     * it is a workaround for a bug that is only fixable by cancelling part of
     * the interaction. Cancelling the entire interaction would prevent players
     * from being able to use these items at all.
     *
     * We believe this is currently safe and the bug has been acknowledged by
     * Hypixel staff but said to be difficult to fix on their end. However, use
     * caution when modifying this code, as improper cancellation of packets
     * could lead to Watchdog bans.
     */
    public static boolean shouldCancelUseOn() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.fixDoubleClicks || !c.section("General")) return false;
        if (!SkyblockUtils.isInSkyblock()) return false;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        ItemStack itemInHand = player.getMainHandItem();
        if (itemInHand.isEmpty()) return false;

        String id = ItemUtil.skyblockId(itemInHand);
        return FISHING_RODS.contains(id) || BLAZE_DAGGERS.contains(id);
    }
}
