// Ported from ADM's p3sim/clear/bots/BotCommands.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.pack.PackSet;
import com.teslamaps.adm.sim.pack.Packs;
import com.teslamaps.adm.sim.pack.layouts.Layout;
import com.teslamaps.adm.sim.pack.runstate.RunStatePack;
import com.teslamaps.adm.sim.run.AdsClientContext;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsServerContext;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.PlacedDoor;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.plugin.Seat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** /ads dev bots, botplan, botreplan (in the world) and botsim (reads the packs, no world needed). */
public final class BotCommands {
    private BotCommands() {}

    public static List<AdsCommand> commands() {
        return List.of(
                new AdsCommand("bots", "/ads dev bots", "Lists the teammate bots: class, where they are, whether they are drawn, and what they are doing next.", true, null, BotCommands::bots),
                new AdsCommand("botplan", "/ads dev botplan [rooms]", "Prints the bots' ClearPlan: the door and key times and who has which room when.", true, null, BotCommands::botPlan),
                new AdsCommand("botreplan", "/ads dev botreplan", "Makes the bots redraw their plan from where the run stands now.", true, null, BotCommands::botReplan),
                new AdsCommand("botsim", "/ads dev botsim [plans] [work scale]", "Builds many ClearPlans over the real layouts and compares their splits with the recorded ones.", false, BotCommands::botSim, null)
        );
    }

    private static BotsModule.Session session(AdsServerContext ctx) {
        Run run = ctx.run();
        if (run == null) { ctx.reply("No run is on."); return null; }
        BotsModule.Session s = BotsModule.session;
        if (s != null && s.run != run) s = null;
        if (s == null) { ctx.reply("This run has no bots (they are off, or there was nobody to plan for)."); return null; }
        return s;
    }

    private static String name(DungeonPlan plan, int room) {
        for (PlacedRoom r : plan.rooms) if (r.index == room) return r.name;
        return "room " + room;
    }

    private static String seatName(BotsModule.Session s, int seat) {
        String n = s.roster.names.get(seat);
        return n != null ? n : "null";
    }

    private static void bots(AdsServerContext ctx) {
        BotsModule.Session s = session(ctx);
        if (s == null) return;
        int t = s.run.t();
        ctx.reply("Bots at t=" + t + " (" + (s.active ? "planning" : "stopped") + "), " + s.replans + " re-plans, " + (s.covered ? "covering your rooms" : "leaving your rooms to you") + ".");
        for (Seat b : s.roster.bots()) {
            Pt p = s.pos.get(b.index);
            String room = "between rooms";
            if (p != null) {
                RoomRecord rr = s.run.state.roomAt(p.x, p.z);
                if (rr != null && rr.plan != null && rr.plan.name != null) room = rr.plan.name;
            }
            Act next = null;
            for (Act a : s.executor.pending()) if (a.seat() == b.index && (next == null || a.t() < next.t())) next = a;
            String doing;
            if (next == null) doing = "nothing left to do";
            else if (next instanceof OpenDoor od) doing = "open door " + od.door + " at t=" + od.t();
            else if (next instanceof ClearMobs cm) doing = "clear " + name(s.run.plan, cm.room) + " by t=" + cm.until();
            else if (next instanceof FindSecrets fs) doing = "find secrets in " + name(s.run.plan, fs.room) + " by t=" + fs.until();
            else if (next instanceof SolvePuzzle sp) doing = "solve " + name(s.run.plan, sp.room) + " by t=" + sp.until();
            else doing = "nothing left to do";
            String at = p != null ? String.format(Locale.ROOT, "%.0f, %.0f", p.x, p.z) : "?";
            ctx.reply(seatName(s, b.index) + " (" + b.dungeonClass + ") in " + room + " at " + at + ", " + (s.bodies.has(b.index) ? "drawn" : "not drawn") + "; next: " + doing);
        }
        if (!s.kept.isEmpty()) {
            List<String> last = s.kept.subList(Math.max(0, s.kept.size() - 3), s.kept.size());
            ctx.reply("Last re-plans: " + String.join(" | ", last));
        }
    }

    private static void botPlan(AdsServerContext ctx) {
        BotsModule.Session s = session(ctx);
        if (s == null) return;
        ClearPlan plan = s.plan();
        PlanMilestones ms = plan.milestones;
        String blood = ms.bloodSeat != null ? ", " + seatName(s, ms.bloodSeat) + " to the blood room" : "";
        ctx.reply("ClearPlan built at t=" + plan.builtAt + ": blood key t=" + ms.bloodKeyT + ", blood door t=" + ms.bloodOpenT + ", Watcher done about t=" + ms.watcherDoneT
                + ", Maxor about t=" + ms.maxorT + blood + ".");
        ArrayList<DoorPlan> doors = new ArrayList<>(plan.doors.values());
        doors.sort(Comparator.comparingInt(d -> d.openT));
        for (DoorPlan d : doors) {
            PlacedDoor door = s.run.plan.doors.get(d.door);
            ctx.reply("Door " + d.door + " (" + name(s.run.plan, door.roomA) + " to " + name(s.run.plan, door.roomB) + ", " + door.kind + "): key t=" + d.keyT
                    + (d.keySeat != d.seat ? " (" + seatName(s, d.keySeat) + ")" : "") + ", opens t=" + d.openT + " by " + seatName(s, d.seat));
        }
        if ("rooms".equals(ctx.arg(0))) {
            ArrayList<RoomPlan> rooms = new ArrayList<>(plan.rooms.values());
            rooms.sort(Comparator.comparingInt(r -> r.arriveT));
            for (RoomPlan r : rooms) {
                String who = s.roster.names.get(r.seat);
                ctx.reply(name(s.run.plan, r.room) + ": " + (who != null ? who : "?") + (r.soft ? " (you)" : "") + " " + r.work + " from t=" + r.arriveT
                        + " white " + (r.whiteT != null ? r.whiteT.toString() : "-") + " green " + (r.greenT != null ? r.greenT.toString() : "-"));
            }
        } else ctx.reply(plan.rooms.size() + " rooms planned. Add 'rooms' to list them.");
    }

    private static void botReplan(AdsServerContext ctx) {
        BotsModule.Session s = session(ctx);
        if (s == null) return;
        BotsModule.INSTANCE.requestReplan(s.run, "command");
        ctx.reply("The bots will redraw their plan in a moment.");
    }

    private static Integer parseInt(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static Double parseDouble(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    private static void botSim(AdsClientContext ctx) {
        Integer pn = parseInt(ctx.arg(0));
        int n = Math.clamp(pn != null ? pn : 500, 10, 5000);
        Double ps = parseDouble(ctx.arg(1));
        double scale = ps != null ? ps : BotTuning.ROOM_WORK_SCALE;
        ctx.reply.accept("Building " + n + " bot plans and comparing them with the recorded splits...");
        CompletableFuture.supplyAsync(() -> Packs.current != null ? Packs.current : Packs.load()).thenAccept(set -> {
            List<String> lines;
            try { lines = gate(set, n, scale); } catch (Throwable t) { lines = List.of("The bot plan check failed: " + t.getMessage()); }
            final List<String> out = lines;
            net.minecraft.client.Minecraft.getInstance().execute(() -> out.forEach(ctx.reply));
        });
    }

    /** The headless gate on the loaded packs (also what the real-pack test does). */
    public static List<String> gate(PackSet set, int n, double scale) {
        if (set.layouts == null) {
            List<String> u = set.userLines();
            return u.isEmpty() ? List.of("The layout pool is not loaded.") : u;
        }
        RunStatePack rs = set.runstate;
        if (rs == null) return List.of("The run-state tables are not loaded, so there is nothing recorded to compare with.");
        ArrayList<DungeonPlan> dungeons = new ArrayList<>();
        List<Layout> pool = set.layouts.pool();
        for (int i = 0; i < pool.size(); i++) {
            DungeonPlan.Result r = DungeonPlan.build(pool.get(i), (long) i, set.rooms, rs);
            if (r instanceof DungeonPlan.Result.Ok ok) dungeons.add(ok.plan);
        }
        if (dungeons.isEmpty()) return List.of("No layout in the pool could be built.");
        return ClearSim.gate(dungeons, new BotTables(rs), n, 7L, GateParking.INSTANCE, scale, new PackRoomAreas(set.rooms)).lines();
    }
}
