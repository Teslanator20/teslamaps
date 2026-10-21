package com.teslamaps.adm.sim.clear.damage;

// Ported from ADM's p3sim/clear/damage/KillCombo.kt

import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KillCombo {
    private static class State {
        int count;
        int lastKill;

        State(int count, int lastKill) {
            this.count = count;
            this.lastKill = lastKill;
        }
    }

    private final HashMap<UUID, State> combos = new HashMap<>();

    public String kill(UUID player, int t) {
        State s = combos.get(player);
        if (s == null || !(t - s.lastKill < TIMEOUT)) {
            s = new State(0, t);
            combos.put(player, s);
        }
        s.count++;
        s.lastKill = t;
        return TIERS.get(s.count);
    }

    public List<Pair<UUID, String>> expire(int t) {
        ArrayList<Pair<UUID, String>> out = new ArrayList<>();
        Iterator<Map.Entry<UUID, State>> it = combos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> en = it.next();
            UUID id = en.getKey();
            State s = en.getValue();
            if (t - s.lastKill < TIMEOUT) continue;
            it.remove();
            out.add(Pair.of(id, s.count >= 5 ? "§cYour Kill Combo has expired! You reached a " + s.count + " Kill Combo!" : null));
        }
        return out;
    }

    public void clear() { combos.clear(); }

    public static final int TIMEOUT = 60;
    private static final String MF3 = "§r§b+3%  Magic Find";
    private static final String COINS = "§r§8+§r§610 §r§7coins per kill";
    public static final Map<Integer, String> TIERS;

    static {
        LinkedHashMap<Integer, String> m = new LinkedHashMap<>();
        m.put(5, "§a§l+5 Kill Combo " + MF3);
        m.put(10, "§a§l+10 Kill Combo " + COINS);
        m.put(15, "§9§l+15 Kill Combo " + MF3);
        m.put(20, "§5§l+20 Kill Combo §r§3+15☯ Combat Wisdom");
        m.put(25, "§5§l+25 Kill Combo " + MF3);
        m.put(30, "§6§l+30 Kill Combo " + COINS);
        m.put(50, "§6§l+50 Kill Combo §r§b+5%  Magic Find");
        m.put(75, "§6§l+75 Kill Combo");
        TIERS = Collections.unmodifiableMap(m);
    }
}
