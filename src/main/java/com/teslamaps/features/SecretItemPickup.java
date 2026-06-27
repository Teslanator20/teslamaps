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
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SoundOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.List;

public class SecretItemPickup {
    private static final List<String> DUNGEON_ITEM_DROPS = List.of(
            "Health Potion VIII Splash Potion", "Healing Potion 8 Splash Potion", "Healing Potion VIII Splash Potion",
            "Healing VIII Splash Potion", "Healing 8 Splash Potion",
            "Decoy", "Inflatable Jerry", "Spirit Leap", "Trap", "Training Weights", "Defuse Kit",
            "Dungeon Chest Key", "Treasure Talisman", "Revive Stone", "Architect's First Draft",
            "Secret Dye", "Candycomb");

    private static long lastPlayed = 0L;

    public static void onTakeItem(int itemId) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.secretSound || !c.secretItemPickupSound) return;
        if (!DungeonManager.isInDungeon() || DungeonManager.isInBoss()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Entity entity = mc.level.getEntity(itemId);
        if (!(entity instanceof ItemEntity item)) return;
        if (entity.distanceTo(mc.player) > 6) return;

        String name = item.getItem().getHoverName().getString();
        boolean match = false;
        for (String drop : DUNGEON_ITEM_DROPS) {
            if (name.toLowerCase().contains(drop.toLowerCase())) { match = true; break; }
        }
        if (!match) return;

        long now = System.currentTimeMillis();
        if (now - lastPlayed <= 50) return;
        lastPlayed = now;

        LoudSound.play(SoundOptions.resolve(c.secretSoundType), c.secretSoundVolume, c.secretSoundPitch);
    }
}
