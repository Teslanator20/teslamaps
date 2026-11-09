package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.TickScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

// Ported from SkyHanni's AutoJoinSkyblock
/** Auto /skyblock. */
public final class MiscQol {

    private static final long JOIN_COOLDOWN_MS = 30_000;

    private static ClientPacketListener lastConnection;
    private static long lastJoin;

    private MiscQol() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MiscQol.Join").section("Auto").legit().when(() -> cfg().autoJoinSkyblock)
                .tickAlways(MiscQol::tickJoin)
                .config("Auto", b -> {
                    b.label("Hypixel");
                    b.toggle("Auto Join SkyBlock", () -> cfg().autoJoinSkyblock, v -> cfg().autoJoinSkyblock = v).cheat()
                            .desc("Sends /skyblock 1s after joining Hypixel, at most every 30s");
                });
    }

    private static void tickJoin() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener conn = mc.getConnection();
        if (conn == lastConnection) return;
        lastConnection = conn;
        if (conn == null || !SkyblockUtils.isOnHypixel()) return;
        long now = System.currentTimeMillis();
        if (now - lastJoin < JOIN_COOLDOWN_MS) return;
        lastJoin = now;
        TickScheduler.schedule(20, () -> {
            ClientPacketListener c = Minecraft.getInstance().getConnection();
            if (c == null || c != lastConnection || !cfg().autoJoinSkyblock || LegitMode.blocksCheats()) return;
            if (SkyblockUtils.isInSkyblock()) return;
            c.sendCommand("skyblock");
        });
    }
}
