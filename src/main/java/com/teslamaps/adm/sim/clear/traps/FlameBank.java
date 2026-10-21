package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/FlameBank.kt

import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The flamethrowers of one part of a room: on every beat one or more emitters shoot a stream that is live for liveTicks ticks; a player in
 * it is hit once per shot. Never the emitter that just fired when another can. Runs on the room's clock. Pure.
 */
public class FlameBank implements Hazard {
    public final FlameRule rule;
    public final FlameStream stream;

    private static class Live {
        final Emitter emitter;
        final int start;
        final double[] box;
        final HashSet<UUID> hit = new HashSet<>();

        Live(Emitter emitter, int start, double[] box) { this.emitter = emitter; this.start = start; this.box = box; }
    }

    private final KRandom rng;
    private final int phase;
    private final ArrayList<Live> live = new ArrayList<>();
    private Set<Integer> last = Set.of();

    public FlameBank(FlameRule rule, FlameStream stream, long seed) {
        this.rule = rule;
        this.stream = stream;
        this.rng = new KRandom(seed);
        this.phase = rng.nextInt(stream.period);
    }

    @Override
    public void tick(int clock, List<PlayerPos> players, HazardOut out) {
        Iterator<Live> it = live.iterator();
        while (it.hasNext()) {
            Live s = it.next();
            int age = clock - s.start;
            if (age > stream.liveTicks) { it.remove(); continue; }
            if (age < 1) continue;
            out.flames.add(new FlameFx(s.emitter, stream.reach * (age - 1) / stream.liveTicks, stream.reach * age / stream.liveTicks));
            for (PlayerPos p : players) if (overlaps(p, s.box) && s.hit.add(p.id)) out.hits.add(new HazardHit(p.id, HazardKind.FLAME));
        }
        if (Math.floorMod(clock + phase, stream.period) != 0) return;
        List<PlayerPos> awake;
        if (rule.region == null) awake = players;
        else {
            awake = new ArrayList<>();
            for (PlayerPos p : players) if (inRegion(p, rule.region)) awake.add(p);
        }
        if (awake.isEmpty()) return;
        double range = rule.pickRange != null ? rule.pickRange : stream.range;
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < rule.emitters.size(); i++) {
            for (PlayerPos p : awake) if (near(p, rule.emitters.get(i), range)) { candidates.add(i); break; }
        }
        if (candidates.isEmpty()) return;
        int n = Math.min(rule.perBeatMax > rule.perBeatMin ? rng.nextInt(rule.perBeatMin, rule.perBeatMax + 1) : rule.perBeatMin, candidates.size());
        List<Integer> fresh = new ArrayList<>();
        for (int c : candidates) if (!last.contains(c)) fresh.add(c);
        List<Integer> pool = new ArrayList<>(fresh.size() >= n ? fresh : candidates);
        KRandom.shuffle(pool, rng);
        List<Integer> chosen = new ArrayList<>(pool.subList(0, Math.min(n, pool.size())));
        last = new HashSet<>(chosen);
        for (int i : chosen) {
            Emitter e = rule.emitters.get(i);
            live.add(new Live(e, clock, box(e)));
            out.cues.add(new SoundCue(HazardSound.FLAME_SHOOT, e.x + 0.5 + e.dx() * 0.5, e.y + 0.5 + e.dy() * 0.5, e.z + 0.5 + e.dz() * 0.5));
        }
    }

    /** The cells the stream of e fills: reach blocks out from the face, halfWidth to each side, height tall. */
    public double[] box(Emitter e) {
        double hw = stream.halfWidth; double r = stream.reach;
        double x0, x1, y0, y1, z0, z1;
        if (e.dx() != 0) {
            x0 = e.dx() > 0 ? e.x + 1.0 : e.x - r; x1 = x0 + r; z0 = e.z + 0.5 - hw; z1 = e.z + 0.5 + hw; y0 = e.y; y1 = y0 + stream.height;
        } else if (e.dz() != 0) {
            z0 = e.dz() > 0 ? e.z + 1.0 : e.z - r; z1 = z0 + r; x0 = e.x + 0.5 - hw; x1 = e.x + 0.5 + hw; y0 = e.y; y1 = y0 + stream.height;
        } else {
            y0 = e.dy() > 0 ? e.y + 1.0 : e.y - r; y1 = y0 + r; x0 = e.x + 0.5 - hw; x1 = e.x + 0.5 + hw; z0 = e.z + 0.5 - hw; z1 = e.z + 0.5 + hw;
        }
        return new double[]{x0, y0, z0, x1, y1, z1};
    }

    private static boolean overlaps(PlayerPos p, double[] b) {
        return p.x + HALF > b[0] && p.x - HALF < b[3] && p.y + HEIGHT > b[1] && p.y < b[4] && p.z + HALF > b[2] && p.z - HALF < b[5];
    }

    private static boolean inRegion(PlayerPos p, int[] r) {
        return p.x >= r[0] && p.x < r[3] + 1 && p.y >= r[1] - 0.5 && p.y < r[4] + 1 && p.z >= r[2] && p.z < r[5] + 1;
    }

    private static boolean near(PlayerPos p, Emitter e, double range) {
        double dx = p.x - (e.x + 0.5); double dz = p.z - (e.z + 0.5);
        return dx * dx + dz * dz <= range * range && Math.abs(p.y - e.y) <= 8.0;
    }

    private static final double HALF = 0.3;
    private static final double HEIGHT = 1.8;
}
