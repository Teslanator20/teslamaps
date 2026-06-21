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
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.UUID;

public class HideCheapCoins {
    private static final UUID COIN_UUID = UUID.fromString("b330b74f-2e3b-3fb6-9143-a1f0e63fad59");

    public static boolean shouldHide(Entity entity) {
        if (!TeslaMapsConfig.get().hideCheapCoins) return false;
        if (!(entity instanceof ItemEntity item)) return false;
        ItemStack stack = item.getItem();
        if (stack.isEmpty() || stack.getItem() != Items.PLAYER_HEAD) return false;
        ResolvableProfile prof = stack.get(DataComponents.PROFILE);
        if (prof == null) return false;
        return COIN_UUID.equals(prof.partialProfile().id());
    }
}
