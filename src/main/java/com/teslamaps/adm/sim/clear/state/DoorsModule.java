// Ported from ADM's p3sim/clear/state/DoorsModule.kt
package com.teslamaps.adm.sim.clear.state;

import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsModule;
import com.teslamaps.adm.sim.run.DoorRecord;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.EndReason;
import com.teslamaps.adm.sim.run.KeyKind;
import com.teslamaps.adm.sim.run.KeyState;
import com.teslamaps.adm.sim.run.PlacedDoor;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunEvent;
import com.teslamaps.adm.sim.run.TileCounters;
import com.teslamaps.adm.sim.run.api.DoorOpenResult;
import com.teslamaps.adm.sim.run.api.DoorService;
import com.teslamaps.adm.sim.run.api.KeyPlan;
import com.teslamaps.adm.sim.settings.BoolSetting;
import com.teslamaps.adm.sim.settings.NumSetting;
import com.teslamaps.adm.sim.settings.SimSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DoorsModule implements AdsModule, DoorService {
    public static final DoorsModule INSTANCE = new DoorsModule();

    private DoorsModule() {}

    @Override public String id() { return "doors"; }
    @Override public String owner() { return "doors"; }

    private static final Logger log = LoggerFactory.getLogger("engineerclient");

    static Supplier<StateEffects> effects = () -> McStateEffects.INSTANCE;

    public static volatile StateController controller = null;

    private static NumSetting countdownDelayS = null;
    private static NumSetting pickupRadiusS = null;
    private static BoolSetting synthesiseS = null;

    @Override public void registerSettings() {
        countdownDelayS = SimSettings.num("Doors Arrival Wait", 60, 0, 400, 5, " ticks", "How long after a run starts the \"Starting in 4 seconds\" count begins. Hypixel: a median 57 ticks after the potions line you read on arriving.");
        pickupRadiusS = SimSettings.num("Doors Key Pickup Radius", 2.5, 1.0, 6.0, 0.5, " blocks", "How close you or a teammate has to be to take a Wither or Blood Key off the floor. Not measured: most keys on Hypixel are taken in the tick they appear (by whoever killed the last mob), the rest after about 8 ticks.");
        synthesiseS = SimSettings.bool("Doors Synthesise Clears", true, "A room with no mob that has to die turns white on the measured schedule (median 50 ticks after it is discovered) instead of staying grey. Trap rooms are not on it: they turn white when their end chest is opened.");
    }

    private static StateConfig config() {
        StateConfig d = new StateConfig();
        return new StateConfig(
                countdownDelayS != null ? countdownDelayS.getInt() : d.countdownDelayTicks,
                pickupRadiusS != null ? pickupRadiusS.get() : d.pickupRadius,
                synthesiseS != null ? synthesiseS.get() : d.synthesiseClears,
                d.keyFallbackTicks
        );
    }

    // ---- lifecycle ----

    @Override public void onRunStart(Run run) {
        StateController c = new StateController(run, StateRules.fromPack(run.packs.runstate), config(), effects.get());
        controller = c;
        c.start();
        MortNpc.spawn(run.plan);
    }

    @Override public void onTick(Run run) {
        StateController c = controller;
        if (c != null) c.tick();
        MortNpc.tick();
    }

    @Override public void onEvent(Run run, RunEvent event) {
        StateController c = controller;
        if (c != null) c.onEvent(event);
    }

    @Override public void onRunEnd(Run run, EndReason reason) {
        StateController c = controller;
        if (c != null) c.end();
        controller = null;
        MortNpc.remove();
    }

    @Override public JsonObject snapshot(Run run) {
        StateController c = controller;
        return c != null ? c.snapshot() : null;
    }

    @Override public void restore(Run run, JsonObject data) {
        StateController c = controller;
        if (c != null) c.restore(data);
        MortNpc.spawn(run.plan);
    }

    // ---- DoorService ----

    @Override public KeyPlan keyPlan(DungeonPlan plan) { return KeyPlans.of(plan); }

    @Override public KeyKind keyForRoom(Run run, int roomIndex) {
        StateController c = controller;
        return c != null ? c.keyForRoom(roomIndex) : null;
    }

    @Override public void dropKey(Run run, int roomIndex, double x, double z) {
        StateController c = controller;
        if (c != null) c.dropKey(roomIndex, x, z);
    }

    @Override public boolean pickUpKey(Run run, UUID by, KeyKind kind, Integer roomIndex, double x, double z) {
        StateController c = controller;
        return c != null && c.pickUpKey(by, kind, roomIndex, x, z);
    }

    @Override public DoorOpenResult tryOpen(Run run, int door, UUID by) {
        StateController c = controller;
        return c != null ? c.tryOpen(door, by) : DoorOpenResult.NOT_A_DOOR;
    }

    @Override public Integer doorAtBlock(Run run, int x, int y, int z) {
        StateController c = controller;
        return c != null ? c.doorAtBlock(x, y, z) : null;
    }

    @Override public boolean touchBlock(Run run, int x, int y, int z, UUID by) {
        StateController c = controller;
        if (c == null) return false;
        Integer door = c.doorAtBlock(x, y, z);
        return door != null && c.touch(door, by);
    }

    public static void completeRoom(Run run, int roomIndex) {
        StateController c = controller;
        if (c != null) c.objectiveDone(roomIndex);
    }

    // ---- clicks ----

    @Override public InteractionResult onUseBlock(Run run, ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        StateController c = controller;
        if (c == null) return null;
        BlockPos pos = hit.getBlockPos();
        Integer door = c.doorAtBlock(pos.getX(), pos.getY(), pos.getZ());
        if (door == null) return null;
        if (!StateEffectsKt.hasBody(run.plan.doors.get(door).kind)) return null;
        c.tryOpen(door, player.getUUID());
        return InteractionResult.SUCCESS;
    }

    // ---- dev commands ----

    private static Integer toIntOrNull(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    @Override public List<AdsCommand> devCommands() {
        return List.of(
                new AdsCommand("doors", "/ads dev doors", "Lists every door with its kind and state, the keys held, and the key stands on the floor.", true, null, ctx -> {
                    Run run = ctx.run(); StateController c = controller;
                    if (run == null || c == null) ctx.reply("No run is on."); else for (String l : describe(run, c)) ctx.reply(l);
                }),
                new AdsCommand("roomstate", "/ads dev roomstate", "Lists every room with its check mark, secrets and who is inside, and the Opened and Completed counters.", true, null, ctx -> {
                    Run run = ctx.run();
                    if (run == null) ctx.reply("No run is on.");
                    else {
                        for (RoomRecord r : run.state.rooms) ctx.reply("#" + r.index() + " " + r.plan.name + " (" + r.plan.type.name().toLowerCase(Locale.ROOT) + ", " + r.plan.tileCount() + "t): "
                                + r.check + " secrets " + r.secretsFound + "/" + r.secretsMax() + " mobs " + r.mobsRequiredAlive + "/" + r.mobsRequired + " inside " + r.occupants.size());
                        TileCounters k = run.state.counters;
                        ctx.reply("Opened " + k.opened + "/" + k.total + ", completed " + k.completed + "/" + k.total + " (" + k.clearedPct() + "%).");
                    }
                }),
                new AdsCommand("givekey", "/ads dev givekey <wither|blood>", "Adds a key to the party count as if it had been picked up.", true, null, ctx -> {
                    StateController c = controller;
                    KeyKind kind = "blood".equalsIgnoreCase(ctx.arg(0)) ? KeyKind.BLOOD : KeyKind.WITHER;
                    if (c == null) ctx.reply("No run is on."); else { c.ledger.pick(kind); ctx.reply("Gave a " + StateText.keyName(kind) + "."); }
                }),
                new AdsCommand("opendoor", "/ads dev opendoor <n>", "Opens door n without a key (barrier, then air 14 ticks later).", true, null, ctx -> {
                    StateController c = controller; Integer n = toIntOrNull(ctx.arg(0));
                    if (c == null || n == null) ctx.reply("Usage: /ads dev opendoor <door number> (see /ads dev doors).");
                    else { c.beginFall(n, ctx.player.getUUID()); ctx.reply("Door " + n + " is falling."); }
                }),
                new AdsCommand("clearroom", "/ads dev clearroom <n>", "Finishes room n's objective now (white, or green with no secret left).", true, null, ctx -> {
                    StateController c = controller; Integer n = toIntOrNull(ctx.arg(0));
                    if (c == null || n == null) ctx.reply("Usage: /ads dev clearroom <room number> (see /ads dev roomstate).");
                    else {
                        c.objectiveDone(n);
                        Run run = ctx.run();
                        Object mark = null;
                        if (run != null && n >= 0 && n < run.state.rooms.size()) mark = run.state.rooms.get(n).check;
                        ctx.reply("Room " + n + ": " + mark + ".");
                    }
                })
        );
    }

    private static List<String> describe(Run run, StateController c) {
        List<String> out = new ArrayList<>();
        KeyState k = run.state.keys;
        out.add("t=" + run.t() + "  keys: wither " + k.witherHeld + " (got " + k.witherObtained + ", used " + k.witherUsed + "), blood "
                + (k.bloodHeld ? "held" : k.bloodUsed ? "used" : "no"));
        out.add("key rooms: wither " + c.keyPlan.witherRooms + ", blood " + c.keyPlan.bloodRoom + "; dropped " + c.ledger.droppedRooms());
        for (PlacedDoor d : run.plan.doors) {
            if (d.kind != DoorKind.NONE && d.kind != DoorKind.NORMAL && d.kind != DoorKind.UNKNOWN) {
                DoorRecord rec = run.state.doors.get(d.index);
                out.add("door " + d.index + " " + d.kind + " between rooms " + d.roomA + " and " + d.roomB + ": " + rec.phase
                        + (rec.openedAt != null ? " since t=" + rec.openedAt : ""));
            }
        }
        for (KeyStand s : c.standList()) out.add("key stand " + s.kind + " in room " + s.room + " at " + String.format(Locale.ROOT, "%.1f", s.x) + ", "
                + String.format(Locale.ROOT, "%.2f", s.y) + ", " + String.format(Locale.ROOT, "%.1f", s.z));
        return out;
    }
}
