package com.teslamaps.adm.sim.clear.ui;
// Ported from ADM's p3sim/clear/ui/UiModule.kt

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.run.AdsClientContext;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsModule;
import com.teslamaps.adm.sim.run.AdsServerContext;
import com.teslamaps.adm.sim.run.LagPreset;
import com.teslamaps.adm.sim.run.LayoutSource;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunConfig;
import com.teslamaps.adm.sim.run.RunStage;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.Savepoint;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;

public final class UiModule implements AdsModule {
    public static final UiModule INSTANCE = new UiModule();

    private UiModule() {}

    @Override
    public String id() { return "ui"; }

    @Override
    public String owner() { return "savepoints"; }

    @Override
    public void registerSettings() { UiSettings.register(); }

    public static void openStartScreen() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.setScreen(new RunStartScreen()));
    }

    @Override
    public List<AdsCommand> runCommands() { return Ec.BETA ? fullRunCommands() : List.of(); }

    public static List<AdsCommand> fullRunCommands() {
        return List.of(
                new AdsCommand("start", "/ads run start", "Opens the run start screen: layout, party, bots, lag, no deaths.", false,
                        ctx -> openStartScreen(), null),
                new AdsCommand("random", "/ads run random [options]", "Starts a full run on a random recorded layout. Options: " + RunArgs.OPTIONS + ".", false,
                        ctx -> start("random", ctx), null),
                new AdsCommand("seed", "/ads run seed <n> [options]", "Starts a full run on the layout that seed picks; the same seed gives the same run.", false,
                        ctx -> start("seed", ctx), null),
                new AdsCommand("layout", "/ads run layout <id> [options]", "Starts a full run on a recorded layout by its id (a start of the id is enough).", false,
                        ctx -> start("layout", ctx), null),
                new AdsCommand("practice", "/ads run practice <room>", "Practises one puzzle or room alone (its name, as Odin shows it). Without a name: the practice screen.", false,
                        ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            String name = String.join(" ", ctx.args).trim();
                            if (name.isEmpty()) mc.execute(() -> mc.gui.setScreen(new PracticeScreen(null)));
                            else mc.execute(() -> RunStarter.start(new RunConfig("F7", new LayoutSource.Practice(name), drawSeed(), false, 1, LagPreset.OFF, false, 1.0, null, null, null), ctx.reply));
                        }, null),
                new AdsCommand("restart", "/ads run restart [room]", "Goes back to the run's start, or with \"room\" to when you entered the room you are in. Same dungeon, no rebuild.", true,
                        null, ctx -> restart(ctx)),
                new AdsCommand("savepoint", "/ads run savepoint", "Takes a savepoint now.", true, null, ctx -> savepoint(ctx)),
                new AdsCommand("savepoints", "/ads run savepoints", "Lists the run's savepoints, oldest first.", true, null, ctx -> list(ctx)),
                new AdsCommand("restore", "/ads run restore <number>", "Goes back to a savepoint from /ads run savepoints.", true, null, ctx -> restore(ctx))
        );
    }

    // client

    private static void start(String mode, AdsClientContext ctx) {
        RunDefaults d = UiSettings.defaults();
        RunArgs.Parsed p = RunArgs.parse(mode, ctx.args, d, () -> drawSeed());
        if (p instanceof RunArgs.Parsed.Bad bad) ctx.reply.accept(bad.message);
        else if (p instanceof RunArgs.Parsed.Ok ok) Minecraft.getInstance().execute(() -> RunStarter.start(ok.config, ctx.reply));
    }

    public static long drawSeed() { return Math.floorMod(System.nanoTime() ^ System.currentTimeMillis(), 1_000_000_000L); }

    // server (the sim server thread, as the player)

    private static void restart(AdsServerContext ctx) {
        Run run = ctx.run();
        if (run == null) { ctx.reply(NO_RUN); return; }
        List<Savepoint> all = run.savepoints.list();
        Restarts.Choice choice;
        String a0 = ctx.arg(0);
        if (a0 != null && a0.toLowerCase(Locale.ROOT).equals("room")) {
            RoomRecord room = run.state.roomAt(ctx.player.getX(), ctx.player.getZ());
            Integer here = room != null ? room.index() : null;
            choice = Restarts.room(all, here);
        } else if (a0 == null) choice = Restarts.start(all);
        else { ctx.reply("Use /ads run restart for the start, or /ads run restart room for this room."); return; }
        goTo(ctx, choice);
    }

    private static void restore(AdsServerContext ctx) {
        Run run = ctx.run();
        if (run == null) { ctx.reply(NO_RUN); return; }
        String word = ctx.arg(0);
        if (word == null) { ctx.reply("Give the savepoint's number from /ads run savepoints."); return; }
        Savepoint sp = Restarts.find(run.savepoints.list(), word);
        if (sp == null) { ctx.reply("There is no savepoint " + word + ". /ads run savepoints lists them."); return; }
        goTo(ctx, new Restarts.Choice.Use(sp));
    }

    private static void goTo(AdsServerContext ctx, Restarts.Choice choice) {
        Run run = ctx.run();
        if (run == null) return;
        if (choice instanceof Restarts.Choice.None none) ctx.reply(none.message);
        else if (choice instanceof Restarts.Choice.Use use) {
            if (use.note != null) ctx.reply(use.note);
            if (run.stage == RunStage.END || run.isOver()) { ctx.reply("The run is over. Start another with /ads run start."); return; }
            if (!run.stage.inDungeon()) { ctx.reply("A restart is only possible before the boss. Start another run with /ads run start."); return; }
            if (run.savepoints.restore(run, use.savepoint.id)) ctx.reply("Back to " + use.savepoint.label + " (" + Restarts.clock(use.savepoint.t) + ").");
            else ctx.reply("That savepoint could not be restored.");
        }
    }

    private static void savepoint(AdsServerContext ctx) {
        Run run = ctx.run();
        if (run == null) { ctx.reply(NO_RUN); return; }
        RoomRecord room = run.state.roomAt(ctx.player.getX(), ctx.player.getZ());
        String label = room != null ? "Manual: " + room.plan.name : "Manual";
        Savepoint sp = run.savepoint(label, null);
        if (sp == null) ctx.reply("Saving is not available in this run.");
        else {
            List<Savepoint> list = run.savepoints.list();
            int idx = -1;
            for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(sp.id)) { idx = i; break; }
            ctx.reply("Savepoint " + (idx + 1) + " taken at " + Restarts.clock(sp.t) + ".");
        }
    }

    private static void list(AdsServerContext ctx) {
        Run run = ctx.run();
        if (run == null) { ctx.reply(NO_RUN); return; }
        List<String> lines = Restarts.lines(run.savepoints.list());
        if (lines.isEmpty()) ctx.reply("There are no savepoints yet.");
        else {
            ctx.reply("Savepoints (restore with /ads run restore <number>):");
            for (String l : lines.subList(Math.max(0, lines.size() - MAX_LISTED), lines.size())) ctx.reply(l);
        }
    }

    private static final String NO_RUN = "No full run is on. Start one with /ads run start.";
    private static final int MAX_LISTED = 25;
}
