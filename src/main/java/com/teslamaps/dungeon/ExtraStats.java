package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;

// Runs /showextrastats at the end of a run so the extra stats land in chat by themselves.
// Concept from Devonian/Odin.
public class ExtraStats {

    public static void onChatMessage(String msg) {
        if (!TeslaMapsConfig.get().autoExtraStats) return;
        if (!msg.contains("> EXTRA STATS <")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        // fires before AutoRequeue's delayed requeue, so the stats still arrive
        mc.execute(() -> {
            if (mc.getConnection() != null) mc.getConnection().sendCommand("showextrastats");
        });
    }
}
