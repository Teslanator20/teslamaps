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
 * (https://github.com/odtheking/Odin, BSD 3-Clause). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// F7/M7 melody terminal helper: announces the terminal opening and the 25/50/75% progress to party
// chat (driven by the lime-terracotta progress marker). Ported from Odin's MelodyMessage (BSD-3).
public class MelodyMessage {
    private static final String TITLE = "Click the button on time!";

    private static boolean announced = false;
    private static int lastClayRow = -1;

    private static boolean inMelody() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        return TITLE.equals(ChatFormatting.stripFormatting(cs.getTitle().getString()));
    }

    public static void tick() {
        if (!inMelody()) { announced = false; lastClayRow = -1; return; }
        if (!announced) {
            announced = true;
            TeslaMapsConfig c = TeslaMapsConfig.get();
            if (c.melodyMessage && Minecraft.getInstance().getConnection() != null) {
                String text = (c.melodyMessageText == null || c.melodyMessageText.isBlank()) ? "Melody Terminal start!" : c.melodyMessageText;
                Minecraft.getInstance().getConnection().sendCommand("pc " + text);
            }
        }
    }

    // Called from the container-slot packet hook. The lime terracotta marks how far the melody has filled.
    public static void onSlotUpdate(int slot, ItemStack item) {
        if (item == null || item.isEmpty() || item.getItem() != Items.LIME_TERRACOTTA) return;
        if (!TeslaMapsConfig.get().melodyProgress) return;
        if (!inMelody()) return;

        int row = slot / 9;
        if (row == lastClayRow) return;
        lastClayRow = row;
        String pct = switch (row) {
            case 2 -> "Melody 25%";
            case 3 -> "Melody 50%";
            case 4 -> "Melody 75%";
            default -> null;
        };
        if (pct != null && Minecraft.getInstance().getConnection() != null) {
            Minecraft.getInstance().getConnection().sendCommand("pc " + pct);
        }
    }

    public static void reset() { announced = false; lastClayRow = -1; }
}
