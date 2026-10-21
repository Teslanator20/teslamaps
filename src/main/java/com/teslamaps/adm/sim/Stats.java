package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/Stats.kt

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

public final class Stats {
    private Stats() {}

    private static int from = 1;
    private static boolean practice = false;
    private static final int[] sections = {-1, -1, -1, -1, -1};
    private static final ConcurrentHashMap<String, Integer> mine = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> deaths = new ConcurrentHashMap<>();

    public static void reset(int from) { reset(from, false); }

    public static void reset(int from, boolean practice) {
        Stats.from = from;
        Stats.practice = practice;
        Arrays.fill(sections, -1);
        mine.clear();
        deaths.clear();
    }

    private static String s(int ticks) { return String.format(Locale.ROOT, "%.2fs", ticks / 20.0); }

    public static void done(String by) { mine.merge(by, 1, Integer::sum); }

    public static void section(int s, int ticks) {
        if (s < 1 || s > 4 || sections[s] >= 0) return;
        sections[s] = ticks;
        String best = practice ? "" : best("S" + s, ticks);
        for (ServerPlayer p : Sim.players()) Sim.asPlayer(p, () -> {
            if (P3Sim.showTimes()) Sim.note("§fS" + s + "§7 " + s(ticks) + (Sim.isHost(p) ? best : ""));
        });
        PartyClient.send("sectionTime", ticks);
    }

    private static File bestFile() { return new File(Ec.configDir().toFile(), "p3sim-pb.properties"); }

    private static Properties bests;

    private static synchronized Properties bests() {
        if (bests == null) {
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream(bestFile())) { p.load(in); } catch (Throwable ignored) {}
            bests = p;
        }
        return bests;
    }

    public static String best(String what, int ticks) {
        String key = what + " @ " + P3Plan.skillName() + " " + Roles.label(P3Sim.ownClass())
                + (P3Plan.skill == P3Plan.RANDOM ? " " + P3Plan.botMin + "-" + P3Plan.botMax + "s" : "");
        Integer old;
        try {
            String v = bests().getProperty(key);
            old = v == null ? null : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            old = null;
        }
        if (old != null && ticks >= old) return " §8PB " + s(old);
        bests().setProperty(key, Integer.toString(ticks));
        try {
            File f = bestFile();
            f.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(f)) { bests().store(out, "ADS personal bests, server ticks"); }
        } catch (Throwable ignored) {}
        return old == null ? " §6PB" : " §6§lNEW PB §8(was " + s(old) + ")";
    }

    public static void deathTick(String by) { deaths.merge(by, 1, Integer::sum); }

    public static void p3(int n) {
        StringBuilder parts = new StringBuilder();
        for (int i = 1; i <= 4; i++) {
            if (sections[i] < 0) continue;
            if (!parts.isEmpty()) parts.append(" §8|§7 ");
            parts.append("S").append(i).append(" ").append(s(sections[i]));
        }
        String best = from == 1 && !practice && P3Sim.showTimesS.get() ? best("P3", n) : "";
        String total = from == 1 ? " §8|§f P3 " + s(n) : "";
        for (ServerPlayer p : Sim.players()) Sim.asPlayer(p, () -> {
            if (!P3Sim.showTimes()) return;
            Sim.note(parts + total + (Sim.isHost(p) ? best : ""));
            String name = p.getGameProfile().name();
            Sim.note("You did §f" + mine.getOrDefault(name, 0) + "§7 of the jobs; death ticks taken: §f" + deaths.getOrDefault(name, 0));
        });
    }

    public static int runStart = -1;

    public static int runTicks() { return runStart >= 0 ? Fight.serverTick - runStart : 0; }
}
