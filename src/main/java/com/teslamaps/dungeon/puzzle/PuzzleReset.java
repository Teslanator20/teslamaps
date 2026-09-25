package com.teslamaps.dungeon.puzzle;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** /tmap puzzle reset [name]: restarts a solver so it scans the room again. */
public final class PuzzleReset {

    private static final Map<String, Runnable> SOLVERS = new LinkedHashMap<>();
    static {
        SOLVERS.put("blaze", DungeonBlaze::reset);
        SOLVERS.put("weirdos", ThreeWeirdos::reset);
        SOLVERS.put("tictactoe", TicTacToe::reset);
        SOLVERS.put("creeper", CreeperBeamsSolver::reset);
        SOLVERS.put("boulder", BoulderSolver::reset);
        SOLVERS.put("quiz", QuizSolver::reset);
        SOLVERS.put("tpmaze", TPMazeSolver::reset);
        SOLVERS.put("waterboard", WaterBoardSolver::reset);
        SOLVERS.put("icefill", IceFillSolver::reset);
        SOLVERS.put("icepath", IcePathSolver::reset);
        SOLVERS.put("arrowalign", ArrowAlignSolver::reset);
        SOLVERS.put("simonsays", SimonSaysSolver::reset);
    }

    private PuzzleReset() {}

    /** false = unknown name. */
    public static boolean reset(String name) {
        String key = name.toLowerCase().replace(" ", "");
        if (key.equals("all")) {
            SOLVERS.values().forEach(Runnable::run);
            return true;
        }
        Runnable r = SOLVERS.get(key);
        if (r == null) return false;
        r.run();
        return true;
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("puzzle")
                .then(ClientCommands.literal("reset")
                        .executes(ctx -> run(ctx.getSource(), "all"))
                        .then(ClientCommands.argument("solver", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    b.suggest("all");
                                    SOLVERS.keySet().forEach(b::suggest);
                                    return b.buildFuture();
                                })
                                .executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "solver")))));
    }

    private static int run(FabricClientCommandSource src, String name) {
        if (!reset(name)) {
            src.sendFeedback(Component.literal("§cUnknown solver: " + name + " §7(" + String.join(", ", SOLVERS.keySet()) + ")"));
            return 0;
        }
        String extra = name.equalsIgnoreCase("waterboard") || name.equalsIgnoreCase("all")
                ? " §7Water Board: flip every lever back to its start first, the solutions only work from the start layout." : "";
        src.sendFeedback(Component.literal("§b[TeslaMaps] §aReset " + name + " solver" + (name.equalsIgnoreCase("all") ? "s" : "") + "." + extra));
        return 1;
    }
}
