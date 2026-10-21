package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/HardDevCommands.kt

import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsServerContext;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.api.Puzzle;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** `/ads dev hard <list|tp|hint>`: the hard puzzles and the trap rooms, for testing in the game. */
final class HardDevCommands {
    private HardDevCommands() {}

    public static final AdsCommand hard = new AdsCommand(
            "hard", "/ads dev hard <list|tp|hint> [water|maze|ice|trap|room number]",
            "Lists the hard puzzle and trap rooms of this run, takes you to one, or tells you what a puzzle needs.",
            true, null, ctx -> run(ctx));

    private static final Map<String, String> KINDS = new LinkedHashMap<>();
    static {
        KINDS.put("water", "Water_Board");
        KINDS.put("maze", "Teleport_Maze");
        KINDS.put("ice", "Ice_Path");
        KINDS.put("trap", "Trap");
    }

    private static boolean matches(PlacedRoom room, String family) {
        return "Trap".equals(family) ? "New_Trap".equals(room.family) || "Old_Trap".equals(room.family) : family.equals(room.family);
    }

    private static List<PlacedRoom> hardRooms(Run run) {
        List<PlacedRoom> out = new ArrayList<>();
        for (PlacedRoom r : run.plan.rooms) {
            for (String fam : KINDS.values()) if (matches(r, fam)) { out.add(r); break; }
        }
        return out;
    }

    private static Integer toIntOrNull(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static PlacedRoom pick(Run run, String what) {
        List<PlacedRoom> rooms = hardRooms(run);
        if (what == null) return rooms.isEmpty() ? null : rooms.get(0);
        Integer n = toIntOrNull(what);
        if (n != null) {
            for (PlacedRoom r : rooms) if (r.index == n) return r;
            return null;
        }
        String fam = KINDS.get(what.toLowerCase(Locale.ROOT));
        if (fam == null) return null;
        for (PlacedRoom r : rooms) if (matches(r, fam)) return r;
        return null;
    }

    private static void run(AdsServerContext ctx) {
        Run run = ctx.run();
        if (run == null) { ctx.reply("No run is on. Start one with /ads run."); return; }
        String a0 = ctx.arg(0);
        if (a0 == null || a0.equals("list")) {
            List<PlacedRoom> rooms = hardRooms(run);
            if (rooms.isEmpty()) { ctx.reply("This layout has no Water Board, Teleport Maze, Ice Path or trap room."); return; }
            for (PlacedRoom r : rooms) ctx.reply("room " + r.index + ": " + r.name + " (" + r.family + ") tile " + r.tiles.get(0).x + "," + r.tiles.get(0).z + " facing " + r.rotation);
            return;
        }
        switch (a0) {
            case "tp" -> {
                PlacedRoom room = pick(run, ctx.arg(1));
                if (room == null) { ctx.reply("No such room. Try /ads dev hard list."); return; }
                Placement pl = room.placement;
                if (pl == null) { ctx.reply("That room has no placement (headless plan)."); return; }
                Triple<Double, Double, Double> spot = standingSpot(room.family);
                Pair<Double, Double> w = Frames.toWorld(pl, spot.first, spot.third);
                Sim.tp(ctx.player, w.first, spot.second, w.second, Frames.yawToWorld(pl, 0f), 0f);
                ctx.reply("In " + room.name + " (room " + room.index + ").");
            }
            case "hint" -> {
                PlacedRoom room = pick(run, ctx.arg(1));
                if (room == null) { ctx.reply("No such room. Try /ads dev hard list."); return; }
                Puzzle p = Services.puzzles().puzzleOf(run, room.index);
                if (p instanceof WaterBoardPuzzle wp) {
                    WaterBoardModel m = wp.boardModel();
                    if (m == null) { ctx.reply("The Water Board could not be read from the room."); return; }
                    ctx.reply("Closed pits (0 purple, 1 orange, 2 blue, 3 green, 4 red): " + sorted(m.closedPits) + ", opened: " + sorted(m.openedPits) + ", water on: " + m.waterOn + ", solved: " + m.solved + ".");
                    StringBuilder drains = new StringBuilder();
                    for (int i = 0; i < WaterBoardModel.DRAIN_X.length; i++) { if (i > 0) drains.append(", "); drains.append(WaterBoardModel.DRAIN_X[i]); }
                    ctx.reply("Odin's solver lists the lever times; the drains are at x = " + drains + " (canonical).");
                } else if (p instanceof TpMazePuzzle tp) {
                    TpMazeModel m = tp.mazeModel();
                    if (m == null) { ctx.reply("The maze is not drawn yet."); return; }
                    StringBuilder sb = new StringBuilder();
                    List<Integer> sol = m.solution();
                    for (int i = 0; i < sol.size(); i++) { if (i > 0) sb.append(" > "); sb.append(sol.get(i)); }
                    ctx.reply("Pads to step on, in order (Odin's numbering): " + sb + "; route groups " + m.route + ".");
                } else if (p instanceof IcePathPuzzle ip) {
                    IcePathModel m = ip.iceModel();
                    if (m == null) { ctx.reply("The Ice Path could not be read from the room."); return; }
                    List<Pair<Integer, Integer>> sol = m.solution();
                    String text;
                    if (sol == null) text = "none";
                    else {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < sol.size(); i++) { if (i > 0) sb.append(" > "); sb.append(sol.get(i).first).append(",").append(sol.get(i).second); }
                        text = sb.toString();
                    }
                    ctx.reply("Slides from the start, as the cell the fish stops on (canonical x,z): " + text + ".");
                } else {
                    ctx.reply("Nothing to hint for " + room.name + ".");
                }
            }
            default -> ctx.reply("Usage: /ads dev hard <list|tp|hint> [water|maze|ice|trap|room number]");
        }
    }

    private static List<Integer> sorted(java.util.Collection<Integer> c) {
        List<Integer> l = new ArrayList<>(c);
        l.sort(null);
        return l;
    }

    /** Where to stand in each kind of room, canonical x, y, z (feet). */
    private static Triple<Double, Double, Double> standingSpot(String family) {
        return switch (family) {
            case "Water_Board" -> Triple.of(15.5, 59.0, 12.5);
            case "Teleport_Maze" -> Triple.of(15.5, 69.0, 9.5);
            case "Ice_Path" -> Triple.of(12.5, 67.0, 12.5);
            default -> Triple.of(15.5, 70.0, 15.5);
        };
    }
}
