// Ported from ADM's p3sim/clear/bots/Traversals.kt
package com.teslamaps.adm.sim.clear.bots;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.pack.JsonKt;
import com.teslamaps.adm.sim.pack.PackReport;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Recorded single-room walks, an OPTIONAL data pack bots.traversals.json.gz in the ADS packs folder. Missing is normal. */
public class TraversalLibrary implements RoomTraversals {
    private final Map<String, List<Traversal>> byDesign;

    public TraversalLibrary(Map<String, List<Traversal>> byDesign) { this.byDesign = byDesign; }

    public int designs() { return byDesign.size(); }

    public int walks() {
        int n = 0;
        for (List<Traversal> l : byDesign.values()) n += l.size();
        return n;
    }

    public boolean has(String designId) {
        List<Traversal> l = byDesign.get(designId);
        return l != null && !l.isEmpty();
    }

    private static Pt world(Placement placement, Traversal tr, int i) {
        WorldPos it = placement.toWorld((int) tr.x[i], 0, (int) tr.z[i]);
        return new Pt(it.x + 0.5, it.z + 0.5);
    }

    @Override
    public Pair<List<Leg>, Pt> legs(PlacedRoom room, Pt from, int t0, int t1, double bpt, KRandom random) {
        Placement placement = room.placement;
        if (placement == null) return null;
        List<Traversal> list = byDesign.get(room.designId);
        if (list == null) return null;
        if (list.isEmpty()) return null;
        int budget = t1 - t0;
        ArrayList<Traversal> fitting = new ArrayList<>();
        for (Traversal tr : list) {
            double entry = from.dist(world(placement, tr, 0)) / Math.max(bpt, 0.1);
            double room4play = budget - entry;
            if (room4play >= tr.ticks * MIN_FIT) fitting.add(tr);
        }
        if (fitting.isEmpty()) return null;
        ArrayList<Traversal> sorted = new ArrayList<>(fitting);
        sorted.sort(Comparator.comparingDouble(tr -> from.dist(world(placement, tr, 0))));
        List<Traversal> near = sorted.subList(0, Math.min(3, sorted.size()));
        Traversal tr = near.get(random.nextInt(near.size()));
        ArrayList<Leg> legs = new ArrayList<>();
        int t = t0;
        Pt cur = from;
        Pt first = world(placement, tr, 0);
        int entryTicks = (int) Math.ceil(from.dist(first) / Math.max(bpt, 0.1));
        if (entryTicks > 0) {
            legs.add(new Leg(t, t + entryTicks, cur, first, from.dist(first) / entryTicks > BotTuning.HOP_ABOVE_BPT));
            t += entryTicks;
            cur = first;
        }
        int play = budget - entryTicks;
        double fit = Math.min((double) play / Math.max(tr.ticks, 1), MAX_FIT);
        int start = t;
        for (int i = 1; i < tr.dt.length; i++) {
            int at = Math.min((int) (start + tr.dt[i] * fit), t1);
            Pt p = world(placement, tr, i);
            if (at < t) continue;
            if (at > t || !p.equals(cur)) {
                legs.add(new Leg(t, at, cur, p, p.dist(cur) > BotTuning.HOP_BLOCKS * 1.5 && p.dist(cur) / Math.max(1, at - t) > BotTuning.HOP_ABOVE_BPT));
                t = at;
                cur = p;
            }
        }
        if (t < t1) legs.add(new Leg(t, t1, cur, cur));
        return Pair.of(legs, cur);
    }

    public static final String FILE = "bots.traversals.json.gz";
    public static final String FORMAT = "ads-bot-traversals";
    public static final int VERSION = 1;

    /** Walks may be played this much slower than recorded (down to) ... */
    public static final double MIN_FIT = 0.6;
    /** ... and no more than this much slower than recorded. */
    public static final double MAX_FIT = 1.4;

    /** Reads the optional file from [dir]. Null when it is not there or not readable; a bad file says why in [report]. */
    public static TraversalLibrary load(Path dir, PackReport report) {
        Path path = dir.resolve(FILE);
        if (!Files.isRegularFile(path)) return null;
        try {
            JsonObject root;
            try (InputStream in = Files.newInputStream(path)) { root = JsonKt.readGzipJson(in); }
            return fromJson(root, report);
        } catch (Exception e) {
            report.warn("The bot walks " + FILE + " could not be read: " + e.getMessage() + ". The bots use plain routes.");
            return null;
        }
    }

    public static TraversalLibrary load(Path dir) { return load(dir, new PackReport()); }

    public static TraversalLibrary fromJson(JsonObject root, PackReport report) {
        if (!FORMAT.equals(JsonKt.str(root, "format"))) {
            report.warn(FILE + " is not a bot walks file (format " + JsonKt.str(root, "format") + "). The bots use plain routes.");
            return null;
        }
        Integer vv = JsonKt.int_(root, "version");
        int v = vv != null ? vv : 0;
        if (v > VERSION) {
            report.warn(FILE + " is version " + v + " but this game reads version " + VERSION + ". The bots use plain routes.");
            return null;
        }
        HashMap<String, List<Traversal>> out = new HashMap<>();
        JsonObject rooms = JsonKt.obj(root, "rooms");
        if (rooms == null) return new TraversalLibrary(out);
        for (Map.Entry<String, JsonElement> en : rooms.entrySet()) {
            String design = en.getKey();
            JsonArray walks = JsonKt.asArr(en.getValue());
            if (walks == null) continue;
            ArrayList<Traversal> list = new ArrayList<>();
            for (JsonElement w : walks) {
                JsonObject o = w.getAsJsonObject();
                JsonArray pts = JsonKt.arr(o, "pts");
                if (pts == null) continue;
                ArrayList<Integer> dt = new ArrayList<>();
                ArrayList<Double> xs = new ArrayList<>();
                ArrayList<Double> zs = new ArrayList<>();
                for (JsonElement p : pts) {
                    JsonArray a = JsonKt.asArr(p);
                    if (a == null) continue;
                    if (a.size() < 3) continue;
                    Double d0 = JsonKt.asDoubleOrNull(a.get(0));
                    if (d0 == null) continue;
                    dt.add((int) (double) d0);
                    Double d1 = JsonKt.asDoubleOrNull(a.get(1));
                    if (d1 == null) continue;
                    xs.add(d1);
                    Double d2 = JsonKt.asDoubleOrNull(a.get(2));
                    if (d2 == null) continue;
                    zs.add(d2);
                }
                if (xs.size() < 2) continue;
                Integer ticks = JsonKt.int_(o, "ticks");
                int[] dta = new int[dt.size()];
                for (int i = 0; i < dta.length; i++) dta[i] = dt.get(i);
                double[] xa = new double[xs.size()];
                for (int i = 0; i < xa.length; i++) xa[i] = xs.get(i);
                double[] za = new double[zs.size()];
                for (int i = 0; i < za.length; i++) za[i] = zs.get(i);
                list.add(new Traversal(ticks != null ? ticks : dt.get(dt.size() - 1), dta, xa, za));
            }
            if (!list.isEmpty()) out.put(design, list);
        }
        return new TraversalLibrary(out);
    }

    public static TraversalLibrary fromJson(JsonObject root) { return fromJson(root, new PackReport()); }
}
