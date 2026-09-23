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

    public static boolean isDungeonDrop(String name) {
        String n = name.toLowerCase();
        for (String drop : DUNGEON_ITEM_DROPS) if (n.contains(drop.toLowerCase())) return true;
        return false;
    }

    public static void onTakeItem(int itemId) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.secretSound || !c.secretItemPickupSound) return;
        if (!DungeonManager.isInDungeon() || DungeonManager.isInBossArea()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Entity entity = mc.level.getEntity(itemId);
        if (!(entity instanceof ItemEntity item)) return;
        if (entity.distanceTo(mc.player) > 6) return;

        if (!isDungeonDrop(item.getItem().getHoverName().getString())) return;

        long now = System.currentTimeMillis();
        if (now - lastPlayed <= 50) return;
        lastPlayed = now;

        LoudSound.play(SoundOptions.resolve(c.secretSoundType), c.secretSoundVolume, c.secretSoundPitch);
    }
}
