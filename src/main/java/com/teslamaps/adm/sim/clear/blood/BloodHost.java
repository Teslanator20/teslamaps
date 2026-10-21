// Ported from ADM's p3sim/clear/blood/BloodHost.kt
package com.teslamaps.adm.sim.clear.blood;

import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.api.MobRecord;
import com.teslamaps.adm.sim.run.api.MobSpec;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.run.plugin.BloodCampHost;
import com.teslamaps.adm.sim.run.plugin.BloodRoomSpec;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** What a camp may ask of the public side. Server thread only; mobsAlive covers only mobs spawned through this host. */
public class BloodHost implements BloodCampHost {
    private final Run run;
    private final BloodRoomSpec room;
    private final BloodFlow flow;
    private final WallHeads heads;
    private final WatcherEntity watcher;
    private final KRandom random;

    private final Set<UUID> spawned = new LinkedHashSet<>();

    public BloodHost(Run run, BloodRoomSpec room, BloodFlow flow, WallHeads heads, WatcherEntity watcher) {
        this.run = run;
        this.room = room;
        this.flow = flow;
        this.heads = heads;
        this.watcher = watcher;
        this.random = run.subRandom("blood");
    }

    @Override public Run run() { return run; }

    @Override public BloodRoomSpec room() { return room; }

    @Override public KRandom random() { return random; }

    @Override public int ticksSinceOpen() { return flow.ticksSinceOpen(run.serverTick); }

    @Override public UUID spawnMob(MobSpec spec) {
        MobRecord rec = Services.mobs().spawn(run, spec);
        if (rec == null) return null;
        spawned.add(rec.id);
        return rec.id;
    }

    /** Counts an existing mob (a savepoint brought it back) among this host's mobs. */
    public void adopt(UUID id) { spawned.add(id); }

    @Override public void removeMob(UUID id) {
        spawned.remove(id);
        Services.mobs().remove(run, id);
    }

    @Override public List<UUID> mobsAlive() {
        List<UUID> out = new ArrayList<>();
        for (UUID id : spawned) {
            MobRecord m = Services.mobs().mob(id);
            if (m != null && m.alive) out.add(id);
        }
        return out;
    }

    @Override public List<UUID> partyInRoom() {
        List<UUID> out = new ArrayList<>();
        for (PlayerRecord p : run.state.players.values()) if (inRoom(p.x, p.z)) out.add(p.uuid);
        return out;
    }

    /** Whether the blood room stands over the column (x, z). */
    public boolean inRoom(double x, double z) {
        PlacedRoom r = run.plan.roomAtBlock(x, z);
        return r != null && r.index == room.roomIndex();
    }

    @Override public void setHead(int slot, String textureKey) { heads.set(slot, textureKey); }

    @Override public void clearHead(int slot) { heads.set(slot, HeadTextures.NONE); }

    @Override public void flyHead(int slot, double x, double y, double z, int ticks) { heads.fly(slot, x, y, z, ticks); }

    @Override public void moveWatcher(double x, double y, double z) { watcher.moveTo(x, y, z); }

    @Override public void moveWatcher(double x, double y, double z, float yaw) { watcher.moveTo(x, y, z, yaw); }

    @Override public void watcherKills(int kills) {
        int before = flow.kills;
        flow.noteKills(kills, run.serverTick);
        if (flow.kills != before) watcher.setKills(flow.kills);
    }

    @Override public void watcherSays(String line) { watcher.say(line); }

    @Override public void note(String text) { Sim.note(text); }

    /** Once per server tick: moves the thrown heads. */
    public void tick() { heads.tick(); }

    /** Removes everything this host spawned that is still around. */
    public void cleanUp() {
        for (UUID id : new ArrayList<>(spawned)) Services.mobs().remove(run, id);
        spawned.clear();
    }
}
