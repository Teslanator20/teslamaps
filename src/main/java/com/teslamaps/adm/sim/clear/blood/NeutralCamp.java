// Ported from ADM's p3sim/clear/blood/NeutralCamp.kt
package com.teslamaps.adm.sim.clear.blood;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.run.api.MobSpec;
import com.teslamaps.adm.sim.run.plugin.BloodCamp;
import com.teslamaps.adm.sim.run.plugin.BloodCampHost;
import com.teslamaps.adm.sim.run.plugin.BloodRoomSpec;
import java.util.List;

/**
 * The stand-in camp for a blood room with too few wall heads for the Watcher's camp: a fixed number of ordinary mobs on a fixed
 * timer. Encodes no behaviour of the real Watcher; every number is a setting.
 */
public class NeutralCamp implements BloodCamp {
    private final BloodCampHost host;
    private final int count;
    private final int firstDelayTicks;
    private final int intervalTicks;
    /** Mob-pack archetype ids to draw from. */
    private final List<String> kinds;
    private int released = 0;

    public NeutralCamp(BloodCampHost host, int count, int firstDelayTicks, int intervalTicks, List<String> kinds) {
        this.host = host;
        this.count = count;
        this.firstDelayTicks = firstDelayTicks;
        this.intervalTicks = intervalTicks;
        this.kinds = kinds;
    }

    @Override public void start() {
        host.note("This blood room has too few wall heads for the Watcher's camp, so a plain stand-in runs: " + count + " mobs, one every " + (intervalTicks / 20.0) + " seconds.");
    }

    @Override public void tick() {
        if (released < count && host.ticksSinceOpen() >= firstDelayTicks + released * intervalTicks) {
            release();
            released++;
        }
        reportKills();
    }

    /** Tells the host how many of the 15 counted kills the stand-in has earned (its dead mobs, scaled). */
    private void reportKills() {
        int alive = host.mobsAlive().size();
        int kills = released >= count && alive == 0 ? WatcherStats.KILLS_TO_DEFEAT
                : (Math.max(released - alive, 0) * (WatcherStats.KILLS_TO_DEFEAT - 1) / Math.max(count, 1));
        host.watcherKills(kills);
    }

    private void release() {
        BloodRoomSpec r = host.room();
        List<double[]> cells = r.mobCells();
        double x, y, z;
        if (!cells.isEmpty()) {
            double[] c = cells.get(host.random().nextInt(cells.size()));
            x = c[0]; y = c[1]; z = c[2];
        } else {
            // a ring around the Watcher, a different spot for each mob
            double a = 2.0 * Math.PI * (released + host.random().nextDouble()) / Math.max(count, 1);
            x = r.watcherX() + RING_RADIUS * Math.cos(a);
            y = r.watcherY();
            z = r.watcherZ() + RING_RADIUS * Math.sin(a);
        }
        int i = host.random().nextInt(Math.max(kinds.size(), 1));
        if (i < 0 || i >= kinds.size()) return;
        String kind = kinds.get(i);
        host.spawnMob(new MobSpec(kind, x, y, z, r.roomIndex(), false, false));
    }

    @Override public JsonObject snapshot() {
        JsonObject o = new JsonObject();
        o.addProperty("released", released);
        return o;
    }

    @Override public void restore(JsonObject data) {
        JsonElement e = data.get("released");
        released = e != null ? e.getAsInt() : 0;
    }

    @Override public boolean finished() { return released >= count && host.mobsAlive().isEmpty(); }

    /** Blocks from the Watcher the stand-in mobs appear. */
    public static final double RING_RADIUS = 6.0;

    /** Mob-pack archetypes the stand-in draws from (ordinary Floor 7 mobs). */
    public static final List<String> DEFAULT_KINDS = List.of("zombie_soldier", "skeleton_soldier", "zombie_knight", "skeleton_master");
}
