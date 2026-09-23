package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ServerTickTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;

import java.util.Set;

// Ported from Devonian's CustomHypeSound
public final class HypeSound {

    private static final Set<String> WITHER_BLADES = Set.of("HYPERION", "VALKYRIE", "SCYLLA", "ASTRAEA");
    private static final long WINDOW_TICKS = 3;

    private static volatile long lastClick = -1;

    private HypeSound() {}

    /** Main thread, own ServerboundUseItemPacket. */
    public static void onUseItem(InteractionHand hand) {
        if (!TeslaMapsConfig.get().customHypeSound) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (WITHER_BLADES.contains(ItemUtil.skyblockId(player.getItemInHand(hand)))) lastClick = ServerTickTimer.now();
    }

    /** Netty thread. True = cancel the packet sound. */
    public static boolean onSound(SoundEvent sound, float vol, float pitch) {
        long click = lastClick;
        if (click < 0) return false;
        if (ServerTickTimer.now() - click > WINDOW_TICKS) {
            lastClick = -1;
            return false;
        }
        if (sound == SoundEvents.ZOMBIE_VILLAGER_CURE && vol == 1f && Math.abs(pitch - 0.6984127f) < 0.001f) return true;
        if (sound != SoundEvents.GENERIC_EXPLODE.value() || vol != 1f || pitch != 1f) return false;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        com.teslamaps.utils.LoudSound.play(com.teslamaps.utils.SoundOptions.resolve(c.customHypeSoundType),
                c.customHypeSoundVolume, c.customHypeSoundPitch);
        return true;
    }
}
