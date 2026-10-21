// Ported from ADM's p3sim/clear/secrets/SecretSites.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomSecret;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.SecretKind;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Triple;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SecretPlan {
    public final List<RoomSecrets> rooms;
    private final Map<String, SecretSite> byKey;

    public SecretPlan(List<RoomSecrets> rooms) {
        this.rooms = rooms;
        Map<String, SecretSite> m = new LinkedHashMap<>();
        for (RoomSecrets r : rooms) {
            for (SecretSite s : r.sites) m.put(s.key(), s);
            for (SecretSite s : r.bonus) m.put(s.key(), s);
        }
        this.byKey = m;
    }

    public RoomSecrets room(int index) { return index >= 0 && index < rooms.size() ? rooms.get(index) : null; }

    public SecretSite site(String key) { return byKey.get(key); }

    public Collection<SecretSite> all() { return byKey.values(); }

    public SecretSite siteAt(int room, int x, int y, int z) {
        RoomSecrets r = room(room);
        return r == null ? null : r.siteAt(x, y, z);
    }

    public KeyGate gateAt(int room, int x, int y, int z) {
        RoomSecrets r = room(room);
        return r == null ? null : r.gateAt(x, y, z);
    }

    public SecretSite bonusAt(int room, int x, int y, int z) {
        RoomSecrets r = room(room);
        return r == null ? null : r.bonusAt(x, y, z);
    }

    public List<KeyGate> gates() {
        List<KeyGate> out = new ArrayList<>();
        for (RoomSecrets r : rooms) out.addAll(r.gates);
        return out;
    }

    private static final List<String> CLOCKWISE = List.of("north", "east", "south", "west");

    public static String rotateFacing(String facing, int quarterTurns) {
        int i = facing == null ? -1 : CLOCKWISE.indexOf(facing.toLowerCase(Locale.ROOT));
        if (i < 0) return facing;
        return CLOCKWISE.get(Math.floorMod(i + quarterTurns, 4));
    }

    public static SecretKind kindOf(String type) {
        return switch (type) {
            case "chest" -> SecretKind.CHEST;
            case "lockedChest" -> SecretKind.LOCKED_CHEST;
            case "item" -> SecretKind.ITEM;
            case "bat" -> SecretKind.BAT;
            case "essence" -> SecretKind.ESSENCE;
            case "redstoneKey" -> SecretKind.REDSTONE_KEY;
            default -> null;
        };
    }

    public static SecretPlan build(DungeonPlan plan, KRandom random) {
        List<RoomSecrets> rooms = new ArrayList<>();
        for (PlacedRoom r : plan.rooms) {
            var design = r.design;
            Placement placement = r.placement;
            if (design == null || placement == null) { rooms.add(new RoomSecrets(r.index, r.secretsMax, List.of())); continue; }
            int q = placement.quarterTurns();
            List<SecretSite> all = new ArrayList<>();
            for (RoomSecret s : design.secrets) {
                SecretSite site = site(r.index, s, placement, q, random);
                if (site != null) all.add(site);
            }
            // Main data first, supplemental last, original order inside each group; then cap at the room's maximum.
            all.sort(Comparator.comparingInt(it -> it.supplemental ? 1 : 0));
            List<SecretSite> kept = new ArrayList<>(all.subList(0, Math.min(r.secretsMax, all.size())));
            KeyRoomSpec spec = RedstoneKeys.forDesign(design.id);
            if (spec == null) { rooms.add(new RoomSecrets(r.index, r.secretsMax, kept)); continue; }
            rooms.add(keyRoom(r.index, r.secretsMax, spec, placement, random, kept));
        }
        return new SecretPlan(rooms);
    }

    private static RoomSecrets keyRoom(int room, int max, KeyRoomSpec spec, Placement placement, KRandom random, List<SecretSite> kept) {
        int q = placement.quarterTurns();
        SocketInfo socket = RedstoneKeys.resolve(spec, room, placement);
        List<KeyGate> gates = new ArrayList<>();
        for (SecretSite k : kept) if (k.kind == SecretKind.REDSTONE_KEY) gates.add(new KeyGate(room, k.key(), k.x, k.y, k.z, q));
        if (gates.isEmpty()) {
            Pos p = RedstoneKeys.pickKeySpot(spec, random);
            if (p != null) {
                var w = placement.toWorld(p.x, p.y, p.z);
                gates.add(new KeyGate(room, room + ":KEY", w.x, w.y, w.z, q));
            }
        }
        var p = placement.toWorld(spec.socket.x, spec.socket.y, spec.socket.z);
        SecretSite site = new SecretSite(room, "SK", SecretKind.REDSTONE_KEY, p.x, p.y, p.z, null, null, false, q, false, socket);
        Set<Triple<Integer, Integer, Integer>> chestCells = new HashSet<>();
        for (RevealChest c : socket.chests) chestCells.add(Triple.of(c.site.x, c.site.y, c.site.z));
        List<SecretSite> counting = new ArrayList<>();
        for (SecretSite k : kept) if (k.kind != SecretKind.REDSTONE_KEY && !chestCells.contains(Triple.of(k.x, k.y, k.z))) counting.add(k);
        counting.add(site);
        for (RevealChest c : socket.chests) if (c.counts) counting.add(c.site);
        List<SecretSite> taken = new ArrayList<>(counting.subList(0, Math.min(max, counting.size())));
        List<SecretSite> bonus = new ArrayList<>();
        for (RevealChest c : socket.chests) if (!c.counts) bonus.add(c.site);
        return new RoomSecrets(room, max, taken, gates, bonus);
    }

    private static SecretSite site(int room, RoomSecret s, Placement placement, int q, KRandom random) {
        SecretKind kind = kindOf(s.type);
        if (kind == null) return null;
        List<Pos> choices = new ArrayList<>();
        choices.add(s.pos);
        choices.addAll(s.alt);
        Pos p = choices.size() > 1 ? choices.get(random.nextInt(choices.size())) : s.pos;
        var w = placement.toWorld(p.x, p.y, p.z);
        Pos lever = null;
        if (s.lever != null) {
            var l = placement.toWorld(s.lever.x, s.lever.y, s.lever.z);
            lever = new Pos(l.x, l.y, l.z);
        }
        return new SecretSite(room, s.id, kind, w.x, w.y, w.z, rotateFacing(s.facing, q), lever, s.baked, q, "supplemental".equals(s.src));
    }
}
