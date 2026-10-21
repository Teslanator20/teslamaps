package com.teslamaps.adm.sim.clear.ui;
// Ported from ADM's p3sim/clear/ui/StrategyChoice.kt

import com.teslamaps.adm.sim.clear.bots.NeutralStrategy;
import com.teslamaps.adm.sim.run.plugin.PartyStrategy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class StrategyChoice {
    private StrategyChoice() {}

    public static final String AUTO = "auto";

    public static boolean hasPresets(List<PartyStrategy> offered) {
        for (PartyStrategy s : offered) if (!s.id().equals(NeutralStrategy.ID)) return true;
        return false;
    }

    public static PartyStrategy autoPick(List<PartyStrategy> offered) {
        for (PartyStrategy s : offered) if (!s.id().equals(NeutralStrategy.ID)) return s;
        return null;
    }

    public static List<String> choices(List<PartyStrategy> offered) {
        Set<String> all = new LinkedHashSet<>();
        all.add(AUTO);
        all.add(NeutralStrategy.ID);
        for (PartyStrategy s : offered) all.add(s.id());
        return new ArrayList<>(all);
    }

    public static String next(String current, List<PartyStrategy> offered) {
        List<String> all = choices(offered);
        String n = normalize(current, offered);
        int at = all.indexOf(n != null ? n : AUTO);
        return all.get((at + 1) % all.size());
    }

    public static String normalize(String word, List<PartyStrategy> offered) {
        if (word == null) return null;
        String w = word.trim();
        if (w.isEmpty()) return null;
        if (w.equalsIgnoreCase(AUTO)) return AUTO;
        if (w.equalsIgnoreCase(NeutralStrategy.ID)) return NeutralStrategy.ID;
        for (PartyStrategy s : offered) if (s.id().equalsIgnoreCase(w) || s.label().equalsIgnoreCase(w)) return s.id();
        return null;
    }

    public static String resolve(String choice, List<PartyStrategy> offered) {
        String n = normalize(choice, offered);
        String c = n != null ? n : AUTO;
        if (c.equals(AUTO)) {
            PartyStrategy p = autoPick(offered);
            return p != null ? p.id() : null;
        }
        if (c.equals(NeutralStrategy.ID)) return null;
        return c;
    }

    public static String label(String choice, List<PartyStrategy> offered) {
        String n = normalize(choice, offered);
        String c = n != null ? n : AUTO;
        if (c.equals(AUTO)) {
            PartyStrategy p = autoPick(offered);
            return "Auto: " + (p != null ? p.label() : NeutralStrategy.INSTANCE.label());
        }
        if (c.equals(NeutralStrategy.ID)) return NeutralStrategy.INSTANCE.label();
        for (PartyStrategy s : offered) if (s.id().equals(c)) return s.label();
        return c;
    }

    public static String about(String choice, List<PartyStrategy> offered) {
        String n = normalize(choice, offered);
        String c = n != null ? n : AUTO;
        String what;
        if (c.equals(AUTO)) {
            PartyStrategy p = autoPick(offered);
            what = "Auto uses the first preset a plug-in offers (" + (p != null ? p.label() : NeutralStrategy.INSTANCE.label()) + "), and the neutral strategy when none does.";
        } else if (c.equals(NeutralStrategy.ID)) what = NeutralStrategy.INSTANCE.description();
        else {
            String d = null;
            for (PartyStrategy s : offered) if (s.id().equals(c)) { d = s.description(); break; }
            what = d != null && !d.isBlank() ? d : "A party strategy from a plug-in.";
        }
        return what + " Click: the next choice. It only matters with bots.";
    }

    public static String words(List<PartyStrategy> offered) { return String.join(", ", choices(offered)); }
}
