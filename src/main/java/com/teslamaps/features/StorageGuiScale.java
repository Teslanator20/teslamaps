package com.teslamaps.features;

// Ported from Bax's StorageGuiScale.java, applied through the resizeGui hook instead of the option
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Own GUI scale while a storage screen is open; held 1.5 s across a page swap, restored on close. */
public final class StorageGuiScale {

    private static final long REOPEN_GRACE_MS = 1500L;

    private static boolean applied;
    private static long expectUntil;

    private StorageGuiScale() {}

    private static boolean enabled() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.storageGuiScaleOverride && c.customStorageOverlay && c.section("Inventory");
    }

    public static void register() {
        com.teslamaps.registry.Feature.of("StorageGuiScale").tickAlways(StorageGuiScale::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(StorageGuiScale::restore));
    }

    /** Scale to force in resizeGui, -1 = leave the player's. */
    public static int override() {
        return applied ? Math.max(1, TeslaMapsConfig.get().storageGuiScale) : -1;
    }

    private static boolean isStorage(Screen s) {
        if (!(s instanceof AbstractContainerScreen<?> cs)) return false;
        String t = StringUtil.strip(cs.getTitle().getString()).trim();
        return t.equals("Storage") || BackpackPreview.keyFromTitle(t) != null;
    }

    /** From setScreen TAIL, the new screen is already in place. */
    public static void onScreenSet(Screen screen) {
        if (isStorage(screen) && enabled()) {
            expectUntil = 0;
            if (!applied) {
                applied = true;
                Minecraft.getInstance().resizeGui();
            }
            return;
        }
        if (!applied) return;
        if (screen == null && System.currentTimeMillis() < expectUntil) return;
        restore();
    }

    /** A click in a storage screen usually swaps the page: Hypixel closes and reopens. */
    public static void onClick(AbstractContainerScreen<?> cs) {
        if (applied && isStorage(cs)) expectUntil = System.currentTimeMillis() + REOPEN_GRACE_MS;
    }

    /** Page commands sent by the storage overlay. */
    public static void expectReopen() {
        if (applied) expectUntil = System.currentTimeMillis() + REOPEN_GRACE_MS;
    }

    private static void tick() {
        if (!applied) return;
        Minecraft mc = Minecraft.getInstance();
        if (isStorage(mc.gui.screen()) && enabled()) return;
        if (mc.gui.screen() == null && System.currentTimeMillis() < expectUntil) return;
        restore();
    }

    private static void restore() {
        if (!applied) return;
        applied = false;
        expectUntil = 0;
        Minecraft.getInstance().resizeGui();
    }
}
