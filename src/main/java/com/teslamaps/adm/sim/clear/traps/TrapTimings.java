package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/TrapTimings.kt

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.adm.sim.pack.PackFiles;
import com.teslamaps.adm.sim.pack.PackSource;
import com.teslamaps.adm.sim.util.KRandom;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The crusher timings of the rooms and the data the other hazards need, from the optional traps.json next to the packs. Without it every
 * crusher uses DEFAULT (5 ticks a block, 30 rest). tripwireHurtRooms are the rooms whose tripwires hit for 15 percent.
 */
public class TrapTimings {
    private final CrusherTiming default_;
    private final Map<String, CrusherTiming> byId;
    public final List<CrusherVariantGroup> variants;
    public final Set<String> tripwireHurtRooms;
    public final Set<String> tripwireClickRooms;
    public final TrapSites sites;

    public TrapTimings(CrusherTiming default_, Map<String, CrusherTiming> byId, List<CrusherVariantGroup> variants, Set<String> tripwireHurtRooms,
                       Set<String> tripwireClickRooms, TrapSites sites) {
        this.default_ = default_;
        this.byId = byId;
        this.variants = variants;
        this.tripwireHurtRooms = tripwireHurtRooms;
        this.tripwireClickRooms = tripwireClickRooms;
        this.sites = sites;
    }

    public TrapTimings(CrusherTiming default_, Map<String, CrusherTiming> byId, List<CrusherVariantGroup> variants, Set<String> tripwireHurtRooms,
                       Set<String> tripwireClickRooms) {
        this(default_, byId, variants, tripwireHurtRooms, tripwireClickRooms, TrapSites.NONE);
    }

    public TrapTimings(CrusherTiming default_, Map<String, CrusherTiming> byId, List<CrusherVariantGroup> variants, Set<String> tripwireHurtRooms) {
        this(default_, byId, variants, tripwireHurtRooms, DEFAULT_TRIPWIRE_CLICK_ROOMS, TrapSites.NONE);
    }

    public TrapTimings(CrusherTiming default_, Map<String, CrusherTiming> byId, List<CrusherVariantGroup> variants) {
        this(default_, byId, variants, DEFAULT_TRIPWIRE_HURT_ROOMS, DEFAULT_TRIPWIRE_CLICK_ROOMS, TrapSites.NONE);
    }

    public TrapTimings(CrusherTiming default_, Map<String, CrusherTiming> byId) {
        this(default_, byId, List.of(), DEFAULT_TRIPWIRE_HURT_ROOMS, DEFAULT_TRIPWIRE_CLICK_ROOMS, TrapSites.NONE);
    }

    public CrusherTiming forCrusher(String id) {
        CrusherTiming t = byId.get(id);
        return t != null ? t : default_;
    }

    /** Whether a tripwire in the room called roomName hits. */
    public boolean tripwiresHurtIn(String roomName) {
        for (String r : tripwireHurtRooms) if (r.equalsIgnoreCase(roomName)) return true;
        return false;
    }

    /** Whether the hooks of a string that does not hit still click. */
    public boolean tripwiresClickIn(String roomName) {
        for (String r : tripwireClickRooms) if (r.equalsIgnoreCase(roomName)) return true;
        return false;
    }

    /** Settles the variants and first-step delays for one run. */
    public CrusherPlan roll(KRandom random) {
        HashMap<String, CrusherTiming> timings = new HashMap<>();
        HashMap<String, Integer> starts = new HashMap<>();
        for (CrusherVariantGroup g : variants) {
            if (g.options.isEmpty()) continue;
            CrusherVariantOption o = g.options.get(random.nextInt(g.options.size()));
            int shared = g.startSpread > 0 ? random.nextInt(g.startSpread + 1) : Crusher.START_LAG;
            for (Map.Entry<String, CrusherTiming> e : o.crushers.entrySet()) {
                timings.put(e.getKey(), e.getValue());
                starts.put(e.getKey(), o.sharedStart ? shared : (g.startSpread > 0 ? random.nextInt(g.startSpread + 1) : Crusher.START_LAG));
            }
        }
        return new CrusherPlan(timings, starts, this);
    }

    public static final CrusherTiming DEFAULT = new CrusherTiming(5, 30);

    /** The rooms in which a real tripwire was ever seen to hit. */
    public static final Set<String> DEFAULT_TRIPWIRE_HURT_ROOMS = new LinkedHashSet<>(List.of(
            "Deathmite", "Cathedral", "Painting", "Chambers", "Pit", "Melon", "Lava Ravine", "Hall", "Golden Oasis", "Altar", "Atlas", "Spider", "Red Green"));

    /** Rooms whose strings click without hitting. */
    public static final Set<String> DEFAULT_TRIPWIRE_CLICK_ROOMS = new LinkedHashSet<>(List.of("Old Trap"));

    public static final TrapTimings NONE = new TrapTimings(DEFAULT, Map.of());

    /** Reads traps.json from a folder; missing or unreadable gives NONE. */
    public static TrapTimings load(Path packsDir) { return packsDir == null ? NONE : load(PackSource.ofDir(packsDir)); }

    /** Reads traps.json as the packs resolved it (jar copy or override folder); missing or unreadable gives NONE. */
    public static TrapTimings load(PackSource source) {
        if (source == null) return NONE;
        Path f = source.file(PackFiles.TRAPS);
        if (f == null) return NONE;
        try { return parse(Files.readString(f)); } catch (Exception e) { return NONE; }
    }

    private static CrusherTiming timing(JsonObject o, CrusherTiming fallback) {
        if (o == null) return fallback;
        JsonElement te = o.get("ticksPerBlock");
        if (te == null) return fallback;
        int tpb = te.getAsInt();
        JsonElement se = o.get("stillTicks");
        int still = se != null ? se.getAsInt() : tpb;
        JsonElement sw = o.get("sweep");
        Integer sweep = sw != null && sw.getAsInt() >= 1 ? sw.getAsInt() : null;
        JsonElement pe = o.get("pincer");
        return tpb >= 1 && still >= 1 ? new CrusherTiming(tpb, still, sweep, pe != null && pe.getAsBoolean()) : fallback;
    }

    public static TrapTimings parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        CrusherTiming def = timing(root.getAsJsonObject("default"), DEFAULT);
        HashMap<String, CrusherTiming> map = new HashMap<>();
        JsonObject cr = root.getAsJsonObject("crushers");
        if (cr != null) for (Map.Entry<String, JsonElement> e : cr.entrySet()) if (e.getValue().isJsonObject()) map.put(e.getKey(), timing(e.getValue().getAsJsonObject(), def));
        List<CrusherVariantGroup> groups = new ArrayList<>();
        var va = root.getAsJsonArray("variants");
        if (va != null) for (JsonElement g : va) {
            if (!g.isJsonObject()) continue;
            JsonObject go = g.getAsJsonObject();
            List<CrusherVariantOption> options = new ArrayList<>();
            var oa = go.getAsJsonArray("options");
            if (oa != null) for (JsonElement oe : oa) {
                if (!oe.isJsonObject()) continue;
                JsonObject oo = oe.getAsJsonObject();
                HashMap<String, CrusherTiming> cs = new HashMap<>();
                JsonObject oc = oo.getAsJsonObject("crushers");
                if (oc != null) for (Map.Entry<String, JsonElement> e : oc.entrySet()) if (e.getValue().isJsonObject()) cs.put(e.getKey(), timing(e.getValue().getAsJsonObject(), def));
                if (cs.isEmpty()) continue;
                JsonElement ss = oo.get("sharedStart");
                options.add(new CrusherVariantOption(cs, ss != null && ss.getAsBoolean()));
            }
            if (options.isEmpty()) continue;
            JsonElement sp = go.get("startSpread");
            groups.add(new CrusherVariantGroup(options, Math.max(sp != null ? sp.getAsInt() : 0, 0)));
        }
        Set<String> hurt = DEFAULT_TRIPWIRE_HURT_ROOMS;
        var ha = root.getAsJsonArray("tripwireHurtRooms");
        if (ha != null) { hurt = new LinkedHashSet<>(); for (JsonElement e : ha) hurt.add(e.getAsString()); }
        Set<String> click = DEFAULT_TRIPWIRE_CLICK_ROOMS;
        var ca = root.getAsJsonArray("tripwireClickRooms");
        if (ca != null) { click = new LinkedHashSet<>(); for (JsonElement e : ca) click.add(e.getAsString()); }
        return new TrapTimings(def, map, groups, hurt, click, TrapSites.parse(root));
    }
}
