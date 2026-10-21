// Ported from ADM's p3sim/run/LagRuntime.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.pack.Packs;
import com.teslamaps.adm.sim.pack.PackSet;
import com.teslamaps.adm.sim.pack.runstate.RunStatePack;
import java.lang.reflect.Field;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;

public final class LagRuntime {
    private LagRuntime() {}

    public static volatile LagPreset settingPreset = LagPreset.OFF;
    public static volatile LagPreset runPreset = LagPreset.OFF;
    public static volatile LagPreset devPreset = null;
    public static volatile double burstRate = LagBurst.DEFAULT_RATE;

    public static LagPreset effective() {
        if (devPreset != null) return devPreset;
        if (runPreset != LagPreset.OFF) return runPreset;
        return settingPreset;
    }

    public static final LagStats stats = new LagStats();

    private static LagGenerator model = null;
    private static LagPreset modelFor = null;
    private static Object modelPack = null;
    private static int burstLeft = 0;
    private static long lastTickNs = 0L;
    private static Field nextTickField = null;
    private static boolean fieldLooked = false;

    public static boolean canForget() { lookField(); return nextTickField != null; }

    private static void lookField() {
        if (fieldLooked) return;
        fieldLooked = true;
        try {
            Field f = MinecraftServer.class.getDeclaredField("nextTickTimeNanos");
            f.setAccessible(true);
            nextTickField = f;
        } catch (Throwable t) {
            Ec.LOG.warn("[ads] the lag layer cannot reach the server's tick clock (" + t.getClass().getSimpleName() + "); lost ticks will be caught up in full");
            nextTickField = null;
        }
    }

    public static void reset() {
        model = null; modelFor = null; burstLeft = 0; lastTickNs = 0; stats.clear(); devPreset = null; runPreset = LagPreset.OFF;
    }

    private static LagGenerator modelFor(LagPreset p) {
        if (p == LagPreset.OFF) return null;
        PackSet set = Packs.current;
        RunStatePack pack = set != null ? set.runstate : null;
        if (model != null && modelFor == p && modelPack == pack) return model;
        double loss = LagPresets.lossPerMinute(p, pack != null && pack.lag != null ? pack.lag.tiers : null);
        model = new LagGenerator(LagParams.fromPack(pack != null && pack.lag != null ? pack.lag.raw : null), loss);
        modelFor = p; modelPack = pack;
        Ec.LOG.info("[ads] lag layer: " + p.label + ", " + String.format(Locale.ROOT, "%.1f", loss) + " ticks lost a minute, "
                + String.format(Locale.ROOT, "%.2f", model.freezesPerMinute) + " freezes a minute");
        return model;
    }

    public static void afterTick(MinecraftServer server) {
        long now = System.nanoTime();
        stats.record(now);
        LagPreset preset = effective();
        LagGenerator m = modelFor(preset);
        if (m == null) { burstLeft = 0; lastTickNs = now; return; }
        if (burstLeft > 0) {
            burstLeft--;
            double wait = LagBurst.sleepMs((now - lastTickNs) / 1e6, burstRate);
            if (wait >= 1.0) sleep(wait);
            lastTickNs = System.nanoTime();
            return;
        }
        LagEvent ev = m.nextTick();
        if (ev == null) { lastTickNs = now; return; }
        sleep(ev.gapMs - LagGenerator.TICK_MS);
        Integer behind = behindTicks(server);
        if (behind == null) { burstLeft = LagBurst.ticksFor(ev.recoveredTicks, burstRate); lastTickNs = System.nanoTime(); return; }
        int keep = Math.min(ev.recoveredTicks, behind);
        int forget = behind - keep;
        Field f = nextTickField;
        if (forget > 0 && f != null) {
            try {
                f.setLong(server, Math.min(f.getLong(server) + forget * (long) (LagGenerator.TICK_MS * 1e6), Util.getNanos()));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        burstLeft = LagBurst.ticksFor(keep, burstRate);
        lastTickNs = System.nanoTime();
    }

    private static Integer behindTicks(MinecraftServer server) {
        lookField();
        Field f = nextTickField;
        if (f == null) return null;
        long behindNs;
        try {
            behindNs = Util.getNanos() - f.getLong(server);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return behindNs <= 0 ? 0 : (int) (behindNs / (LagGenerator.TICK_MS * 1e6));
    }

    private static void sleep(double ms) {
        if (ms < 1.0) return;
        try {
            Thread.sleep((long) ms, (int) ((ms - (long) ms) * 1e6));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
