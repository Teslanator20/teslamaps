// Ported from ADM's p3sim/clear/secrets/SecretBook.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.run.BonusKind;
import com.teslamaps.adm.sim.run.BonusScored;
import com.teslamaps.adm.sim.run.CryptDestroyed;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.RunBus;
import com.teslamaps.adm.sim.run.RunState;
import com.teslamaps.adm.sim.run.SecretFound;
import com.teslamaps.adm.sim.run.SecretKind;
import com.teslamaps.adm.sim.run.SecretLedger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class SecretBook {
    private final SecretPlan plan;
    private final RunState state;
    private final RunBus bus;
    private final Supplier<Integer> clock;
    private final BooleanSupplier autoCredit;

    private final LinkedHashSet<String> found = new LinkedHashSet<>();
    private final LinkedHashSet<String> anonymous = new LinkedHashSet<>();
    private final LinkedHashSet<Integer> creditedRooms = new LinkedHashSet<>();

    public SecretBook(SecretPlan plan, RunState state, RunBus bus, Supplier<Integer> clock, BooleanSupplier autoCredit) {
        this.plan = plan;
        this.state = state;
        this.bus = bus;
        this.clock = clock;
        this.autoCredit = autoCredit;
    }

    public SecretBook(SecretPlan plan, RunState state, RunBus bus, Supplier<Integer> clock) { this(plan, state, bus, clock, () -> true); }

    public Set<Integer> autoCreditedRooms() { return creditedRooms; }

    public boolean isFound(SecretSite site) { return found.contains(site.key()); }

    public Set<String> foundKeys() { return found; }

    public List<SecretSite> pending(int room) {
        RoomSecrets rs = plan.room(room);
        List<SecretSite> out = new ArrayList<>();
        if (rs == null) return out;
        for (SecretSite s : rs.sites) if (!found.contains(s.key())) out.add(s);
        return out;
    }

    public int remaining(int room) {
        RoomRecord r = state.room(room);
        return r != null ? r.secretsMax() - r.secretsFound : 0;
    }

    public Claim claim(int room, SecretKind kind, UUID by, int x, int y, int z) {
        RoomRecord rr = state.room(room);
        if (rr == null) return Claim.NoRoom.INSTANCE;
        if (rr.secretsFound >= rr.secretsMax()) return Claim.RoomFull.INSTANCE;
        RoomSecrets rs = plan.room(room);
        SecretSite site = rs != null ? rs.siteAt(x, y, z) : null;
        if (site == null && rs != null) {
            double best = 0;
            for (SecretSite s : rs.sites) {
                if (found.contains(s.key()) || s.kind != kind) continue;
                double d = dist2(s, x, y, z);
                if (site == null || d < best) { site = s; best = d; }
            }
        }
        if (site != null) {
            if (!found.add(site.key())) return Claim.Duplicate.INSTANCE;
        } else if (!anonymous.add(room + ":" + x + "," + y + "," + z)) return Claim.Duplicate.INSTANCE;
        credit(room, kind, by, site != null ? site.x : x, site != null ? site.y : y, site != null ? site.z : z);
        int extra = 0;
        if (autoCredit.getAsBoolean() && rs != null && rs.missing() > 0 && !rs.sites.isEmpty() && allFound(rs)) extra = creditMissing(room, kind, by, x, y, z);
        return new Claim.Credited(site, extra);
    }

    private boolean allFound(RoomSecrets rs) {
        for (SecretSite s : rs.sites) if (!found.contains(s.key())) return false;
        return true;
    }

    public int roomCleared(int room, UUID by, int x, int y, int z) {
        RoomSecrets rs = plan.room(room);
        if (rs == null) return 0;
        if (!autoCredit.getAsBoolean() || !rs.sites.isEmpty() || rs.max <= 0) return 0;
        return creditMissing(room, SecretKind.CHEST, by, x, y, z);
    }

    public int creditRemaining(int room, UUID by, int centreX, int centreZ) {
        RoomRecord r0 = state.room(room);
        if (r0 == null) return 0;
        int before = r0.secretsFound;
        for (SecretSite s : pending(room)) claim(room, s.kind, by, s.x, s.y, s.z);
        RoomRecord rr = state.room(room);
        if (rr == null) return 0;
        if (rr.secretsFound < rr.secretsMax()) creditMissing(room, SecretKind.CHEST, by, centreX, 70, centreZ);
        return rr.secretsFound - before;
    }

    private int creditMissing(int room, SecretKind kind, UUID by, int x, int y, int z) {
        RoomRecord rr = state.room(room);
        if (rr == null) return 0;
        int n = 0;
        while (rr.secretsFound < rr.secretsMax()) { credit(room, kind, by, x, y, z); n++; }
        if (n > 0) creditedRooms.add(room);
        return n;
    }

    private void credit(int room, SecretKind kind, UUID by, int x, int y, int z) {
        RoomRecord rr = state.room(room);
        if (rr == null) return;
        SecretLedger led = state.secrets;
        rr.secretsFound++;
        led.teamFound++;
        if (by != null) {
            led.byPlayer.merge(by, 1, Integer::sum);
            PlayerRecord p = state.players.get(by);
            if (p != null) p.secretsFound++;
        }
        if (kind == SecretKind.ESSENCE) led.witherEssence++;
        bus.post(new SecretFound(clock.get(), room, kind, by, x, y, z));
    }

    public void crypt(int room, UUID by) {
        state.secrets.crypts++;
        bus.post(new CryptDestroyed(clock.get(), room, by));
    }

    public boolean bonus(BonusKind kind, UUID by) {
        SecretLedger led = state.secrets;
        boolean first = switch (kind) {
            case MIMIC -> { boolean was = led.mimicKilled; led.mimicKilled = true; yield !was; }
            case PRINCE -> { boolean was = led.princeKilled; led.princeKilled = true; yield !was; }
            case BAT -> { boolean was = led.batKilled; led.batKilled = true; yield !was; }
        };
        if (first) bus.post(new BonusScored(clock.get(), kind, by));
        return first;
    }

    // ---- savepoints

    public JsonObject snapshot() {
        JsonObject o = new JsonObject();
        JsonArray f = new JsonArray();
        for (String s : found) f.add(s);
        o.add("found", f);
        JsonArray an = new JsonArray();
        for (String s : anonymous) an.add(s);
        o.add("anon", an);
        JsonArray cr = new JsonArray();
        for (Integer i : creditedRooms) cr.add(i);
        o.add("credited", cr);
        JsonArray rooms = new JsonArray();
        for (RoomRecord r : state.rooms) rooms.add(r.secretsFound);
        o.add("rooms", rooms);
        SecretLedger led = state.secrets;
        o.addProperty("team", led.teamFound);
        o.addProperty("crypts", led.crypts);
        o.addProperty("essence", led.witherEssence);
        o.addProperty("mimic", led.mimicKilled); o.addProperty("prince", led.princeKilled); o.addProperty("bat", led.batKilled);
        JsonObject players = new JsonObject();
        for (Map.Entry<UUID, Integer> e : led.byPlayer.entrySet()) players.addProperty(e.getKey().toString(), e.getValue());
        o.add("players", players);
        JsonObject blessings = new JsonObject();
        for (Map.Entry<String, Integer> e : led.blessings.entrySet()) blessings.addProperty(e.getKey(), e.getValue());
        o.add("blessings", blessings);
        return o;
    }

    public void restore(JsonObject o) {
        found.clear(); anonymous.clear(); creditedRooms.clear();
        JsonArray fa = o.getAsJsonArray("found");
        if (fa != null) for (JsonElement e : fa) found.add(e.getAsString());
        JsonArray aa = o.getAsJsonArray("anon");
        if (aa != null) for (JsonElement e : aa) anonymous.add(e.getAsString());
        JsonArray ca = o.getAsJsonArray("credited");
        if (ca != null) for (JsonElement e : ca) creditedRooms.add(e.getAsInt());
        JsonArray ra = o.getAsJsonArray("rooms");
        if (ra != null) for (int i = 0; i < ra.size(); i++) {
            RoomRecord r = state.room(i);
            if (r != null) r.secretsFound = ra.get(i).getAsInt();
        }
        SecretLedger led = state.secrets;
        led.teamFound = o.get("team") != null ? o.get("team").getAsInt() : led.teamFound;
        led.crypts = o.get("crypts") != null ? o.get("crypts").getAsInt() : led.crypts;
        led.witherEssence = o.get("essence") != null ? o.get("essence").getAsInt() : led.witherEssence;
        led.mimicKilled = o.get("mimic") != null && o.get("mimic").getAsBoolean();
        led.princeKilled = o.get("prince") != null && o.get("prince").getAsBoolean();
        led.batKilled = o.get("bat") != null && o.get("bat").getAsBoolean();
        led.byPlayer.clear();
        JsonObject players = o.getAsJsonObject("players");
        if (players != null) for (Map.Entry<String, JsonElement> e : players.entrySet()) {
            UUID id;
            try { id = UUID.fromString(e.getKey()); } catch (Throwable t) { id = null; }
            if (id != null) led.byPlayer.put(id, e.getValue().getAsInt());
        }
        for (PlayerRecord p : state.players.values()) {
            Integer n = led.byPlayer.get(p.uuid);
            p.secretsFound = n != null ? n : 0;
        }
        led.blessings.clear();
        JsonObject bl = o.getAsJsonObject("blessings");
        if (bl != null) for (Map.Entry<String, JsonElement> e : bl.entrySet()) led.blessings.put(e.getKey(), e.getValue().getAsInt());
    }

    private double dist2(SecretSite s, int x, int y, int z) {
        double dx = s.x - x, dy = s.y - y, dz = s.z - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
