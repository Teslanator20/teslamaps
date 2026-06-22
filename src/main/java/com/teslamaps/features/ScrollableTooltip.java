/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

public class ScrollableTooltip {

    private static int offset = 0;
    private static int lastSlot = -1;

    private static int focusedSlot() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return -1;
        Slot s = ((HandledScreenAccessor) cs).getFocusedSlot();
        return (s == null || s.getItem().isEmpty()) ? -1 : s.index;
    }

    public static boolean onScroll(double scrollY) {
        if (!TeslaMapsConfig.get().scrollableTooltip) return false;
        int slot = focusedSlot();
        if (slot == -1) return false;
        if (slot != lastSlot) { offset = 0; lastSlot = slot; }
        offset += (int) (scrollY * 10);
        if (offset > 0) offset = 0;
        if (offset < -4000) offset = -4000;
        return true;
    }

    public static int applyY(int y) {
        if (!TeslaMapsConfig.get().scrollableTooltip) return y;
        int slot = focusedSlot();
        if (slot != lastSlot) { offset = 0; lastSlot = slot; }
        return y + offset;
    }
}
