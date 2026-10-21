// Ported from ADM's p3sim/run/RunState.kt
package com.teslamaps.adm.sim.run;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** The hub: facts, one writer per field, no Minecraft. Server thread only. */
public class RunState {
    public final DungeonPlan plan;
    private final RunBus bus;
    private final Supplier<Integer> clock;

    public final List<RoomRecord> rooms;
    public final List<DoorRecord> doors;
    public final KeyState keys = new KeyState();
    public final TileCounters counters = new TileCounters();
    public final SecretLedger secrets = new SecretLedger();
    public final DeathLedger deaths = new DeathLedger();
    public final ScoreState score = new ScoreState();
    public final List<PuzzleRecord> puzzles = new ArrayList<>();
    public final Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
    public final Milestones milestones = new Milestones();

    public RunState(DungeonPlan plan, RunBus bus, Supplier<Integer> clock) {
        this.plan = plan;
        this.bus = bus;
        this.clock = clock;
        List<RoomRecord> rs = new ArrayList<>();
        for (PlacedRoom r : plan.rooms) rs.add(new RoomRecord(r));
        this.rooms = rs;
        List<DoorRecord> ds = new ArrayList<>();
        for (PlacedDoor d : plan.doors) ds.add(new DoorRecord(d));
        this.doors = ds;
        this.secrets.teamMax = plan.secretsTotal();
    }

    public RoomRecord room(int index) { return index >= 0 && index < rooms.size() ? rooms.get(index) : null; }

    public DoorRecord door(int index) { return index >= 0 && index < doors.size() ? doors.get(index) : null; }

    /** The room record a world position stands in, or null. */
    public RoomRecord roomAt(double x, double z) {
        PlacedRoom p = plan.roomAtBlock(x, z);
        return p != null ? rooms.get(p.index) : null;
    }

    /** The party members that are not ghosts. */
    public List<PlayerRecord> alivePlayers() {
        List<PlayerRecord> out = new ArrayList<>();
        for (PlayerRecord p : players.values()) if (!p.ghost) out.add(p);
        return out;
    }

    /**
     * Moves a room's check mark and posts the matching event (RoomDiscovered first off UNDISCOVERED, then RoomCleared/RoomGreen/RoomFailed).
     * Counters are not touched. Doors/state module only.
     */
    public void setCheck(int roomIndex, CheckMark to) {
        RoomRecord r = room(roomIndex);
        if (r == null) return;
        if (r.check == to) return;
        int t = clock.get();
        boolean wasUndiscovered = r.check == CheckMark.UNDISCOVERED;
        r.check = to;
        if (wasUndiscovered && to != CheckMark.UNDISCOVERED) {
            if (r.tDiscovered == null) r.tDiscovered = t;
            bus.post(new RoomDiscovered(t, roomIndex));
        }
        switch (to) {
            case WHITE -> { if (r.tWhite == null) r.tWhite = t; bus.post(new RoomCleared(t, roomIndex)); }
            case GREEN -> { if (r.tGreen == null) r.tGreen = t; bus.post(new RoomGreen(t, roomIndex)); }
            case RED -> { if (r.tRed == null) r.tRed = t; bus.post(new RoomFailed(t, roomIndex)); }
            default -> {}
        }
    }

    /** Absolute server ticks are not saved. */
    public JsonObject snapshot() { return RunStateJson.write(this); }

    /** Restores in place; posts no events. */
    public void restore(JsonObject data) { RunStateJson.read(this, data); }
}
