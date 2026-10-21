// Ported from ADM's p3sim/run/AdsCommands.kt
package com.teslamaps.adm.sim.run;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.P3Sim;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimNet;
import com.teslamaps.adm.sim.pack.Packs;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** The command registry: `/ads dev|run` lists, `/ads dev|run <name> [args...]` runs one. */
public final class AdsCommands {
    private AdsCommands() {}

    private static final String NET_KIND = "adscmd";

    /** Commands the skeleton itself provides. */
    private static final List<AdsCommand> builtinDev = List.of(
            new AdsCommand("packs", "/ads dev packs",
                    "Reloads the ADS data packs and says what loaded, and for each file whether it came from the jar or your override folder.", false, ctx ->
                    Packs.loadAsync().thenAccept(set -> Minecraft.getInstance().execute(() -> {
                        List<String> lines = new ArrayList<>();
                        lines.add("Data packs (bundled in the jar; a file in " + set.dir + " overrides it):");
                        for (String l : set.originLines()) lines.add("  " + l);
                        lines.add("  rooms: " + (set.rooms != null ? set.rooms.designs.size() + " designs, " + set.rooms.cores.size() + " cores" : "not loaded"));
                        lines.add("  layouts: " + (set.layouts != null ? set.layouts.layouts.size() + " layouts, " + set.layouts.pool().size() + " in the default pool" : "not loaded"));
                        lines.add("  mobs: " + (set.mobs != null ? set.mobs.archetypes.size() + " archetypes, " + set.mobs.rooms.size() + " rooms" : "not loaded"));
                        lines.add("  run-state: " + (set.runstate != null
                                ? set.runstate.rooms.size() + " rooms" + (set.runstate.problems.isEmpty() ? "" : ", " + set.runstate.problems.size() + " section problems")
                                : "not loaded"));
                        lines.addAll(set.userLines());
                        lines.forEach(ctx.reply);
                    }))),
            new AdsCommand("events", "/ads dev events", "Prints the last events the run bus delivered.", true, null, ctx -> {
                Run run = ctx.run();
                if (run == null) {
                    ctx.reply("No run is on.");
                } else {
                    List<RunEvent> recent = run.bus.recentEvents();
                    for (RunEvent e : recent.subList(Math.max(0, recent.size() - 15), recent.size())) ctx.reply(e.toString());
                }
            }));

    private static List<AdsCommand> collect(String label, List<AdsCommand> list) {
        LinkedHashMap<String, AdsCommand> seen = new LinkedHashMap<>();
        for (AdsCommand c : list) {
            if (seen.putIfAbsent(c.name, c) != null) Ec.LOG.warn("[ads] two /ads " + label + " commands are named '" + c.name + "'; the first wins");
        }
        return new ArrayList<>(seen.values());
    }

    private static volatile List<AdsCommand> dev;
    private static volatile List<AdsCommand> run;

    /** All `/ads dev` commands: the skeleton's, then each module's in module order. */
    public static List<AdsCommand> dev() {
        List<AdsCommand> l = dev;
        if (l != null) return l;
        synchronized (AdsCommands.class) {
            if (dev == null) {
                List<AdsCommand> all = new ArrayList<>(builtinDev);
                for (AdsModule m : AdsModules.all()) all.addAll(safeList(m.id() + ".devCommands", m::devCommands));
                dev = collect("dev", all);
            }
            return dev;
        }
    }

    /** All `/ads run` commands. */
    public static List<AdsCommand> run() {
        List<AdsCommand> l = run;
        if (l != null) return l;
        synchronized (AdsCommands.class) {
            if (run == null) {
                List<AdsCommand> all = new ArrayList<>();
                for (AdsModule m : AdsModules.all()) all.addAll(safeList(m.id() + ".runCommands", m::runCommands));
                run = collect("run", all);
            }
            return run;
        }
    }

    private static List<AdsCommand> safeList(String what, Supplier<List<AdsCommand>> f) {
        try {
            return f.get();
        } catch (Throwable t) {
            Ec.LOG.error("[ads] " + what + " failed", t);
            return List.of();
        }
    }

    /** Adds the `dev` and `run` groups under an `/ads` (or `/p3sim`) builder. */
    public static void attach(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(group("dev", dev())).then(group("run", run()));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> group(String label, List<AdsCommand> cmds) {
        LiteralArgumentBuilder<FabricClientCommandSource> g = ClientCommands.literal(label).executes(c -> {
            list(label, cmds, s -> c.getSource().sendFeedback(Component.literal(s)));
            return 1;
        });
        for (AdsCommand cmd : cmds) {
            g.then(ClientCommands.literal(cmd.name)
                    .executes(c -> {
                        exec(label, cmd, List.of(), s -> c.getSource().sendFeedback(Component.literal(s)));
                        return 1;
                    })
                    .then(ClientCommands.argument("args", StringArgumentType.greedyString()).executes(c -> {
                        List<String> args = Arrays.stream(StringArgumentType.getString(c, "args").trim().split("\\s+")).filter(x -> !x.isEmpty()).toList();
                        exec(label, cmd, args, s -> c.getSource().sendFeedback(Component.literal(s)));
                        return 1;
                    })));
        }
        return g;
    }

    private static void list(String label, List<AdsCommand> cmds, Consumer<String> out) {
        if (cmds.isEmpty()) { out.accept("No /ads " + label + " commands are installed."); return; }
        out.accept("§6/ads " + label + "§7 commands:");
        for (AdsCommand c : cmds) out.accept("§f" + c.usage + "§7  " + c.summary);
    }

    private static void exec(String label, AdsCommand cmd, List<String> args, Consumer<String> reply) {
        Ec.safely("ads " + label + " " + cmd.name, () -> {
            if (cmd.requiresSim && !P3Sim.inSim()) { reply.accept("§8[§6ADS§8] §7Open ADS first (the title screen button, or /ads)."); return; }
            if (cmd.client != null) cmd.client.accept(new AdsClientContext(args, s -> reply.accept("§8[§6ADS§8] §7" + s)));
            if (cmd.server != null) {
                JsonObject o = new JsonObject();
                o.addProperty("group", label);
                o.addProperty("name", cmd.name);
                JsonArray a = new JsonArray();
                for (String s : args) a.add(s);
                o.add("args", a);
                SimNet.send(NET_KIND, o.toString());
            }
        });
    }

    /** Registers the server-side receiver for commands with a server part. */
    public static void onInit() {
        SimNet.onServer(NET_KIND, (p, data) -> {
            JsonObject o;
            try { o = JsonParser.parseString(data).getAsJsonObject(); } catch (Exception e) { return; }
            JsonElement ge = o.get("group");
            if (ge == null) return;
            String group = ge.getAsString();
            JsonElement ne = o.get("name");
            if (ne == null) return;
            String name = ne.getAsString();
            List<String> args = new ArrayList<>();
            JsonArray arr = o.getAsJsonArray("args");
            if (arr != null) for (JsonElement e : arr) args.add(e.getAsString());
            List<AdsCommand> pool = "dev".equals(group) ? dev() : "run".equals(group) ? run() : List.of();
            AdsCommand cmd = null;
            for (AdsCommand c : pool) if (c.name.equals(name)) { cmd = c; break; }
            if (cmd == null) return;
            Consumer<AdsServerContext> server = cmd.server;
            if (server == null) return;
            // only the host runs the commands that restore, force or stop the run; a joiner may only read the channel dump
            if (!Sim.isHost(p) && !("dev".equals(group) && "hud".equals(name) && !args.isEmpty() && "dump".equals(args.get(0)))) {
                Sim.asPlayer(p, false, () -> Sim.note("Only the host can use /ads " + group + " " + name + "."));
                return;
            }
            try { server.accept(new AdsServerContext(p, args)); } catch (Throwable t) { Ec.LOG.error("[ads] " + group + " " + name + " failed", t); }
        });
    }
}
