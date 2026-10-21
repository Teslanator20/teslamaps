// Ported from ADM's p3sim/run/EngineCommands.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.P3Sim;
import com.teslamaps.adm.sim.SimServer;
import com.teslamaps.adm.sim.SimWorld;
import com.teslamaps.adm.sim.clear.bots.BotStrategies;
import com.teslamaps.adm.sim.clear.ui.StrategyChoice;
import com.teslamaps.adm.sim.run.plugin.PartyStrategy;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gamerules.GameRules;

public final class EngineCommands {
    private EngineCommands() {}

    private static String lower(String s) { return s == null ? null : s.toLowerCase(Locale.ROOT); }

    private static Long longOrNull(String s) {
        if (s == null) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    private static Integer intOrNull(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static String getOrNull(List<String> l, int i) { return i >= 0 && i < l.size() ? l.get(i) : null; }

    private static LagPreset lagWord(String w) {
        if (w == null) return LagPreset.OFF;
        return switch (w) {
            case "good" -> LagPreset.GOOD;
            case "typical" -> LagPreset.TYPICAL;
            case "bad" -> LagPreset.BAD;
            case "slot" -> LagPreset.HIGH_SLOT;
            default -> LagPreset.OFF;
        };
    }

    public static List<AdsCommand> dev() {
        List<AdsCommand> out = new ArrayList<>();

        out.add(new AdsCommand("difficulty", "/ads dev difficulty [normal|easy|hard|peaceful]",
                "Puts the world on NORMAL (or the one you name) and watches the boss sim for 20 s: does anything vanish, target you or hurt you? Start a boss phase first.",
                true, null, ctx -> {
                    MinecraftServer server = SimServer.server;
                    String a = lower(ctx.arg(0));
                    Difficulty to;
                    if (a == null) to = Difficulty.NORMAL;
                    else to = switch (a) {
                        case "normal" -> Difficulty.NORMAL;
                        case "easy" -> Difficulty.EASY;
                        case "hard" -> Difficulty.HARD;
                        case "peaceful" -> Difficulty.PEACEFUL;
                        default -> null;
                    };
                    if (server == null || to == null) ctx.reply("Say normal, easy, hard or peaceful.");
                    else EngineSpikes.difficulty(server, to);
                }));

        out.add(new AdsCommand("cost", "/ads dev cost [base] [own] [quick]",
                "Rebuilds the world with a made-up dungeon (169 roofed chunks) beside the arena and measures: time to playable, server ms per tick with 0, 300 and 750 static mobs at the pinned distance (6), client FPS with 430 entities, Arena.resync. 'base' builds the plain arena for the comparison, 'own' keeps your own view distance, 'quick' shortens the windows.",
                false, ctx -> {
                    boolean base = ctx.args.contains("base");
                    EngineSpikes.beginWorldClock(); EngineSpikes.expectPlayable();
                    EngineSpikes.costOnJoin = new EngineSpikes.CostOptions(!ctx.args.contains("own"), ctx.args.contains("quick"));
                    if (base) { WorldRegions.unregister(StubDungeon.NAME); WorldRegions.unregister(ArenaRegion.NAME); }
                    else RunLauncher.registerDungeon(StubDungeon.region());
                    ctx.reply.accept(base ? "Rebuilding the plain arena world and measuring. Stand still when it opens." : "Rebuilding the world with the made-up dungeon and measuring. Stand still when it opens.");
                    if (P3Sim.inSim()) SimWorld.rebuild(true); else SimWorld.open(true);
                }, null));

        out.add(new AdsCommand("costnow", "/ads dev costnow [own] [quick]", "The cost measurements in the world you are in now (no rebuild). Stand in a room.",
                true, null, ctx -> EngineSpikes.costNow(new EngineSpikes.CostOptions(!ctx.args.contains("own"), ctx.args.contains("quick")))));

        out.add(new AdsCommand("lag", "/ads dev lag <off|good|typical|bad|slot|report> [seconds]",
                "Turns the lag layer on for this session and, after the seconds (default 60), says what the server's ticks looked like: freezes, the bursts after them, the loss a minute.",
                true, null, ctx -> {
                    String word = lower(ctx.arg(0));
                    Integer parsed = intOrNull(ctx.arg(1));
                    Integer secs = parsed != null ? Math.clamp(parsed, 5, 3600) : null;
                    LagPreset preset = word == null ? null : switch (word) {
                        case "off" -> LagPreset.OFF;
                        case "good" -> LagPreset.GOOD;
                        case "typical" -> LagPreset.TYPICAL;
                        case "bad" -> LagPreset.BAD;
                        case "slot", "high" -> LagPreset.HIGH_SLOT;
                        default -> null;
                    };
                    if ("report".equals(word)) EngineSpikes.lagReport(secs != null ? secs : 60);
                    else if (preset != null) EngineSpikes.lag(preset, secs != null ? secs : 60);
                    else ctx.reply("Say off, good, typical, bad, slot or report.");
                }));

        out.add(new AdsCommand("launch", "/ads dev launch [random|seed <n>|layout <id>] [lag good|typical|bad|slot] [solo] [nodeaths] [strategy auto|neutral|<id>]",
                "Starts a full run from the data packs (rebuilds the world). The same call the run screen makes.",
                false, ctx -> {
                    LayoutSource layout = LayoutSource.Random.INSTANCE;
                    LagPreset lag = LagPreset.OFF; boolean solo = false; boolean noDeaths = false; String strategy = null;
                    int i = 0;
                    while (i < ctx.args.size()) {
                        switch (lower(ctx.args.get(i))) {
                            case "random" -> layout = LayoutSource.Random.INSTANCE;
                            case "seed" -> {
                                Long n = longOrNull(getOrNull(ctx.args, ++i));
                                if (n == null) { ctx.reply.accept("seed needs a number."); return; }
                                layout = new LayoutSource.Seed(n);
                            }
                            case "layout" -> {
                                String id = getOrNull(ctx.args, ++i);
                                if (id == null) { ctx.reply.accept("layout needs an id."); return; }
                                layout = new LayoutSource.Recorded(id);
                            }
                            case "lag" -> lag = lagWord(lower(getOrNull(ctx.args, ++i)));
                            case "solo" -> solo = true;
                            case "nodeaths" -> noDeaths = true;
                            case "strategy" -> {
                                List<PartyStrategy> offered = BotStrategies.available();
                                String choice = StrategyChoice.normalize(getOrNull(ctx.args, ++i), offered);
                                if (choice == null) {
                                    String w = getOrNull(ctx.args, i);
                                    ctx.reply.accept("There is no party strategy called '" + (w != null ? w : "") + "'. Choose from: " + StrategyChoice.words(offered) + ".");
                                    return;
                                }
                                strategy = StrategyChoice.resolve(choice, offered);
                            }
                            default -> { ctx.reply.accept("I do not know '" + ctx.args.get(i) + "'."); return; }
                        }
                        i++;
                    }
                    RunLauncher.launch(new RunConfig("F7", layout, System.nanoTime(), !solo, solo ? 1 : 5, lag, noDeaths, 1.0, null, strategy, null), ctx.reply);
                }, null));

        out.add(new AdsCommand("stubrun", "/ads dev stubrun [lag good|typical|bad|slot]",
                "Starts a run in a made-up dungeon (no data packs needed) to try the run container, stages and savepoints by hand.",
                false, ctx -> {
                    LagPreset lag = "lag".equals(lower(ctx.arg(0))) ? lagWord(lower(ctx.arg(1))) : LagPreset.OFF;
                    RunConfig config = new RunConfig("F7", LayoutSource.Random.INSTANCE, System.nanoTime(), false, 1, lag, false, 1.0, null, null, null);
                    RunLauncher.registerDungeon(StubDungeon.region());
                    RunLauncher.setPending(config, StubDungeon.plan(config.seed));
                    ctx.reply.accept("Building the made-up dungeon...");
                    RunLauncher.launchWorld();
                }, null));

        out.add(new AdsCommand("stage", "/ads dev stage <clear|blood|entry|boss|end>", "Moves the run to the next stage by hand (stages go forward one at a time; end from anywhere).",
                true, null, ctx -> {
                    Run run = ctx.run();
                    String a = lower(ctx.arg(0));
                    RunStage to = a == null ? null : switch (a) {
                        case "clear" -> RunStage.CLEAR;
                        case "blood" -> RunStage.BLOOD;
                        case "entry" -> RunStage.BOSS_ENTRY;
                        case "boss" -> RunStage.BOSS;
                        case "end" -> RunStage.END;
                        default -> null;
                    };
                    if (run == null) ctx.reply("No run is on.");
                    else if (to == null) ctx.reply("Say clear, blood, entry, boss or end.");
                    else if (to == RunStage.BOSS) { run.beginBoss(); ctx.reply("Stage is now " + run.stage + "."); }
                    else ctx.reply(run.requestStage(to) ? "Stage is now " + run.stage + "." : "Not allowed from " + run.stage + ".");
                }));

        out.add(new AdsCommand("boss", "/ads dev boss", "Hands the run to the boss sim now (walks the stages forward first).",
                true, null, ctx -> {
                    Run run = ctx.run();
                    if (run == null) ctx.reply("No run is on.");
                    else { run.beginBoss(); ctx.reply("Stage is now " + run.stage + "."); }
                }));

        out.add(new AdsCommand("savepoint", "/ads dev savepoint [label]", "Takes a savepoint now.",
                true, null, ctx -> {
                    Run run = ctx.run();
                    String label = String.join(" ", ctx.args);
                    Savepoint sp = run != null ? run.savepoint(label.isBlank() ? "Manual" : label) : null;
                    ctx.reply(sp == null ? "No savepoint (no run, or not in the dungeon)." : "Savepoint " + sp.id + " \"" + sp.label + "\" at t=" + sp.t + ".");
                }));

        out.add(new AdsCommand("savepoints", "/ads dev savepoints", "Lists the run's savepoints.",
                true, null, ctx -> {
                    Run run = ctx.run();
                    if (run == null) ctx.reply("No run is on.");
                    else {
                        List<Savepoint> all = run.savepoints.list();
                        for (Savepoint it : all.subList(Math.max(0, all.size() - 15), all.size())) ctx.reply(it.id + "  t=" + it.t + "  " + it.stage + "  " + it.label);
                    }
                }));

        out.add(new AdsCommand("restore", "/ads dev restore [id]", "Restores a savepoint (the last one with no id): stage, clock, state, blocks, mobs and your gear and place.",
                true, null, ctx -> {
                    Run run = ctx.run();
                    if (run == null) { ctx.reply("No run is on."); return; }
                    String id = ctx.arg(0);
                    if (id == null) {
                        List<Savepoint> all = run.savepoints.list();
                        id = all.isEmpty() ? null : all.get(all.size() - 1).id;
                    }
                    ctx.reply(id != null && run.savepoints.restore(run, id) ? "Restored " + id + "." : "Could not restore " + (id != null ? id : "anything") + ".");
                }));

        out.add(new AdsCommand("runinfo", "/ads dev runinfo", "Says where the run is: stage, clock, party, lag, view distance pin, world regions.",
                true, null, ctx -> {
                    Run run = ctx.run();
                    ctx.reply(run == null ? "No run is on (the boss practice)." : "Run " + run.id + ": " + run.stage + ", t=" + run.t() + ", tick " + run.serverTick + ", " + run.state.players.size() + " in the party, layout " + run.plan.layoutId + ".");
                    Pair<Integer, Integer> saved = ViewPin.saved();
                    StringBuilder regions = new StringBuilder();
                    for (WorldRegion r : WorldRegions.regions()) {
                        if (regions.length() > 0) regions.append(", ");
                        regions.append(r.name());
                    }
                    String reg = regions.toString().isBlank() ? "none" : regions.toString();
                    ctx.reply("Lag: " + LagRuntime.effective().label + ". View pin: " + (saved != null ? "on (yours were " + saved.first + "/" + saved.second + ")" : "off") + ". Regions: " + reg + ".");
                }));

        out.add(new AdsCommand("worldenv", "/ads dev worldenv", "Says what the world is: its height and sky light, the biome, the chunk window, the pinned distances, the keep-alives, and what the Hypixel mod API was told.",
                true,
                ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    ClientLevel level = mc.level;
                    LocalPlayer player = mc.player;
                    if (level == null || player == null) { ctx.reply.accept("Not in a world."); return; }
                    DimensionType dim = level.dimensionType();
                    ctx.reply.accept("Client: dimension y " + dim.minY() + ".." + (dim.minY() + dim.height() - 1) + " (height " + dim.height() + "), sky light " + (dim.hasSkyLight() ? "ON" : "off") + ", " + level.getChunkSource().getLoadedChunksCount() + " chunks held.");
                    ClientPacketListener conn = mc.getConnection();
                    String brand = conn != null ? conn.serverBrand() : null;
                    ctx.reply.accept("Client: biome " + level.getBiome(player.blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("?") + ", server brand \"" + (brand != null ? brand : "?") + "\".");
                    ctx.reply.accept("Client: " + HypixelModApi.status() + ".");
                },
                ctx -> {
                    MinecraftServer server = SimServer.server;
                    if (server == null) return;
                    ServerLevel level = SimServer.level();
                    if (level == null) return;
                    BlockPos pos = ctx.player.blockPosition();
                    ctx.reply("Server: view distance " + server.getPlayerList().getViewDistance() + ", simulation distance " + server.getPlayerList().getSimulationDistance() + ", " + level.getChunkSource().getLoadedChunksCount() + " chunks loaded, " + EngineModule.heldChunkCount() + " held for the arena, clock " + (server.getGameRules().get(GameRules.ADVANCE_TIME) ? "running" : "frozen") + ".");
                    ctx.reply("Server: light at your feet sky " + level.getLightEngine().getLayerListener(LightLayer.SKY).getLightValue(pos) + ", block " + level.getLightEngine().getLayerListener(LightLayer.BLOCK).getLightValue(pos) + ". " + HypixelModApi.serverStatus() + ".");
                }));

        return out;
    }
}
