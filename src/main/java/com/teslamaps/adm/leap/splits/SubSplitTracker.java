package com.teslamaps.adm.leap.splits;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ported from ADM's splits/SubSplits.kt
public final class SubSplitTracker {

    private record Step(String split, String label, String id) {}

    private static final String WATCHER = "[BOSS] The Watcher: ";
    private static final String WATCHER_HANDLE = "[BOSS] The Watcher: Let's see how you can handle this.";
    private static final String WATCHER_DONE = "[BOSS] The Watcher: You have proven yourself. You may pass.";
    private static final String MAXOR_START = "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!";
    private static final String LASER_CHARGING = "The Energy Laser is charging up!";
    private static final String STORM_START = "[BOSS] Storm: Pathetic Maxor, just like expected.";
    private static final String STORM_ENRAGED = "⚠ Storm is enraged! ⚠";
    private static final String STORM_DEAD = "[BOSS] Storm: I should have known that I stood no chance.";
    private static final String GOLDOR_START = "[BOSS] Goldor: Who dares trespass into my domain?";
    private static final String GATE_DESTROYED = "The gate has been destroyed!";
    private static final String CORE_OPENING = "The Core entrance is opening!";
    private static final String NECRON_ARGH = "[BOSS] Necron: ARGH!";
    private static final Pattern SECTION_DONE = Pattern.compile("^(\\w+) (?:activated|completed) a (?:terminal|device|lever)! \\((\\d+)/(\\d+)\\)$");
    private static final Set<String> MAXOR_STUN = Set.of("[BOSS] Maxor: YOU TRICKED ME!", "[BOSS] Maxor: THAT BEAM! IT HURTS! IT HURTS!!");
    private static final Set<String> STORM_LIGHTNING = Set.of("[BOSS] Storm: ENERGY HEED MY CALL!", "[BOSS] Storm: THUNDER LET ME BE YOUR CATALYST!");
    private static final Set<String> STORM_CRUSHED = Set.of("[BOSS] Storm: Oof", "[BOSS] Storm: Ouch, that hurt!");
    private static final Set<String> NECRON_START = Set.of(
            "[BOSS] Necron: Finally, I heard so much about you. The Eye likes you very much.",
            "[BOSS] Necron: You went further than any human before, congratulations.");

    private static final int BLOOD_MOBS = 19;
    private static final int CRYSTALS_BACK = 41;
    private static final int MAXOR_DESPAWN = 54;
    private static final int MIN_HIT_GAP = 60;
    private static final int STORM_LEAVES = 99;
    private static final double STORM_SPOT_X = 102.375, STORM_SPOT_Z = 52.375;
    private static final double YELLOW_X = 46.0, YELLOW_Z = 65.0, YELLOW_REACHED = 2.4;
    private static final double OFF_MID = 0.5, ON_MID = 0.05;

    private static final int W_DIALOGUE = 0, W_WAIT = 1, W_CAMP = 2, W_CLEAR = 3, W_END = 4;
    private static final int M_CRYSTALS = 5, M_LURE = 6, M_COOLDOWN = 7, M_KILL = 8, M_ANIMATION = 9;
    private static final int S_OPENING = 10, S_CRUSH1 = 11, S_PIN = 12, S_FLIGHT = 13, S_CRUSH2 = 14, S_KILL = 15, S_ANIMATION = 16;
    private static final int T_S1 = 17, T_S3 = 19, T_S4 = 20;
    private static final int G_LEAPS = 21, G_KILL = 22;
    private static final int N_INTRO = 23, N_TRIP1 = 24, N_LOCK1 = 25, N_KILL = 26, N_RELICS = 27, N_END = 28;

    private static final List<Step> SEQUENCE = List.of(
            new Step(SplitTracker.BLOOD, "&7Dialogue", "watcher.dialogue"), new Step(SplitTracker.BLOOD, "&5Wait", "watcher.wait"),
            new Step(SplitTracker.BLOOD, "&cCamp", "watcher.camp"), new Step(SplitTracker.BLOOD, "&aClear", "watcher.clear"),
            new Step(SplitTracker.BLOOD, null, ""),
            new Step(SplitTracker.MAXOR, "&dCrystals", "maxor.crystals"), new Step(SplitTracker.MAXOR, "&6Lure", "maxor.lure"),
            new Step(SplitTracker.MAXOR, "&5Cooldown", "maxor.cooldown"), new Step(SplitTracker.MAXOR, "&cKill", "maxor.kill"),
            new Step(SplitTracker.MAXOR, "&dAnimation", "maxor.animation"),
            new Step(SplitTracker.STORM, "&aOpening", "storm.opening"), new Step(SplitTracker.STORM, "&6Crush", "storm.crush1"),
            new Step(SplitTracker.STORM, "&cPin", "storm.pin"), new Step(SplitTracker.STORM, "&bFlight", "storm.flight"),
            new Step(SplitTracker.STORM, "&6Crush", "storm.crush2"), new Step(SplitTracker.STORM, "&cKill", "storm.kill"),
            new Step(SplitTracker.STORM, "&aAnimation", "storm.animation"),
            new Step(SplitTracker.TERMS, "&6S1", "terms.s1"), new Step(SplitTracker.TERMS, "&6S2", "terms.s2"),
            new Step(SplitTracker.TERMS, "&6S3", "terms.s3"), new Step(SplitTracker.TERMS, "&6S4", "terms.s4"),
            new Step(SplitTracker.GOLDOR, "&5Leaps", "goldor.leaps"), new Step(SplitTracker.GOLDOR, "&cKill", "goldor.kill"),
            new Step(SplitTracker.NECRON, "&dIntro", "necron.intro"), new Step(SplitTracker.NECRON, "&cTrip", "necron.trip1"),
            new Step(SplitTracker.NECRON, "&aLock", "necron.lock1"), new Step(SplitTracker.NECRON, "&cKill", "necron.kill"),
            new Step(SplitTracker.NECRON, "&3Relics", "necron.relics"),
            new Step(SplitTracker.NECRON, null, ""));

    private final Stamp[] starts = new Stamp[SEQUENCE.size()];
    private int current = -1;
    private int ticks = 0;
    private Stamp lightning;
    private int bloodMobs = 0;
    private boolean gateBlown, gateWaiting;
    private boolean watchingCore;

    public boolean watchingCore() { return watchingCore; }
    public Stamp s4Start() { return starts[T_S4]; }

    public void reset() {
        Arrays.fill(starts, null);
        current = -1; ticks = 0; lightning = null; bloodMobs = 0;
        gateBlown = false; gateWaiting = false; watchingCore = false;
    }

    public List<Split> forSplit(String split) {
        List<Split> out = new ArrayList<>();
        for (int i = 0; i < SEQUENCE.size(); i++) {
            Step step = SEQUENCE.get(i);
            if (!step.split.equals(split) || step.label == null) continue;
            Stamp start = starts[i];
            if (start == null) continue;
            Stamp stop = null;
            for (int k = i + 1; k < starts.length; k++) if (starts[k] != null) { stop = starts[k]; break; }
            out.add(new Split(step.label, start, stop));
        }
        return out;
    }

    public List<String> idsForSplit(String split) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < SEQUENCE.size(); i++) {
            Step s = SEQUENCE.get(i);
            if (s.split.equals(split) && s.label != null && starts[i] != null) out.add(s.id);
        }
        return out;
    }

    public void onServerTick() {
        ticks++;
        Stamp from = lightning;
        if (from == null) return;
        if (current == S_OPENING && ticks >= STORM_LEAVES) {
            lightning = null;
            jumpTo(S_CRUSH1, from.plus(STORM_LEAVES));
        }
    }

    public void onWatcherMoved(Stamp at) {
        if (current == W_WAIT) jumpTo(W_CAMP, at);
    }

    public void onBloodMobSpawn(Stamp at) {
        if (current < W_DIALOGUE || current > W_CAMP) return;
        if (++bloodMobs >= BLOOD_MOBS) jumpTo(W_CLEAR, at);
    }

    public void onMaxorKilled(Stamp at) {
        if (current >= M_CRYSTALS && current < M_ANIMATION) jumpTo(M_ANIMATION, at);
    }

    public void onMaxorDead(Stamp at) {
        if (current == M_KILL) jumpTo(M_ANIMATION, Stamp.later(at.minus(MAXOR_DESPAWN), starts[M_KILL]));
    }

    public void onTopCrystal(Stamp at) {
        Stamp hit = at.minus(CRYSTALS_BACK);
        if (current == M_LURE) jumpTo(M_COOLDOWN, Stamp.later(hit, starts[current]));
        else if (current == M_COOLDOWN && hit.tick() - starts[M_COOLDOWN].tick() >= MIN_HIT_GAP)
            jumpTo(M_KILL, Stamp.later(hit, starts[current]));
    }

    public void onStormPosition(Stamp at, double x, double y, double z) {
        if (current == S_OPENING) {
            if (lightning != null && Math.hypot(x - STORM_SPOT_X, z - STORM_SPOT_Z) > 0.5) {
                lightning = null;
                jumpTo(S_CRUSH1, at);
            }
        } else if (current == S_FLIGHT) {
            if (Math.hypot(x - YELLOW_X, z - YELLOW_Z) <= YELLOW_REACHED) jumpTo(S_CRUSH2, at);
        }
    }

    public void onNecronPosition(Stamp at, double fromMid) {
        if (current == N_INTRO) { if (fromMid > OFF_MID) jumpTo(N_TRIP1, at); }
        else if (current == N_TRIP1) { if (fromMid < ON_MID) jumpTo(N_LOCK1, at); }
    }

    public void onNecronDeath(Stamp at) {
        if (current >= N_INTRO && current < N_RELICS) jumpTo(N_RELICS, at);
    }

    public void onSectionDoor(Stamp at, int section) {
        int step = T_S1 + section - 1;
        if (current == step) jumpTo(step + 1, at);
    }

    public void startTerms(int section, List<Stamp> st) {
        reset();
        for (int s = 1; s <= Math.min(section, 4); s++) jumpTo(T_S1 + s - 1, st.get(s - 1));
    }

    public void onEveryoneInCore(Stamp at) {
        if (!watchingCore) return;
        watchingCore = false;
        if (current == G_LEAPS) jumpTo(G_KILL, at);
    }

    public void onChat(String msg, Stamp at) {
        if (msg.startsWith(WATCHER) && current < W_DIALOGUE) { reset(); jumpTo(W_DIALOGUE, at); return; }
        if (msg.equals(WATCHER_HANDLE)) { if (current == W_DIALOGUE) jumpTo(W_WAIT, at); return; }
        if (msg.equals(WATCHER_DONE)) { if (current >= W_DIALOGUE && current <= W_CLEAR) jumpTo(W_END, at); return; }
        if (msg.equals(MAXOR_START)) { jumpTo(M_CRYSTALS, at); return; }
        if (msg.equals(STORM_START)) { lightning = null; jumpTo(S_OPENING, at); return; }
        if (msg.equals(GOLDOR_START)) { gateBlown = false; gateWaiting = false; jumpTo(T_S1, at); return; }
        if (msg.equals(CORE_OPENING)) { if (current >= T_S1 && current <= T_S4) { jumpTo(G_LEAPS, at); watchingCore = true; } return; }
        if (NECRON_START.contains(msg)) { watchingCore = false; jumpTo(N_INTRO, at); return; }
        if (SplitTracker.EXTRA_STATS.matcher(msg).matches()) { if (current >= N_INTRO && current < N_END) jumpTo(N_END, at); return; }
        if (msg.equals(SplitTracker.RELICS_PLACED)) { if (current >= N_INTRO && current < N_END) jumpTo(N_END, at); return; }
        if (current < 0) return;

        if (msg.equals(LASER_CHARGING)) { if (current == M_CRYSTALS) jumpTo(M_LURE, at); return; }
        if (MAXOR_STUN.contains(msg)) {
            if (current == M_CRYSTALS || current == M_LURE) jumpTo(M_COOLDOWN, at);
            else if (current == M_COOLDOWN && at.tick() - starts[M_COOLDOWN].tick() >= MIN_HIT_GAP) jumpTo(M_KILL, at);
            return;
        }
        if (STORM_LIGHTNING.contains(msg)) { if (current == S_OPENING && lightning == null) { lightning = at; ticks = 0; } return; }
        if (STORM_CRUSHED.contains(msg)) {
            if (current == S_OPENING || current == S_CRUSH1) jumpTo(S_PIN, at);
            else if (current == S_CRUSH2) jumpTo(S_KILL, at);
            else if (current == S_FLIGHT) { jumpTo(S_CRUSH2, at); jumpTo(S_KILL, at); }
            return;
        }
        if (msg.equals(STORM_ENRAGED)) { if (current == S_PIN) jumpTo(S_FLIGHT, at); return; }
        if (msg.equals(STORM_DEAD)) { if (current >= S_OPENING && current < S_ANIMATION) jumpTo(S_ANIMATION, at); return; }
        if (msg.equals(NECRON_ARGH)) { if (current >= N_INTRO && current <= N_LOCK1) jumpTo(N_KILL, at); return; }
        if (msg.equals(GATE_DESTROYED)) {
            if (current >= T_S1 && current <= T_S3) {
                if (gateWaiting) jumpTo(current + 1, at); else gateBlown = true;
            }
            return;
        }
        if (current < T_S1 || current > T_S4) return;
        Matcher m = SECTION_DONE.matcher(msg);
        if (!m.find()) return;
        if (!m.group(2).equals(m.group(3))) return;
        if (current == T_S4) { jumpTo(G_LEAPS, at); watchingCore = true; }
        else if (gateBlown) jumpTo(current + 1, at);
        else gateWaiting = true;
    }

    private void jumpTo(int step, Stamp at) {
        ticks = 0;
        gateBlown = false;
        gateWaiting = false;
        current = step;
        starts[step] = at;
    }
}
