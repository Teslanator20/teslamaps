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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class SlotLock {

    private static boolean isPlayerInv(Slot slot) {
        return slot != null && slot.container instanceof Inventory;
    }

    public static boolean isLocked(int containerSlot) {
        return TeslaMapsConfig.get().lockedSlots.contains(containerSlot);
    }

    public static boolean onKey(int key, Slot hovered) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.slotLock || c.slotLockKey < 0 || key != c.slotLockKey) return false;
        if (!isPlayerInv(hovered)) return false;
        int s = hovered.getContainerSlot();
        boolean locked;
        if (c.lockedSlots.remove((Integer) s)) locked = false;
        else { c.lockedSlots.add(s); locked = true; }
        TeslaMapsConfig.save();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, locked ? 1.5f : 0.7f);
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    locked ? "§a[TeslaMaps] §fLocked slot §e" + s : "§7[TeslaMaps] Unlocked slot " + s));
        }
        return true;
    }

    public static boolean blockSlot(Slot slot) {
        return TeslaMapsConfig.get().slotLock && isPlayerInv(slot) && isLocked(slot.getContainerSlot());
    }

    public static boolean blockSelectedDrop() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.slotLock) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && isLocked(mc.player.getInventory().getSelectedSlot());
    }

    public static void render(GuiGraphicsExtractor ctx) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.slotLock || c.lockedSlots.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int left = acc.getX(), top = acc.getY();
        for (Slot slot : cs.getMenu().slots) {
            if (!isPlayerInv(slot) || !isLocked(slot.getContainerSlot())) continue;
            int sx = left + slot.x, sy = top + slot.y;
            ctx.fill(sx, sy, sx + 16, sy + 16, 0x80FF0000);
            int col = TeslaMapsConfig.parseColor(c.colorSlotLock);
            ctx.fill(sx, sy, sx + 16, sy + 1, col);
            ctx.fill(sx, sy + 15, sx + 16, sy + 16, col);
            ctx.fill(sx, sy, sx + 1, sy + 16, col);
            ctx.fill(sx + 15, sy, sx + 16, sy + 16, col);
        }
    }
}
