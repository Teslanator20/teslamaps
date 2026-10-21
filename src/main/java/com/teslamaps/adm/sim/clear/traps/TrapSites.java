package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/TrapSites.kt

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The per-room hazard data of traps.json beyond the crusher paces: falling floors, arrow banks, flamethrowers (canonical, y absolute). */
public class TrapSites {
    public final List<FallRule> falls;
    public final List<ArrowRule> arrows;
    public final List<FlameRule> flames;
    public final FlameStream flameStream;
    public final ArrowFlight arrowFlight;

    public TrapSites(List<FallRule> falls, List<ArrowRule> arrows, List<FlameRule> flames, FlameStream flameStream, ArrowFlight arrowFlight) {
        this.falls = falls;
        this.arrows = arrows;
        this.flames = flames;
        this.flameStream = flameStream;
        this.arrowFlight = arrowFlight;
    }

    public TrapSites(List<FallRule> falls, List<ArrowRule> arrows, List<FlameRule> flames, FlameStream flameStream) { this(falls, arrows, flames, flameStream, new ArrowFlight()); }

    public TrapSites(List<FallRule> falls, List<ArrowRule> arrows, List<FlameRule> flames) { this(falls, arrows, flames, new FlameStream(), new ArrowFlight()); }

    public TrapSites(List<FallRule> falls, List<ArrowRule> arrows) { this(falls, arrows, List.of(), new FlameStream(), new ArrowFlight()); }

    public TrapSites(List<FallRule> falls) { this(falls, List.of(), List.of(), new FlameStream(), new ArrowFlight()); }

    public static final TrapSites NONE = new TrapSites(List.of());

    private static int[] ints(JsonArray a) {
        if (a == null) return null;
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsInt();
        return out;
    }

    private static List<Emitter> emitters(JsonArray a) {
        List<Emitter> out = new ArrayList<>();
        if (a == null) return out;
        for (JsonElement e : a) {
            if (!e.isJsonArray()) continue;
            JsonArray t = e.getAsJsonArray();
            if (t.size() != 4) continue;
            String f = t.get(3).getAsString();
            if (!FACINGS.contains(f)) continue;
            out.add(new Emitter(t.get(0).getAsInt(), t.get(1).getAsInt(), t.get(2).getAsInt(), f));
        }
        return out;
    }

    private static final Set<String> FACINGS = Set.of("north", "south", "east", "west", "up", "down");

    public static TrapSites parse(JsonObject root) {
        List<FallRule> falls = new ArrayList<>();
        JsonArray fa = root.getAsJsonArray("falls");
        if (fa != null) for (JsonElement e : fa) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            JsonElement de = o.get("design");
            if (de == null) continue;
            String design = de.getAsString();
            JsonElement te = o.get("trigger");
            if (te == null) continue;
            String trigger = te.getAsString();
            if (!trigger.equals("stand") && !trigger.equals("string")) continue;
            int[] hook = ints(o.getAsJsonArray("hook"));
            if (hook != null && hook.length != 3) hook = null;
            if (trigger.equals("string") && hook == null) continue;
            List<int[]> also = new ArrayList<>();
            JsonArray aa = o.getAsJsonArray("also");
            if (aa != null) for (JsonElement b : aa) {
                int[] box = b.isJsonArray() ? ints(b.getAsJsonArray()) : null;
                if (box != null && box.length == 6) also.add(box);
            }
            int[] cluster = ints(o.getAsJsonArray("cluster"));
            if (cluster != null && cluster.length != 3) cluster = null;
            falls.add(new FallRule(design, trigger, hook, cluster, also));
        }
        List<ArrowRule> arrows = new ArrayList<>();
        JsonArray ar = root.getAsJsonArray("arrows");
        if (ar != null) for (JsonElement e : ar) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            JsonElement de = o.get("design");
            if (de == null) continue;
            String design = de.getAsString();
            JsonElement pe = o.get("period");
            if (pe == null) continue;
            int period = pe.getAsInt();
            if (period < 1) continue;
            List<Emitter> ds = emitters(o.getAsJsonArray("dispensers"));
            if (ds.isEmpty()) continue;
            JsonElement pd = o.get("perDispenser");
            arrows.add(new ArrowRule(design, period, Math.clamp(pd != null ? pd.getAsInt() : 1, 1, 8), ds));
        }
        List<FlameRule> flames = new ArrayList<>();
        JsonArray fl = root.getAsJsonArray("flames");
        if (fl != null) for (JsonElement e : fl) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            JsonElement de = o.get("design");
            if (de == null) continue;
            String design = de.getAsString();
            List<Emitter> ems = emitters(o.getAsJsonArray("emitters"));
            if (ems.isEmpty()) continue;
            int[] pb = ints(o.getAsJsonArray("perBeat"));
            if (pb == null || !(pb.length == 2 && pb[0] >= 1 && pb[1] >= pb[0])) pb = new int[]{1, 1};
            int[] region = ints(o.getAsJsonArray("region"));
            if (region != null && region.length != 6) region = null;
            JsonElement pr = o.get("pickRange");
            flames.add(new FlameRule(design, ems, region, pb[0], pb[1], pr != null ? pr.getAsDouble() : null));
        }
        JsonObject fs = root.getAsJsonObject("flameStream");
        FlameStream stream;
        if (fs == null) stream = new FlameStream();
        else {
            JsonElement x;
            int period = (x = fs.get("period")) != null && x.getAsInt() >= 1 ? x.getAsInt() : 8;
            double reach = (x = fs.get("reach")) != null ? x.getAsDouble() : 6.0;
            double halfWidth = (x = fs.get("halfWidth")) != null ? x.getAsDouble() : 1.0;
            double height = (x = fs.get("height")) != null ? x.getAsDouble() : 1.4;
            int liveTicks = (x = fs.get("liveTicks")) != null && x.getAsInt() >= 1 ? x.getAsInt() : 5;
            double range = (x = fs.get("range")) != null ? x.getAsDouble() : 16.0;
            stream = new FlameStream(period, reach, halfWidth, height, liveTicks, range);
        }
        JsonObject af = root.getAsJsonObject("arrowFlight");
        ArrowFlight flight;
        if (af == null) flight = new ArrowFlight();
        else {
            JsonElement x;
            float speed = (x = af.get("speed")) != null ? x.getAsFloat() : 1.5f;
            float inaccuracy = (x = af.get("inaccuracy")) != null ? x.getAsFloat() : 6.0f;
            double range = (x = af.get("range")) != null ? x.getAsDouble() : 48.0;
            int maxTicks = (x = af.get("maxTicks")) != null && x.getAsInt() >= 1 ? x.getAsInt() : 60;
            flight = new ArrowFlight(speed, inaccuracy, range, maxTicks);
        }
        return new TrapSites(falls, arrows, flames, stream, flight);
    }
}
