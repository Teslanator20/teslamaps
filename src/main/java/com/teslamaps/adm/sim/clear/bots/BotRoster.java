// Ported from ADM's p3sim/clear/bots/BotRoster.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.run.plugin.Seat;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Triple;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Builds the party the clear plans for. Humans take the first seats, the bots the classes no human plays. Pure. */
public final class BotRoster {
    private BotRoster() {}

    public static final List<String> CLASSES = List.of("HEALER", "MAGE", "BERSERK", "ARCHER", "TANK");

    /** The levels a class-word teammate shows. */
    public static final int DEFAULT_SB_LEVEL = 326;
    public static final int DEFAULT_CLASS_LEVEL = 48;

    /** The boss sim's bot name for a class ("Healer"). */
    public static String label(String cls) {
        String l = cls.toLowerCase(Locale.ROOT);
        return l.isEmpty() ? l : l.substring(0, 1).toUpperCase(Locale.ROOT) + l.substring(1);
    }

    /** The profile id of the bot of a class: the same name-based id the boss sim gives its bot. */
    public static UUID botUuid(String cls) { return UUID.nameUUIDFromBytes(("p3sim:" + label(cls)).getBytes(StandardCharsets.UTF_8)); }

    /**
     * [humans] are (id, name, class or null) in seat order. [order] is the class order bots are taken in. With a [random], each bot is a player of
     * its own; without one, a bot is named for its class and has the boss sim's profile id.
     */
    public static Roster build(List<Triple<UUID, String, String>> humans, boolean botsOn, int partySize, List<String> order, KRandom random) {
        ArrayList<Seat> seats = new ArrayList<>();
        HashMap<Integer, UUID> uuids = new HashMap<>();
        HashMap<Integer, String> names = new HashMap<>();
        HashMap<Integer, BotIdentity> identities = new HashMap<>();
        HashSet<String> used = new HashSet<>();
        for (int i = 0; i < humans.size(); i++) {
            Triple<UUID, String, String> h = humans.get(i);
            String cls = null;
            if (h.third != null) {
                String u = h.third.toUpperCase(Locale.ROOT);
                if (CLASSES.contains(u) && !used.contains(u)) cls = u;
            }
            if (cls == null) {
                ArrayList<String> all = new ArrayList<>(order);
                all.addAll(CLASSES);
                for (String c : all) if (!used.contains(c)) { cls = c; break; }
            }
            if (cls == null) cls = CLASSES.get(0);
            used.add(cls);
            seats.add(new Seat(i, cls, true));
            uuids.put(i, h.first);
            names.put(i, h.second);
        }
        if (botsOn) {
            LinkedHashSet<String> distinct = new LinkedHashSet<>();
            for (String o : order) distinct.add(o.toUpperCase(Locale.ROOT));
            distinct.addAll(CLASSES);
            ArrayList<String> free = new ArrayList<>();
            for (String c : distinct) if (CLASSES.contains(c) && !used.contains(c)) free.add(c);
            int n = Math.clamp(partySize - humans.size(), 0, free.size());
            for (int k = 0; k < n; k++) {
                String cls = free.get(k);
                int idx = seats.size();
                seats.add(new Seat(idx, cls, false));
                BotIdentity identity;
                if (random != null) {
                    HashSet<String> taken = new HashSet<>();
                    for (String nm : names.values()) taken.add(nm.toLowerCase(Locale.ROOT));
                    identity = BotIdentities.draw(random, taken);
                } else identity = BotIdentities.classWord(cls, botUuid(cls), DEFAULT_SB_LEVEL, DEFAULT_CLASS_LEVEL);
                identities.put(idx, identity);
                uuids.put(idx, identity.uuid);
                names.put(idx, identity.name);
            }
        }
        return new Roster(seats, uuids, names, identities);
    }

    public static Roster build(List<Triple<UUID, String, String>> humans, boolean botsOn, int partySize, List<String> order) {
        return build(humans, botsOn, partySize, order, null);
    }

    public static Roster build(List<Triple<UUID, String, String>> humans, boolean botsOn, int partySize) {
        return build(humans, botsOn, partySize, CLASSES, null);
    }
}
