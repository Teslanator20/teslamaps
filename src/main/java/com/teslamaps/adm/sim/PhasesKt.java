package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/Phases.kt

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class PhasesKt {
    private PhasesKt() {}

    public static Vec3 closest(Vec3 p) {
        List<Vec3> all = new ArrayList<>();
        for (ServerPlayer pl : Sim.players()) all.add(pl.position());
        for (Party.Bot b : Party.bots()) if (b.entity != null) all.add(b.pos);
        Vec3 best = null;
        double bestD = 0;
        for (Vec3 v : all) {
            double d = v.distanceToSqr(p);
            if (best == null || d < bestD) { best = v; bestD = d; }
        }
        return best != null ? best : p;
    }

    public static boolean lookingAt(Vec3 at, double degrees, double range) {
        ServerPlayer p = Sim.player();
        if (p == null) return false;
        Vec3 eye = p.getEyePosition();
        Vec3 to = at.subtract(eye);
        if (to.length() > range) return false;
        Vec3 look = p.getViewVector(1f);
        double cos = look.dot(to.normalize());
        return cos >= Math.cos(Math.toRadians(degrees));
    }
}
