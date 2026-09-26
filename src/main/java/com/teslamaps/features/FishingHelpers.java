package com.teslamaps.features;

import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;

/** Shared rod / bobber / sub-area checks for fishing features. */
public final class FishingHelpers {

    private static final Set<String> LAVA_RODS = Set.of(
            "STARTER_LAVA_ROD", "INFERNO_ROD", "MAGMA_ROD", "HELLFIRE_ROD", "POLISHED_TOPAZ_ROD");

    private static long lastBobber;

    private FishingHelpers() {}

    public static boolean holdingRod() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getMainHandItem().getItem() instanceof FishingRodItem;
    }

    public static boolean holdingLavaRod() {
        Minecraft mc = Minecraft.getInstance();
        return holdingRod() && LAVA_RODS.contains(ItemUtil.skyblockId(mc.player.getMainHandItem()));
    }

    public static FishingHook bobber() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        FishingHook h = mc.player.fishing;
        return h != null && !h.isRemoved() ? h : null;
    }

    /** Bobber out now or in the last 30 s. */
    public static boolean fishingRecently() {
        if (bobber() != null) lastBobber = System.currentTimeMillis();
        return System.currentTimeMillis() - lastBobber < 30_000;
    }

    /** Scoreboard "⏣ <area>" line, "" when none. */
    public static String subArea() {
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            String s = ScoreboardUtils.cleanLine(line);
            if (s.startsWith("⏣") || s.startsWith("ф")) return s.substring(1).trim();
        }
        return "";
    }
}
