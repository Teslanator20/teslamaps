package com.teslamaps.features.foraging;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Title when the Queen Bee shard refills a hive instantly. */
public class HoneyhiveReminder {

    private static final String QUEEN_BEE = "QUEEN BEE! The Honeyhive instantly refilled with loot!";

    public static void onChatMessage(String text) {
        if (!TeslaMapsConfig.get().honeyhiveQueenBeeTitle) return;
        if (!Foraging.strip(text).trim().equals(QUEEN_BEE)) return;
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.setTimes(0, 40, 5);
        mc.gui.hud.setTitle(Component.literal("§6Honeyhive Instantly Refilled!"));
    }
}
