package com.teslamaps.features;

// SkyHanni DungeonCreationCooldown: block /joininstance during the 30s cooldown, chat line when it ends
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class WarpCooldownBlock {

    private static long armed = 0L;

    private WarpCooldownBlock() {}

    public static void register() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(WarpCooldownBlock::allow);
        Feature.of("WarpCooldownEnd").section("Timers")
                .when(() -> TeslaMapsConfig.get().warpCooldown && TeslaMapsConfig.get().warpCooldownEndMessage)
                .tick(WarpCooldownBlock::tick);
    }

    private static boolean allow(String command) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.warpCooldown || !c.warpCooldownBlockJoin || !c.section("Timers")) return true;
        String low = command.trim().toLowerCase(java.util.Locale.ROOT);
        if (!low.startsWith("joininstance") && !low.equals("instancerequeue")) return true;
        long left = TimerTriggers.ownWarpEnd() - System.currentTimeMillis();
        if (left <= 0) return true;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
                    "§c[TeslaMaps] Blocked /" + low + " §7- dungeon cooldown §b"
                            + String.format(java.util.Locale.ROOT, "%.1fs", left / 1000.0)));
        });
        return false;
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        long end = TimerTriggers.ownWarpEnd();
        if (end > now) armed = end;
        if (armed > 0 && now >= armed) {
            armed = 0L;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] Dungeon creation cooldown is over!"));
        }
    }
}
