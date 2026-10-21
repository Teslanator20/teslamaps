// Ported from ADM's p3sim/run/EngineModule.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.Arena;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Fight;
import com.teslamaps.adm.sim.P4Necron;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimServer;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.settings.NumSetting;
import com.teslamaps.adm.sim.settings.SelSetting;
import com.teslamaps.adm.sim.settings.SimSettings;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;

public final class EngineModule implements AdsModule {
    public static final EngineModule INSTANCE = new EngineModule();

    private EngineModule() {}

    @Override public String id() { return "engine"; }
    @Override public String owner() { return "engine"; }

    public enum LagOption {
        OFF(LagPreset.OFF), GOOD(LagPreset.GOOD), TYPICAL(LagPreset.TYPICAL), BAD(LagPreset.BAD), HIGH_SLOT(LagPreset.HIGH_SLOT);

        public final LagPreset preset;

        LagOption(LagPreset preset) { this.preset = preset; }

        @Override public String toString() { return preset.label; }
    }

    public static SelSetting lagPresetS;
    public static NumSetting lagBurstS;

    @Override
    public void registerSettings() {
        List<String> options = new ArrayList<>();
        for (LagOption o : LagOption.values()) options.add(o.toString());
        lagPresetS = SimSettings.sel("Lag Preset", LagOption.OFF.toString(), options, "Makes the sim's server lose ticks the way Hypixel's does: it freezes for a moment, then runs a burst to catch up, and part of the lost time never comes back. Good, Typical and Bad lose 14.5, 30.3 and 56.6 ticks a minute (the measured 10th, 50th and 90th percentile of F7 runs); High slot is an instance slot above 200 (about 1.8 times Typical). A run's own lag choice takes priority.");
        lagBurstS = SimSettings.num("Lag Burst Rate", LagBurst.DEFAULT_RATE, 1.0, 6.0, 0.5, "", "How many times faster than normal the server runs the ticks it catches up after a freeze. Not measured on Hypixel (only how many ticks come back is), so this is a guess; 2 is the default.");
    }

    @Override
    public void onInit() {
        Ec.safely("ads spike client tick", () ->
                ClientTickEvents.END_CLIENT_TICK.register(mc -> Ec.safely("ads spike client", () -> EngineSpikes.clientTick(mc))));
        Ec.safely("ads hypixel mod api", HypixelModApi::install);
        Ec.safely("ads persistent mobs", () ->
                ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
                    if (entity instanceof Mob mob && SimServer.isSimLevel(level) && entity.entityTags().contains(Sim.TAG)
                            && level.getServer().getWorldData().getDifficulty() != Difficulty.PEACEFUL) mob.setPersistenceRequired();
                }));
    }

    public static void lagTick(MinecraftServer server) {
        EngineSpikes.tick(server);
        if (lagPresetS != null) {
            int i = lagPresetS.index();
            LagOption[] all = LagOption.values();
            if (i >= 0 && i < all.length) LagRuntime.settingPreset = all[i].preset;
        }
        if (lagBurstS != null) LagRuntime.burstRate = lagBurstS.get();
        LagRuntime.afterTick(server);
    }

    // ---- the run's plumbing ----

    public static final int END_SCREEN_AFTER_GONE_TICKS = 27;

    public static final int STAGE_RESEND_TICKS = 100;

    private static boolean defeatedMarked = false;

    @Override
    public void onRunStart(Run run) {
        defeatedMarked = false;
        holdArena(false);
        for (ServerPlayer p : Sim.players()) addHuman(run, p);
        Ec.safely("ads hypixel location", () -> HypixelModApi.runStarted(run));
    }

    @Override
    public void onStage(Run run, RunStage from, RunStage to) {
        if (to == RunStage.BLOOD) holdArena(true);
        else if (to.ordinal() < RunStage.BLOOD.ordinal()) holdArena(false);
    }

    @Override
    public void onRunEnd(Run run, EndReason reason) {
        holdArena(false);
        HypixelModApi.runEnded();
    }

    private static final ArrayList<Pair<Integer, Integer>> heldChunks = new ArrayList<>();

    public static int heldChunkCount() { return heldChunks.size(); }

    private static void holdArena(boolean on) {
        ServerLevel level = SimServer.level();
        if (!on) {
            if (level != null) for (Pair<Integer, Integer> c : heldChunks) Ec.safely("ads arena release", () -> level.setChunkForced(c.first, c.second, false));
            heldChunks.clear();
            return;
        }
        if (level == null || !heldChunks.isEmpty()) return;
        Arena.Data a = Arena.data();
        for (Pair<Integer, Integer> c : WorldEnv.chunksOf(a.x0, a.z0, a.w, a.d)) {
            Ec.safely("ads arena hold", () -> { level.setChunkForced(c.first, c.second, true); heldChunks.add(Pair.of(c.first, c.second)); });
        }
    }

    public static void addHuman(Run run, ServerPlayer p) {
        PlayerRecord rec = run.state.players.computeIfAbsent(p.getUUID(), k -> new PlayerRecord(p.getUUID(), p.getGameProfile().name(), Runs.classLabel(p), false));
        rec.x = p.getX(); rec.y = p.getY(); rec.z = p.getZ();
    }

    @Override
    public void onTick(Run run) {
        for (ServerPlayer p : Sim.players()) {
            if (run.state.players.get(p.getUUID()) == null) addHuman(run, p);
            PlayerRecord r = run.state.players.get(p.getUUID());
            if (r != null) { r.x = p.getX(); r.y = p.getY(); r.z = p.getZ(); }
        }
        if (run.serverTick % STAGE_RESEND_TICKS == 0) run.broadcastStage();
        HypixelModApi.tick(run);
        if (run.stage == RunStage.BOSS) watchNecron(run);
    }

    private static void watchNecron(Run run) {
        if (!(Fight.phase instanceof P4Necron ph)) return;
        if (ph.t >= P4Necron.GONE && !defeatedMarked) { defeatedMarked = true; run.state.milestones.bossDefeatedT = run.t(); }
        if (ph.t >= P4Necron.GONE + END_SCREEN_AFTER_GONE_TICKS) {
            if (run.state.milestones.endT == null) run.state.milestones.endT = run.t();
            Services.score().showEndScreen(run);
            run.requestStage(RunStage.END);
        }
    }

    @Override
    public void onEvent(Run run, RunEvent event) {
        if (event instanceof RoomEntered e) roomEntered(run, e);
        if (event instanceof PlayerJoined j) {
            for (ServerPlayer p : Sim.players()) {
                if (p.getUUID().equals(j.player)) { HypixelModApi.playerJoined(p); break; }
            }
        }
    }

    private static void roomEntered(Run run, RoomEntered e) {
        PlayerRecord who = run.state.players.get(e.player);
        if (who == null) return;
        if (who.isBot || run.stage.ordinal() > RunStage.CLEAR.ordinal()) return;
        RoomRecord room = run.state.room(e.room);
        if (room == null) return;
        if (room.plan.type == RoomType.ENTRANCE) return;
        SavepointStore store = run.savepoints;
        Savepoint last = store.forRoom(e.room);
        if (last != null && last.t <= run.t()) return;
        store.take(run, "Room: " + room.plan.name, e.room);
    }

    @Override
    public List<AdsCommand> devCommands() { return EngineCommands.dev(); }
}
