package com.teslamaps.adm.sim.clear.damage;

// Ported from ADM's p3sim/clear/damage/DamageNumbers.kt

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Fight;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimStand;
import com.teslamaps.adm.sim.Station;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.Locale;

public final class DamageNumbers {
    private DamageNumbers() {}

    public static final int LIFE_TICKS = 20;
    public static final double CRIT_SHARE = 0.27;
    public static final int DEFAULT_CHANCE = 100;
    private static final char[] CRIT_CYCLE = {'f', 'f', 'e', '6', 'c', 'c'};

    public static String digits(double amount) {
        return String.format(Locale.ROOT, "%,d", Math.max(Math.round(amount), 1L));
    }

    public static String name(double amount, boolean crit) {
        if (!crit) return "§7" + digits(amount);
        String text = "✧" + digits(amount) + "✧";
        StringBuilder sb = new StringBuilder();
        char last = ' ';
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            char c = CRIT_CYCLE[i % CRIT_CYCLE.length];
            if (c != last) {
                if (i > 0) sb.append("§r");
                sb.append('§').append(c);
                last = c;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    public static double rise(double u) {
        double[] q = {0.0, 0.1, 0.5, 0.9, 1.0};
        double[] v = {-0.6, -0.32, 1.16, 2.03, 2.4};
        for (int i = 1; i < q.length; i++) if (u <= q[i]) return v[i - 1] + (v[i] - v[i - 1]) * (u - q[i - 1]) / (q[i] - q[i - 1]);
        return v[v.length - 1];
    }

    public static double[] place(double x, double y, double z, KRandom rnd) {
        double a = rnd.nextDouble() * Math.PI * 2;
        double r = 1.9 * Math.sqrt(rnd.nextDouble()) * 0.62;
        return new double[]{x + Math.cos(a) * r, y + rise(rnd.nextDouble()), z + Math.sin(a) * r};
    }

    public static void show(double x, double y, double z, double amount, KRandom rnd) {
        Ec.safely("ads damage number", () -> {
            double[] at = place(x, y, z, rnd);
            SimStand s = new SimStand(Sim.level());
            s.snapTo(at[0], at[1], at[2], 0f, 0f);
            s.setInvisible(true);
            s.setInvulnerable(true);
            s.setSilent(true);
            s.setNoGravity(true);
            Station.setMarker(s);
            s.setCustomName(Sim.legacy(name(amount, rnd.nextDouble() < CRIT_SHARE)));
            s.setCustomNameVisible(true);
            SimStand e = Sim.spawn(s);
            Fight.later(LIFE_TICKS, "damage number gone", e::discard);
        });
    }
}
