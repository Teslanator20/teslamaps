package com.teslamaps.features;

// Ported from SkyHanni's SpringBootsHelper.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;

public class SpringBootsHelper {

    private static int streak = 0;

    /** Sound packet hook; runs on both threads, acts on the main one. */
    public static void onSound(SoundEvent sound) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.springBootsReady) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.player == null) return;
        boolean pling = sound == SoundEvents.NOTE_BLOCK_PLING.value();
        if (!pling && sound != SoundEvents.FIREWORK_ROCKET_LAUNCH && sound != SoundEvents.GENERIC_EAT.value()) return;
        DungeonFloor f = DungeonManager.getCurrentFloor();
        if (f == null || f.getLevel() != 7 || !DungeonManager.isInBossArea()) return;
        if (!"SPRING_BOOTS".equals(ItemUtil.skyblockId(mc.player.getItemBySlot(EquipmentSlot.FEET)))) return;
        if (!pling) { streak = 0; return; }
        if (!mc.player.isShiftKeyDown()) return;
        if (++streak == 5) {
            mc.gui.setTimes(0, 40, 5);
            mc.gui.setTitle(Component.literal("§cSpring Boots ready!"));
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 2f);
        }
    }
}
