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
        return com.teslamaps.utils.ItemUtil.skyblockId(stack);
    }
}
