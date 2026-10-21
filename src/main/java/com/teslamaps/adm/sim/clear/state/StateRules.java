// Ported from ADM's p3sim/clear/state/StateRules.kt
package com.teslamaps.adm.sim.clear.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.pack.runstate.ChestClear;
import com.teslamaps.adm.sim.pack.runstate.Dist;
import com.teslamaps.adm.sim.pack.runstate.DoorRules;
import com.teslamaps.adm.sim.pack.runstate.RoomRun;
import com.teslamaps.adm.sim.pack.runstate.RunStatePack;
import com.teslamaps.adm.sim.pack.runstate.TypeStates;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public class StateRules {
    public final int startLeadTicks;
    public final int entranceFallAfterStart1;
    public final int freeDoorOffset;
    public final Map<DoorKind, Integer> barrierTicks;
    public final int keyDoorBarrierDelay;
    public final int firstBlockToRestTicks;
    public final int autoCollectTicks;
    public final double pickupInstantFrac;
    public final Dist pickupDelayed;
    public final double standYBelowSurface;
    public final double standYDefault;
    public final int entranceNtoG;
    private final Map<String, Dist> checkTicks;
    private final Function<String, Dist> roomToWhite;
    public final Map<String, ChestClear> chestClears;
    public final int bloodGreenAfterPass;
    public final int bloodGreenAfterPortal;

    public StateRules(int startLeadTicks, int entranceFallAfterStart1, int freeDoorOffset, Map<DoorKind, Integer> barrierTicks,
                      int keyDoorBarrierDelay, int firstBlockToRestTicks, int autoCollectTicks, double pickupInstantFrac, Dist pickupDelayed,
                      double standYBelowSurface, double standYDefault, int entranceNtoG, Map<String, Dist> checkTicks,
                      Function<String, Dist> roomToWhite, Map<String, ChestClear> chestClears, int bloodGreenAfterPass, int bloodGreenAfterPortal) {
        this.startLeadTicks = startLeadTicks;
        this.entranceFallAfterStart1 = entranceFallAfterStart1;
        this.freeDoorOffset = freeDoorOffset;
        this.barrierTicks = barrierTicks;
        this.keyDoorBarrierDelay = keyDoorBarrierDelay;
        this.firstBlockToRestTicks = firstBlockToRestTicks;
        this.autoCollectTicks = autoCollectTicks;
        this.pickupInstantFrac = pickupInstantFrac;
        this.pickupDelayed = pickupDelayed;
        this.standYBelowSurface = standYBelowSurface;
        this.standYDefault = standYDefault;
        this.entranceNtoG = entranceNtoG;
        this.checkTicks = checkTicks;
        this.roomToWhite = roomToWhite;
        this.chestClears = chestClears;
        this.bloodGreenAfterPass = bloodGreenAfterPass;
        this.bloodGreenAfterPortal = bloodGreenAfterPortal;
    }

    public StateRules() {
        this(START_LEAD_TICKS, ENTRANCE_FALL_AFTER_START1, FREE_DOOR_OFFSET, DEFAULT_BARRIER, KEY_DOOR_BARRIER_DELAY, 0, 200,
                PICKUP_INSTANT_FRAC, PICKUP_DELAYED, STAND_Y_BELOW_SURFACE, STAND_Y_DEFAULT, ENTRANCE_N_TO_G, Collections.emptyMap(),
                name -> null, Collections.emptyMap(), BLOOD_GREEN_AFTER_PASS, BLOOD_GREEN_AFTER_PORTAL);
    }

    public int barrierFor(DoorKind kind) {
        Integer v = barrierTicks.get(kind);
        if (v != null) return v;
        Integer w = barrierTicks.get(DoorKind.WITHER);
        return w != null ? w : 14;
    }

    public ChestClear chestClear(String roomName) {
        ChestClear c = chestClears.get(roomName.replace("​", ""));
        return c != null && c.cell != null ? c : null;
    }

    public int chestOpenDelay(String roomName, double u) {
        ChestClear c = chestClear(roomName);
        Dist d = c == null ? null : c.afterOpen;
        if (d != null && d.advisory()) d = null;
        double v = d != null ? d.sample(u) : CHEST_OPEN_DELAY_TICKS;
        return Math.max((int) Math.round(v), 0);
    }

    public int whiteDelay(String roomName, String type, double u) {
        Dist d = roomToWhite.apply(roomName);
        if (d != null && d.advisory()) d = null;
        if (d == null) {
            d = checkTicks.get(type.toLowerCase(Locale.ROOT) + ".NtoW");
            if (d != null && d.advisory()) d = null;
        }
        if (d == null) {
            d = checkTicks.get(type.toLowerCase(Locale.ROOT) + ".NtoG");
            if (d != null && d.advisory()) d = null;
        }
        double v = d != null ? d.sample(u) : SYNTH_WHITE_TICKS;
        return Math.max((int) Math.round(v), 1);
    }

    public static final int START_LEAD_TICKS = 20;
    public static final int ENTRANCE_FALL_AFTER_START1 = 22;
    public static final int FREE_DOOR_OFFSET = -1;
    public static final int KEY_DOOR_BARRIER_DELAY = 1;
    public static final int DEBRIS_RIDE_DELAY = 1;
    public static final int DEBRIS_SINK_START = 7;
    public static final int DEBRIS_REMOVE = 23;
    public static final double DEBRIS_DY = 0.34375;
    public static final double DEBRIS_FIRST_DROP = 0.306;
    public static final double DEBRIS_DROP_PER_TICK = 0.3125;
    public static final int DEBRIS_LAST_DROP_TICK = 19;
    public static final float STAND_SPIN_DEG = 2.0f;
    public static final double STAND_BOB_MIN = 0.04;
    public static final double STAND_BOB_STEP = 0.01;
    public static final int STAND_BOB_HALF_PERIOD = 18;
    public static final double MODEL_STAND_BELOW = 0.25;
    public static final int NAME_STAND_LINGER = 2;
    public static final double PICKUP_INSTANT_FRAC = 0.669;
    public static final Dist PICKUP_DELAYED = new Dist(190, new double[]{5.0, 25.0, 50.0, 75.0, 95.0}, new double[]{2.0, 4.0, 8.0, 13.0, 26.0}, null, null);
    public static final double STAND_Y_BELOW_SURFACE = 0.46875;
    public static final double STAND_Y_DEFAULT = 68.53125;
    public static final int ENTRANCE_N_TO_G = 5;
    public static final double CHEST_OPEN_DELAY_TICKS = 2.0;
    public static final int BLOOD_GREEN_AFTER_PASS = 90;
    public static final int BLOOD_GREEN_AFTER_PORTAL = 1;
    public static final double SYNTH_WHITE_TICKS = 50.0;
    public static final Map<DoorKind, Integer> DEFAULT_BARRIER;

    static {
        Map<DoorKind, Integer> m = new EnumMap<>(DoorKind.class);
        m.put(DoorKind.WITHER, 14);
        m.put(DoorKind.FAIRY, 14);
        m.put(DoorKind.BLOOD, 14);
        m.put(DoorKind.ENTRANCE, 15);
        DEFAULT_BARRIER = Collections.unmodifiableMap(m);
    }

    private static Integer mode(JsonObject fall, String kind) {
        if (fall == null) return null;
        JsonObject byKind = fall.getAsJsonObject("barrierTicksByKind");
        if (byKind == null) return null;
        JsonObject k = byKind.getAsJsonObject(kind);
        if (k == null) return null;
        JsonObject ticks = k.getAsJsonObject("ticks");
        if (ticks == null) return null;
        Map.Entry<String, JsonElement> best = null;
        double bestV = 0;
        for (Map.Entry<String, JsonElement> e : ticks.entrySet()) {
            double v = e.getValue().getAsDouble();
            if (best == null || v > bestV) { best = e; bestV = v; }
        }
        if (best == null) return null;
        return toIntOrNull(best.getKey());
    }

    private static Integer toIntOrNull(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    public static StateRules fromPack(RunStatePack pack) {
        DoorRules doors = pack == null ? null : pack.doors;
        JsonObject raw = doors == null ? null : doors.raw;
        JsonObject fall = raw == null ? null : raw.getAsJsonObject("fall");
        HashMap<DoorKind, Integer> modes = new HashMap<>(DEFAULT_BARRIER);
        Integer mw = mode(fall, "wither");
        if (mw != null) { modes.put(DoorKind.WITHER, mw); modes.put(DoorKind.FAIRY, mw); }
        Integer mb = mode(fall, "blood");
        if (mb != null) modes.put(DoorKind.BLOOD, mb);
        Integer me = mode(fall, "entrance");
        if (me != null) modes.put(DoorKind.ENTRANCE, me);
        Integer offset = null;
        JsonObject entrance = raw == null ? null : raw.getAsJsonObject("entrance");
        JsonObject ffo = entrance == null ? null : entrance.getAsJsonObject("freeFairyOffset");
        if (ffo != null) {
            Dist dd = Dist.parse(ffo, Dist.DEFAULT_PCT);
            if (dd != null) offset = (int) Math.round(dd.median());
        }
        HashMap<String, Dist> ticks = new HashMap<>();
        if (pack != null && pack.checkStates != null) {
            for (Map.Entry<String, TypeStates> te : pack.checkStates.types.entrySet()) {
                for (Map.Entry<String, Dist> de : te.getValue().ticks.entrySet()) ticks.put(te.getKey() + "." + de.getKey(), de.getValue());
            }
        }
        int startLead = doors != null && doors.startLeadTicks != null ? doors.startLeadTicks : START_LEAD_TICKS;
        int entranceFall = doors != null && doors.entranceFallAfterStart1 != null
                ? (int) Math.round(doors.entranceFallAfterStart1.median()) : ENTRANCE_FALL_AFTER_START1;
        int firstBlock = 0;
        JsonElement fb = fall == null ? null : fall.get("firstBlockToRestTicks");
        if (fb != null && fb.isJsonPrimitive()) firstBlock = fb.getAsInt();
        Dist delayed = doors != null && doors.pickupDelayed != null && !doors.pickupDelayed.advisory() ? doors.pickupDelayed : PICKUP_DELAYED;
        Map<String, ChestClear> chests = pack == null || pack.chestClears == null ? Collections.emptyMap() : pack.chestClears;
        return new StateRules(
                startLead,
                entranceFall,
                offset != null ? offset : FREE_DOOR_OFFSET,
                modes,
                KEY_DOOR_BARRIER_DELAY,
                firstBlock,
                doors != null ? doors.autoCollectTicks : 200,
                doors != null ? doors.pickupInstantFrac : PICKUP_INSTANT_FRAC,
                delayed,
                STAND_Y_BELOW_SURFACE,
                STAND_Y_DEFAULT,
                ENTRANCE_N_TO_G,
                ticks,
                name -> {
                    if (pack == null) return null;
                    RoomRun r = pack.room(name);
                    return r == null ? null : r.toWhite;
                },
                chests,
                BLOOD_GREEN_AFTER_PASS,
                BLOOD_GREEN_AFTER_PORTAL
        );
    }
}
