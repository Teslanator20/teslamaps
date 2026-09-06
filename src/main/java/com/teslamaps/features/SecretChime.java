package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SoundOptions;

public class SecretChime {
    private static DungeonRoom lastRoom = null;
    private static int count = 0;

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

    public static void reset() {
        lastRoom = null;
        count = 0;
    }
}
