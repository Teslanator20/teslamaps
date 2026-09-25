package com.teslamaps.features;

// Ported from SkyHanni's PageScrolling.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.Set;

public class PageScroll {

    private static final Set<String> FORWARD = Set.of(
            "Next Page", "Scroll Up", "Levels 26 - 50", "Next Page →", "Scroll Right");
    private static final Set<String> BACKWARD = Set.of(
            "Previous Page", "Scroll Down", "Levels 1 - 25", "← Previous Page", "Scroll Left");

    private static int lastContainer = -1;
    private static int lastState = -1;
    private static long lastClick = 0;

    public static boolean onScroll(AbstractContainerScreen<?> cs, Slot hovered, double scrollY) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.pageKeybinds || !c.pageScrollWheel || scrollY == 0) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return false;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (title.equals("Chest") || title.equals("Large Chest")) return false;
        if (hovered != null && hovered.hasItem() && !mc.hasShiftDown()) return false;

        var menu = cs.getMenu();
        long now = System.currentTimeMillis();
        boolean fresh = menu.containerId != lastContainer || menu.getStateId() != lastState;
        if (!fresh && now - lastClick < 1000) return true;

        Set<String> want = (scrollY > 0) != c.pageScrollInvert ? FORWARD : BACKWARD;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory || !slot.hasItem()) continue;
            if (!want.contains(StringUtil.stripTrim(slot.getItem().getHoverName().getString()))) continue;
            mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
            lastContainer = menu.containerId;
            lastState = menu.getStateId();
            lastClick = now;
            return true;
        }
        return false;
    }
}
