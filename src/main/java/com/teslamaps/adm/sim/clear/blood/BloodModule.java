// Ported from ADM's p3sim/clear/blood/BloodModule.kt
package com.teslamaps.adm.sim.clear.blood;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.BossBar;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.clear.blood.camp.WatcherCamp;
import com.teslamaps.adm.sim.pack.mobs.MobArchetype;
import com.teslamaps.adm.sim.pack.rooms.RoomPack;
import com.teslamaps.adm.sim.pack.rooms.RoomVolume;
import com.teslamaps.adm.sim.pack.rooms.SkullTexture;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsModule;
import com.teslamaps.adm.sim.run.AdsServerContext;
import com.teslamaps.adm.sim.run.BloodDoorOpened;
import com.teslamaps.adm.sim.run.EndReason;
import com.teslamaps.adm.sim.run.ModuleEvent;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunEvent;
import com.teslamaps.adm.sim.run.RunStage;
import com.teslamaps.adm.sim.run.WatcherDone;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.run.plugin.AdsPlugins;
import com.teslamaps.adm.sim.run.plugin.BloodCamp;
import com.teslamaps.adm.sim.run.plugin.BloodCampFactory;
import com.teslamaps.adm.sim.run.plugin.HeadSlot;
import com.teslamaps.adm.sim.settings.BoolSetting;
import com.teslamaps.adm.sim.settings.NumSetting;
import com.teslamaps.adm.sim.settings.SimSettings;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;

/**
 * The public blood room and the boss seam: on BloodDoorOpened the run goes to BLOOD, the Watcher and the wall heads go up and a
 * camp runs (a plug-in's, else WatcherCamp, NeutralCamp for a room with too few heads). The exit (BloodExit) counts from the
 * defeat; one tick after a body touches the portal, BossEntry jumps the party to Maxor. Dev commands: `/ads dev blood`.
 */
public final class BloodModule implements AdsModule {
    public static final BloodModule INSTANCE = new BloodModule();

    private BloodModule() {}

    @Override public String id() { return "blood"; }

    @Override public String owner() { return "blood"; }

    // ---- settings (null until registerSettings; the defaults are used before that)

    /** UNMEASURED: five minutes, a safety net only. */
    public static final int DEFAULT_CAMP_TIMEOUT = 6000;
    public static final int DEFAULT_PLACEHOLDER_MOBS = 6;
    public static final int DEFAULT_PLACEHOLDER_FIRST = 60;
    public static final int DEFAULT_PLACEHOLDER_INTERVAL = 40;

    /** Ticks between the Watcher bar's resends (recorded 19 to 21). */
    public static final int BAR_RESEND_TICKS = 20;

    /** The most steps one tick can answer. */
    private static final int MAX_STEPS = 8;

    /** Portal cooldown for every human while the portal is open, so vanilla's portal never moves a player. */
    private static final int PORTAL_COOLDOWN = 40;

    private static NumSetting campTimeoutS = null;
    private static BoolSetting headsS = null;
    private static NumSetting placeholderMobsS = null;
    private static NumSetting placeholderFirstS = null;
    private static NumSetting placeholderIntervalS = null;

    @Override public void registerSettings() {
        campTimeoutS = SimSettings.num("Blood Camp Timeout", DEFAULT_CAMP_TIMEOUT, 0, 24000, 100, " ticks", "If the blood room's camp has not finished this long after the blood door opened, the Watcher lets the party pass anyway. 0: never. Only a safety net; not measured.");
        headsS = SimSettings.bool("Blood Wall Heads", true, "Put the blood room's wall heads on their stands when the blood door opens. Off: bare walls.");
        placeholderMobsS = SimSettings.num("Blood Placeholder Mobs", DEFAULT_PLACEHOLDER_MOBS, 0, 40, 1, "", "Only for a blood room with too few wall heads for the Watcher's camp: the plain stand-in releases this many ordinary mobs, one at a time. Not the real camp.");
        placeholderFirstS = SimSettings.num("Blood Placeholder First Mob", DEFAULT_PLACEHOLDER_FIRST, 0, 1200, 5, " ticks", "Ticks from the blood door opening to the stand-in's first mob.");
        placeholderIntervalS = SimSettings.num("Blood Placeholder Interval", DEFAULT_PLACEHOLDER_INTERVAL, 1, 400, 1, " ticks", "Ticks between the stand-in's mobs.");
    }

    private static int campTimeout() { return campTimeoutS != null ? campTimeoutS.getInt() : DEFAULT_CAMP_TIMEOUT; }
    private static boolean wallHeads() { return headsS != null ? headsS.get() : true; }
    private static int phMobs() { return placeholderMobsS != null ? placeholderMobsS.getInt() : DEFAULT_PLACEHOLDER_MOBS; }
    private static int phFirst() { return placeholderFirstS != null ? placeholderFirstS.getInt() : DEFAULT_PLACEHOLDER_FIRST; }
    private static int phInterval() { return placeholderIntervalS != null ? placeholderIntervalS.getInt() : DEFAULT_PLACEHOLDER_INTERVAL; }

    // ---- state of the current run's blood stage

    private static BloodFlow flow = newFlow(null);
    private static BloodRoom room = null;
    private static BloodHost host = null;
    private static BloodCamp camp = null;
    private static boolean campBroken = false;
    private static boolean forcePass = false;

    /** A replay: the recording's Watcher let the party pass now. False when no camp is running. Server thread. */
    public static boolean passNow() {
        if (flow.phase != BloodFlow.Phase.CAMP) return false;
        forcePass = true;
        return true;
    }

    private static WatcherEntity watcher = null;
    private static WallHeads heads = null;

    /** Whether the Watcher's boss bar is up, and what it last showed. */
    private static boolean barUp = false;
    private static int barKills = -1;
    private static int barSince = 0;

    private static BloodFlow newFlow(Run run) { return new BloodFlow(campTimeout(), run != null ? run.subRandom("blood-exit") : new KRandom(0)); }

    private static void reset(Run run) {
        // the bar goes with the room (a restore to before the door); tearDown alone leaves it for the entry to rename
        if (barUp) BossBar.hide();
        tearDown();
        flow = newFlow(run);
        room = null; camp = null; host = null; campBroken = false; forcePass = false;
        barKills = -1; barSince = 0;
    }

    /** Removes the Watcher, the wall-head stands and the camp's leftovers. The portal blocks stay. */
    private static void tearDown() {
        try { if (host != null) host.cleanUp(); } catch (Throwable t) { Ec.LOG.warn("[ads] blood cleanup failed", t); }
        try { if (watcher != null) watcher.remove(); } catch (Throwable t) { Ec.LOG.warn("[ads] blood watcher removal failed", t); }
        try { if (heads != null) heads.removeAll(); } catch (Throwable t) { Ec.LOG.warn("[ads] blood heads removal failed", t); }
        watcher = null; heads = null;
        barUp = false;
    }

    @Override public void onRunStart(Run run) { reset(run); }

    @Override public void onRunEnd(Run run, EndReason reason) { reset(run); }

    @Override public void onStage(Run run, RunStage from, RunStage to) {
        // a savepoint restore before the blood door: the room is as generated again
        if (to.ordinal() < RunStage.BLOOD.ordinal()) reset(run);
        // the entry renamed the bar to Maxor's: it is the boss stage's now
        else if (to.ordinal() > RunStage.BLOOD.ordinal()) tearDown();
    }

    @Override public void onEvent(Run run, RunEvent event) {
        if (event instanceof BloodDoorOpened) { onBloodDoor(run); return; }
        BloodCamp c = camp;
        if (c == null) return;
        if (flow.phase != BloodFlow.Phase.CAMP || campBroken) return;
        try { c.onEvent(event); } catch (Throwable t) { breakCamp("onEvent", t); }
    }

    @Override public void onTick(Run run) {
        if (flow.phase == BloodFlow.Phase.CLOSED || flow.phase == BloodFlow.Phase.ENTERED) return;
        if (run.stage != RunStage.BLOOD) return;
        if (host != null) host.tick();
        BloodCamp c = camp;
        if (flow.phase == BloodFlow.Phase.CAMP && c != null && !campBroken) {
            try { c.tick(); } catch (Throwable t) { breakCamp("tick", t); }
        }
        updateBar(run);
        boolean finished;
        if (forcePass || campBroken || c == null) finished = true;
        else {
            try { finished = c.finished(); } catch (Throwable t) { breakCamp("finished", t); finished = true; }
        }
        for (int i = 0; i < MAX_STEPS; i++) {
            if (flow.phase == BloodFlow.Phase.PORTAL) keepVanillaPortalAway();
            boolean contact = flow.phase == BloodFlow.Phase.PORTAL && portalContact(run);
            switch (flow.step(run.serverTick, finished, contact)) {
                case SAY_PASS -> sayPass(run);
                case STRIKE -> BossEntry.strike(room != null ? room.exit : null);
                case OPEN_PORTAL -> openPortal(run);
                case REMOVE_WATCHER -> { if (watcher != null) watcher.remove(); }
                case ENTER -> { enter(run); return; }
                case NONE -> { return; }
            }
        }
    }

    // ---- the blood door

    private static RoomVolume volumeOf(Run run) {
        PlacedRoom placed = run.plan.blood();
        RoomPack rooms = run.packs.rooms;
        return placed != null && placed.design != null && rooms != null ? rooms.volumeFor(placed.design, placed.variantId) : null;
    }

    private static List<SkullTexture> textures(Run run) {
        RoomPack rooms = run.packs.rooms;
        return rooms != null && rooms.textures != null ? rooms.textures : List.of();
    }

    private static void onBloodDoor(Run run) {
        if (flow.phase != BloodFlow.Phase.CLOSED) return;
        RoomVolume volume = volumeOf(run);
        BloodRoom r = BloodRoom.of(run.plan, volume);
        if (r == null) {
            // no blood room: skip the stage and go to the boss
            Ec.LOG.warn("[ads] this layout has no blood room; skipping to the boss");
            Sim.note("This layout has no blood room, so the run goes straight to Maxor.");
            if (run.stage == RunStage.CLEAR || run.stage == RunStage.BLOOD) {
                if (run.state.milestones.bloodOpenT == null) run.state.milestones.bloodOpenT = run.t();
                run.state.milestones.watcherDoneT = run.t();
                BossEntry.enter(run);
            }
            return;
        }
        if (run.stage == RunStage.CLEAR) run.requestStage(RunStage.BLOOD);
        if (run.stage != RunStage.BLOOD) { Ec.LOG.warn("[ads] the blood door opened in stage " + run.stage + "; the blood room does not start"); return; }
        if (run.state.milestones.bloodOpenT == null) run.state.milestones.bloodOpenT = run.t();
        room = r;
        // the room's RED mark is the doors module's

        BloodFlow f = newFlow(run);
        f.open(run.serverTick);
        flow = f;
        startRoom(run, r, textures(run), wh -> wh.placeAll());
        campBroken = false; forcePass = false;
        try { if (camp != null) camp.start(); } catch (Throwable t) { breakCamp("start", t); }
    }

    /** Stands the Watcher, the wall-head stands and the camp up; fill fills the wall. */
    private static void startRoom(Run run, BloodRoom r, List<SkullTexture> textures, Consumer<WallHeads> fill) {
        // the doorway starts open: some recorded builds have the fight's portal standing in it
        if (!flow.portalOpen() && r.exit != null) BossEntry.clearPortal(r.exit);
        WatcherEntity w = new WatcherEntity(r.watcherX(), r.watcherHomeY(), r.watcherZ(), r.watcherYaw(), textures, run.subRandom("blood-watcher"));
        if (!flow.watcherRemoved()) { w.spawn(); w.setKills(flow.kills); }
        watcher = w;
        WallHeads wh = new WallHeads(wallHeads() ? r.heads() : new ArrayList<>(), textures);
        fill.accept(wh);
        heads = wh;
        BloodHost h = new BloodHost(run, r, flow, wh, w);
        host = h;
        camp = makeCamp(run, h, r);
    }

    private static BloodCamp makeCamp(Run run, BloodHost h, BloodRoom r) {
        BloodCampFactory factory = AdsPlugins.bloodCamp(r);
        if (factory != null) {
            try { return factory.create(h); } catch (Throwable t) { Ec.LOG.warn("[ads] the blood camp plug-in failed to start; using the Watcher's own camp", t); }
        }
        if (WatcherCamp.supports(r)) return new WatcherCamp(h);
        Set<String> known = null;
        if (run.packs.mobs != null && run.packs.mobs.archetypes != null) {
            known = new HashSet<>();
            for (MobArchetype a : run.packs.mobs.archetypes) known.add(a.id);
        }
        List<String> kinds = new ArrayList<>();
        for (String k : NeutralCamp.DEFAULT_KINDS) if (known == null || known.contains(k)) kinds.add(k);
        if (kinds.isEmpty()) kinds = NeutralCamp.DEFAULT_KINDS;
        return new NeutralCamp(h, phMobs(), phFirst(), phInterval(), kinds);
    }

    private static void breakCamp(String what, Throwable t) {
        if (!campBroken) {
            campBroken = true;
            Ec.LOG.warn("[ads] the blood camp failed in " + what + "; ending the Watcher stage", t);
            Sim.note("The blood room's camp failed (" + what + "), so the Watcher lets you pass.");
        }
    }

    // ---- the Watcher's bar

    /** Up from the first tick a human is inside the room, fill = his health, resent every BAR_RESEND_TICKS. */
    private static void updateBar(Run run) {
        BloodHost h = host;
        if (h == null) return;
        float progress = WatcherStats.barProgress(flow.kills);
        if (!barUp) {
            boolean anyone = false;
            for (PlayerRecord p : run.state.players.values()) if (!p.isBot && h.inRoom(p.x, p.z)) { anyone = true; break; }
            if (!anyone) return;
            BossBar.show(BAR_NAME, progress);
            barUp = true; barKills = flow.kills; barSince = run.serverTick;
            return;
        }
        // a human who came in after the bar went up sees it too
        BossBar.show(BAR_NAME, progress);
        if (flow.kills != barKills) { barKills = flow.kills; barSince = run.serverTick; }
        if (run.serverTick - barSince >= BAR_RESEND_TICKS) { BossBar.resend(); barSince = run.serverTick; }
    }

    // ---- the pass line, the portal and the jump

    private static void sayPass(Run run) {
        if (watcher != null) watcher.say(WatcherLines.PASS);
        else Services.channels().chat(WatcherLines.chat(WatcherLines.PASS));
        if (flow.timedOut) Sim.note("The blood room's camp took longer than the Blood Camp Timeout setting, so the Watcher lets you pass.");
        run.state.milestones.watcherDoneT = run.t();
        run.bus.post(new WatcherDone(run.t()));
    }

    private static void openPortal(Run run) {
        BossEntry.openPortal(run, room != null ? room.exit : null);
        // the room turns GREEN now (the doors module answers this notice)
        run.bus.post(new ModuleEvent(run.t(), "blood", "portal"));
        keepVanillaPortalAway();
    }

    /** Whether a living member's body touches the open portal; without an alcove the jump follows the portal tick. */
    private static boolean portalContact(Run run) {
        BloodGeometry.ExitSite site = room != null ? room.exit : null;
        if (site == null) return true;
        for (PlayerRecord rec : run.state.players.values()) {
            if (rec.ghost) continue;
            if (site.touches(rec.x, rec.y, rec.z)) return true;
        }
        return false;
    }

    /** A cooldown keeps vanilla's nether portal from taking a player to another dimension. */
    private static void keepVanillaPortalAway() {
        for (ServerPlayer p : Sim.players()) if (p.getPortalCooldown() < PORTAL_COOLDOWN / 2) p.setPortalCooldown(PORTAL_COOLDOWN);
    }

    private static void enter(Run run) {
        tearDown();
        BossEntry.enter(run);
    }

    // ---- clicks on the wall: the stands are scenery

    @Override public InteractionResult onUseEntity(Run run, ServerPlayer player, InteractionHand hand, Entity entity) {
        return heads != null && heads.owns(entity) ? InteractionResult.FAIL : null;
    }

    @Override public InteractionResult onAttackEntity(Run run, ServerPlayer player, InteractionHand hand, Entity entity) {
        return heads != null && heads.owns(entity) ? InteractionResult.FAIL : null;
    }

    // ---- savepoints

    @Override public JsonObject snapshot(Run run) {
        if (flow.phase == BloodFlow.Phase.CLOSED) return null;
        BloodFlow.FlowSnapshot s = flow.snapshot(run.serverTick);
        JsonObject o = new JsonObject();
        o.addProperty("phase", s.phase.name());
        if (s.sinceOpen != null) o.addProperty("sinceOpen", s.sinceOpen);
        if (s.sincePass != null) o.addProperty("sincePass", s.sincePass);
        o.addProperty("timedOut", s.timedOut);
        o.addProperty("kills", s.kills);
        if (s.sinceDefeat != null) o.addProperty("sinceDefeat", s.sinceDefeat);
        if (s.portalDelay != null) o.addProperty("portalDelay", s.portalDelay);
        if (s.sinceContact != null) o.addProperty("sinceContact", s.sinceContact);
        o.addProperty("struck", s.struck);
        o.addProperty("watcherGone", s.watcherGone);
        JsonArray a = new JsonArray();
        if (heads != null) for (String k : heads.keys()) a.add(k != null ? k : "");
        o.add("heads", a);
        try {
            if (camp != null) {
                JsonObject cs = camp.snapshot();
                if (cs != null) o.add("camp", cs);
            }
        } catch (Throwable t) { Ec.LOG.warn("[ads] the blood camp could not be saved", t); }
        return o;
    }

    private static Integer intOrNull(JsonObject data, String key) {
        JsonElement e = data.get(key);
        return e != null ? e.getAsInt() : null;
    }

    private static boolean boolOr(JsonObject data, String key) {
        JsonElement e = data.get(key);
        return e != null && e.getAsBoolean();
    }

    @Override public void restore(Run run, JsonObject data) {
        reset(run);
        BloodFlow.Phase phase;
        try { phase = BloodFlow.Phase.valueOf(data.get("phase").getAsString()); } catch (Exception e) { return; }
        if (phase == BloodFlow.Phase.CLOSED || phase == BloodFlow.Phase.ENTERED) return;
        Integer kills = intOrNull(data, "kills");
        BloodFlow.FlowSnapshot s = new BloodFlow.FlowSnapshot(
                phase, intOrNull(data, "sinceOpen"), intOrNull(data, "sincePass"), boolOr(data, "timedOut"),
                kills != null ? kills : 0, intOrNull(data, "sinceDefeat"), intOrNull(data, "portalDelay"), intOrNull(data, "sinceContact"),
                boolOr(data, "struck"), boolOr(data, "watcherGone"));
        RoomVolume volume = volumeOf(run);
        BloodRoom r = BloodRoom.of(run.plan, volume);
        if (r == null) return;
        room = r;
        BloodFlow f = newFlow(run);
        f.restore(s, run.serverTick);
        flow = f;
        List<String> saved = new ArrayList<>();
        JsonArray ha = data.getAsJsonArray("heads");
        if (ha != null) for (JsonElement e : ha) { String k = e.getAsString(); saved.add(k.isEmpty() ? null : k); }
        startRoom(run, r, textures(run), wh -> wh.reapply(saved));
        // the reset took the portal blocks away with the rest of the room: put them back when the portal was open
        if (flow.portalOpen() && r.exit != null) BossEntry.setPortal(r.exit);
        JsonObject cd = data.getAsJsonObject("camp");
        if (cd != null) {
            try { if (camp != null) camp.restore(cd); } catch (Throwable t) { Ec.LOG.warn("[ads] the blood camp could not be restored", t); }
        }
    }

    // ---- dev commands

    @Override public List<AdsCommand> devCommands() {
        return List.of(new AdsCommand("blood", "/ads dev blood <status|open|pass|kills N|enter|heads>",
                "Drives the blood room and the boss entry: status, open the blood door, end the camp, set the Watcher's counted kills (15 = defeat, portal 80 or 90 ticks later), jump to Maxor now, list the wall heads.",
                true, null, BloodModule::devBlood));
    }

    private static void devBlood(AdsServerContext ctx) {
        Run run = ctx.run();
        if (run == null) { ctx.reply("No run is on. Start one with /ads run random."); return; }
        String a0 = ctx.arg(0);
        if (a0 == null || a0.equals("status")) {
            ctx.reply("Stage " + run.stage + ", blood phase " + flow.phase + ", " + flow.ticksSinceOpen(run.serverTick) + " ticks since the door opened.");
            String what;
            if (camp == null) what = "none yet";
            else if (camp instanceof NeutralCamp) what = "the plain stand-in";
            else if (camp instanceof WatcherCamp) what = "the Watcher's camp";
            else what = "a plug-in camp";
            ctx.reply("Camp: " + what + (campBroken ? " (failed)" : ""));
            BloodExit e = flow.exit;
            ctx.reply("Watcher: " + flow.kills + " counted kills, health " + WatcherStats.health(flow.kills)
                    + (e == null ? ", not defeated" : ", defeated " + (run.serverTick - e.defeatTick) + " ticks ago, portal at +" + e.portalDelay + ", gone at +" + BloodExit.WATCHER_GONE));
            int onWall = 0;
            if (heads != null) for (String k : heads.keys()) if (k != null) onWall++;
            ctx.reply("Heads: " + (heads != null ? heads.slots.size() : 0) + " slots, " + onWall + " on the wall. Portal " + (flow.portalOpen() ? "open" : "closed") + ".");
            return;
        }
        switch (a0) {
            case "open" -> {
                if (run.stage != RunStage.CLEAR) ctx.reply("The blood door can only open in the clear (the stage is " + run.stage + ").");
                else { run.bus.post(new BloodDoorOpened(run.t())); ctx.reply("Blood door opened."); }
            }
            case "pass" -> {
                if (flow.phase != BloodFlow.Phase.CAMP) ctx.reply("The camp is not running.");
                else { forcePass = true; ctx.reply("The Watcher will let you pass this tick."); }
            }
            case "kills" -> {
                Integer n = null;
                String a1 = ctx.arg(1);
                if (a1 != null) { try { n = Integer.parseInt(a1); } catch (NumberFormatException ignored) {} }
                BloodHost h = host;
                if (n == null) ctx.reply("Usage: /ads dev blood kills <0 to 15>");
                else if (h == null || flow.phase == BloodFlow.Phase.CLOSED) ctx.reply("The blood door has not opened.");
                else { h.watcherKills(n); ctx.reply("The Watcher has " + flow.kills + " counted kills."); }
            }
            case "enter" -> {
                switch (flow.phase) {
                    case CAMP, PASSED, PORTAL -> { flow.expedite(); ctx.reply("Skipping the waits: the pass line, the portal and the jump to Maxor."); }
                    default -> ctx.reply("The blood door has not opened (or the jump is done).");
                }
            }
            case "heads" -> {
                WallHeads h = heads;
                if (h == null) { ctx.reply("The blood room is not open yet."); return; }
                List<String> keys = h.keys();
                for (HeadSlot s : h.slots) {
                    String k = keys.get(s.index);
                    ctx.reply("slot " + s.index + ": " + s.x + " " + s.y + " " + s.z + " facing " + s.facing + ", shows " + (k != null ? k : "nothing"));
                }
            }
            default -> ctx.reply("Usage: /ads dev blood <status|open|pass|kills N|enter|heads>");
        }
    }

    /** The boss bar's name while the Watcher is the one it is about. */
    private static final String BAR_NAME = "§c§lThe Watcher";
}
