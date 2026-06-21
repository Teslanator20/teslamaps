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
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class LastBreathSound {

    private static int ticks = 0;

    public static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (!c.lastBreathSound || mc.player == null) { ticks = 0; return; }

        if (!isPullingLastBreath(mc)) { ticks = 0; return; }

        ticks++;
        float pitch = (float) (0.5 + Math.min(ticks, 20) / 20.0 * 1.5); // rescale 0..20 ticks -> 0.5..2.0
        var sound = (ticks >= c.lastBreathThreshold) ? SoundEvents.ARROW_HIT_PLAYER : SoundEvents.NOTE_BLOCK_PLING.value();
        mc.player.playSound(sound, c.lastBreathVolume, pitch);
    }

    private static boolean isPullingLastBreath(Minecraft mc) {
        if (!mc.player.isUsingItem()) return false;
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty() || mc.player.getUseItem() != held) return false;
        String id = skyblockId(held);
        return "LAST_BREATH".equals(id) || "STARRED_LAST_BREATH".equals(id);
    }

    private static String skyblockId(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d == null ? "" : d.copyTag().getString("id").orElse("");
    }
}
