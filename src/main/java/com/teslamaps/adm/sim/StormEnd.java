package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/StormEnd.kt

import java.util.List;
import net.minecraft.world.phys.Vec3;

public class StormEnd extends Fight.Phase {
    public StormEnd() { super("Storm end"); }

    @Override
    public Fight.Start restart() { return Fight.Start.P3; }

    private BossWither body = null;

    @Override
    public void start() {
        Sim.forEachPlayer(p -> {
            Spots.Spot spot = Spots.p3Start(1);
            Sim.tp(p, spot.x, spot.y, spot.z, spot.yaw, spot.pitch);
            SimItems.giveHotbar(p, true);
        });
        Party.standAt(SS_LANDING);
        Party.startLeadIn(LEAD - GoldorPhase.LEAD_IN);
        BossWither b = new BossWither("Storm", DEATH_AT, 1, false);
        body = b;
        StormFx.line(b, "I should have known that I stood no chance.");
        BossBar.show("§c§lStorm", 0.45f);
        new StormCorpse(b).start();
    }

    @Override
    public void tick() {
        Party.tickLeadIn(t);
        if (t == 4) BossBar.progress(0f);
        else if (t == SON) StormFx.line(body, "At least my son died by your hands.");
        else if (t == LEAD - GoldorPhase.LEAD_IN) Fight.begin(new GoldorPhase(1, true));
    }

    public static final int LEAD = 62;
    public static final int SON = 42;
    public static final Vec3 DEATH_AT = new Vec3(44.6, 172.9, 65.2);
    public static final List<Vec3> SS_LANDING = List.of(new Vec3(108.3, 120.0, 94.0), new Vec3(107.4, 120.0, 92.8), new Vec3(108.3, 120.0, 95.4), new Vec3(107.0, 120.0, 94.6));
}
