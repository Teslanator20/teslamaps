// Ported from ADM's p3sim/clear/bots/BotRoster.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.run.plugin.Seat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Who is in the party: the human seats first, then the bots. [uuids] and [names] are by seat index. */
public class Roster {
    public final List<Seat> seats;
    public final Map<Integer, UUID> uuids;
    public final Map<Integer, String> names;
    public final Map<Integer, BotIdentity> identities;

    public Roster(List<Seat> seats, Map<Integer, UUID> uuids, Map<Integer, String> names, Map<Integer, BotIdentity> identities) {
        this.seats = seats;
        this.uuids = uuids;
        this.names = names;
        this.identities = identities;
    }

    public Roster(List<Seat> seats, Map<Integer, UUID> uuids, Map<Integer, String> names) { this(seats, uuids, names, Map.of()); }

    public List<Seat> bots() {
        ArrayList<Seat> out = new ArrayList<>();
        for (Seat s : seats) if (!s.isHuman) out.add(s);
        return out;
    }

    public List<Seat> humans() {
        ArrayList<Seat> out = new ArrayList<>();
        for (Seat s : seats) if (s.isHuman) out.add(s);
        return out;
    }

    public Seat seatOf(UUID uuid) {
        Integer idx = null;
        for (Map.Entry<Integer, UUID> e : uuids.entrySet()) if (e.getValue().equals(uuid)) { idx = e.getKey(); break; }
        if (idx == null) return null;
        for (Seat s : seats) if (s.index == idx) return s;
        return null;
    }
}
