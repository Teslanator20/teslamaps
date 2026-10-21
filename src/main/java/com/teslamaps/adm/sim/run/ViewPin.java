// Ported from ADM's p3sim/run/ViewPin.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.SimServer;
import com.teslamaps.adm.sim.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

public final class ViewPin {
    private ViewPin() {}

    public static final int DISTANCE = 6;

    public static final float CHUNKS_PER_TICK = 5.0f;

    public static boolean pacingChunks() {
        return SimServer.server != null && (RunLauncher.pending != null || Runs.current != null);
    }

    private static Integer savedRender = null;
    private static Integer savedSim = null;

    public static Pair<Integer, Integer> saved() {
        Integer r = savedRender;
        if (r == null) return null;
        Integer s = savedSim;
        if (s == null) return null;
        return Pair.of(r, s);
    }

    public static void pin() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> Ec.safely("ads view pin", () -> {
            if (savedRender == null) {
                savedRender = mc.options.renderDistance().get(); savedSim = mc.options.simulationDistance().get();
            }
            mc.options.renderDistance().set(DISTANCE); mc.options.simulationDistance().set(DISTANCE);
        }));
        MinecraftServer s = SimServer.server;
        if (s != null) {
            if (s.isSameThread()) { s.getPlayerList().setViewDistance(DISTANCE); s.getPlayerList().setSimulationDistance(DISTANCE); }
        }
    }

    public static void release() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> Ec.safely("ads view release", () -> {
            Integer r = savedRender; Integer s = savedSim;
            savedRender = null; savedSim = null;
            if (r != null) mc.options.renderDistance().set(r);
            if (s != null) mc.options.simulationDistance().set(s);
        }));
    }
}
