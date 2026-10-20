package com.teslamaps.adm.leap.splits;

// Ported from ADM's splits/DungeonSplits.kt
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.adm.Adm;
import com.teslamaps.adm.leap.AdmWorld;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.features.LegitMode;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Sub Splits: blood rush, Storm and Goldor sub-split HUDs, plus the run tracking behind the ADM splits look. */
public final class DungeonSplits {

    private DungeonSplits() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static final SplitTracker tracker = new SplitTracker();
    private static final SubSplitTracker subs = new SubSplitTracker();
    private static final BloodRunDetail blood = new BloodRunDetail();
    private static final BossMoments moments = new BossMoments();
    private static final StormDetail storm = new StormDetail();
    private static final StormCrushView crushView = new StormCrushView();
    private static final GoldorCore core = new GoldorCore();

    private static final int RECENT = 20;
    private static final int RECENT_MIN = 5;
    private static final int LINE_HEIGHT = 10;

    private static int serverTicks;
    private static long lastTimerTick = -1;

    private static Stamp now() { return new Stamp(System.currentTimeMillis(), serverTicks); }

    private static int[] coreGoldor; // id, tick
    private static Vec3 coreGoldorAt;
    private static final List<int[]> barriers = new ArrayList<>();
    private static final List<int[]> cleared = new ArrayList<>();
    private static final Set<Integer> keysSeen = new HashSet<>();
    private static final Set<Integer> crystalsSeen = new HashSet<>();
    private static final Set<Integer> watchedMobs = new HashSet<>();
    private static final int[] sectionDoor = new int[3];
    private static final int[][] SECTION_DOORS = {{92, 108, 120, 125}, {15, 20, 124, 140}, {0, 16, 47, 52}};
    private static final int SECTION_DOOR_BLOCKS = 100;
    private static final Vec3 NECRON_MID = new Vec3(54.0, 66.0, 76.0);
    private static final double GOLDOR_GO = 0.25;
    private static final String CORE_OPEN = "The Core entrance is opening!";
    private static final String NECRON_LINE = "[BOSS] Necron: ";
    private static final Pattern KEY = Pattern.compile("(?:Wither|Blood) Key");
    private static final List<String> WATCHER_SKINS = List.of("5662b6fb4b8b", "2739d7f4e66a", "bf6e1e7ed365", "4cec40008e1c", "b37dd18b5983", "f5f0d78fe38d", "51967db5e319", "9fd61e8055f6", "e5c1dc47a04c");
    private static final int DEATH_BURST_TNT = 3;
    private static final int DEATH_BURST_TICKS = 2;
    private static final int NECRON_GONE = 20;

    private static final ArrayDeque<Stamp> necronTnt = new ArrayDeque<>();
    private static Integer necronId;
    private static Stamp necronDiedAt;
    private static int[] goldorAtId;
    private static Vec3 goldorAtPos;
    private static boolean goldorMoved;
    private static int watcherId = -1;
    private static Vec3 watcherPos;

    private static final Set<String> bestsChecked = new HashSet<>();
    private static boolean realRun = true;
    private static final ArrayDeque<Long> lagSamples = new ArrayDeque<>();
    private static boolean lagShown;

    private static HudEditElement bloodHud, stormHud, goldorHud;

    private static final List<String> BLOOD_SAMPLE = List.of(
            "§70.52s §8| \t§c1.73s \t§5Hallway: \t§62.31s",
            "\t§411.73s \t§dDino: \t§622.31s");
    private static final List<String> STORM_SAMPLE = List.of(
            "§bStorm §f41.30s",
            "§aOpening §732.40s",
            " §8· §eGiga Lightning §726.00s", " §8· §eLightning line §727.45s", " §8· §5Purple pad §7read 1 §729.95s", " §8· §bLeft his spot §8(4.95s after the line) §732.40s",
            "§6Crush §a0.55s", " §8· §6Crushed by §5Purple §8t660 §a(first check) §733.00s",
            "§cPin §a0.10s", "§bFlight §e4.50s", " §8· §eReached Yellow §737.60s",
            "§6Crush §a0.95s", "§cKill §a0.15s", "§aAnimation §73.10s");
    private static final List<String> GOLDOR_SAMPLE = List.of("§eCore", "§7Teammate §3-2.40s", "§fYou §a0.05s", "§7Another §8~0.50s", "§7Last §61.35s", "§eGoldor moved §f1.40s §8(+0.05s after the last in)");

    /** Sub Splits on, or Recore's Paste Last In Core: the run is tracked. */
    public static boolean tracking() {
        TeslaMapsConfig c = cfg();
        return Adm.on() && (c.admSubSplits || (c.admRecore && c.admRecorePasteLast));
    }

    private static boolean crushInfo() {
        return cfg().admSubSplits && cfg().admStormCrushInfo && !LegitMode.blocksCheats();
    }

    public static void register() {
        bloodHud = HudEditElement.of("admBrSubSplit", "BR Sub Split").ctx(HudEditElement.HudCtx.DUNGEON).section(Adm.SECTION)
                .enabled(() -> cfg().admSubSplits)
                .size(() -> size(BLOOD_SAMPLE)[0], () -> size(BLOOD_SAMPLE)[1])
                .preview((g, mc, x, y, s) -> drawAt(g, x, y, s, BLOOD_SAMPLE));
        stormHud = HudEditElement.of("admStormSubSplit", "Storm Sub Split").ctx(HudEditElement.HudCtx.DUNGEON).section(Adm.SECTION)
                .enabled(() -> cfg().admSubSplits)
                .size(() -> size(STORM_SAMPLE)[0], () -> size(STORM_SAMPLE)[1])
                .preview((g, mc, x, y, s) -> drawAt(g, x, y, s, STORM_SAMPLE));
        goldorHud = HudEditElement.of("admGoldorSubSplit", "Goldor Sub Split").ctx(HudEditElement.HudCtx.DUNGEON).section(Adm.SECTION)
                .enabled(() -> cfg().admSubSplits)
                .size(() -> size(GOLDOR_SAMPLE)[0], () -> size(GOLDOR_SAMPLE)[1])
                .preview((g, mc, x, y, s) -> drawAt(g, x, y, s, GOLDOR_SAMPLE));

        Feature.of("AdmSubSplits").section(Adm.SECTION).when(DungeonSplits::tracking)
                .tick(DungeonSplits::tick)
                .serverChat(DungeonSplits::onChat)
                .world((pose, cam) -> renderWorld(pose, cam))
                .hud("admsubsplits", DungeonSplits::renderHud)
                .config("ADM Splits", b -> {
                    b.label("Sub Splits");
                    b.toggle("Sub Splits", () -> cfg().admSubSplits, v -> cfg().admSubSplits = v)
                            .desc("The blood rush sub splits, room by room; Storm's: his steps with the lightning, every pad read and how late each crush came; and Goldor's: each leap into the core after S4 and Goldor starting to move. HUDs in /tmap gui.");
                    b.dropdown("Blood Rush Detail", new String[]{"Compact", "Detailed", "Debug"}, () -> cfg().admBrDetail, v -> cfg().admBrDetail = v)
                            .desc("How much the Blood Rush sub-split HUD shows. Debug adds every extra moment known about it.");
                    b.toggle("Blood Rush Total Row", () -> cfg().admBrTotalRow, v -> cfg().admBrTotalRow = v)
                            .desc("The averages row at the bottom of the compact blood rush splits.");
                    b.toggle("Blood Rush Hide In Boss", () -> cfg().admBrHideInBoss, v -> cfg().admBrHideInBoss = v)
                            .desc("Hides the blood rush sub splits once you are in the boss.");
                    b.dropdown("Storm Detail", new String[]{"Compact", "Detailed"}, () -> cfg().admStormDetail, v -> cfg().admStormDetail = v)
                            .desc("Compact: the steps on one line. Detailed: a line per step, with what happened in it under each.");
                    b.toggle("Show Crush Info", () -> cfg().admStormCrushInfo, v -> cfg().admStormCrushInfo = v).cheat()
                            .desc("Storm's crush hitbox (through walls): from his lightning on, at every 20-tick crush check with him within 5 blocks of a pillar, where the check had him - green if inside that pillar's crush zone, red if not, and by how much - and each check in the Storm sub split.");
                    b.slider("Hitbox Seconds", 1, 10, () -> (float) cfg().admStormHitboxSeconds, v -> cfg().admStormHitboxSeconds = Math.round(v), 0)
                            .desc("How long each check's hitbox stays up.");
                    b.toggle("Hitbox Margins", () -> cfg().admStormHitboxMargins, v -> cfg().admStormHitboxMargins = v)
                            .desc("Over each hitbox, how far in or out of the zone sideways, and how far his head was above or below the pillar's bottom.");
                    b.toggle("Pillar Outline", () -> cfg().admStormPillarOutline, v -> cfg().admStormPillarOutline = v)
                            .desc("The square the hitbox has to fit inside, at the height of the pillar's bottom, with each hitbox.");
                    b.color("Inside Color", () -> cfg().admStormInsideColor, v -> cfg().admStormInsideColor = v)
                            .desc("Hitbox colour when the check had him inside the zone.");
                    b.color("Outside Color", () -> cfg().admStormOutsideColor, v -> cfg().admStormOutsideColor = v)
                            .desc("Hitbox colour when the check had him outside it.");
                });
    }

    // ------------------------------------------------------------------ run state

    private static void resetRun() {
        bestsChecked.clear();
        realRun = true;
        synchronized (lagSamples) { lagShown = false; lagSamples.clear(); }
        tracker.reset(); subs.reset(); blood.reset(); moments.reset();
        core.reset(); coreGoldor = null; coreGoldorAt = null;
        storm.reset(); crushView.reset();
        goldorAtId = null; goldorAtPos = null; goldorMoved = false; necronId = null; necronDiedAt = null;
        watcherId = -1; watcherPos = null;
        barriers.clear(); cleared.clear(); keysSeen.clear(); crystalsSeen.clear(); watchedMobs.clear(); necronTnt.clear();
    }

    public static void onWorldChange() {
        resetRun();
        lastTimerTick = ServerTickTimer.now();
    }

    private static void onServerTick() {
        serverTicks++;
        subs.onServerTick();
        sampleLag();
        storm.onServerTick(serverTicks);
        Integer t = storm.phase(serverTicks);
        if (crushInfo() && storm.checking() && t != null && StormCrush.isCheck(t)) {
            StormCrushView.Check c = crushView.check(serverTicks);
            if (c != null) storm.onCheck(serverTicks, c.verdict(), c.armed());
        }
    }

    private static boolean inDungeons() { return DungeonManager.isInDungeon(); }

    private static String floor() { return DungeonManager.getFloorName(); }

    private static void onChat(String raw) {
        if (!inDungeons()) return;
        String text = StringUtil.strip(raw);
        Stamp at = now();
        tracker.onChat(text, at);
        moments.onChat(text, at);
        subs.onChat(text, at);
        blood.onChat(text, at);
        storm.onChat(text, at.tick(), "M7".equals(floor()));
        if (text.equals(CORE_OPEN)) core.onCoreOpen(at.tick());
        else if (text.startsWith(NECRON_LINE)) core.onNecron();
    }

    public static void onBlockUpdate(BlockPos pos, BlockState old, BlockState updated) {
        if (!tracking()) return;
        if (blood.active()) {
            if (updated.is(Blocks.BARRIER) && !old.is(Blocks.BARRIER)) barriers.add(new int[]{pos.getX(), pos.getZ()});
            else if (old.is(Blocks.BARRIER) && updated.isAir()) cleared.add(new int[]{pos.getX(), pos.getZ()});
        }
        if (open(SplitTracker.TERMS) && old.is(Blocks.BARRIER) && updated.isAir()) {
            for (int i = 0; i < SECTION_DOORS.length; i++) {
                int[] d = SECTION_DOORS[i];
                if (pos.getX() >= d[0] && pos.getX() <= d[1] && pos.getZ() >= d[2] && pos.getZ() <= d[3]) { sectionDoor[i]++; break; }
            }
        }
        if (storm.active() && updated.is(Blocks.MOVING_PISTON) && pos.getY() >= StormCrush.FLOOR && pos.getY() <= StormCrush.TOP) {
            for (StormCrush.Pillar p : StormCrush.PILLARS) {
                if (pos.getX() == p.columnX() && pos.getZ() == p.columnZ()) {
                    storm.onPillarStep(p, serverTicks);
                    crushView.onStep(p, serverTicks);
                    break;
                }
            }
        }
        if (open(SplitTracker.MAXOR) && pos.getX() == 73 && pos.getY() == 221 && pos.getZ() == 73 && updated.is(Blocks.BEDROCK)) subs.onMaxorKilled(now());
    }

    public static void onEntityAdd(Entity e) {
        if (!tracking()) return;
        if (e instanceof EndCrystal && open(SplitTracker.MAXOR) && crystalsSeen.add(e.getId())) {
            if (e.getY() >= 231) subs.onTopCrystal(now());
        } else if (e instanceof PrimedTnt && open(SplitTracker.NECRON)) {
            onNecronTnt(now());
        } else if (e instanceof Player p && open(SplitTracker.BLOOD) && !teamNames().contains(p.getName().getString())) {
            if (watchedMobs.add(e.getId())) subs.onBloodMobSpawn(now());
        }
    }

    public static void onEntityRemove(Entity e) {
        if (!tracking()) return;
        if (e instanceof WitherBoss && open(SplitTracker.NECRON) && necronId != null && e.getId() == necronId && distanceTo(e) <= 48) {
            necronDead(now().minus(NECRON_GONE));
        }
        if (AdmWorld.isBossWither(e) && open(SplitTracker.MAXOR) && distanceTo(e) <= 48) subs.onMaxorDead(now());
        watchedMobs.remove(e.getId());
    }

    private static void tick() {
        long t = ServerTickTimer.now();
        if (lastTimerTick < 0 || t < lastTimerTick) lastTimerTick = t;
        int n = (int) Math.min(t - lastTimerTick, 40);
        lastTimerTick = t;
        for (int i = 0; i < n; i++) onServerTick();

        if (!inDungeons()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        recordSplitBests();
        if (barriers.size() >= DoorBlocks.DOOR_BLOCKS) door(barriers, true);
        if (cleared.size() >= DoorBlocks.DOOR_BLOCKS) door(cleared, false);
        barriers.clear(); cleared.clear();
        for (int i = 0; i < sectionDoor.length; i++) {
            if (sectionDoor[i] >= SECTION_DOOR_BLOCKS) subs.onSectionDoor(now(), i + 1);
            sectionDoor[i] = 0;
        }

        if (open(SplitTracker.STORM)) {
            WitherBoss s = bossWither(level, "Storm");
            if (s != null) {
                Vec3 b = s.getPositionCodec().getBase();
                subs.onStormPosition(now(), b.x, b.y, b.z);
                storm.onPosition(serverTicks, b.x, b.y, b.z);
            }
        }
        if (open(SplitTracker.NECRON)) {
            WitherBoss nec = bossWither(level, "Necron");
            if (nec != null) {
                necronId = nec.getId();
                subs.onNecronPosition(now(), nec.getPositionCodec().getBase().distanceTo(NECRON_MID));
            }
        }

        if (blood.active()) {
            for (Entity e : level.entitiesForRendering()) {
                if (!(e instanceof ArmorStand) || keysSeen.contains(e.getId())) continue;
                if (e.getCustomName() == null) continue;
                if (KEY.matcher(e.getCustomName().getString()).find()) {
                    keysSeen.add(e.getId());
                    blood.onKeySpawned(now(), distanceTo(e));
                }
            }
        }

        Boolean inCore = (subs.watchingCore() || open(SplitTracker.GOLDOR)) ? everyoneInCore(level) : Boolean.FALSE;
        if (Boolean.TRUE.equals(inCore)) {
            subs.onEveryoneInCore(now());
            moments.onEveryoneInCore(now());
        }
        if (open(SplitTracker.GOLDOR) && moments.waitingForCore()) watchGoldor(level, inCore == null);
        else { goldorAtId = null; goldorAtPos = null; }

        Stamp s4 = subs.s4Start();
        if (s4 != null) core.onS4(s4.tick());
        if (core.active()) watchCore(level);

        if (open(SplitTracker.BLOOD) && moments.waitingForWatcher()) watchWatcher(level);
    }

    private static Player findPlayer(ClientLevel level, String name) {
        for (Player p : level.players()) if (!p.isRemoved() && p.getName().getString().equals(name)) return p;
        return null;
    }

    private static Boolean everyoneInCore(ClientLevel level) {
        List<PlayerTracker.DungeonPlayer> alive = new ArrayList<>();
        for (PlayerTracker.DungeonPlayer dp : PlayerTracker.getPlayers()) if (!AdmWorld.isDead(dp.getName())) alive.add(dp);
        if (alive.isEmpty()) return false;
        boolean unseen = false;
        for (PlayerTracker.DungeonPlayer mate : alive) {
            Player p = findPlayer(level, mate.getName());
            if (p == null) { unseen = true; continue; }
            if (!(p.getX() >= 39 && p.getX() < 71 && p.getY() < 155.5 && p.getZ() >= 54 && p.getZ() < 118)) return false;
        }
        return unseen ? null : Boolean.TRUE;
    }

    private static void watchCore(ClientLevel level) {
        if (core.look(serverTicks)) {
            Player me = Minecraft.getInstance().player;
            List<String> alive = aliveNames();
            String myName = me == null ? null : me.getName().getString();
            if (me != null && alive.contains(myName)) core.observe(myName, inCoreBox(me.getX(), me.getY(), me.getZ()), serverTicks);
            for (PlayerTracker.DungeonPlayer mate : PlayerTracker.getPlayers()) {
                if (!alive.contains(mate.getName()) || mate.getName().equals(myName)) continue;
                Player p = findPlayer(level, mate.getName());
                Vec3 at = p == null ? null : p.getPositionCodec().getBase();
                core.observe(mate.getName(), at == null ? null : inCoreBox(at.x, at.y, at.z), serverTicks);
            }
            for (PlayerTracker.DungeonPlayer mate : PlayerTracker.getPlayers()) if (AdmWorld.isDead(mate.getName())) core.onDeath(mate.getName(), serverTicks);
            if (myName != null && AdmWorld.isDead(myName)) core.onDeath(myName, serverTicks);
            pasteLastIn();
        }
        if (core.openTick() == null || core.goldorMoved() != null) return;
        if (coreGoldor == null) {
            WitherBoss g = bossWither(level, "Goldor");
            if (g != null) { coreGoldor = new int[]{g.getId(), serverTicks}; coreGoldorAt = g.getPositionCodec().getBase(); }
            return;
        }
        Entity e = level.getEntity(coreGoldor[0]);
        if (e == null) { coreGoldor = null; return; }
        Vec3 at = e.getPositionCodec().getBase();
        if (at.equals(coreGoldorAt)) return;
        double dx = at.x - coreGoldorAt.x, dz = at.z - coreGoldorAt.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        int ticks = Math.max(serverTicks - coreGoldor[1], 1);
        if (d < 8 && d / ticks > GOLDOR_GO) core.onGoldorMoved(coreGoldor[1]);
        else { coreGoldor = new int[]{e.getId(), serverTicks}; coreGoldorAt = at; }
    }

    private static void pasteLastIn() {
        TeslaMapsConfig c = cfg();
        Minecraft mc = Minecraft.getInstance();
        if (!(c.admRecore && c.admRecorePasteLast) || core.pasted || mc.hasSingleplayerServer()) return;
        if (mc.player == null) return;
        String me = mc.player.getName().getString();
        List<PlayerTracker.DungeonPlayer> mates = new ArrayList<>();
        for (PlayerTracker.DungeonPlayer dp : PlayerTracker.getPlayers()) if (!dp.getName().equals(me)) mates.add(dp);
        if (mates.size() != 4) return;
        for (PlayerTracker.DungeonPlayer m : mates) if (!m.isAlive()) return;
        List<String> alive = core.counted(aliveNames());
        if (core.lastIn(alive) == null || core.openTick() == null) return;
        core.pasted = true;
        String line = core.pasteLine(alive);
        if (line != null) com.teslamaps.features.ChatQueue.command("pc " + line);
    }

    private static List<String> aliveNames() {
        Player me = Minecraft.getInstance().player;
        String my = me == null ? null : me.getName().getString();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        boolean meListed = false;
        for (PlayerTracker.DungeonPlayer dp : PlayerTracker.getPlayers()) {
            if (dp.getName().equals(my)) meListed = true;
            if (dp.isAlive()) out.add(dp.getName());
        }
        if (my != null && !meListed) out.add(my);
        return new ArrayList<>(out);
    }

    private static boolean inCoreBox(double x, double y, double z) {
        return x >= 39 && x < 71 && y >= 110 && y < 155.5 && z >= 54 && z < 118;
    }

    private static void onNecronTnt(Stamp at) {
        necronTnt.addLast(at);
        while (!necronTnt.isEmpty() && necronTnt.peekFirst().tick() < at.tick() - (DEATH_BURST_TICKS - 1)) necronTnt.removeFirst();
        if (necronTnt.size() < DEATH_BURST_TNT) return;
        Stamp first = necronTnt.peekFirst();
        necronTnt.clear();
        necronDead(first);
    }

    private static void necronDead(Stamp at) {
        if (necronDiedAt != null) return;
        necronDiedAt = at;
        if (floor().startsWith("M")) subs.onNecronDeath(at);
    }

    private static WitherBoss bossWither(ClientLevel level, String name) {
        Entity tag = null;
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof ArmorStand && e.getCustomName() != null && e.getCustomName().getString().contains(name)) { tag = e; break; }
        }
        Entity anchor = tag != null ? tag : Minecraft.getInstance().player;
        if (anchor == null) return null;
        WitherBoss best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof WitherBoss w) || !AdmWorld.isBossWither(w)) continue;
            double d = w.distanceToSqr(anchor);
            if (d < bd) { bd = d; best = w; }
        }
        return best;
    }

    private static void watchGoldor(ClientLevel level, boolean trusted) {
        if (goldorMoved) return;
        if (goldorAtId == null) {
            WitherBoss g = bossWither(level, "Goldor");
            if (g != null) { goldorAtId = new int[]{g.getId()}; goldorAtPos = g.position(); }
            return;
        }
        Entity e = level.getEntity(goldorAtId[0]);
        if (e == null) { goldorAtId = null; return; }
        double d = e.position().distanceTo(goldorAtPos);
        if (d > 8) { goldorAtId = new int[]{e.getId()}; goldorAtPos = e.position(); }
        else if (d > 0.1) {
            if (trusted) {
                moments.onEveryoneInCore(now());
                subs.onEveryoneInCore(now());
            }
            goldorMoved = true;
        }
    }

    private static boolean open(String label) {
        Split s = tracker.split(label);
        return s != null && s.stop() == null;
    }

    private static Set<String> teamNames() {
        Set<String> set = new HashSet<>();
        for (PlayerTracker.DungeonPlayer dp : PlayerTracker.getPlayers()) set.add(dp.getName());
        Player me = Minecraft.getInstance().player;
        if (me != null) set.add(me.getName().getString());
        return set;
    }

    private static void door(List<int[]> blocks, boolean start) {
        for (DoorBlocks.Door d : DoorBlocks.doors(blocks)) {
            BloodRunDetail.MapRoom a = room(d.ax(), d.az()), b = room(d.bx(), d.bz());
            if (start) blood.onDoorStart(now(), a, b);
            else blood.onDoorDown(now(), a, b);
        }
    }

    private static BloodRunDetail.MapRoom room(int x, int z) {
        if (x < 0 || x > 5 || z < 0 || z > 5) return null;
        DungeonRoom r = DungeonManager.getRoomAt(x, z);
        if (r == null || r.getName() == null) return null;
        return new BloodRunDetail.MapRoom(String.valueOf(System.identityHashCode(r)), r.getName(),
                r.getType() == RoomType.FAIRY, r.getType() == RoomType.ENTRANCE);
    }

    private static void watchWatcher(ClientLevel level) {
        Stamp handle = moments.watcherHandle();
        if (handle == null) return;
        int prevId = watcherId;
        Vec3 prevPos = watcherPos;
        Entity e = prevId >= 0 ? level.getEntity(prevId) : null;
        if (e == null) {
            for (Entity x : level.entitiesForRendering()) if (x instanceof Zombie && isWatcherHead(x)) { e = x; break; }
        }
        if (e == null) { watcherId = -1; watcherPos = null; return; }
        watcherId = e.getId();
        watcherPos = e.position();
        if (prevPos == null || prevId != e.getId() || serverTicks - handle.tick() < 45) return;
        if (e.position().distanceTo(prevPos) > 0.001) {
            moments.onWatcherMoved(now());
            subs.onWatcherMoved(now());
        }
    }

    private static boolean isWatcherHead(Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        ItemStack head = le.getItemBySlot(EquipmentSlot.HEAD);
        if (head == null || head.isEmpty()) return false;
        ResolvableProfile rp = head.get(DataComponents.PROFILE);
        if (rp == null) return false;
        Collection<Property> props = rp.partialProfile().properties().get("textures");
        if (props.isEmpty()) return false;
        String decoded;
        try { decoded = new String(Base64.getDecoder().decode(props.iterator().next().value())); } catch (Exception ex) { return false; }
        for (String s : WATCHER_SKINS) if (decoded.contains(s)) return true;
        return false;
    }

    private static double distanceTo(Entity e) {
        Player p = Minecraft.getInstance().player;
        return p == null ? 0.0 : p.distanceTo(e);
    }

    // ------------------------------------------------------------------ HUDs

    private static List<String> bloodLines() {
        if (cfg().admBrHideInBoss && DungeonManager.isInBossArea()) return List.of();
        BloodRunDetail.Level level = switch (cfg().admBrDetail == null ? "" : cfg().admBrDetail) {
            case "Detailed" -> BloodRunDetail.Level.DETAILED;
            case "Debug" -> BloodRunDetail.Level.DEBUG;
            default -> BloodRunDetail.Level.COMPACT;
        };
        return blood.lines(level, now(), cfg().admBrTotalRow);
    }

    private static List<String> stormLines() {
        Split split = tracker.split(SplitTracker.STORM);
        if (split == null) return List.of();
        Stamp now = now();
        String floor = floor();
        Map<String, Long> bests = bests(floor);
        List<Split> steps = subs.forSplit(SplitTracker.STORM);
        List<String> ids = subs.idsForSplit(SplitTracker.STORM);
        List<String[]> graded = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            Split st = steps.get(i);
            Stamp stop = st.stop() != null ? st.stop() : now;
            long ms = stop.realMs() - st.start().realMs();
            long ticks = stop.tick() - st.start().tick();
            String id = i < ids.size() ? ids.get(i) : "";
            long value = SubSplitGrades.value(id, ms, ticks, stop.tick() - split.start().tick());
            String own = st.label().substring(0, Math.min(2, st.label().length())).replace('&', '§');
            String grade = SubSplitGrades.colour(id, value, st.stop() != null, validBest(id, bests.get(id), floor), floor, own);
            boolean real = SubSplitGrades.clock(id) == SubSplitGrades.Clock.REAL;
            graded.add(new String[]{String.valueOf(i), grade + Split.seconds(real ? ms : ticks * 50), Split.seconds(real ? ticks * 50 : ms)});
        }
        if (graded.isEmpty()) return List.of();
        if ("Compact".equals(cfg().admStormDetail)) {
            StringBuilder sb = new StringBuilder("§bStorm: ");
            for (int i = 0; i < graded.size(); i++) { if (i > 0) sb.append(" §8| "); sb.append(graded.get(i)[1]); }
            return List.of(sb.toString());
        }
        Stamp stop = split.stop() != null ? split.stop() : now;
        List<String> out = new ArrayList<>();
        out.add("§bStorm §f" + Split.seconds((stop.tick() - split.start().tick()) * 50L));
        Integer start = storm.start();
        List<StormDetail.Moment> ms = storm.moments();
        int base = start != null ? start : split.start().tick() - 1;
        int firstStart = steps.get(0).start().tick();
        for (StormDetail.Moment m : ms) if (base + m.tick() < firstStart) out.add(moment(m));
        for (int i = 0; i < graded.size(); i++) {
            Split st = steps.get(i);
            String lab = st.label().substring(0, Math.min(2, st.label().length())).replace('&', '§') + st.label().substring(Math.min(2, st.label().length()));
            out.add(lab + " " + graded.get(i)[1] + " §8(§7" + graded.get(i)[2] + "§8)");
            int end = i + 1 < steps.size() ? steps.get(i + 1).start().tick() : Integer.MAX_VALUE;
            for (StormDetail.Moment m : ms) {
                int at = base + m.tick();
                if (at >= st.start().tick() && at < end) out.add(moment(m));
            }
        }
        return out;
    }

    private static String moment(StormDetail.Moment m) { return " §8· " + m.text() + " §7" + StormDetail.secs(m.tick()); }

    private static List<String> goldorLines() {
        Player me = Minecraft.getInstance().player;
        return core.lines(serverTicks, me == null ? null : me.getName().getString(), core.counted(aliveNames()));
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!cfg().admSubSplits || Minecraft.getInstance().options.hideGui) return;
        drawAt(g, bloodHud.x(), bloodHud.y(), bloodHud.scale(), bloodLines());
        drawAt(g, stormHud.x(), stormHud.y(), stormHud.scale(), stormLines());
        drawAt(g, goldorHud.x(), goldorHud.y(), goldorHud.scale(), goldorLines());
    }

    private static void renderWorld(PoseStack pose, Vec3 cam) {
        if (!crushInfo()) return;
        TeslaMapsConfig c = cfg();
        crushView.render(pose, cam, c.admStormHitboxSeconds, c.admStormHitboxMargins, c.admStormPillarOutline,
                TeslaMapsConfig.parseColor(c.admStormInsideColor), TeslaMapsConfig.parseColor(c.admStormOutsideColor));
    }

    private static int[] size(List<String> lines) {
        int[] wh = measure(lines);
        return new int[]{wh[0] + 6, wh[1] + 6};
    }

    private static void drawAt(GuiGraphicsExtractor g, int x, int y, float scale, List<String> lines) {
        if (lines.isEmpty()) return;
        int[] wh = measure(lines);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, wh[0] + 6, wh[1] + 6);
        g.pose().translate(3, 3);
        draw(g, lines);
        g.pose().popMatrix();
    }

    private static int[] measure(List<String> lines) {
        var font = Minecraft.getInstance().font;
        boolean table = false;
        for (String l : lines) if (l.indexOf('\t') >= 0) { table = true; break; }
        if (!table) {
            int w = 0;
            for (String l : lines) w = Math.max(w, font.width(l));
            return new int[]{w, lines.size() * LINE_HEIGHT};
        }
        int[] widths = columnWidths(lines);
        int w = 0;
        for (int c : widths) w += c;
        return new int[]{w, lines.size() * LINE_HEIGHT};
    }

    private static int[] columnWidths(List<String> lines) {
        var font = Minecraft.getInstance().font;
        int cols = 0;
        List<String[]> rows = new ArrayList<>();
        for (String l : lines) { String[] r = l.split("\t", -1); rows.add(r); cols = Math.max(cols, r.length); }
        int[] widths = new int[cols];
        for (String[] r : rows) for (int c = 0; c < r.length; c++) widths[c] = Math.max(widths[c], font.width(r[c]));
        return widths;
    }

    private static void draw(GuiGraphicsExtractor g, List<String> lines) {
        var font = Minecraft.getInstance().font;
        boolean table = false;
        for (String l : lines) if (l.indexOf('\t') >= 0) { table = true; break; }
        if (!table) {
            for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), 0, i * LINE_HEIGHT, 0xFFFFFFFF, true);
            return;
        }
        int[] widths = columnWidths(lines);
        int[] starts = new int[widths.length];
        for (int c = 1; c < widths.length; c++) starts[c] = starts[c - 1] + widths[c - 1];
        Set<Integer> right = Set.of(0, 1, 3);
        for (int i = 0; i < lines.size(); i++) {
            String[] row = lines.get(i).split("\t", -1);
            for (int c = 0; c < row.length; c++) {
                if (row[c].isEmpty()) continue;
                int x = right.contains(c) ? starts[c] + widths[c] - font.width(row[c]) : starts[c];
                g.text(font, row[c], x, i * LINE_HEIGHT, 0xFFFFFFFF, true);
            }
        }
    }

    // ------------------------------------------------------------------ pace, lag, bests

    public static SplitPace.Clocks pace() {
        if (!"F7".equals(floor())) return null;
        List<Split> splits = tracker.splits();
        if (splits.isEmpty()) return null;
        return SplitPace.pace(splits, label -> {
            List<Split> s = subs.forSplit(label);
            List<String> ids = subs.idsForSplit(label);
            List<SplitPace.Sub> out = new ArrayList<>();
            for (int i = 0; i < Math.min(s.size(), ids.size()); i++) out.add(new SplitPace.Sub(ids.get(i), s.get(i)));
            return out;
        }, now(), label -> AdmSplitsHud.paceTarget(label, false));
    }

    private static boolean keepsTimes() { return realRun; }

    private static void recordSplitBests() {
        String floor = floor();
        if (!"F7".equals(floor) && !"M7".equals(floor)) return;
        if (!keepsTimes()) return;
        List<Split> splits = tracker.splits();
        for (int i = 0; i < splits.size(); i++) {
            Split split = splits.get(i);
            Stamp stop = split.stop();
            if (stop == null) continue;
            Split next = i + 1 < splits.size() ? splits.get(i + 1) : null;
            if ("M7".equals(floor) && split.label().equals(SplitTracker.NECRON) && (next == null || !next.label().equals(SplitTracker.DRAGONS))) continue;
            if (!bestsChecked.add(split.label())) continue;
            int order = SplitPace.ALL.indexOf(split.label());
            boolean whole = next != null ? SplitPace.ALL.indexOf(next.label()) == order + 1
                    : split.label().equals(SplitTracker.DRAGONS) || ("F7".equals(floor) && split.label().equals(SplitTracker.NECRON));
            String id = SplitPace.SPLIT_IDS.get(split.label());
            if (id == null || !whole) continue;
            long ms = stop.realMs() - split.start().realMs();
            long ticks = stop.tick() - split.start().tick();
            recordBest(floor, id, SubSplitGrades.value(id, ms, ticks), true);
            recordRecent(floor, id, new SplitPace.Clocks(ms, ticks));
        }
    }

    public static Long lag() {
        synchronized (lagSamples) {
            if (!lagShown || lagSamples.isEmpty()) return null;
            long min = Long.MAX_VALUE;
            for (long l : lagSamples) min = Math.min(min, l);
            return min;
        }
    }

    private static void sampleLag() {
        List<Split> splits = tracker.splits();
        if (splits.isEmpty()) return;
        long ms = SplitPace.lag(splits, now());
        synchronized (lagSamples) {
            lagSamples.addLast(ms);
            while (lagSamples.size() > 20) lagSamples.removeFirst();
            long min = Long.MAX_VALUE;
            for (long l : lagSamples) min = Math.min(min, l);
            if (min >= 50) lagShown = true;
        }
    }

    public static Long bestOf(String floor, String id) { return validBest(id, bests(floor).get(id), floor); }

    public static void recordBest(String floor, String id, long value, boolean finished) {
        if (!finished || !keepsTimes() || !SubSplitGrades.canBeBest(id, value, floor)) return;
        Map<String, Long> bests = bests(floor);
        Long cur = validBest(id, bests.get(id), floor);
        if (value < (cur == null ? Long.MAX_VALUE : cur)) {
            bests.put(id, value);
            saveBests(floor, bests);
        }
    }

    private static Long validBest(String id, Long best, String floor) {
        return best != null && SubSplitGrades.canBeBest(id, best, floor) ? best : null;
    }

    private static String recentText(String floor) {
        if ("F7".equals(floor)) return cfg().admRecentF7 == null ? "" : cfg().admRecentF7;
        if ("M7".equals(floor)) return cfg().admRecentM7 == null ? "" : cfg().admRecentM7;
        return "";
    }

    private static final Map<String, Object[]> recentParsed = new HashMap<>();

    private static Map<String, List<SplitPace.Clocks>> recentView(String floor) {
        String text = recentText(floor);
        Object[] cached = recentParsed.get(floor);
        if (cached != null && cached[0].equals(text)) {
            @SuppressWarnings("unchecked") Map<String, List<SplitPace.Clocks>> m = (Map<String, List<SplitPace.Clocks>>) cached[1];
            return m;
        }
        Map<String, List<SplitPace.Clocks>> m = recent(floor);
        recentParsed.put(floor, new Object[]{text, m});
        typicalCache.clear();
        return m;
    }

    private static Map<String, List<SplitPace.Clocks>> recent(String floor) {
        Map<String, List<SplitPace.Clocks>> out = new HashMap<>();
        for (String e : recentText(floor).split(",")) {
            String[] kv = e.split("=");
            if (kv.length != 2) continue;
            List<SplitPace.Clocks> list = new ArrayList<>();
            for (String c : kv[1].split("\\|")) {
                String[] p = c.split(":");
                if (p.length != 2) continue;
                try { list.add(new SplitPace.Clocks(Long.parseLong(p[0]), Long.parseLong(p[1]))); } catch (NumberFormatException ignored) { }
            }
            out.put(kv[0].trim(), list);
        }
        return out;
    }

    private static void recordRecent(String floor, String id, SplitPace.Clocks c) {
        Map<String, List<SplitPace.Clocks>> all = recent(floor);
        List<SplitPace.Clocks> list = all.computeIfAbsent(id, k -> new ArrayList<>());
        list.add(c);
        while (list.size() > RECENT) list.remove(0);
        List<String> keys = new ArrayList<>(all.keySet());
        keys.sort(null);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if (sb.length() > 0) sb.append(',');
            sb.append(k).append('=');
            List<SplitPace.Clocks> v = all.get(k);
            for (int i = 0; i < v.size(); i++) {
                if (i > 0) sb.append('|');
                sb.append(v.get(i).ms()).append(':').append(v.get(i).ticks());
            }
        }
        if ("F7".equals(floor)) cfg().admRecentF7 = sb.toString();
        else if ("M7".equals(floor)) cfg().admRecentM7 = sb.toString();
        else return;
        TeslaMapsConfig.save();
    }

    private static final Map<String, SplitPace.Clocks> typicalCache = new HashMap<>();

    public static SplitPace.Clocks typical(String floor, String label) {
        String id = SplitPace.SPLIT_IDS.get(label);
        if (id == null) return null;
        Map<String, List<SplitPace.Clocks>> view = recentView(floor);
        String key = floor + ":" + id;
        if (typicalCache.containsKey(key)) return typicalCache.get(key);
        List<SplitPace.Clocks> list = view.get(id);
        SplitPace.Clocks out = null;
        if (list != null && list.size() >= RECENT_MIN) {
            long[] ms = new long[list.size()], tk = new long[list.size()];
            for (int i = 0; i < list.size(); i++) { ms[i] = list.get(i).ms(); tk[i] = list.get(i).ticks(); }
            out = new SplitPace.Clocks(median(ms), median(tk));
        }
        typicalCache.put(key, out);
        return out;
    }

    private static long median(long[] v) {
        long[] s = v.clone();
        java.util.Arrays.sort(s);
        return s.length % 2 == 1 ? s[s.length / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2;
    }

    private static Map<String, Long> bests(String floor) {
        String text = "F7".equals(floor) ? cfg().admBestsF7 : "M7".equals(floor) ? cfg().admBestsM7 : "";
        return SubSplitGrades.parseBests(text == null ? "" : text);
    }

    private static void saveBests(String floor, Map<String, Long> bests) {
        String text = SubSplitGrades.formatBests(bests);
        if ("F7".equals(floor)) cfg().admBestsF7 = text;
        else if ("M7".equals(floor)) cfg().admBestsM7 = text;
        else return;
        TeslaMapsConfig.save();
    }
}
