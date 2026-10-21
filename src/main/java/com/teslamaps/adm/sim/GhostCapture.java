package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/GhostCapture.kt

import com.teslamaps.adm.sim.util.Pair;
import java.util.Locale;
import java.util.NoSuchElementException;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class GhostCapture {
    private GhostCapture() {}

    private static GhostRecording rec = null;
    private static GoldorPhase phase = null;
    private static boolean wasSwinging = false;
    private static int inCoreFor = 0;

    public static void start(GoldorPhase p) {
        rec = null;
        phase = null;
        if (p.from != 1 || p.practice) return;
        DungeonClass c = P3Sim.myClass();
        if (c == null) return;
        rec = new GhostRecording(c.name(), P3Plan.skillName());
        phase = p;
        inCoreFor = 0;
    }

    public static void drop() { rec = null; phase = null; }

    public static void invalidate(String why) {
        if (rec == null) return;
        drop();
        Sim.note("§7This run won't count as a best (" + why + ").");
    }

    public static void stopped(GoldorPhase p) {
        if (p != phase) return;
        if (p.section >= 5) keep(); else drop();
    }

    public static void event(String type, String a, int k) {
        GoldorPhase p = phase;
        if (p == null) return;
        GhostRecording r = rec;
        if (r != null) r.event(p.n(), type, a, k);
    }

    public static void event(String type, String a) { event(type, a, 0); }

    public static void event(String type) { event(type, "", 0); }

    public static void tick(GoldorPhase p) {
        GhostRecording r = rec;
        if (r == null) return;
        if (p != phase) return;
        ServerPlayer player = Sim.player();
        if (player == null) return;
        if (player.isCreative() || player.isSpectator() || player.getAbilities().flying) { invalidate("creative, spectator or flying"); return; }
        int n = p.n();
        r.section(p.section, n);
        Vec3 pos = player.position();
        r.frame(n, new GhostRun.Frame(new P(pos.x, pos.y, pos.z), player.getYRot(), player.getXRot(), heldKey(SimItems.idOf(player.getMainHandItem()))));
        if (player.swinging && !wasSwinging) r.event(n, "swing");
        wasSwinging = player.swinging;
        if (p.section >= 5) {
            if (GoldorPhase.CORE_BOX.contains(pos)) inCoreFor++;
            if (inCoreFor >= KEEP_IN_CORE || n - r.sectionN[5] >= KEEP_MAX) keep();
        }
    }

    private static void keep() {
        GhostRecording r = rec;
        if (r == null) return;
        rec = null;
        phase = null;
        GhostRun run = r.finish();
        if (run == null) return;
        Ec.safely("p3sim ghost save", () -> {
            Pair<Boolean, GhostRun> res = GhostStore.offer(run);
            boolean saved = res.first;
            GhostRun old = res.second;
            DungeonClass cls = null;
            for (DungeonClass c : Party.CLASSES()) if (c.name().equals(run.clazz)) { cls = c; break; }
            if (cls == null) throw new NoSuchElementException("Collection contains no element matching the predicate.");
            String label = Roles.label(cls);
            if (saved) Sim.note("§dNew best as " + label + "§7 (" + run.skill + "): §f" + secs(run.time) + "§7" + (old != null ? " (was " + secs(old.time) + ")" : "") + ". The " + label + " bot can play it (menu > Plan).");
        });
    }

    private static String secs(int n) { return String.format(Locale.ROOT, "%.2fs", n / 20.0); }

    public static String heldKey(String id) { return id != null && !id.equals("SKYBLOCK_MENU") ? id : ""; }

    private static final int KEEP_IN_CORE = 40;
    private static final int KEEP_MAX = 400;
}
