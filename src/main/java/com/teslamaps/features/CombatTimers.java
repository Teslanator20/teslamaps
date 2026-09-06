package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

public class CombatTimers {

    private static long witherEnd = 0;
    private static long tacticalClick = 0;
    private static long tacticalEnd = 0;

    private static long now() { return System.currentTimeMillis(); }

    public static void onUseItem(ItemStack item) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.witherShieldTimer) {
            var scrolls = ItemUtil.abilityScrolls(item);
            if (scrolls.contains("WITHER_SHIELD_SCROLL")) {
                witherEnd = now() + (scrolls.size() >= 3 ? 100L : 200L) * 50L;
            }
        }
        if (c.tacticalInsertionTimer && ItemUtil.skyblockId(item).equals("TACTICAL_INSERTION")) {
            tacticalClick = now();
        }
    }

    public static void onSound(SoundEvent sound, float pitch) {
        if (!TeslaMapsConfig.get().tacticalInsertionTimer) return;
        if (sound == SoundEvents.FLINTANDSTEEL_USE && Math.abs(pitch - 0.74603176f) < 0.01f
                && tacticalClick != 0 && now() - tacticalClick < 500 && tacticalEnd < now()) {
            tacticalEnd = now() + 3000L;
            tacticalClick = 0;
        }
    }

    public static void tick() {
        if (witherEnd > now()) DungeonTimers.set("witherShield", "§dW-Impact§f: §c" + fmt(witherEnd));
        else DungeonTimers.clear("witherShield");

        if (tacticalEnd > now()) DungeonTimers.set("tactical", "§eTactical§f: §c" + fmt(tacticalEnd));
        else DungeonTimers.clear("tactical");
    }

    private static String fmt(long endMs) {
        return String.format("%.2fs", Math.max(0, endMs - now()) / 1000.0);
    }

    public static void reset() {
        witherEnd = 0; tacticalClick = 0; tacticalEnd = 0;
        DungeonTimers.clear("witherShield");
        DungeonTimers.clear("tactical");
    }
}
