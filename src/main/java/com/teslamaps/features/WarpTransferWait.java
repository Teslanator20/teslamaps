package com.teslamaps.features;

// SkyHanni TransferCooldown + GanKura WarpCooldownHandler: hold warps back during the 3s transfer cooldown
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class WarpTransferWait {

    private static final long COOLDOWN_MS = 3000L;
    private static final long CONFIRM_TIMEOUT_MS = 3000L;

    private static long cooldownEnd;
    private static boolean announce;
    private static long awaitingSince;
    private static String queued;
    private static Object lastLevel;

    private WarpTransferWait() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean on() {
        TeslaMapsConfig c = cfg();
        return c.warpCooldown && c.warpTransferWait && c.section("Timers");
    }

    public static void register() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(WarpTransferWait::allow);
        Feature.of("WarpTransferWait").section("Timers")
                .when(() -> cfg().warpCooldown && cfg().warpTransferWait)
                .tick(WarpTransferWait::tick)
                .serverChat(WarpTransferWait::onChat);
    }

    private static boolean isWarp(String low) {
        String head = low.split(" ", 2)[0];
        return head.equals("warp") || head.equals("is") || head.equals("hub") || head.equals("warpforge");
    }

    private static boolean allow(String command) {
        if (!on() || !SkyblockUtils.isInSkyblock()) return true;
        String low = command.trim().toLowerCase(Locale.ROOT);
        if (!isWarp(low)) return true;
        long now = System.currentTimeMillis();
        boolean awaiting = awaitingSince > 0 && now - awaitingSince < CONFIRM_TIMEOUT_MS;
        if (awaiting || now < cooldownEnd) {
            String q = command.trim();
            queued = q;
            long left = Math.max(cooldownEnd - now, 0);
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
                        "§7[TeslaMaps] Queued §f/" + q + " §7- transfer cooldown"
                                + (left > 0 ? " §b" + (left / 100) / 10.0 + "s" : "")));
            });
            return false;
        }
        awaitingSince = now;
        return true;
    }

    private static void onChat(String raw) {
        if (awaitingSince == 0) return;
        String t = StringUtil.stripTrim(raw);
        if (t.startsWith("Unknown destination")) {
            awaitingSince = 0;
        } else if (t.startsWith("Warping")) {
            awaitingSince = 0;
            cooldownEnd = System.currentTimeMillis() + COOLDOWN_MS;
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.level != lastLevel) {
            boolean first = lastLevel == null;
            lastLevel = mc.level;
            awaitingSince = 0;
            if (!first) {
                cooldownEnd = now + COOLDOWN_MS;
                announce = true;
            }
        }
        if (awaitingSince > 0 && now - awaitingSince >= CONFIRM_TIMEOUT_MS) awaitingSince = 0;
        if (now < cooldownEnd || awaitingSince > 0) return;
        if (announce) {
            announce = false;
            if (cfg().warpTransferWaitMessage && SkyblockUtils.isInSkyblock() && mc.player != null)
                mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] Player Transfer Cooldown has ended."));
        }
        String q = queued;
        if (q == null) return;
        queued = null;
        if (mc.getConnection() != null) mc.getConnection().sendCommand(q);
    }
}
