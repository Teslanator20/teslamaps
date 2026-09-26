package com.teslamaps.features;

// Conditions ported from Skysoft's AutoSprint.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;

public final class AutoSprint {

    private static boolean allowed = true;
    private static boolean wasActive;

    private AutoSprint() {}

    public static boolean allowed() { return allowed; }

    public static void register() {
        Feature.of("AutoSprint").section("Auto").tick(AutoSprint::tick);
    }

    private static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (!c.autoSprint && !wasActive) return;
        boolean ok = true;
        if (!c.autoSprintGarden && SkyblockUtils.isInGarden()) ok = false;
        else if (c.autoSprintFarmTool && FarmingSensitivity.isFarmingTool(mc.player.getMainHandItem())) ok = false;
        allowed = ok;
        boolean active = c.autoSprint && ok;
        if (wasActive && !active && !mc.options.keySprint.isDown()) mc.player.setSprinting(false);
        wasActive = active;
    }
}
