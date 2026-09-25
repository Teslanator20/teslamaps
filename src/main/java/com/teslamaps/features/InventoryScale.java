package com.teslamaps.features;

// Ported from Devonian's InventoryScale.kt, through the resizeGui hook like the terminal scale
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/** Own GUI scale while a chest-style container or the inventory is open. */
public final class InventoryScale {

    private static boolean applied;

    private InventoryScale() {}

    /** Scale to force in resizeGui, 0 = auto, -1 = leave the player's. */
    public static int override() {
        return applied ? Math.clamp(TeslaMapsConfig.get().inventoryScale, 0, 5) : -1;
    }

    /** From setScreen TAIL. */
    public static void onScreenSet(Screen screen) {
        boolean want = TeslaMapsConfig.get().inventoryScaleOverride
                && (screen instanceof ContainerScreen || screen instanceof InventoryScreen);
        if (want == applied) return;
        applied = want;
        Minecraft.getInstance().resizeGui();
    }
}
