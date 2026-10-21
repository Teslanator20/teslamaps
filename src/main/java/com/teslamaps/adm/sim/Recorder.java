package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/Recorder.kt

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

public final class Recorder {
    private Recorder() {}

    private static final int IN_A_ROW = 50;
    private static int inRow = 0;
    private static BufferedWriter out = null;
    private static File file = null;
    private static final List<String> events = new ArrayList<>();

    public static File dir() { return new File(Ec.configDir().toFile(), "p3sim-runs"); }

    public static void begin(String what) {
        finish();
        if (!P3Sim.record()) {
            inRow = 0;
            return;
        }
        if (inRow >= IN_A_ROW) {
            P3Sim.recordS.set(false);
            inRow = 0;
            Sim.note("§cRecord Runs is off: " + IN_A_ROW + " runs recorded in a row (config/teslamaps/adm/p3sim-runs). Turn it back on for " + IN_A_ROW + " more.");
            return;
        }
        inRow++;
        try {
            dir().mkdirs();
            File f = new File(dir(), "run-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")) + ".jsonl");
            file = f;
            out = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8);
            JsonObject o = new JsonObject();
            o.addProperty("start", what);
            o.addProperty("class", P3Sim.myClass().name());
            o.addProperty("skill", P3Plan.preset().name);
            JsonArray mine = new JsonArray();
            for (String m : P3Plan.mine()) mine.add(m);
            o.add("mine", mine);
            o.addProperty("ping", P3Sim.ping());
            o.addProperty("speed", P3Sim.speed());
            line(o);
        } catch (Throwable ignored) {
        }
    }

    public static void event(String text) {
        if (out != null) events.add(text);
    }

    public static void tick(int serverTick) {
        BufferedWriter w = out;
        if (w == null) return;
        try {
            JsonObject o = new JsonObject();
            o.addProperty("t", serverTick);
            Fight.Phase phase = Fight.phase;
            o.addProperty("phase", phase != null ? phase.name : null);
            o.addProperty("pt", phase != null ? phase.t : null);
            ServerPlayer p = Sim.player();
            if (p != null) {
                JsonArray you = new JsonArray();
                you.add(r(p.getX()));
                you.add(r(p.getY()));
                you.add(r(p.getZ()));
                you.add(r(p.getYRot()));
                you.add(r(p.getXRot()));
                o.add("you", you);
                if (p.containerMenu != p.inventoryMenu) o.addProperty("window", p.containerMenu.getClass().getSimpleName());
                String held = SimItems.idOf(p.getMainHandItem());
                if (held != null) o.addProperty("held", held);
            }
            if (phase instanceof GoldorPhase g) {
                o.addProperty("n", g.n());
                o.addProperty("section", g.section);
                JsonArray left = new JsonArray();
                for (Station s : g.stations) if (s.section == g.section && !s.done) left.add(s.id());
                o.add("left", left);
                Party.record(o);
            }
            if (!events.isEmpty()) {
                JsonArray ev = new JsonArray();
                for (String e : events) ev.add(e);
                o.add("ev", ev);
                events.clear();
            }
            w.write(o.toString());
            w.newLine();
        } catch (Throwable ignored) {
        }
    }

    public static void finish() {
        BufferedWriter w = out;
        if (w == null) return;
        out = null;
        events.clear();
        try { w.close(); } catch (Throwable ignored) {}
        if (file != null) Sim.note("§7Run recorded: §f" + file.getName());
    }

    private static void line(JsonObject o) throws java.io.IOException {
        if (out != null) {
            out.write(o.toString());
            out.newLine();
        }
    }

    public static double r(double d) { return Math.round(d * 100) / 100.0; }
}
