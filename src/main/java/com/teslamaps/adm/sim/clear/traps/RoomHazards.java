package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/RoomHazards.kt

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.clear.puzzles.hard.BlockEdit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The moving and firing hazards of one room: crushers, tripwires and extras (falling floor, flamethrowers). Runs on its own clock that
 * only moves while somebody is in the room. Pure: edits, hits and sounds come out, the caller applies them.
 */
public class RoomHazards {
    public final List<Crusher> crushers;
    public final List<TripLine> lines;
    public final boolean linesHurt;
    public final boolean linesClick;
    public final List<Hazard> extras;

    /** Ticks the clock has run (only ticks with a player in the room). */
    public int clock = 0;

    private final int[] extension;
    private final int[] hitSoundAt;
    private final int[] readyAt;
    private final int[] releaseAt;

    public RoomHazards(List<Crusher> crushers, List<TripLine> lines, boolean linesHurt, boolean linesClick, List<Hazard> extras) {
        this.crushers = crushers;
        this.lines = lines;
        this.linesHurt = linesHurt;
        this.linesClick = linesClick;
        this.extras = extras;
        this.extension = new int[crushers.size()];
        this.hitSoundAt = new int[crushers.size()];
        Arrays.fill(hitSoundAt, -HIT_SOUND_GAP);
        this.readyAt = new int[lines.size()];
        this.releaseAt = new int[lines.size()];
        Arrays.fill(releaseAt, NEVER);
    }

    public RoomHazards(List<Crusher> crushers, List<TripLine> lines, boolean linesHurt, boolean linesClick) { this(crushers, lines, linesHurt, linesClick, List.of()); }

    public RoomHazards(List<Crusher> crushers, List<TripLine> lines, boolean linesHurt) { this(crushers, lines, linesHurt, linesHurt, List.of()); }

    public RoomHazards(List<Crusher> crushers, List<TripLine> lines) { this(crushers, lines, true, true, List.of()); }

    public static class Result {
        public final List<BlockEdit> edits;
        public final List<HazardHit> hits;
        public final List<SoundCue> cues;
        public final List<int[]> debris;
        public final List<ArrowRule> volleys;
        public final List<FlameFx> flames;

        public Result(List<BlockEdit> edits, List<HazardHit> hits, List<SoundCue> cues, List<int[]> debris, List<ArrowRule> volleys, List<FlameFx> flames) {
            this.edits = edits;
            this.hits = hits;
            this.cues = cues;
            this.debris = debris;
            this.volleys = volleys;
            this.flames = flames;
        }

        public Result(List<BlockEdit> edits, List<HazardHit> hits, List<SoundCue> cues, List<int[]> debris, List<ArrowRule> volleys) { this(edits, hits, cues, debris, volleys, List.of()); }

        public Result(List<BlockEdit> edits, List<HazardHit> hits, List<SoundCue> cues, List<int[]> debris) { this(edits, hits, cues, debris, List.of(), List.of()); }

        public Result(List<BlockEdit> edits, List<HazardHit> hits, List<SoundCue> cues) { this(edits, hits, cues, List.of(), List.of(), List.of()); }

        public Result(List<BlockEdit> edits, List<HazardHit> hits) { this(edits, hits, List.of(), List.of(), List.of(), List.of()); }
    }

    public Result tick(List<PlayerPos> players) {
        clock++;
        HazardOut out = new HazardOut();
        for (int i = 0; i < crushers.size(); i++) {
            Crusher c = crushers.get(i);
            int k = c.extensionAt(clock);
            if (k != extension[i]) {
                out.edits.addAll(c.edits(extension[i], k));
                if (k == c.sweep && k > extension[i]) { double[] f = c.tipFace(k); out.cues.add(new SoundCue(HazardSound.CRUSHER_SLAM, f[0], f[1], f[2])); }
                extension[i] = k;
            }
            if (k == 0) continue;
            int[] box = c.armBox(k);
            for (PlayerPos p : players) if (overlaps(p, box)) {
                out.hits.add(new HazardHit(p.id, HazardKind.CRUSHER));
                if (clock - hitSoundAt[i] >= HIT_SOUND_GAP) {
                    hitSoundAt[i] = clock;
                    double[] f = c.tipFace(k);
                    out.cues.add(new SoundCue(HazardSound.CRUSHER_SLAM, f[0], f[1], f[2]));
                }
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            TripLine l = lines.get(i);
            if (releaseAt[i] == clock) { if (linesClick) hooks(l, HazardSound.LEVER_OFF, out); releaseAt[i] = NEVER; }
            if (clock < readyAt[i]) continue;
            List<PlayerPos> touching = new ArrayList<>();
            for (PlayerPos p : players) if (l.touches(p.x, p.y, p.z)) touching.add(p);
            if (touching.isEmpty()) continue;
            readyAt[i] = clock + HOOK_TICKS;
            releaseAt[i] = clock + HOOK_TICKS;
            out.touched.add(i);
            if (linesClick) hooks(l, HazardSound.LEVER_ON, out);
            if (linesHurt) for (PlayerPos p : touching) {
                out.hits.add(new HazardHit(p.id, HazardKind.TRIPWIRE, i));
                out.cues.add(new SoundCue(HazardSound.TRIPWIRE_BLAST, p.x, p.y, p.z));
            }
        }
        for (Hazard h : extras) h.tick(clock, players, out);
        return new Result(out.edits, out.hits, out.cues, out.debris, out.volleys, out.flames);
    }

    /** A sound at both hooks of l. */
    private void hooks(TripLine l, HazardSound sound, HazardOut out) {
        for (int end : new int[]{l.from, l.to}) {
            double x = l.axis == 'x' ? end + 0.5 : l.fixed + 0.5;
            double z = l.axis == 'x' ? l.fixed + 0.5 : end + 0.5;
            out.cues.add(new SoundCue(sound, x, (double) l.y, z));
        }
    }

    /** Every hazard's changes in their current state, for re-applying after a savepoint restore. */
    public List<BlockEdit> currentEdits() {
        List<BlockEdit> out = new ArrayList<>();
        for (int i = 0; i < crushers.size(); i++) if (extension[i] != 0) out.addAll(crushers.get(i).edits(0, extension[i]));
        for (Hazard h : extras) out.addAll(h.currentEdits());
        return out;
    }

    public JsonObject snapshot() {
        JsonObject o = new JsonObject();
        o.addProperty("clock", clock);
        JsonArray ready = new JsonArray();
        for (int v : readyAt) ready.add(v);
        o.add("ready", ready);
        JsonArray release = new JsonArray();
        for (int v : releaseAt) release.add(v);
        o.add("release", release);
        for (int i = 0; i < extras.size(); i++) {
            JsonObject x = new JsonObject();
            extras.get(i).snapshot(x);
            o.add("x" + i, x);
        }
        return o;
    }

    /** Restores the clock; the crushers' extensions are re-derived from it (the caller applies currentEdits). */
    public void restore(JsonObject o) {
        JsonElement c = o.get("clock");
        clock = c != null ? c.getAsInt() : 0;
        JsonArray ready = o.getAsJsonArray("ready");
        if (ready != null) for (int i = 0; i < ready.size(); i++) if (i < readyAt.length) readyAt[i] = ready.get(i).getAsInt();
        JsonArray release = o.getAsJsonArray("release");
        if (release != null) for (int i = 0; i < release.size(); i++) if (i < releaseAt.length) releaseAt[i] = release.get(i).getAsInt();
        for (int i = 0; i < crushers.size(); i++) extension[i] = crushers.get(i).extensionAt(clock);
        for (int i = 0; i < extras.size(); i++) {
            JsonObject x = o.getAsJsonObject("x" + i);
            if (x != null) extras.get(i).restore(x);
        }
    }

    /** Whether the player's box meets the cells of box (inclusive cell corners x0, y0, z0, x1, y1, z1). */
    private static boolean overlaps(PlayerPos p, int[] box) {
        return p.x + HALF > box[0] && p.x - HALF < box[3] + 1 && p.y + HEIGHT > box[1] && p.y < box[4] + 1 && p.z + HALF > box[2] && p.z - HALF < box[5] + 1;
    }

    /** Ticks the hooks stay powered after the string is touched (recorded 9 to 11). */
    public static final int HOOK_TICKS = 10;

    /** Ticks between two hit sounds of one crusher (the damage service's immunity window). */
    public static final int HIT_SOUND_GAP = 10;
    private static final int NEVER = Integer.MIN_VALUE;
    private static final double HALF = 0.3;
    private static final double HEIGHT = 1.8;
}
