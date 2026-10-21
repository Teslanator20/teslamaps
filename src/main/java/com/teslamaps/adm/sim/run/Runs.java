// Ported from ADM's p3sim/run/Runs.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Fight;
import com.teslamaps.adm.sim.Players;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimItems;
import com.teslamaps.adm.sim.SimServer;
import com.teslamaps.adm.sim.clear.layout.DoorGeometry;
import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.PackSet;
import com.teslamaps.adm.sim.pack.Packs;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.util.Pair;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Holds the one current Run and starts and stops it. Start: RunLauncher.launch on the client (packs, plan, regions, world), then
 * start on the server thread once the player has joined. A pack problem is a chat line, never an exception.
 */
public final class Runs {
    private Runs() {}

    public static volatile Run current = null;

    /** message: plain English for the player. */
    public interface StartResult {
        final class Started implements StartResult {
            public final Run run;

            public Started(Run run) { this.run = run; }
        }

        final class Failed implements StartResult {
            public final String message;

            public Failed(String message) { this.message = message; }
        }
    }

    /** Starts the run that is RunLauncher.pending (server thread, sim world). */
    public static StartResult start(RunConfig config) {
        Pair<RunConfig, DungeonPlan> pending = RunLauncher.takePending();
        if (pending == null) return new StartResult.Failed("Start a run with /ads run (the world has to be built for it).");
        PackSet set = RunLauncher.takePendingPacks();
        // the run pins the set itself once it begins; the launch's pin is let go either way
        try {
            return start(pending.first, pending.second, set);
        } finally {
            if (set != null) set.unpin();
        }
    }

    public static StartResult start(RunConfig config, DungeonPlan plan) { return start(config, plan, null); }

    /** Starts a run for an already planned dungeon whose region the world was built from. Server thread. */
    public static StartResult start(RunConfig config, DungeonPlan plan, PackSet launchPacks) {
        MinecraftServer server = SimServer.server;
        if (server == null) return new StartResult.Failed("The ADS world is not running.");
        if (!server.isSameThread()) return new StartResult.Failed("A run starts on the sim server's thread.");
        ServerLevel level = SimServer.level();
        if (level == null) return new StartResult.Failed("The ADS world is not running.");
        List<ServerPlayer> humans = Sim.players();
        if (humans.isEmpty()) return new StartResult.Failed("Nobody is in the ADS world yet.");
        if (current != null) stop(EndReason.STOPPED);
        // the set the world was built from, else whatever is loaded now
        PackSet packs = launchPacks != null ? launchPacks : Packs.current != null ? Packs.current : Packs.ensureLoaded();

        // NORMAL difficulty, view and simulation distance pinned, the chosen lag
        server.setDifficulty(Difficulty.NORMAL, true);
        ViewPin.pin();
        server.getPlayerList().setViewDistance(ViewPin.DISTANCE);
        server.getPlayerList().setSimulationDistance(ViewPin.DISTANCE);
        LagRuntime.runPreset = config.lag;
        LagRuntime.devPreset = null;
        startClock(server, config.seed);

        Spawn spawn = plan.practice != null ? practiceSpawn(plan.practice) : entranceSpawn(level, plan, config.seed);
        for (ServerPlayer p : humans) {
            // game mode, speed, health, no knockback; then the clear's kit (the boss kit comes back at the jump)
            Fight.setup(p, false);
            Sim.asPlayer(p, false, () -> {
                SimItems.giveClearKit(p);
                Sim.chat("§fYou can no longer consume or splash any potions during the remainder of this Dungeon run!");
                Sim.tp(p, spawn.x, spawn.y, spawn.z, spawn.yaw, 0f);
            });
            // let the client drop the login chunks the new window leaves out
            for (int after : FORGET_LOGIN_CHUNKS_AFTER) Fight.later(after, "forget login chunks", () -> forgetLoginChunks(p));
        }

        String id = "run-" + Long.toHexString(config.seed);
        Run run = new Run(id, config, plan, packs, new RunBus(() -> {
            MinecraftServer s = SimServer.server;
            return s != null && s.isSameThread();
        }));
        run.savepoints = new RunSavepoints(MinecraftSavepointWorld.INSTANCE);
        run.bossStarter = BossHandoff::start;
        install(run);
        Ec.LOG.info("[ads] run " + id + " starts: layout " + plan.layoutId + ", seed " + config.seed + ", " + humans.size() + " player(s), lag " + config.lag.label);
        run.begin(Fight.serverTick);
        run.savepoint("Start");
        return new StartResult.Started(run);
    }

    /** Server ticks after the teleport at which the login's chunks are forgotten. */
    private static final int[] FORGET_LOGIN_CHUNKS_AFTER = {3, 40};

    /** The login render distance when the pin has no record of the player's own. */
    private static final int LOGIN_DISTANCE_GUESS = 16;

    /** A forget for every chunk of the login window the tracked window does not contain. */
    private static void forgetLoginChunks(ServerPlayer player) {
        if (player.isRemoved() || SimServer.server == null) return;
        ChunkTrackingView view = player.getChunkTrackingView();
        Pair<Integer, Integer> saved = ViewPin.saved();
        int loginDistance = Math.clamp(saved != null ? saved.first : LOGIN_DISTANCE_GUESS, ViewPin.DISTANCE, 32);
        for (Pair<Integer, Integer> c : ChunkWindow.leftBehind(loginDistance, (x, z) -> view.contains(x, z)))
            player.connection.send(new ClientboundForgetLevelChunkPacket(new ChunkPos(c.first, c.second)));
    }

    /** Hypixel's clock runs from a time of day drawn from the seed. */
    private static void startClock(MinecraftServer server, long seed) {
        server.getGameRules().set(GameRules.ADVANCE_TIME, true, server);
        Sim.command("time set " + new java.util.Random(seed ^ 0x71BEL).nextInt(24000));
    }

    /** Where the party starts: feet position and facing. */
    public static final class Spawn {
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;

        public Spawn(double x, double y, double z, float yaw) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof Spawn s && Double.compare(x, s.x) == 0 && Double.compare(y, s.y) == 0 && Double.compare(z, s.z) == 0
                    && Float.compare(yaw, s.yaw) == 0;
        }
        @Override public int hashCode() { return Objects.hash(x, y, z, yaw); }
        @Override public String toString() { return "Spawn(x=" + x + ", y=" + y + ", z=" + z + ", yaw=" + yaw + ")"; }
    }

    public static Spawn entranceSpawn(ServerLevel level, DungeonPlan plan) { return entranceSpawn(level, plan, 0L); }

    /**
     * On the outer edge of the entrance tile, a few blocks above its floor, facing in (inner y 75.5 / outer y 76.5, drawn from seed).
     * An entrance not on the edge keeps the middle of its tile on the floor.
     */
    public static Spawn entranceSpawn(ServerLevel level, DungeonPlan plan, long seed) {
        Tile tile = null;
        PlacedRoom ent = plan.entrance();
        if (ent != null && !ent.tiles.isEmpty()) tile = ent.tiles.get(0);
        if (tile == null && !plan.rooms.isEmpty() && !plan.rooms.get(0).tiles.isEmpty()) tile = plan.rooms.get(0).tiles.get(0);
        Spawn edge = tile != null ? spawnOnEdge(tile.x, tile.z, new java.util.Random(seed ^ 0x5A17L).nextBoolean()) : null;
        if (edge != null) return edge;
        double x = (tile != null ? DungeonFrame.tileCentre(tile.x) : DungeonFrame.tileCentre(0)) + 0.5;
        double z = (tile != null ? DungeonFrame.tileCentre(tile.z) : DungeonFrame.tileCentre(0)) + 0.5;
        return new Spawn(x, floorY(level, (int) x, (int) z), z, 180f);
    }

    /** A practice run's start: in its one doorway, on the floor, facing into the room. Pure. */
    public static Spawn practiceSpawn(PracticeStart p) {
        Pair<Integer, Integer> c = DoorGeometry.sideCell(p.tx, p.tz, p.side, 15, 1);
        float yaw = switch (p.side) {
            case 'N' -> 0f;
            case 'S' -> 180f;
            case 'W' -> 270f;
            default -> 90f;
        };
        return new Spawn(c.first + 0.5, 69.0, c.second + 0.5, yaw);
    }

    /** The edge spawn for tile (tx, tz), or null when not on the edge of the grid. Pure. */
    public static Spawn spawnOnEdge(int tx, int tz, boolean outer) {
        int last = DungeonFrame.TILES - 1;
        double y = outer ? 76.5 : 75.5;
        double minX = DungeonFrame.tileMin(tx);
        double minZ = DungeonFrame.tileMin(tz);
        if (tz == 0) return new Spawn(minX + 15.5, y, minZ + (outer ? 11.5 : 14.5), 0f);
        if (tz == last) return new Spawn(minX + 15.5, y, minZ + (outer ? 19.5 : 17.5), 180f);
        if (tx == 0) return new Spawn(minX + (outer ? 11.5 : 14.5), y, minZ + 15.5, 270f);
        if (tx == last) return new Spawn(minX + (outer ? 19.5 : 17.5), y, minZ + 15.5, 90f);
        return null;
    }

    private static boolean solid(ServerLevel level, int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    /** First y from the bottom with two free blocks on something solid; SPAWN_FALLBACK_Y otherwise. */
    private static int floorY(ServerLevel level, int x, int z) {
        for (int y = 62; y <= 130; y++) if (solid(level, x, y - 1, z) && !solid(level, x, y, z) && !solid(level, x, y + 1, z)) return y;
        return SPAWN_FALLBACK_Y;
    }

    /** A room's floor is y 68, so a player stands at 69. */
    public static final int SPAWN_FALLBACK_Y = 69;

    public static void stop() { stop(EndReason.STOPPED); }

    /** Stops the current run. */
    public static void stop(EndReason reason) {
        Run r = current;
        if (r == null) return;
        r.end(reason);
        current = null;
        // no run any more: the clients' bridge goes back to "in boss"
        RunWire.broadcast(null);
        onStopped.accept(r);
    }

    /** The game side of a stop. AdsBootstrap installs the real one. */
    public static Consumer<Run> onStopped = r -> {};

    /** Every server tick from Fight.tick. */
    public static void tick(int serverTick) {
        Run r = current;
        if (r != null) r.tick(serverTick);
    }

    /** Engine only: makes run current (after begin). */
    public static void install(Run run) { current = run; }

    /** Fight.join: a party joiner arrived while a run is on. */
    public static void playerJoined(ServerPlayer player) {
        Run run = current;
        if (run == null) return;
        if (run.isOver()) return;
        EngineModule.addHuman(run, player);
        run.bus.post(new PlayerJoined(run.t(), player.getUUID()));
    }

    /** Fight.leave: a party joiner left. */
    public static void playerLeft(ServerPlayer player) {
        Run run = current;
        if (run == null) return;
        if (run.isOver()) return;
        run.state.players.remove(player.getUUID());
        run.bus.post(new PlayerLeft(run.t(), player.getUUID()));
    }

    /** The class a human plays, spelled as the bots' records spell it (Odin's enum names). */
    public static String classLabel(ServerPlayer player) { return Players.classOf(player).name(); }
}
