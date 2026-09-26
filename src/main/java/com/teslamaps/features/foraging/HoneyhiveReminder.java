package com.teslamaps.features.foraging;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Honeyhives refill an hour after looting. Reminds once the cooldown is over and shows a
 * title when the Queen Bee shard refills a hive instantly.
 */
public class HoneyhiveReminder {

    private static final long COOLDOWN_MS = 60 * 60 * 1000L;
    private static final String LOOTED = "You stick your hand into the honeyhive and feel around...";
    private static final String QUEEN_BEE = "QUEEN BEE! The Honeyhive instantly refilled with loot!";

    private static long lastReminder = 0;

    public static void onChatMessage(String text) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String clean = Foraging.strip(text).trim();

        if (clean.equals(LOOTED)) {
            c.honeyhiveReadyAt = System.currentTimeMillis() + COOLDOWN_MS;
            return;
        }
        if (clean.equals(QUEEN_BEE)) {
            if (c.honeyhiveQueenBeeTitle) {
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setTimes(0, 40, 5);
                mc.gui.setTitle(Component.literal("§6Honeyhive Instantly Refilled!"));
            }
            c.honeyhiveReadyAt = System.currentTimeMillis() + COOLDOWN_MS;
        }
    }

    public static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.honeyhiveReminder || c.honeyhiveReadyAt == 0) return;

        long now = System.currentTimeMillis();
        if (now < c.honeyhiveReadyAt) return;
        if (now - lastReminder < 5 * 60 * 1000L) return;

        boolean inCanyon = Foraging.inTorrhus();
        if (!inCanyon && !c.honeyhiveRemindOutside) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        lastReminder = now;
        if (c.honeyhiveNavOffer && c.honeyhiveHelper) {
            if (inCanyon) HoneyhiveHelper.offer("§eHoneyhives are ready to be collected. §aClick to navigate.");
            else mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §eHoneyhives are ready to be collected on the Torrhus Canyon. §aClick to warp.")
                    .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/warp torrhus"))));
            return;
        }
        mc.player.sendSystemMessage(Component.literal(inCanyon
                ? "§a[TeslaMaps] §eHoneyhives are ready to be collected."
                : "§a[TeslaMaps] §eHoneyhives are ready to be collected on the Torrhus Canyon."));
    }
}
