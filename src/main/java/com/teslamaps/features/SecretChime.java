package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SoundOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

public class SecretChime {
    private static DungeonRoom lastRoom = null;
    private static int count = 0;
    private static long lastExtra = 0;

    public static void onSecretFound() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.secretChime) return;

        DungeonRoom room = DungeonManager.isInDungeon() ? DungeonManager.getCurrentRoom() : null;
        if (room != lastRoom) { lastRoom = room; count = 0; }
        count++;

        int total = room != null ? room.getSecrets() : 0;
        float pitch = total > 0
                ? 0.5f + 1.5f * Math.min(1f, (float) count / total)
                : Math.min(2.0f, 0.5f + 0.15f * (count - 1));

        LoudSound.play(SoundOptions.resolve(c.secretChimeSound), c.secretChimeVolume, pitch);
    }

    // SkyHanni DungeonSecretChime: bat death / secret item pickup also chime
    private static void extra() {
        long now = System.currentTimeMillis();
        if (now - lastExtra < 300) return;
        lastExtra = now;
        onSecretFound();
    }

    private static boolean inClear() {
        return DungeonManager.isInDungeon() && !DungeonManager.isInBossArea();
    }

    /** Main thread. True = cancel the sound. */
    public static boolean onSoundPacket(ClientboundSoundPacket packet) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.secretMuteChest && !c.secretMuteLever && !(c.secretChime && c.secretChimeBat)) return false;
        if (!c.section("Sounds") || !DungeonManager.isInDungeon()) return false;
        SoundEvent s = packet.getSound().value();
        float vol = packet.getVolume(), pitch = packet.getPitch();
        if (c.secretChime && c.secretChimeBat && s == SoundEvents.BAT_DEATH && vol == 0.1f && inClear()) extra();
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

    /** Main thread, before the item entity is removed. */
    public static void onTakeItem(int itemId, int playerId) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.secretChime || !c.secretChimeItem || !c.section("Sounds") || !inClear()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || playerId != mc.player.getId()) return;
        Entity e = mc.level.getEntity(itemId);
        if (e instanceof ItemEntity item && SecretItemPickup.isDungeonDrop(item.getItem().getHoverName().getString())) extra();
    }

    public static void reset() {
        lastRoom = null;
        count = 0;
    }
}
