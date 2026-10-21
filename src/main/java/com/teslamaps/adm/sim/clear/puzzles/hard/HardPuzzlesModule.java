package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/HardPuzzlesModule.kt

import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsModule;
import com.teslamaps.adm.sim.run.ModuleEvent;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunEvent;
import com.teslamaps.adm.sim.run.api.Puzzle;
import com.teslamaps.adm.sim.run.api.PuzzleType;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.settings.NumSetting;
import com.teslamaps.adm.sim.settings.SimSettings;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;

/** The hard puzzles: Water Board, Teleport Maze, Ice Path. Provides their puzzle types and routes hits on the Ice Path's fish. */
public final class HardPuzzlesModule implements AdsModule {
    public static final HardPuzzlesModule INSTANCE = new HardPuzzlesModule();

    private HardPuzzlesModule() {}

    @Override public String id() { return "hardpuzzles"; }

    @Override public String owner() { return "traps"; }

    private static NumSetting routeGroupsS;

    @Override
    public void registerSettings() {
        routeGroupsS = SimSettings.num("Teleport Maze Route Groups", TpMazeModel.DEFAULT_ROUTE_GROUPS, 2, 7, 1, "",
                "How many of the Teleport Maze's seven pad groups the way to the chest passes through. A recorded solve takes about 11 hops; this model visits each group once, so 7 is the closest.");
    }

    /** The setting, or its default when the settings were never registered (headless). */
    public static int tpRouteGroups() { return routeGroupsS != null ? routeGroupsS.getInt() : TpMazeModel.DEFAULT_ROUTE_GROUPS; }

    @Override public List<AdsCommand> devCommands() { return List.of(HardDevCommands.hard); }

    @Override
    public List<PuzzleType> puzzleTypes() {
        return List.of(
                new PuzzleType("WATER_BOARD", "Water Board", Set.of("Water_Board"), WaterBoardPuzzle::new),
                new PuzzleType("TP_MAZE", "Teleport Maze", Set.of("Teleport_Maze"), TpMazePuzzle::new),
                new PuzzleType("ICE_PATH", "Ice Path", Set.of("Ice_Path"), IcePathPuzzle::new));
    }

    @Override
    public InteractionResult onAttackEntity(Run run, ServerPlayer player, InteractionHand hand, Entity entity) {
        for (PlacedRoom room : run.plan.rooms) {
            if (!"Ice_Path".equals(room.family)) continue;
            Puzzle p = Services.puzzles().puzzleOf(run, room.index);
            if (!(p instanceof IcePathPuzzle puzzle)) continue;
            if (!puzzle.ownsFish(entity)) continue;
            puzzle.onFishHit(player);
            return InteractionResult.SUCCESS;
        }
        return null;
    }

    /** An arrow hit an entity: the damage module posts ModuleEvent("damage", "arrowHitEntity", {id, by}). */
    @Override
    public void onEvent(Run run, RunEvent event) {
        if (!(event instanceof ModuleEvent me) || !"damage".equals(me.source) || !"arrowHitEntity".equals(me.name)) return;
        if (!(me.data.get("id") instanceof Integer id)) return;
        if (!(me.data.get("by") instanceof UUID by)) return;
        ServerPlayer shooter = null;
        for (ServerPlayer p : Sim.players()) if (p.getUUID().equals(by)) { shooter = p; break; }
        if (shooter == null) return;
        for (PlacedRoom room : run.plan.rooms) {
            if (!"Ice_Path".equals(room.family)) continue;
            Puzzle p = Services.puzzles().puzzleOf(run, room.index);
            if (!(p instanceof IcePathPuzzle puzzle)) continue;
            if (puzzle.ownsFishId(id)) { puzzle.onFishArrow(shooter); return; }
        }
    }
}
