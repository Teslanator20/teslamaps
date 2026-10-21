// Ported from ADM's p3sim/clear/blood/camp/WatcherCamp.kt
package com.teslamaps.adm.sim.clear.blood.camp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.clear.blood.BloodGeometry;
import com.teslamaps.adm.sim.clear.blood.BloodHost;
import com.teslamaps.adm.sim.clear.blood.WatcherStats;
import com.teslamaps.adm.sim.pack.rooms.SkullTexture;
import com.teslamaps.adm.sim.run.MobKilled;
import com.teslamaps.adm.sim.run.RunEvent;
import com.teslamaps.adm.sim.run.api.MobSpec;
import com.teslamaps.adm.sim.run.plugin.BloodCamp;
import com.teslamaps.adm.sim.run.plugin.BloodCampHost;
import com.teslamaps.adm.sim.run.plugin.BloodRoomSpec;
import com.teslamaps.adm.sim.run.plugin.HeadSlot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** The Watcher's camp on the blood room, as the real server plays it. */
public class WatcherCamp implements BloodCamp, CampPort {
    private final BloodCampHost host;
    private CampEngine engine = null;
    private List<WallStation> wall = new ArrayList<>();
    private List<Integer> slots = new ArrayList<>();
    private Vec3 home;
    private String opening = CampLines.INTRO;

    /** The camp tick the opening was said at (-1 not yet). */
    private int openedAt = -1;
    private CampConfig config = new CampConfig();
    /** A replay's recorded lines still to be said. */
    private ArrayDeque<String> replayTexts = null;

    public WatcherCamp(BloodCampHost host) {
        this.host = host;
    }

    @Override public boolean finished() { return engine != null && engine.finished; }

    @Override public void start() {
        buildRoom();
        WallLook look = WallLook.of(textures());
        wall = WallPlan.assign(stands(), host.random());
        Function<List<WallStation>, Replayed> rf = replay;
        Replayed rp = rf != null ? rf.apply(wall) : null;
        // A replay: a Wandering Soul drawn onto a recorded throw trades its class with a head that was not thrown.
        List<WallStation> order = null;
        if (rp != null && rp.order != null) {
            List<WallStation> o = rp.order;
            Set<Integer> thrown = new HashSet<>();
            for (WallStation st : o) thrown.add(st.slot);
            ArrayDeque<WallStation> spares = new ArrayDeque<>();
            for (WallStation st : wall) if (!thrown.contains(st.slot) && st.cls != WallClass.WANDERING_SOUL) spares.add(st);
            Map<Integer, WallStation> swap = new HashMap<>();
            for (WallStation st : o) if (st.cls == WallClass.WANDERING_SOUL) {
                WallStation sp = spares.pollFirst();
                if (sp != null) {
                    swap.put(st.slot, new WallStation(st.slot, st.stand, sp.cls, sp.mobName));
                    swap.put(sp.slot, new WallStation(sp.slot, sp.stand, st.cls, st.mobName));
                }
            }
            List<WallStation> nw = new ArrayList<>(wall.size());
            for (WallStation it : wall) nw.add(swap.getOrDefault(it.slot, it));
            wall = nw;
            order = new ArrayList<>(o.size());
            for (WallStation st : o) {
                WallStation found = null;
                for (WallStation it : wall) if (it.slot == st.slot) { found = it; break; }
                if (found == null) throw new NoSuchElementException("Collection contains no element matching the predicate.");
                order.add(found);
            }
        }
        for (WallStation st : wall) host.setHead(slots.get(st.slot), look.keyFor(st, host.random()));
        if (rp != null) {
            config = new CampConfig(false, CampMech.KILLS_TO_DEFEAT, CampMech.LATER_THROWS, WallPlan.WS_THROW_CHANCE, 0.07, rp.lines);
            replayTexts = new ArrayDeque<>(rp.texts);
        }
        engine = new CampEngine(home, wall, host.random(), this, config, order);
        opening = rp != null && rp.opening != null ? rp.opening : CampLines.opening(host.random());
        int standard = 0, boss = 0;
        for (WallStation st : wall) {
            if (st.cls == WallClass.STANDARD) standard++;
            if (st.cls == WallClass.BOSS) boss++;
        }
        Ec.LOG.info("[ads] the Watcher's camp started: " + standard + " standard, " + boss + " boss heads on " + wall.size() + " stands");
    }

    private List<SkullTexture> textures() {
        var rooms = host.run().packs.rooms;
        return rooms != null && rooms.textures != null ? rooms.textures : List.of();
    }

    @Override public void tick() {
        CampEngine e = engine;
        if (e == null) return;
        Set<String> alive = new HashSet<>();
        for (UUID id : host.mobsAlive()) alive.add(id.toString());
        e.step(host.ticksSinceOpen(), alive);
        if (opening.equals(CampLines.INTRO_OTHER) && openedAt >= 0) {
            int since = host.ticksSinceOpen() - openedAt;
            int i = since / CampLines.INTRO_OTHER_GAP - 1;
            if (since % CampLines.INTRO_OTHER_GAP == 0 && i >= 0 && i < CampLines.INTRO_OTHER_MORE.size()) host.watcherSays(CampLines.INTRO_OTHER_MORE.get(i));
        }
    }

    @Override public void onEvent(RunEvent event) {
        if (event instanceof MobKilled k && engine != null) engine.noteDeath(k.mob.id.toString(), host.ticksSinceOpen());
    }

    // ---- the room

    private List<HeadSlot> sortedHeads() {
        List<HeadSlot> h = new ArrayList<>(host.room().heads());
        h.sort(Comparator.comparingInt(s -> s.index));
        return h;
    }

    private void buildRoom() {
        BloodRoomSpec room = host.room();
        List<Integer> s = new ArrayList<>();
        for (HeadSlot h : sortedHeads()) s.add(h.index);
        slots = s;
        home = new Vec3(room.watcherX(), room.watcherY() + WatcherStats.RISE, room.watcherZ());
    }

    /** A head hangs on the wall as a stand STAND_ABOVE_CELL above its block cell. */
    private List<Vec3> stands() {
        List<Vec3> out = new ArrayList<>();
        for (HeadSlot it : sortedHeads()) out.add(new Vec3(it.x + 0.5, it.y + BloodGeometry.STAND_ABOVE_CELL, it.z + 0.5));
        return out;
    }

    // ---- CampPort

    @Override public String spawn(Landing landing) {
        WallStation st = landing.station;
        if (engine == null) return null;
        Vec3 end = engine.endOf(landing);
        double floor = host.room().watcherY();
        double y = floor + CampMobs.SPAWN_RISE_LOW + host.random().nextDouble() * (CampMobs.SPAWN_RISE_HIGH - CampMobs.SPAWN_RISE_LOW);
        CampMob mob = CampMobs.roll(st, host.random());
        Map<String, String> extras = CampMobs.extras(mob, landing.initial ? "initial" : "later");
        String kind = switch (st.cls) {
            case GIANT -> KIND_GIANT;
            case BOSS -> KIND_BOSS;
            default -> KIND_MOB;
        };
        UUID id = host.spawnMob(new MobSpec(kind, end.x, y, end.z, host.room().roomIndex(), false, false, mob.hp, extras));
        return id != null ? id.toString() : null;
    }

    @Override public void killsCounted(int total) { host.watcherKills(total); }

    @Override public void flyHead(WallStation station, Vec3 end, int ticks) { host.flyHead(slots.get(station.slot), end.x, end.y, end.z, ticks); }

    @Override public void watcherAt(double x, double y, double z, float yaw) { host.moveWatcher(x, y, z, yaw); }

    @Override public void say(CampMech.Line line) {
        if (line == CampMech.Line.INTRO) openedAt = host.ticksSinceOpen();
        String text;
        if (line == CampMech.Line.INTRO) text = opening;
        else {
            text = replayTexts != null ? replayTexts.pollFirst() : null;
            if (text == null) text = CampLines.pick(line, host.random());
            if (text == null) return;
        }
        host.watcherSays(text);
    }

    @Override public void log(String text) { Ec.LOG.info("[ads] " + text); }

    // ---- savepoints

    @Override public JsonObject snapshot() {
        CampEngine e = engine;
        if (e == null) return null;
        JsonObject o = new JsonObject();
        o.addProperty("opening", opening);
        o.addProperty("openedAt", openedAt);
        JsonArray a = new JsonArray();
        for (WallStation st : wall) { JsonArray p = new JsonArray(); p.add(st.cls.name()); p.add(st.mobName); a.add(p); }
        o.add("wall", a);
        o.add("engine", e.snapshot());
        return o;
    }

    @Override public void restore(JsonObject data) {
        buildRoom();
        JsonArray savedArr = data.getAsJsonArray("wall");
        if (savedArr == null) return;
        List<JsonArray> saved = new ArrayList<>();
        for (JsonElement el : savedArr) saved.add(el.getAsJsonArray());
        List<Vec3> stands = stands();
        if (saved.size() != stands.size()) {
            Ec.LOG.warn("[ads] the saved camp had a wall of " + saved.size() + " heads but this room has " + stands.size() + "; the camp starts over");
            start();
            return;
        }
        List<WallStation> nw = new ArrayList<>(stands.size());
        for (int i = 0; i < stands.size(); i++) nw.add(new WallStation(i, stands.get(i), WallClass.valueOf(saved.get(i).get(0).getAsString()), saved.get(i).get(1).getAsString()));
        wall = nw;
        JsonObject state = data.getAsJsonObject("engine");
        if (state == null) return;
        List<WallStation> order = new ArrayList<>();
        for (JsonElement el : state.getAsJsonArray("order")) order.add(wall.get(el.getAsInt()));
        CampEngine e = new CampEngine(home, wall, host.random(), this, config, order);
        if (!e.restore(state)) { start(); return; }
        engine = e;
        JsonElement op = data.get("opening");
        if (op != null) opening = op.getAsString();
        JsonElement oa = data.get("openedAt");
        openedAt = oa != null ? oa.getAsInt() : -1;
        // the mob service brings the mobs back; the host counts them as this camp's again
        if (host instanceof BloodHost h) {
            for (JsonElement m : state.getAsJsonArray("mobs")) {
                JsonElement hid = m.getAsJsonObject().get("h");
                if (hid == null) continue;
                try { h.adopt(UUID.fromString(hid.getAsString())); } catch (IllegalArgumentException ignored) {}
            }
        }
        Vec3 rest = e.watcherRest();
        host.moveWatcher(rest.x, rest.y, rest.z);
    }

    /** A replayed camp: throw order (null: drawn), queue requests, opening line and the other lines in order. */
    public static class Replayed {
        public final List<WallStation> order;
        public final List<CampMech.Request> lines;
        public final String opening;
        public final List<String> texts;

        public Replayed(List<WallStation> order, List<CampMech.Request> lines, String opening, List<String> texts) {
            this.order = order;
            this.lines = lines;
            this.opening = opening;
            this.texts = texts;
        }
    }

    /** A replay's camp from its wall's stations, or null for a camp of its own. */
    public static volatile Function<List<WallStation>, Replayed> replay = null;

    public static final String KIND_GIANT = "blood_giant";
    public static final String KIND_BOSS = "blood_boss";
    public static final String KIND_MOB = "blood_mob";

    /** A real wall has 30 heads; the camp needs at least the 4 + 11 it throws. */
    public static final int MIN_HEADS = 4 + CampMech.LATER_THROWS;

    public static boolean supports(BloodRoomSpec room) { return room.heads().size() >= MIN_HEADS; }
}
