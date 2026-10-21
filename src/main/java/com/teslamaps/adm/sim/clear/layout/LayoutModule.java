package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/LayoutModule.kt

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.SimServer;
import com.teslamaps.adm.sim.pack.DoorModel;
import com.teslamaps.adm.sim.pack.PackSet;
import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.pack.layouts.LayoutPack;
import com.teslamaps.adm.sim.pack.rooms.RoomPack;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsModule;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.LayoutPick;
import com.teslamaps.adm.sim.run.LayoutSource;
import com.teslamaps.adm.sim.run.PlanFailure;
import com.teslamaps.adm.sim.run.PracticePlan;
import com.teslamaps.adm.sim.run.PracticeStart;
import com.teslamaps.adm.sim.run.RunConfig;
import com.teslamaps.adm.sim.run.WorldRegion;
import com.teslamaps.adm.sim.run.api.LayoutService;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;

public final class LayoutModule implements AdsModule, LayoutService {
    public static final LayoutModule INSTANCE = new LayoutModule();

    private LayoutModule() {}

    @Override public String id() { return "layout"; }

    @Override public String owner() { return "layout"; }

    @Override
    public void onInit() {
        Spike.installServerHooks();
        Spike.installClientHooks();
    }

    @Override
    public DungeonPlan.Result plan(RunConfig config, PackSet packs) {
        LayoutPack layouts = packs.layouts;
        if (layouts == null) return new DungeonPlan.Result.Failed(new PlanFailure("The layout pool is not loaded, so there is no dungeon to build."));
        RoomPack rooms = packs.rooms;
        if (rooms == null) return new DungeonPlan.Result.Failed(new PlanFailure("The room library is not loaded, so there is no dungeon to build."));
        if (config.layout instanceof LayoutSource.Practice pr) return PracticePlan.build(pr.room, layouts, config.seed, rooms, packs.runstate, config.floor);
        LayoutPick.Result picked = LayoutPick.pick(config, layouts);
        if (picked instanceof LayoutPick.Result.Failed f) return new DungeonPlan.Result.Failed(new PlanFailure(f.message));
        LayoutPick.Result.Ok ok = (LayoutPick.Result.Ok) picked;
        return DungeonPlan.build(ok.picked.layout, config.seed, rooms, packs.runstate);
    }

    @Override
    public WorldRegion region(DungeonPlan plan, PackSet packs) {
        RoomPack rooms = packs.rooms;
        if (rooms == null) return null;
        PracticeStart practice = plan.practice;
        Slot start = practice != null ? DoorGeometry.slotOf(practice.tx, practice.tz, practice.side) : null;
        Map<Slot, DoorKind> doors = start != null ? Map.of(start, DoorKind.NORMAL) : LayoutBuilder.doorKinds(plan);
        LayoutBuilder.Result b = LayoutBuilder.build(plan, rooms, doors);
        if (b instanceof LayoutBuilder.Result.Failed f) {
            Ec.LOG.warn("[ads] layout: {}", f.message);
            return null;
        }
        LayoutBuilder.Built built = ((LayoutBuilder.Result.Ok) b).built;
        List<Edit> edits = new ArrayList<>(built.edits);
        if (start != null) edits.addAll(practiceBackstop(start));
        return new LayoutRegion("dungeon", built.instances, edits, rooms.textures);
    }

    static List<Edit> practiceBackstop(Slot slot) {
        List<Edit> out = new ArrayList<>();
        for (int a = DoorGeometry.FRAME_MIN; a <= DoorGeometry.FRAME_MAX; a++) {
            Pair<Integer, Integer> c = DoorGeometry.gapCell(slot, a);
            for (int y = DoorModel.THRESHOLD_Y; y <= DoorModel.BODY_Y_MAX + 1; y++) out.add(new Edit(c.first, y, c.second, "minecraft:bedrock"));
        }
        return out;
    }

    private static Long toLongOrNull(String s) {
        if (s == null) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<AdsCommand> devCommands() {
        return List.of(
                new AdsCommand(
                        "rooms", "/ads dev rooms",
                        "Rebuilds the ADS world with three rooms side by side (Andesite, Blue Skulls turned, Pirate) and asks Odin whether it names them.",
                        false,
                        ctx -> Spike.launch(ctx.reply, Spike::prepareRooms)
                ),
                new AdsCommand(
                        "layout", "/ads dev layout <id|random|seed N>",
                        "Rebuilds the ADS world as a whole recorded dungeon (a recorded run's id, a random one, or the one a seed picks) and checks Odin against it.",
                        false,
                        ctx -> {
                            List<String> a = ctx.args;
                            RunConfig config;
                            if (a.isEmpty() || a.get(0).equalsIgnoreCase("random")) config = new RunConfig("F7", LayoutSource.Random.INSTANCE);
                            else if (a.get(0).equalsIgnoreCase("seed")) {
                                Long s = toLongOrNull(a.size() > 1 ? a.get(1) : null);
                                config = s != null ? new RunConfig("F7", new LayoutSource.Seed(s), s) : null;
                            } else config = new RunConfig("F7", new LayoutSource.Recorded(a.get(0)));
                            if (config == null) ctx.reply.accept("Say which seed: /ads dev layout seed 42");
                            else Spike.launch(ctx.reply, Spike.prepareLayout(config));
                        }
                ),
                new AdsCommand(
                        "resync", "/ads dev resync",
                        "Puts every block of the built dungeon back to how it was built (what a savepoint restore does for the dungeon) and says how many it fixed.",
                        true, null,
                        ctx -> {
                            LayoutRegion region = Spike.lastRegion;
                            ServerLevel level = SimServer.level();
                            if (region == null || level == null) ctx.reply("No dungeon has been built this session; use /ads dev rooms or /ads dev layout first.");
                            else {
                                long t0 = System.nanoTime();
                                int fixed = region.resync(level);
                                ctx.reply("Resynced " + region.stats().chunks + " chunks in " + (System.nanoTime() - t0) / 1_000_000 + " ms: " + fixed + " blocks put back.");
                            }
                        }
                ),
                new AdsCommand(
                        "probe", "/ads dev probe",
                        "Says which rooms the unmodified Odin recognised in the world you stand in, with their rotation, and how long the last build took.",
                        false,
                        ctx -> Spike.report().forEach(ctx.reply)
                )
        );
    }
}
