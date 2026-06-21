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
