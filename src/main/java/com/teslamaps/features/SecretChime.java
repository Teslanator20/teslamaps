package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SoundOptions;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public class SecretChime {
    private static DungeonRoom lastRoom = null;
    private static int count = 0;

    public static boolean chimeMode(TeslaMapsConfig c) {
        return "Rising Chime".equals(c.secretSoundMode);
    }

    public static void playSecret() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (chimeMode(c)) chime(c);
        else LoudSound.play(SoundOptions.resolve(c.secretSoundType), c.secretSoundVolume, c.secretSoundPitch);
    }

    private static void chime(TeslaMapsConfig c) {
        DungeonRoom room = DungeonManager.isInDungeon() ? DungeonManager.getCurrentRoom() : null;
        if (room != lastRoom) { lastRoom = room; count = 0; }
        count++;

        int total = room != null ? room.getSecrets() : 0;
        float pitch = total > 0
                ? 0.5f + 1.5f * Math.min(1f, (float) count / total)
                : Math.min(2.0f, 0.5f + 0.15f * (count - 1));

        LoudSound.play(SoundOptions.resolve(c.secretChimeSound), c.secretChimeVolume, pitch);
    }

    /** Main thread. True = cancel the sound. */
    public static boolean onSoundPacket(ClientboundSoundPacket packet) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.secretMuteChest && !c.secretMuteLever) return false;
        if (!c.section("Sounds") || !DungeonManager.isInDungeon()) return false;
        SoundEvent s = packet.getSound().value();
        float vol = packet.getVolume(), pitch = packet.getPitch();
        if (c.secretMuteChest) {
            if (s == SoundEvents.CHEST_OPEN && vol == 0.5f) return true;
            if (s == SoundEvents.NOTE_BLOCK_HARP.value() && vol == 1f && (pitch == 0.7936508f || pitch == 0.8888889f
                    || pitch == 1f || pitch == 1.0952381f || pitch == 1.1904762f)) return true;
        }
        if (c.secretMuteLever) {
            if (s == SoundEvents.ANVIL_BREAK && vol == 1f && pitch == 1.6984127f) return true;
            if (s == SoundEvents.LEVER_CLICK && (vol == 1f || vol == 2f) && pitch == 0.4920635f) return true;
        }
        return false;
    }

    public static void reset() {
        lastRoom = null;
        count = 0;
    }
}
