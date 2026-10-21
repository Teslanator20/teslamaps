// Ported from ADM's p3sim/clear/secrets/SecretsModule.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimItems;
import com.teslamaps.adm.sim.clear.hud.ActionBarModel;
import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.pack.rooms.SkullTexture;
import com.teslamaps.adm.sim.pack.runstate.Dist;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsModule;
import com.teslamaps.adm.sim.run.BlessingGranted;
import com.teslamaps.adm.sim.run.BonusKind;
import com.teslamaps.adm.sim.run.EndReason;
import com.teslamaps.adm.sim.run.MobKilled;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.RoomCleared;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.RoomType;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunBlocks;
import com.teslamaps.adm.sim.run.RunEvent;
import com.teslamaps.adm.sim.run.SecretKind;
import com.teslamaps.adm.sim.run.api.Audience;
import com.teslamaps.adm.sim.run.api.MobRecord;
import com.teslamaps.adm.sim.run.api.SecretService;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Secrets of every type from the rooms pack, crypts, Superboom walls, the Dungeonbreaker's dungeon rules, blessings and loot.
 * Interface methods are on {@link #INSTANCE}; the rest is static. Server thread only.
 */
public final class SecretsModule implements AdsModule, SecretService {
    public static final SecretsModule INSTANCE = new SecretsModule();

    private SecretsModule() {}

    @Override public String id() { return "secrets"; }

    @Override public String owner() { return "secrets"; }

    private static final class ChestRec {
        final SecretSite site;
        final Drop drop;
        final UUID by;
        final int start;
        final boolean trapped;
        final HashSet<Integer> done = new HashSet<>();
        boolean finished;

        ChestRec(SecretSite site, Drop drop, UUID by, int start, boolean trapped) {
            this.site = site;
            this.drop = drop;
            this.by = by;
            this.start = start;
            this.trapped = trapped;
            this.finished = trapped;
        }

        ChestRec(SecretSite site, Drop drop, UUID by, int start) { this(site, drop, by, start, false); }

        boolean reward() { return !(drop instanceof Drop.Nothing); }
    }

    private static final class SkullRec {
        final SecretSite site;
        final UUID by;
        final int start;
        final HashSet<Integer> done = new HashSet<>();

        SkullRec(SecretSite site, UUID by, int start) {
            this.site = site;
            this.by = by;
            this.start = start;
        }
    }

    private static final class WallTicks {
        final Vec3 at;
        final int start;
        final HashSet<Integer> done = new HashSet<>();

        WallTicks(Vec3 at, int start) {
            this.at = at;
            this.start = start;
        }
    }

    private static final class RunData {
        final Run run;
        final SecretPlan plan;
        final CellGroups groups;
        final SecretBook book;
        final KRandom lootRandom;
        final HashMap<Long, LeverInfo> levers = new HashMap<>();
        final HashSet<String> placed = new HashSet<>();
        final HashMap<String, UUID> entities = new HashMap<>();
        final HashSet<String> unlocked = new HashSet<>();
        final HashMap<Long, UUID> leverUsed = new HashMap<>();
        final ArrayList<Pair<Integer, List<Pos>>> walls = new ArrayList<>();
        final LinkedHashMap<Long, Pair<Pos, BlockState>> fixes = new LinkedHashMap<>();
        final BonusBatTracker bat = new BonusBatTracker(0);
        Integer mimicAt = null;
        Integer princeAt = null;
        boolean princeLoud = false;
        int lastSpawnPass = -SPAWN_PERIOD;
        final HashMap<SecretKind, SkullTexture> skullTextures = new HashMap<>();
        final ArrayList<Triple<Integer, CellGroup, UUID>> botBlasts = new ArrayList<>();
        final KRandom miscRandom;
        final KRandom sounds;
        final KRandom dropRandom;
        String trapped = null;
        final HashMap<String, ChestRec> chests = new HashMap<>();
        final HashMap<String, SkullRec> skulls = new HashMap<>();
        final ArrayList<WallTicks> wallTicks = new ArrayList<>();
        final HashSet<String> lidsOwed = new HashSet<>();
        final HashSet<String> gatesTaken = new HashSet<>();
        final HashSet<Integer> keyRooms = new HashSet<>();
        final HashMap<Integer, UUID> lastKiller = new HashMap<>();
        final TimedQueue<Runnable> later = new TimedQueue<>();
        final HashMap<UUID, Integer> lockedSaid = new HashMap<>();
        final ArrayList<UUID> mimicBodies = new ArrayList<>();

        RunData(Run run, SecretPlan plan, CellGroups groups, SecretBook book, KRandom lootRandom) {
            this.run = run;
            this.plan = plan;
            this.groups = groups;
            this.book = book;
            this.lootRandom = lootRandom;
            this.miscRandom = run.subRandom("secrets-blessings");
            this.sounds = run.subRandom("secrets-sounds");
            this.dropRandom = run.subRandom("secrets-drops");
        }
    }

    private static RunData data = null;

    private static final UUID NIL = new UUID(0, 0);

    private static RunData d(Run run) {
        RunData rd = data;
        return rd != null && rd.run == run ? rd : null;
    }

    // ---- lifecycle

    @Override public void registerSettings() { SecretsSettings.register(); }

    @Override
    public void onRunStart(Run run) {
        data = null;
        KRandom random = run.subRandom("secrets");
        SecretPlan plan = SecretPlan.build(run.plan, random);
        CellGroups groups = CellGroups.build(run.plan);
        SecretBook book = new SecretBook(plan, run.state, run.bus, () -> run.t(), () -> SecretsSettings.autoCredit());
        RunData rd = new RunData(run, plan, groups, book, run.subRandom("secrets-loot"));
        rd.bat.index = run.subRandom("secrets-bat").nextInt(100) < SecretsSettings.bonusBatPercent() ? BonusBat.draw(run.subRandom("secrets-bat-index")) : 0;
        rollBonusMobs(rd);
        List<SkullTexture> textures = run.packs.rooms != null ? run.packs.rooms.textures : List.of();
        rd.skullTextures.put(SecretKind.ESSENCE, SecretWorld.essenceTexture(SecretWorld.textureFor(textures, "essence"), SecretWorld.textureFor(textures, SecretWorld.ESSENCE_SKIN_ROLE)));
        rd.skullTextures.put(SecretKind.REDSTONE_KEY, SecretWorld.textureFor(textures, "redstoneKey"));
        Pair<Map<Long, LeverInfo>, Set<String>> lv = Levers.build(run.plan, plan);
        rd.levers.putAll(lv.first);
        rd.unlocked.addAll(lv.second);
        data = rd;
        pickTrappedChest(rd);
        placeChests(rd);
        int placeable = 0;
        for (RoomSecrets r : plan.rooms) placeable += r.placeable();
        Ec.LOG.info("[ads] secrets: {} placeable of {} printed, {} crypt/wall groups, trapped chest {}", placeable, run.plan.secretsTotal(), groups.groups.size(),
                rd.trapped != null ? rd.trapped : "none");
    }

    private static Double primDouble(JsonObject b, String key) {
        JsonElement e = b.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsDouble() : null;
    }

    private static Integer roll(JsonObject b, KRandom random, String frac, String tick) {
        Double f = primDouble(b, frac);
        if (f == null) return null;
        Dist dist = Dist.parse(b.get(tick));
        if (dist == null) return null;
        double u = random.nextDouble();
        int at = (int) dist.sample(random.nextDouble());
        return u < f ? at : null;
    }

    private static void rollBonusMobs(RunData rd) {
        if (rd.run.packs.runstate == null) return;
        JsonObject b = rd.run.packs.runstate.section("bonus");
        if (b == null) return;
        KRandom random = rd.run.subRandom("secrets-bonus-mobs");
        rd.mimicAt = roll(b, random, "mimicKilledFrac", "mimicTick");
        rd.princeAt = roll(b, random, "princeKilledFrac", "princeTick");
        Double chatD = primDouble(b, "princeChatPerClear");
        double chat = chatD != null ? chatD : 0.0;
        Double princeD = primDouble(b, "princeKilledFrac");
        double prince = princeD != null ? princeD : 1.0;
        double loudShare = prince > 0.0 ? chat / prince : 0.0;
        rd.princeLoud = random.nextDouble() < loudShare;
    }

    private static void pickTrappedChest(RunData rd) {
        List<SecretSite> candidates = new ArrayList<>();
        for (SecretSite it : rd.plan.all()) {
            if (it.kind != SecretKind.CHEST || it.baked || it.hidden) continue;
            PlacedRoom r = it.room >= 0 && it.room < rd.run.plan.rooms.size() ? rd.run.plan.rooms.get(it.room) : null;
            RoomType t = r != null ? r.type : null;
            if (t == RoomType.NORMAL || t == RoomType.RARE || t == RoomType.YELLOW) candidates.add(it);
        }
        SecretSite picked = TrappedChest.pick(candidates, TrappedChest.DEFAULT_CHANCE, rd.run.subRandom("secrets-trapped"));
        rd.trapped = picked != null ? picked.key() : null;
        if (rd.trapped != null) rd.mimicAt = null;
    }

    private static void placeChests(RunData rd) {
        BlockState redstone = Blocks.REDSTONE_BLOCK.defaultBlockState();
        for (SecretSite s : rd.plan.all()) if (s.socket != null) setOrFix(rd, s.x, s.y, s.z, redstone);
        for (SecretSite s : rd.plan.all()) {
            if ((s.kind != SecretKind.CHEST && s.kind != SecretKind.LOCKED_CHEST) || s.hidden || s.baked) continue;
            setOrFix(rd, s.x, s.y, s.z, s.key().equals(rd.trapped) ? SecretWorld.trappedChestState(s) : SecretWorld.chestState(s));
        }
    }

    /** While a replay runs the team is the recording's. */
    public static volatile boolean teamFromRecording = false;

    /** A replay's drops are the recording's. */
    public static volatile boolean dropsFromRecording = false;

    public static void recordedBlessing(Run run, UUID finder, Blessing b, int level, boolean timed, boolean obtained) {
        RunData rd = d(run);
        if (rd == null) return;
        int t = run.t();
        Who f = who(rd, finder);
        if (obtained && finder != null) Services.channels().chat(Blessings.obtained(f.label(), b));
        for (ServerPlayer p : humans(rd)) {
            List<String> lines = new ArrayList<>();
            if (finder == null) { lines.add(Blessings.pickedUp(b)); lines.add(Blessings.announceOwn(b, level, t)); }
            else lines.add(p.getUUID().equals(finder) ? Blessings.announceYouFound(b, level, timed ? t : null) : Blessings.announceOther(f.name, b, level, f.colour, timed ? t : null));
            lines.addAll(Blessings.grantLines(b, level, SecretsSettings.blessingBoost()));
            for (String l : lines) Services.channels().chat(l, new Audience.Only(p.getUUID()));
            run.state.secrets.blessings.merge(b.name(), level, Integer::sum);
            run.bus.post(new BlessingGranted(t, b.name(), level, p.getUUID()));
        }
    }

    public static void recordedItem(Run run, UUID finder, LootItem item, int count) {
        RunData rd = d(run);
        if (rd == null) return;
        giveItem(rd, who(rd, finder), item, count);
    }

    public static void recordedEssence(Run run, UUID finder, String type, int amount) {
        RunData rd = d(run);
        if (rd == null) return;
        Who f = who(rd, finder);
        Drop.Essence drop = new Drop.Essence(type, amount);
        tellEach(rd, f.uuid, SecretLines.chestEssenceSelf(drop), SecretLines.chestEssenceOther(f.colour, f.name, drop));
        Services.channels().actionBarSegment("essence", "§d+" + drop.amount + " " + drop.type + " Essence", ActionBarModel.REPLACES_DEFENSE, ESSENCE_SEGMENT_TICKS, Audience.Everyone.INSTANCE);
        for (ServerPlayer h : humans(rd)) orbPing(h);
    }

    public static List<Pos> standingCrypts(Run run, int room) {
        RunData rd = d(run);
        if (rd == null) return List.of();
        List<Pos> out = new ArrayList<>();
        for (CellGroup g : rd.groups.ofRoom(room, GroupKind.CRYPT)) {
            if (rd.groups.isDestroyed(g)) continue;
            if (!g.cells.isEmpty()) out.add(g.cells.get(0));
        }
        return out;
    }

    private static void tickBonusMobs(RunData rd) {
        if (teamFromRecording) return;
        var led = rd.run.state.secrets;
        int t = rd.run.t();
        if (rd.mimicAt != null && t >= rd.mimicAt && !led.mimicKilled) bonus(rd, BonusKind.MIMIC, null);
        if (rd.princeAt != null && t >= rd.princeAt && !led.princeKilled) bonus(rd, BonusKind.PRINCE, null, rd.princeLoud);
    }

    @Override
    public void onRunEnd(Run run, EndReason reason) {
        FloorDrops.clear(); WallDebris.clear();
        RunData rd = d(run);
        if (rd != null) discardEntities(rd);
    }

    // ---- ticking

    @Override
    public void onTick(Run run) {
        RunData rd = d(run);
        if (rd == null) return;
        if (!run.stage.inDungeon()) return;
        int now = run.serverTick;
        if (now % 20 == 0) { applyFixes(rd); sendOwedLids(rd); }
        if (now - rd.lastSpawnPass >= SPAWN_PERIOD) { rd.lastSpawnPass = now; spawnPass(rd); }
        tickBonusMobs(rd);
        FloorDrops.tick(run); WallDebris.tick();
        tickItems(rd);
        tickBats(rd);
        tickWalls(rd, now);
        tickBotBlasts(rd, now);
        tickChests(rd, now);
        tickSkulls(rd, now);
        tickWallTicks(rd, now);
        for (Runnable job : rd.later.takeDue(now)) job.run();
    }

    public static final int SPAWN_PERIOD = 20;

    private static List<ServerPlayer> humans(RunData rd) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : Sim.players()) {
            PlayerRecord r = rd.run.state.players.get(p.getUUID());
            if (r != null && !r.isBot && !r.ghost) out.add(p);
        }
        return out;
    }

    private static boolean within(List<ServerPlayer> near, int x, int z, double range) {
        double r2 = range * range;
        for (ServerPlayer p : near) {
            double dx = x + 0.5 - p.getX(), dz = z + 0.5 - p.getZ();
            if (dx * dx + dz * dz <= r2) return true;
        }
        return false;
    }

    private static void spawnPass(RunData rd) {
        List<ServerPlayer> near = humans(rd);
        if (near.isEmpty()) return;
        for (SecretSite s : rd.plan.all()) {
            double range;
            switch (s.kind) {
                case ITEM -> range = SecretsSettings.itemRange();
                case BAT -> range = SecretsSettings.batRange();
                case ESSENCE -> range = SecretsSettings.skullRange();
                default -> { continue; }
            }
            if (within(near, s.x, s.z, range)) ensure(rd, s);
        }
        for (KeyGate g : rd.plan.gates()) if (!rd.gatesTaken.contains(g.key) && within(near, g.x, g.z, SecretsSettings.skullRange())) ensureGate(rd, g);
    }

    private static void ensure(RunData rd, SecretSite s) {
        if (!RunBlocks.isLoaded(s.x, s.z)) return;
        boolean found = rd.book.isFound(s);
        switch (s.kind) {
            case ESSENCE -> { if (!found && rd.placed.add(s.key())) SecretWorld.ensureSkull(s, rd.skullTextures.get(s.kind)); }
            case ITEM -> {
                if (!found && !alive(rd, s)) {
                    LootItem item = LootTables.itemSecret(new KRandom(rd.run.config.seed ^ (long) s.key().hashCode()));
                    UUID id = SecretWorld.spawnItem(s, item);
                    if (id != null) rd.entities.put(s.key(), id);
                }
            }
            case BAT -> {
                if (!found && !alive(rd, s)) {
                    UUID id = SecretWorld.spawnBat(s);
                    if (id != null) rd.entities.put(s.key(), id);
                }
            }
            default -> {}
        }
    }

    private static void ensureGate(RunData rd, KeyGate g) {
        if (!RunBlocks.isLoaded(g.x, g.z)) return;
        if (rd.placed.add(g.key)) SecretWorld.ensureSkull(g.x, g.y, g.z, g.quarterTurns, rd.skullTextures.get(SecretKind.REDSTONE_KEY));
    }

    private static boolean alive(RunData rd, SecretSite s) {
        UUID id = rd.entities.get(s.key());
        if (id == null) return false;
        Entity e = SecretWorld.entity(id);
        if (e == null || e.isRemoved() || !e.isAlive()) { rd.entities.remove(s.key()); return false; }
        return true;
    }

    private static List<Pair<String, UUID>> entitiesCopy(RunData rd) {
        List<Pair<String, UUID>> out = new ArrayList<>(rd.entities.size());
        for (Map.Entry<String, UUID> e : rd.entities.entrySet()) out.add(Pair.of(e.getKey(), e.getValue()));
        return out;
    }

    private static void tickItems(RunData rd) {
        if (rd.entities.isEmpty()) return;
        double pad = SecretsSettings.pickupRange();
        List<ServerPlayer> players = humans(rd);
        for (Pair<String, UUID> en : entitiesCopy(rd)) {
            String key = en.first;
            SecretSite s = rd.plan.site(key);
            if (s == null) continue;
            if (s.kind != SecretKind.ITEM) continue;
            Entity raw = SecretWorld.entity(en.second);
            ItemEntity e = raw instanceof ItemEntity ie ? ie : null;
            if (e == null || e.isRemoved()) { rd.entities.remove(key); continue; }
            // The pickup box: the player's, widened sideways and half a block up and down.
            ServerPlayer p = null;
            for (ServerPlayer it : players) if (it.getBoundingBox().inflate(pad, 0.5, pad).intersects(e.getBoundingBox())) { p = it; break; }
            if (p == null) continue;
            Vec3 at = e.position();
            if (SecretWorld.pickUp(e, p)) {
                rd.entities.remove(key);
                // Hypixel adds a server sound to the client's own pickup sound.
                soundTo(p, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, at.x, at.y, at.z, 0.2f, SecretSounds.pickup(rd.sounds));
                claim(rd, s.room, SecretKind.ITEM, p.getUUID(), s.x, s.y, s.z);
            }
        }
    }

    private static void tickBats(RunData rd) {
        if (rd.entities.isEmpty()) return;
        for (Pair<String, UUID> en : entitiesCopy(rd)) {
            String key = en.first;
            SecretSite s = rd.plan.site(key);
            if (s == null) continue;
            if (s.kind != SecretKind.BAT) continue;
            Entity raw = SecretWorld.entity(en.second);
            Bat e = raw instanceof Bat b ? b : null;
            if (e == null || e.isRemoved()) { rd.entities.remove(key); continue; }
            // A secret bat has 100 health and dies to any hit.
            if (e.isAlive() && e.getHealth() >= SecretWorld.BAT_HEALTH) continue;
            batDied(rd, s, e, killer(rd, e));
        }
    }

    private static UUID killer(RunData rd, Entity e) {
        if (e instanceof LivingEntity le && le.getLastHurtByMob() instanceof ServerPlayer sp) return sp.getUUID();
        ServerPlayer best = null;
        double bestD = 0;
        for (ServerPlayer p : humans(rd)) {
            double dd = p.distanceToSqr(e);
            if (best == null || dd < bestD) { best = p; bestD = dd; }
        }
        return best != null ? best.getUUID() : null;
    }

    private static void batDied(RunData rd, SecretSite s, Bat e, UUID by) {
        rd.entities.remove(s.key());
        killBatBody(rd, e);
        claim(rd, s.room, SecretKind.BAT, by, s.x, s.y, s.z);
    }

    private static void killBatBody(RunData rd, Bat e) {
        Sim.sound(SoundEvents.BAT_DEATH, BAT_VOLUME, SecretSounds.batDeath(rd.sounds), e.position(), SoundSource.NEUTRAL);
        SecretWorld.killBat(e);
    }

    /** Odin's bat secret detection listens for the bat's death sound at exactly this volume. */
    private static final float BAT_VOLUME = 0.1f;

    private static void debris(int x, int y, int z) {
        BlockState st = RunBlocks.get(x, y, z);
        if (st != null) WallDebris.spawn(x, y, z, st);
    }

    private static void tickWalls(RunData rd, int now) {
        if (rd.walls.isEmpty()) return;
        Iterator<Pair<Integer, List<Pos>>> it = rd.walls.iterator();
        while (it.hasNext()) {
            Pair<Integer, List<Pos>> w = it.next();
            if (now < w.first) continue;
            it.remove();
            for (Pos c : w.second) { debris(c.x, c.y, c.z); setOrFix(rd, c.x, c.y, c.z, Blocks.AIR.defaultBlockState()); }
        }
    }

    // ---- sounds and lines

    private static void soundTo(ServerPlayer p, SoundEvent sound, SoundSource source, double x, double y, double z, float vol, float pitch) {
        soundTo(p, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), source, x, y, z, vol, pitch);
    }

    private static void soundTo(ServerPlayer p, Holder<SoundEvent> sound, SoundSource source, double x, double y, double z, float vol, float pitch) {
        p.connection.send(new ClientboundSoundPacket(sound, source, x, y, z, vol, pitch, p.level().getRandom().nextLong()));
    }

    private static void orbPing(ServerPlayer p) { orbPing(p, 1f, SecretSounds.ORB_PITCH); }

    private static void orbPing(ServerPlayer p, float vol) { orbPing(p, vol, SecretSounds.ORB_PITCH); }

    private static void orbPing(ServerPlayer p, float vol, float pitch) {
        soundTo(p, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, p.getX(), p.getY(), p.getZ(), vol, pitch);
    }

    private static final String RANK_COLOUR = "§b";
    private static final String RANK_BRACKET = "§b[MVP§r§c+§r§b]";

    private static final class Who {
        final UUID uuid;
        final String name;
        final String colour;
        final String bracket;
        final ServerPlayer player;
        final boolean bot;

        Who(UUID uuid, String name, String colour, String bracket, ServerPlayer player, boolean bot) {
            this.uuid = uuid;
            this.name = name;
            this.colour = colour;
            this.bracket = bracket;
            this.player = player;
            this.bot = bot;
        }

        String label() { return bracket + " " + name; }
    }

    private static Who who(RunData rd, UUID id) {
        PlayerRecord rec = id != null ? rd.run.state.players.get(id) : null;
        ServerPlayer player = null;
        if (id != null) for (ServerPlayer p : Sim.players()) if (p.getUUID().equals(id)) { player = p; break; }
        String name = rec != null && rec.name != null ? rec.name : (player != null && player.getGameProfile().name() != null ? player.getGameProfile().name() : "Someone");
        Object nc = rec != null ? rec.extras.get("nameColour") : null;
        String colour = nc instanceof String s ? s : RANK_COLOUR;
        Object rb = rec != null ? rec.extras.get("rankBracket") : null;
        String bracket = rb instanceof String s ? s : RANK_BRACKET;
        return new Who(id, name, colour, bracket, rec != null && !rec.isBot ? player : null, rec != null && rec.isBot);
    }

    private static void tellEach(RunData rd, UUID finder, String self, String other) {
        for (ServerPlayer h : humans(rd)) Services.channels().chat(h.getUUID().equals(finder) ? self : other, new Audience.Only(h.getUUID()));
    }

    // ---- counting

    private static Claim claim(RunData rd, int room, SecretKind kind, UUID by, int x, int y, int z) {
        Claim c = rd.book.claim(room, kind, by, x, y, z);
        if (c instanceof Claim.Credited cr) {
            if (cr.site != null) afterFound(rd, cr.site, by);
            if ((cr.site != null ? cr.site.kind : kind) == SecretKind.BAT) {
                batCounted(rd, by);
                if (rd.miscRandom.nextDouble() < BAT_ANNOUNCE) botSays(rd, by, "Bat Killed!");
            }
            if (cr.autoCredited > 0 && rd.book.autoCreditedRooms().contains(room)) {
                Ec.LOG.info("[ads] secrets: room {} counted {} secret(s) the room data does not place", room, cr.autoCredited);
            }
        }
        return c;
    }

    private static void afterFound(RunData rd, SecretSite s, UUID by) {
        switch (s.kind) {
            case ESSENCE -> startSkull(rd, s, by);
            case REDSTONE_KEY -> { if (s.socket != null) openSocket(rd, s, by); else removeLater(rd, s.x, s.y, s.z); }
            case ITEM -> {
                UUID id = rd.entities.remove(s.key());
                if (id != null && SecretWorld.entity(id) instanceof ItemEntity e) {
                    Entity collector = by != null ? SecretWorld.entity(by) : null;
                    SecretWorld.takeBy(e, collector);
                }
            }
            case BAT -> {
                UUID id = rd.entities.remove(s.key());
                if (id != null && SecretWorld.entity(id) instanceof Bat b && b.isAlive()) killBatBody(rd, b);
            }
            default -> {}
        }
    }

    private static void removeLater(RunData rd, int x, int y, int z) {
        rd.later.add(rd.run.serverTick + 1, () -> setOrFix(rd, x, y, z, Blocks.AIR.defaultBlockState()));
    }

    private static void setOrFix(RunData rd, int x, int y, int z, BlockState state) {
        if (RunBlocks.isLoaded(x, z)) { RunBlocks.set(x, y, z, state); rd.fixes.remove(CellGroups.key(x, y, z)); }
        else rd.fixes.put(CellGroups.key(x, y, z), Pair.of(new Pos(x, y, z), state));
    }

    private static void applyFixes(RunData rd) {
        if (rd.fixes.isEmpty()) return;
        Iterator<Pair<Pos, BlockState>> it = rd.fixes.values().iterator();
        while (it.hasNext()) {
            Pair<Pos, BlockState> f = it.next();
            Pos p = f.first;
            if (!RunBlocks.isLoaded(p.x, p.z)) continue;
            RunBlocks.set(p.x, p.y, p.z, f.second);
            it.remove();
        }
    }

    private static void discardEntities(RunData rd) {
        for (UUID id : rd.entities.values()) { Entity e = SecretWorld.entity(id); if (e != null) e.discard(); }
        rd.entities.clear();
        for (UUID id : rd.mimicBodies) { Entity e = SecretWorld.entity(id); if (e != null) e.discard(); }
        rd.mimicBodies.clear();
    }

    // ---- SecretService

    @Override
    public boolean found(Run run, int room, SecretKind kind, UUID by, int x, int y, int z) {
        RunData rd = d(run);
        if (rd == null) return false;
        Claim c = claim(rd, room, kind, by, x, y, z);
        if (!(c instanceof Claim.Credited cr)) return false;
        // A teammate's chest: it opens by itself the way a click would.
        SecretSite s = cr.site;
        if (s != null && (s.kind == SecretKind.CHEST || s.kind == SecretKind.LOCKED_CHEST) && who(rd, by).bot) startChest(rd, s, by);
        return true;
    }

    @Override
    public void cryptDestroyed(Run run, int room, UUID by, int x, int y, int z) {
        RunData rd = d(run);
        if (rd == null) return;
        CellGroup g = rd.groups.standingAt(x, y, z);
        if (g != null) destroyGroup(rd, g, by); else rd.book.crypt(room, by);
    }

    @Override
    public void bonus(Run run, BonusKind kind, UUID by) {
        RunData rd = d(run);
        if (rd == null) return;
        bonus(rd, kind, by);
    }

    // ---- teammates' mods in party chat
    public static final double BAT_ANNOUNCE = 0.55;
    public static final double MIMIC_ANNOUNCE = 0.3;
    public static final double PRINCE_ANNOUNCE = 0.6;

    private static void botSays(RunData rd, UUID id, String text) {
        Who w = who(rd, id);
        // A replay's teammates said what the recording says they did.
        if (id == null || !w.bot || teamFromRecording) return;
        String line = "§9Party §8> " + w.bracket + " " + w.name + "§f: " + text;
        rd.later.add(rd.run.serverTick + 1 + rd.miscRandom.nextInt(15), () -> Services.channels().chat(line));
    }

    private static List<UUID> bots(RunData rd) {
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, PlayerRecord> e : rd.run.state.players.entrySet()) if (e.getValue().isBot && !e.getValue().ghost) out.add(e.getKey());
        return out;
    }

    private static void bonus(RunData rd, BonusKind kind, UUID by) { bonus(rd, kind, by, true); }

    private static void bonus(RunData rd, BonusKind kind, UUID by, boolean loud) {
        if (!rd.book.bonus(kind, by)) return;
        switch (kind) {
            case MIMIC -> { for (UUID b : bots(rd)) if (rd.miscRandom.nextDouble() < MIMIC_ANNOUNCE) botSays(rd, b, "Mimic Killed!"); }
            case PRINCE -> {
                UUID killer = by;
                if (killer == null) {
                    List<UUID> bs = bots(rd);
                    if (!bs.isEmpty()) killer = bs.get(rd.miscRandom.nextInt(bs.size()));
                }
                if (rd.miscRandom.nextDouble() < PRINCE_ANNOUNCE) botSays(rd, killer, "Prince Killed!");
            }
            default -> {}
        }
        switch (kind) {
            case PRINCE -> {
                if (loud) {
                    // Only the killer gets the line. A timed roll has no killer: it shows as yours.
                    String line = "§r§eA Prince falls. §r§a+1 Bonus Score";
                    if (by == null) Services.channels().chat(line);
                    else for (ServerPlayer h : humans(rd)) if (h.getUUID().equals(by)) Services.channels().chat(line, new Audience.Only(h.getUUID()));
                }
            }
            case BAT -> {
                // Only the bat's killer gets the line and the pings.
                for (ServerPlayer h : humans(rd)) if (h.getUUID().equals(by)) {
                    Services.channels().chat("§r§eA Bat has been slain. §r§a+1 Bonus Score", new Audience.Only(h.getUUID()));
                    // Two orb pings at the killer's position: a spread one at volume 0.5 and the usual one.
                    orbPing(h, 0.5f, SecretSounds.bonusPing(rd.sounds));
                    orbPing(h);
                }
            }
            case MIMIC -> {}   // Hypixel says nothing; the party-chat "Mimic Killed!" line is a mod's
        }
    }

    private static void batCounted(RunData rd, UUID by) {
        if (rd.bat.count()) bonus(rd, BonusKind.BAT, by);
    }

    @Override
    public String breakerRefusal(Run run, int x, int y, int z) {
        RunData rd = d(run);
        if (rd == null) return null;
        // Before T0 the server puts every block back and says nothing.
        if (run.t0Tick == null || run.t() < 0) return "";
        BlockState state = RunBlocks.get(x, y, z);
        // Any chest is a secret block, found or not: the Dungeonbreaker never digs one.
        if (SecretWorld.isChestBlock(state)) return BreakerRules.REFUSAL;
        // A skull is not dug either: the server puts the head back and takes the hit for a click.
        if (SecretWorld.isSkullBlock(state)) {
            RoomRecord rr = run.state.roomAt(x + 0.5, z + 0.5);
            Integer room = rr != null ? rr.index() : null;
            if (room != null) {
                KeyGate gate = rd.plan.gateAt(room, x, y, z);
                SecretSite site = rd.plan.siteAt(room, x, y, z);
                if (gate != null || (site != null && site.kind == SecretKind.ESSENCE)) {
                    ServerPlayer who = Sim.acting();
                    if (who != null) {
                        PlayerRecord pr = rd.run.state.players.get(who.getUUID());
                        if (pr != null && pr.isBot) who = null;
                    }
                    if (who == null) {
                        double bestD = 0;
                        for (ServerPlayer h : humans(rd)) {
                            double dd = h.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
                            if (who == null || dd < bestD) { who = h; bestD = dd; }
                        }
                    }
                    if (who != null) {
                        if (gate != null) pickUpKey(rd, who, gate);
                        else if (site != null && !rd.skulls.containsKey(site.key())) claim(rd, room, SecretKind.ESSENCE, who.getUUID(), site.x, site.y, site.z);
                    }
                    return "";
                }
            }
        }
        return BreakerRules.refusal(run.plan, rd.groups, (px, py, pz) -> uncollectedSecretAt(rd, px, py, pz), x, y, z);
    }

    private static boolean uncollectedSecretAt(RunData rd, int x, int y, int z) {
        RoomRecord rr = rd.run.state.roomAt(x + 0.5, z + 0.5);
        if (rr == null) return false;
        SecretSite s = rd.plan.siteAt(rr.index(), x, y, z);
        if (s == null) return false;
        return s.kind == SecretKind.CHEST || s.kind == SecretKind.LOCKED_CHEST;
    }

    @Override
    public int onSuperboom(Run run, UUID by, int x, int y, int z) {
        RunData rd = d(run);
        if (rd == null) return 0;
        int blocks = 0;
        for (CellGroup g : rd.groups.hitBy(x + 0.5, y + 0.5, z + 0.5, SecretsSettings.superboomRadius())) blocks += destroyGroup(rd, g, by, new Pos(x, y, z));
        return blocks;
    }

    private static int destroyGroup(RunData rd, CellGroup g, UUID by) { return destroyGroup(rd, g, by, null); }

    private static int destroyGroup(RunData rd, CellGroup g, UUID by, Pos aim) {
        if (!rd.groups.destroy(g)) return 0;
        BlockState air = Blocks.AIR.defaultBlockState();
        Pos first = null;
        if (aim == null) first = g.cells.isEmpty() ? null : g.cells.get(0);
        else {
            long best = 0;
            for (Pos c : g.cells) {
                long dd = sq(c.x - aim.x) + sq(c.y - aim.y) + sq(c.z - aim.z);
                if (first == null || dd < best) { first = c; best = dd; }
            }
        }
        if (first != null) { debris(first.x, first.y, first.z); setOrFix(rd, first.x, first.y, first.z, air); }
        List<Pos> rest = new ArrayList<>();
        for (Pos c : g.cells) if (c != first) rest.add(c);
        if (!rest.isEmpty()) rd.walls.add(Pair.of(rd.run.serverTick + 1, rest));
        if (g.kind == GroupKind.CRYPT) rd.book.crypt(g.room, by);
        return g.cells.size();
    }

    private static long sq(int v) { return (long) v * v; }

    public static final double CRYPT_BOT_P = 0.17;
    public static final int CRYPT_BOT_DELAY = 40;
    public static final int CHEST_STAGGER = 6;

    private static void tickBotBlasts(RunData rd, int now) {
        if (rd.botBlasts.isEmpty()) return;
        Iterator<Triple<Integer, CellGroup, UUID>> it = rd.botBlasts.iterator();
        while (it.hasNext()) {
            Triple<Integer, CellGroup, UUID> b = it.next();
            if (now < b.first) continue;
            it.remove();
            CellGroup g = b.second;
            if (g.cells.isEmpty()) continue;
            Pos c = g.cells.get(0);
            if (destroyGroup(rd, g, b.third, new Pos(c.x, c.y, c.z)) > 0)
                Sim.sound(SoundEvents.GENERIC_EXPLODE, 1f, SecretSounds.quantise((float) (0.81 + rd.miscRandom.nextDouble() * 0.174)), new Vec3(c.x + 0.5, c.y + 0.5, c.z + 0.5));
        }
    }

    @Override
    public int creditRoom(Run run, int room, UUID by) {
        RunData rd = d(run);
        if (rd == null) return 0;
        PlacedRoom r = room >= 0 && room < run.plan.rooms.size() ? run.plan.rooms.get(room) : null;
        if (r == null) return 0;
        int cx = 0, cz = 0;
        if (!r.tiles.isEmpty()) {
            Tile t = r.tiles.get(0);
            cx = DungeonFrame.tileCentre(t.x);
            cz = DungeonFrame.tileCentre(t.z);
        }
        List<SecretSite> pending = rd.book.pending(room);
        int n = rd.book.creditRemaining(room, by, cx, cz);
        int chestsOpened = 0;
        for (SecretSite s : pending) {
            if (!rd.book.isFound(s)) continue;
            afterFound(rd, s, by);
            if (s.kind == SecretKind.BAT) batCounted(rd, by);
            if (s.kind == SecretKind.CHEST || s.kind == SecretKind.LOCKED_CHEST) startChest(rd, s, by, CHEST_STAGGER * chestsOpened++);
        }
        // A teammate blows some of the room's tombs too, a moment after the check-off.
        if (!teamFromRecording) for (CellGroup g : rd.groups.ofRoom(room, GroupKind.CRYPT)) {
            if (rd.groups.isDestroyed(g)) continue;
            boolean queued = false;
            for (Triple<Integer, CellGroup, UUID> b : rd.botBlasts) if (b.second == g) { queued = true; break; }
            if (queued) continue;
            if (rd.miscRandom.nextDouble() < CRYPT_BOT_P) rd.botBlasts.add(Triple.of(rd.run.serverTick + rd.miscRandom.nextInt(CRYPT_BOT_DELAY + 1), g, by));
        }
        return n;
    }

    public static List<SecretSite> pendingSecrets(Run run, int room) {
        RunData rd = d(run);
        return rd != null ? rd.book.pending(room) : List.of();
    }

    public static int killBats(Run run, AABB box, UUID by) {
        RunData rd = d(run);
        if (rd == null) return 0;
        int n = 0;
        for (Pair<String, UUID> en : entitiesCopy(rd)) {
            SecretSite s = rd.plan.site(en.first);
            if (s == null) continue;
            if (s.kind != SecretKind.BAT) continue;
            if (!(SecretWorld.entity(en.second) instanceof Bat e)) continue;
            if (e.isAlive() && box.intersects(e.getBoundingBox())) { batDied(rd, s, e, by); n++; }
        }
        return n;
    }

    // ---- clicks

    @Override
    public InteractionResult onUseBlock(Run run, ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        RunData rd = d(run);
        if (rd == null) return null;
        PlayerRecord pr = rd.run.state.players.get(player.getUUID());
        if (pr != null && pr.ghost) return null;
        BlockPos pos = hit.getBlockPos();
        RoomRecord rr = run.state.roomAt(pos.getX() + 0.5, pos.getZ() + 0.5);
        if (rr == null) return null;
        int room = rr.index();
        BlockState block = RunBlocks.get(pos.getX(), pos.getY(), pos.getZ());
        KeyGate gate = rd.plan.gateAt(room, pos.getX(), pos.getY(), pos.getZ());
        if (gate != null && SecretWorld.isSkullBlock(block)) return pickUpKey(rd, player, gate);
        SecretSite site = rd.plan.siteAt(room, pos.getX(), pos.getY(), pos.getZ());
        if (site != null) return switch (site.kind) {
            case CHEST, LOCKED_CHEST -> SecretWorld.isChestBlock(block) ? chestClick(rd, player, site) : null;
            case ESSENCE -> SecretWorld.isSkullBlock(block) ? skullClick(rd, player, site) : null;
            case REDSTONE_KEY -> site.socket != null ? socketClick(rd, player, site) : (SecretWorld.isSkullBlock(block) ? skullClick(rd, player, site) : null);
            default -> null;
        };
        SecretSite bonusSite = rd.plan.bonusAt(room, pos.getX(), pos.getY(), pos.getZ());
        if (bonusSite != null && SecretWorld.isChestBlock(block)) return lootChestClick(rd, player, bonusSite);
        LeverInfo lever = rd.levers.get(CellGroups.key(pos.getX(), pos.getY(), pos.getZ()));
        if (lever != null) return leverClick(rd, player, lever);
        return null;
    }

    // ---- chests

    private static KRandom chestRandom(RunData rd, SecretSite s) {
        return new KRandom(rd.run.config.seed ^ ((long) s.key().hashCode() * -0x61c8864680b583ebL));
    }

    private static InteractionResult chestClick(RunData rd, ServerPlayer p, SecretSite s) {
        int now = rd.run.serverTick;
        if (s.kind == SecretKind.LOCKED_CHEST && !rd.unlocked.contains(s.key())) {
            // The refusal answers once; the right-click repeats and gets nothing.
            if (LockedChest.answers(rd.lockedSaid.get(p.getUUID()), now)) {
                rd.lockedSaid.put(p.getUUID(), now);
                Sim.chat(SecretRules.CHEST_LOCKED);
                Sim.sound(SoundEvents.ENDERMAN_TELEPORT, SecretSounds.DENIED_VOLUME, 0f, p.position(), SoundSource.HOSTILE);
            }
            return InteractionResult.SUCCESS;
        }
        ChestRec rec = rd.chests.get(s.key());
        if (rec != null) {
            // The Mimic's chest never opens and says nothing more; a reward chest is silent while its harp rolls; the rest are searched.
            if (SecretRules.repeatClick(rec.trapped, rec.reward(), rec.finished) == SecretRules.Repeat.SEARCHED) Sim.chat(SecretRules.CHEST_SEARCHED);
            return InteractionResult.SUCCESS;
        }
        Claim c = claim(rd, s.room, SecretKind.CHEST, p.getUUID(), s.x, s.y, s.z);
        if (!(c instanceof Claim.Credited)) { Sim.chat(SecretRules.CHEST_SEARCHED); return InteractionResult.SUCCESS; }
        startChest(rd, s, p.getUUID());
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult lootChestClick(RunData rd, ServerPlayer p, SecretSite s) {
        ChestRec rec = rd.chests.get(s.key());
        if (rec == null) { startChest(rd, s, p.getUUID()); return InteractionResult.SUCCESS; }
        if (SecretRules.repeatClick(false, rec.reward(), rec.finished) == SecretRules.Repeat.SEARCHED) Sim.chat(SecretRules.CHEST_SEARCHED);
        return InteractionResult.SUCCESS;
    }

    private static void startChest(RunData rd, SecretSite s, UUID by) { startChest(rd, s, by, 0); }

    private static void startChest(RunData rd, SecretSite s, UUID by, int delay) {
        if (rd.chests.containsKey(s.key())) return;
        int start = rd.run.serverTick + delay;
        if (s.key().equals(rd.trapped)) {
            rd.chests.put(s.key(), new ChestRec(s, Drop.Nothing.INSTANCE, by, start, true));
            openTrapped(rd, s, by, start);
            return;
        }
        rd.chests.put(s.key(), new ChestRec(s, dropsFromRecording ? Drop.Nothing.INSTANCE : LootTables.chest(chestRandom(rd, s)), by, start));
    }

    private static void openTrapped(RunData rd, SecretSite s, UUID by, int start) {
        int delay = TrappedChest.KILL_DELAY[0] + rd.miscRandom.nextInt(TrappedChest.KILL_DELAY[1] - TrappedChest.KILL_DELAY[0] + 1);
        UUID[] body = {null};
        rd.later.add(start + 1, () -> {
            ServerLevel level = RunBlocks.level();
            if (level != null && RunBlocks.isLoaded(s.x, s.z)) {
                Zombie z = EntityType.ZOMBIE.create(level, EntitySpawnReason.EVENT);
                if (z != null) {
                    z.setPos(s.x + 0.5, (double) s.y, s.z + 0.5);
                    z.setNoAi(true);
                    z.setPersistenceRequired();
                    Sim.spawn(z);
                    body[0] = z.getUUID();
                    rd.mimicBodies.add(z.getUUID());
                }
            }
        });
        rd.later.add(start + 1 + delay, () -> {
            if (body[0] != null && SecretWorld.entity(body[0]) instanceof LivingEntity z) {
                ServerLevel l = RunBlocks.level();
                if (l != null && z.isAlive()) z.kill(l);
            }
            bonus(rd, BonusKind.MIMIC, by);
        });
    }

    private static void tickChests(RunData rd, int now) {
        if (rd.chests.isEmpty()) return;
        for (ChestRec rec : new ArrayList<>(rd.chests.values())) {
            if (rec.finished) continue;
            List<SecretChestFinale.Item> schedule = SecretChestFinale.schedule(rec.reward());
            int elapsed = now - rec.start;
            for (int i : Beats.due(ats(schedule), elapsed, rec.done)) chestBeat(rd, rec, schedule.get(i));
            if (rec.done.size() >= schedule.size()) rec.finished = true;
        }
    }

    private static List<Integer> ats(List<SecretChestFinale.Item> schedule) {
        List<Integer> out = new ArrayList<>(schedule.size());
        for (SecretChestFinale.Item it : schedule) out.add(it.at);
        return out;
    }

    private static List<Integer> skullAts(List<SkullFinale.Item> schedule) {
        List<Integer> out = new ArrayList<>(schedule.size());
        for (SkullFinale.Item it : schedule) out.add(it.at);
        return out;
    }

    private static void chestBeat(RunData rd, ChestRec rec, SecretChestFinale.Item item) {
        SecretSite s = rec.site;
        Vec3 corner = new Vec3(s.x, s.y, s.z);
        switch (item.step) {
            case CORNER_OPEN -> Sim.sound(SoundEvents.CHEST_OPEN, 0.5f, SecretSounds.chestOpen(rd.sounds), corner, SoundSource.BLOCKS);
            case LID_AND_CENTRE_OPEN -> {
                sendLid(s);
                Sim.sound(SoundEvents.CHEST_OPEN, 0.5f, SecretSounds.chestOpen(rd.sounds), new Vec3(s.x + 0.5, s.y + 0.5, s.z + 0.5), SoundSource.BLOCKS);
            }
            case HARP -> Sim.sound(SoundEvents.NOTE_BLOCK_HARP, 1f, SecretSounds.HARP[item.note], corner, SoundSource.BLOCKS);
            case REVEAL -> applyChestDrop(rd, rec);
        }
    }

    /** The lid goes up for everyone, and stays up (the packet's block is the cherry log Hypixel names; the client ignores it). */
    private static void sendLid(SecretSite s) {
        if (!RunBlocks.isLoaded(s.x, s.z)) return;
        ClientboundBlockEventPacket packet = new ClientboundBlockEventPacket(new BlockPos(s.x, s.y, s.z), Blocks.CHERRY_LOG, 1, 1);
        for (ServerPlayer p : Sim.players()) p.connection.send(packet);
    }

    private static void sendOwedLids(RunData rd) {
        if (rd.lidsOwed.isEmpty()) return;
        Iterator<String> it = rd.lidsOwed.iterator();
        while (it.hasNext()) {
            SecretSite s = rd.plan.site(it.next());
            if (s == null) { it.remove(); continue; }
            if (!RunBlocks.isLoaded(s.x, s.z) || !SecretWorld.isChestBlock(RunBlocks.get(s.x, s.y, s.z))) continue;
            sendLid(s);
            it.remove();
        }
    }

    // ---- loot

    private static void applyChestDrop(RunData rd, ChestRec rec) {
        Who f = who(rd, rec.by);
        Drop drop = rec.drop;
        if (drop instanceof Drop.Item di) giveItem(rd, f, di.item, di.count);
        else if (drop instanceof Drop.Essence de) {
            tellEach(rd, f.uuid, SecretLines.chestEssenceSelf(de), SecretLines.chestEssenceOther(f.colour, f.name, de));
            // Everyone gets the essence, so everyone's bar shows it, whoever opened the chest.
            Services.channels().actionBarSegment("essence", "§d+" + de.amount + " " + de.type + " Essence", ActionBarModel.REPLACES_DEFENSE, ESSENCE_SEGMENT_TICKS, Audience.Everyone.INSTANCE);
            for (ServerPlayer h : humans(rd)) orbPing(h);
        } else if (drop instanceof Drop.Blessing db) grantBlessing(rd, f, db.blessing);
    }

    private static void giveItem(RunData rd, Who f, LootItem item, int count) {
        Services.channels().chat(obtainedLine(f.label(), item, count));
        if (item == LootItem.REVIVE_STONE && f.uuid != null) {
            PlayerRecord rec = rd.run.state.players.get(f.uuid);
            if (rec != null) {
                Object prev = rec.extras.get("reviveStones");
                rec.extras.put("reviveStones", (prev instanceof Number n ? n.intValue() : 0) + 1);
            }
        }
        ServerPlayer p = f.player;
        if (p != null) {
            SecretWorld.give(p, SecretWorld.stack(item, count, rd.run.packs.rooms != null ? rd.run.packs.rooms.textures : List.of()));
            soundTo(p, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, p.getX(), p.getY(), p.getZ(), 1f, 1f);
        }
    }

    static String obtainedLine(String who, LootItem item, int count) { return SecretLines.obtained(who, item, count); }

    private static void grantBlessing(RunData rd, Who finder, Blessing b) {
        int t = rd.run.t();
        double boost = SecretsSettings.blessingBoost();
        for (ServerPlayer p : humans(rd)) {
            boolean mine = p.getUUID().equals(finder.uuid);
            int level = Blessings.chestLevel(b, rd.miscRandom);
            List<String> lines = new ArrayList<>();
            lines.add(mine ? Blessings.announceYouFound(b, level, null) : Blessings.announceOther(finder.name, b, level, finder.colour, null));
            lines.addAll(Blessings.grantLines(b, level, boost));
            for (String l : lines) Services.channels().chat(l, new Audience.Only(p.getUUID()));
            rd.run.state.secrets.blessings.merge(b.name(), level, Integer::sum);
            rd.run.bus.post(new BlessingGranted(t, b.name(), level, p.getUUID()));
        }
    }

    // ---- room-clear drops

    private static UUID clearer(RunData rd, int room) {
        UUID k = rd.lastKiller.get(room);
        if (k != null) return k;
        List<UUID> members = new ArrayList<>(rd.run.state.players.keySet());
        return members.isEmpty() ? null : members.get(rd.dropRandom.nextInt(members.size()));
    }

    private static void roomClearDrop(RunData rd, int room) {
        if (dropsFromRecording) return;
        if (!(LootTables.roomClear(rd.dropRandom) instanceof Drop.Item drop)) return;
        giveItem(rd, who(rd, clearer(rd, room)), drop.item, drop.count);
    }

    private static void mobKillDrop(RunData rd, UUID killer, UUID mob) {
        if (dropsFromRecording) return;
        Drop drop = LootTables.mobKill(rd.dropRandom);
        if (drop == null) return;
        MobRecord rec = Services.mobs().mob(mob);
        double[] at = rec != null ? new double[]{rec.x, rec.y, rec.z} : null;
        if (at != null && (Double.isNaN(at[0]) || (at[0] == 0.0 && at[2] == 0.0))) at = null;
        Run run = rd.run;
        if (drop instanceof Drop.Item di) {
            if (at == null) { giveItem(rd, who(rd, killer), di.item, di.count); return; }
            boolean superboom = di.item == LootItem.SUPERBOOM;
            String name = di.item.colour + di.item.displayName + (di.count > 1 ? " §r§8x" + di.count : "");
            ItemStack model = superboom ? SimItems.SUPERBOOM().copy() : FloorDrops.head("revive_stone", run);
            FloorDrops.drop(run, name, model, superboom, at[0], at[1], at[2], 0, killer, rd.dropRandom, by -> giveItem(rd, who(rd, by != null ? by : killer), di.item, di.count));
        } else if (drop instanceof Drop.Blessing db) {
            if (at == null) { grantKillBlessing(rd, who(rd, killer), db.blessing); return; }
            FloorDrops.drop(run, "§dBlessing of " + db.blessing.displayName, FloorDrops.head(FloorDrops.BLESSING_TEXTURE, run), false, at[0], at[1], at[2], 1, killer, rd.dropRandom,
                    by -> grantKillBlessing(rd, who(rd, by != null ? by : killer), db.blessing));
        }
    }

    private static void grantKillBlessing(RunData rd, Who finder, Blessing b) {
        int t = rd.run.t();
        int level = Blessings.OWN_LEVEL;
        double boost = SecretsSettings.blessingBoost();
        boolean nameless = rd.miscRandom.nextDouble() < Blessings.KILL_PICKED_UP_SHARE;
        if (!nameless) Services.channels().chat(Blessings.obtained(finder.label(), b));
        for (ServerPlayer p : humans(rd)) {
            List<String> lines = new ArrayList<>();
            if (nameless) { lines.add(Blessings.pickedUp(b)); lines.add(Blessings.announceOwn(b, level, t)); }
            else lines.add(p.getUUID().equals(finder.uuid) ? Blessings.announceYouFound(b, level, t) : Blessings.announceOther(finder.name, b, level, finder.colour, t));
            lines.addAll(Blessings.grantLines(b, level, boost));
            for (String l : lines) Services.channels().chat(l, new Audience.Only(p.getUUID()));
            rd.run.state.secrets.blessings.merge(b.name(), level, Integer::sum);
            rd.run.bus.post(new BlessingGranted(t, b.name(), level, p.getUUID()));
        }
    }

    // ---- skulls and keys

    private static InteractionResult skullClick(RunData rd, ServerPlayer p, SecretSite s) {
        // The first click counts and starts the sequence; later clicks do nothing.
        if (rd.skulls.containsKey(s.key()) || rd.book.isFound(s)) return InteractionResult.SUCCESS;
        claim(rd, s.room, s.kind, p.getUUID(), s.x, s.y, s.z);
        return InteractionResult.SUCCESS;
    }

    private static void startSkull(RunData rd, SecretSite s, UUID by) {
        if (rd.skulls.containsKey(s.key())) return;
        rd.skulls.put(s.key(), new SkullRec(s, by, rd.run.serverTick));
    }

    private static void tickSkulls(RunData rd, int now) {
        if (rd.skulls.isEmpty()) return;
        for (Map.Entry<String, SkullRec> en : new ArrayList<>(rd.skulls.entrySet())) {
            String key = en.getKey();
            SkullRec rec = en.getValue();
            Who f = who(rd, rec.by);
            List<SkullFinale.Item> schedule = SkullFinale.schedule(true);
            for (int i : Beats.due(skullAts(schedule), now - rec.start, rec.done)) skullBeat(rd, rec, f, schedule.get(i));
            if (rec.done.size() >= schedule.size()) rd.skulls.remove(key);
        }
    }

    private static void skullBeat(RunData rd, SkullRec rec, Who f, SkullFinale.Item item) {
        SecretSite s = rec.site;
        switch (item.step) {
            case PLING -> {
                ServerPlayer it = f.player;
                if (it != null) soundTo(it, SoundEvents.NOTE_BLOCK_PLING, SoundSource.BLOCKS, it.getX(), it.getY(), it.getZ(), SecretSounds.SKULL_PLING_VOLUME, SecretSounds.SKULL_PLING_PITCH);
            }
            case HARP -> Sim.sound(SoundEvents.NOTE_BLOCK_HARP, 1f, SecretSounds.HARP[item.note], new Vec3(s.x, s.y, s.z), SoundSource.BLOCKS);
            case REWARD -> {
                setOrFix(rd, s.x, s.y, s.z, Blocks.AIR.defaultBlockState());
                tellEach(rd, f.uuid, SecretLines.WITHER_ESSENCE_SELF, SecretLines.witherEssenceOther(f.colour, f.name));
                for (ServerPlayer h : humans(rd)) {
                    int pings = SkullFinale.orbPings(h.getUUID().equals(f.uuid));
                    for (int k = 0; k < pings; k++) orbPing(h);
                }
                // Only the finder's bar shows the +1 (the essence is everyone's, but the number is the finder's).
                if (f.uuid != null && f.player != null)
                    Services.channels().actionBarSegment("essence", "§d+1 Wither Essence", ActionBarModel.REPLACES_DEFENSE, ESSENCE_SEGMENT_TICKS, new Audience.Only(f.uuid));
            }
        }
    }

    public static final int ESSENCE_SEGMENT_TICKS = 30;

    private static InteractionResult pickUpKey(RunData rd, ServerPlayer p, KeyGate gate) {
        if (!rd.gatesTaken.add(gate.key)) return InteractionResult.SUCCESS;
        rd.keyRooms.add(gate.room);
        Services.channels().chat(SecretRules.KEY_FOUND, new Audience.Only(p.getUUID()));
        soundTo(p, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, p.getX(), p.getY(), p.getZ(), 1f, 1f);
        removeLater(rd, gate.x, gate.y, gate.z);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult socketClick(RunData rd, ServerPlayer p, SecretSite s) {
        if (!rd.keyRooms.contains(s.room) || rd.book.isFound(s)) return null;
        claim(rd, s.room, SecretKind.REDSTONE_KEY, p.getUUID(), s.x, s.y, s.z);
        return InteractionResult.SUCCESS;
    }

    private static void openSocket(RunData rd, SecretSite s, UUID by) { openSocket(rd, s, by, false); }

    private static void openSocket(RunData rd, SecretSite s, UUID by, boolean immediate) {
        SocketInfo socket = s.socket;
        if (socket == null) return;
        rd.keyRooms.add(s.room);
        RoomSecrets rs = rd.plan.room(s.room);
        if (rs != null) for (KeyGate g : rs.gates) { rd.gatesTaken.add(g.key); setOrFix(rd, g.x, g.y, g.z, Blocks.AIR.defaultBlockState()); }
        if (!immediate) {
            Who f = who(rd, by);
            Vec3 centre = new Vec3(s.x + 0.5, s.y + 0.5, s.z + 0.5);
            Sim.sound(SoundEvents.PISTON_CONTRACT, 1f, 1f, f.player != null ? f.player.position() : centre, SoundSource.BLOCKS);
            if (f.player != null) Services.channels().chat(SecretRules.SOCKET_USED, new Audience.Only(f.player.getUUID()));
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        if (RunBlocks.isLoaded(socket.head.pos.x, socket.head.pos.z)) SecretWorld.placeHead(socket.head, rd.skullTextures.get(SecretKind.REDSTONE_KEY));
        else rd.later.add(rd.run.serverTick + 20, () -> SecretWorld.placeHead(socket.head, rd.skullTextures.get(SecretKind.REDSTONE_KEY)));
        for (Pos c : socket.opens) setOrFix(rd, c.x, c.y, c.z, air);
        for (RevealChest c : socket.chests) setOrFix(rd, c.site.x, c.site.y, c.site.z, SecretWorld.chestState(c.site));
        if (rs != null) for (SecretSite c : rs.sites) if (c.kind == SecretKind.LOCKED_CHEST) rd.unlocked.add(c.key());
    }

    // ---- levers

    private static InteractionResult leverClick(RunData rd, ServerPlayer p, LeverInfo l) {
        long key = CellGroups.key(l.pos.x, l.pos.y, l.pos.z);
        boolean used = rd.leverUsed.get(key) != null;
        for (String line : SecretRules.leverLines(used, p.isShiftKeyDown())) Sim.chat(line);
        Sim.sound(SoundEvents.ANVIL_BREAK, 1f, SecretSounds.ANVIL_BREAK_PITCH, p.position(), SoundSource.BLOCKS);
        if (used) {
            SecretWorld.resend(p, l.pos.x, l.pos.y, l.pos.z);
            return InteractionResult.SUCCESS;
        }
        rd.leverUsed.put(key, p.getUUID());
        if (rd.sounds.nextDouble() < LeverWall.LEVER_CLICK_SHARE)
            Sim.sound(SoundEvents.LEVER_CLICK, SecretSounds.LEVER_CLICK_VOLUME, SecretSounds.LEVER_CLICK_PITCH, new Vec3(l.pos.x + 0.5, l.pos.y + 0.5, l.pos.z + 0.5), SoundSource.BLOCKS);
        openLever(rd, l, false);
        SecretWorld.resend(p, l.pos.x, l.pos.y, l.pos.z);
        return InteractionResult.SUCCESS;
    }

    private static void openLever(RunData rd, LeverInfo l, boolean immediate) {
        SecretWorld.powerLever(l.pos.x, l.pos.y, l.pos.z);
        rd.unlocked.addAll(l.chests);
        if (l.opens.isEmpty()) return;
        if (immediate) { for (Pos c : l.opens) setOrFix(rd, c.x, c.y, c.z, Blocks.AIR.defaultBlockState()); return; }
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        for (Pos c : l.opens) setOrFix(rd, c.x, c.y, c.z, barrier);
        rd.walls.add(Pair.of(rd.run.serverTick + SecretsSettings.leverWallTicks(), l.opens));
        PlacedRoom room = l.room >= 0 && l.room < rd.run.plan.rooms.size() ? rd.run.plan.rooms.get(l.room) : null;
        String roomName = room != null && room.name != null ? room.name : "";
        if (LeverWall.ticks(roomName)) {
            int n = l.opens.size();
            int sx = 0, sy = 0, sz = 0;
            for (Pos c : l.opens) { sx += c.x; sy += c.y; sz += c.z; }
            Vec3 centre = new Vec3(sx / n + 0.5, sy / n + 0.5, sz / n + 0.5);
            rd.wallTicks.add(new WallTicks(centre, rd.run.serverTick));
        }
    }

    private static void tickWallTicks(RunData rd, int now) {
        if (rd.wallTicks.isEmpty()) return;
        List<Integer> ats = new ArrayList<>(LeverWall.TICKS.length);
        for (int t : LeverWall.TICKS) ats.add(t);
        Iterator<WallTicks> it = rd.wallTicks.iterator();
        while (it.hasNext()) {
            WallTicks w = it.next();
            for (int ignored : Beats.due(ats, now - w.start, w.done)) Sim.sound(SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON, 2f, SecretSounds.WALL_TICK_PITCH, w.at, SoundSource.MASTER);
            if (w.done.size() >= ats.size()) it.remove();
        }
    }

    @Override
    public InteractionResult onAttackEntity(Run run, ServerPlayer player, InteractionHand hand, Entity entity) {
        RunData rd = d(run);
        if (rd == null) return null;
        if (!(entity instanceof Bat bat)) return null;
        String key = null;
        for (Map.Entry<String, UUID> e : rd.entities.entrySet()) if (e.getValue().equals(bat.getUUID())) { key = e.getKey(); break; }
        if (key == null) return null;
        SecretSite s = rd.plan.site(key);
        if (s == null) return null;
        batDied(rd, s, bat, player.getUUID());
        return InteractionResult.SUCCESS;
    }

    // ---- savepoints

    private static JsonObject ceremony(String key, UUID by, int elapsed) {
        JsonObject j = new JsonObject();
        j.addProperty("key", key);
        j.addProperty("by", by != null ? by.toString() : null);
        j.addProperty("elapsed", elapsed);
        return j;
    }

    @Override
    public JsonObject snapshot(Run run) {
        RunData rd = d(run);
        if (rd == null) return null;
        int now = run.serverTick;
        JsonObject o = rd.book.snapshot();
        JsonArray destroyed = new JsonArray();
        for (String id : rd.groups.destroyedIds()) destroyed.add(id);
        o.add("destroyed", destroyed);
        JsonObject levers = new JsonObject();
        for (Map.Entry<Long, UUID> e : rd.leverUsed.entrySet()) levers.addProperty(e.getKey().toString(), e.getValue().toString());
        o.add("levers", levers);
        JsonArray unlocked = new JsonArray();
        for (String k : rd.unlocked) unlocked.add(k);
        o.add("unlocked", unlocked);
        o.addProperty("bonusBatIndex", rd.bat.index);
        o.addProperty("batClaims", rd.bat.counted);
        JsonArray keyRooms = new JsonArray();
        for (Integer r : rd.keyRooms) keyRooms.add(r);
        o.add("keyRooms", keyRooms);
        JsonArray gatesTaken = new JsonArray();
        for (String g : rd.gatesTaken) gatesTaken.add(g);
        o.add("gatesTaken", gatesTaken);
        // Ceremonies under way, as ticks since they began: the harp keeps rolling from where it was.
        JsonArray chests = new JsonArray();
        for (ChestRec r : rd.chests.values()) if (!r.finished) chests.add(ceremony(r.site.key(), r.by, now - r.start));
        o.add("chests", chests);
        JsonArray skulls = new JsonArray();
        for (SkullRec r : rd.skulls.values()) skulls.add(ceremony(r.site.key(), r.by, now - r.start));
        o.add("skulls", skulls);
        return o;
    }

    private static UUID ceremonyBy(JsonObject j) {
        JsonElement e = j.get("by");
        if (e == null || e.isJsonNull()) return null;
        String s = e.getAsString();
        try { return UUID.fromString(s); } catch (Throwable t) { return null; }
    }

    private static int ceremonyElapsed(JsonObject j) {
        JsonElement e = j.get("elapsed");
        return e != null ? e.getAsInt() : 0;
    }

    /**
     * The region resync has put every block back to the start state and the engine has removed every entity; put the chests, opened
     * walls, destroyed crypts, used levers, used keys and collected skulls back, and let the proximity pass bring the rest in again.
     */
    @Override
    public void restore(Run run, JsonObject data) {
        RunData rd = d(run);
        if (rd == null) return;
        int now = run.serverTick;
        rd.book.restore(data);
        rd.placed.clear(); rd.entities.clear(); rd.walls.clear(); rd.fixes.clear(); rd.leverUsed.clear();
        rd.chests.clear(); rd.skulls.clear(); rd.wallTicks.clear(); rd.lidsOwed.clear(); rd.later.clear(); rd.lockedSaid.clear();
        for (UUID id : rd.mimicBodies) { Entity e = SecretWorld.entity(id); if (e != null) e.discard(); }
        rd.mimicBodies.clear();
        List<String> destroyed = new ArrayList<>();
        JsonArray da = data.getAsJsonArray("destroyed");
        if (da != null) for (JsonElement e : da) destroyed.add(e.getAsString());
        rd.groups.setDestroyed(destroyed);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (String id : destroyed) {
            CellGroup g = rd.groups.group(id);
            if (g != null) for (Pos c : g.cells) setOrFix(rd, c.x, c.y, c.z, air);
        }
        rd.unlocked.clear();
        JsonArray ua = data.getAsJsonArray("unlocked");
        if (ua != null) for (JsonElement e : ua) rd.unlocked.add(e.getAsString());
        // Chests without a lever were never locked.
        rd.unlocked.addAll(Levers.build(run.plan, rd.plan).second);
        JsonObject lo = data.getAsJsonObject("levers");
        if (lo != null) for (Map.Entry<String, JsonElement> e : lo.entrySet()) {
            Long key;
            try { key = Long.parseLong(e.getKey()); } catch (NumberFormatException ex) { key = null; }
            if (key == null) continue;
            UUID used;
            try { used = UUID.fromString(e.getValue().getAsString()); } catch (Throwable t) { used = NIL; }
            rd.leverUsed.put(key, used);
            LeverInfo l = rd.levers.get(key);
            if (l != null) openLever(rd, l, true);
        }
        rd.keyRooms.clear(); rd.gatesTaken.clear();
        JsonArray kr = data.getAsJsonArray("keyRooms");
        if (kr != null) for (JsonElement e : kr) rd.keyRooms.add(e.getAsInt());
        JsonArray gt = data.getAsJsonArray("gatesTaken");
        if (gt != null) for (JsonElement e : gt) rd.gatesTaken.add(e.getAsString());
        for (KeyGate g : rd.plan.gates()) if (rd.gatesTaken.contains(g.key)) setOrFix(rd, g.x, g.y, g.z, air);
        // The chests are back (they stood from the start), the opened ones with their lids to be raised again; the sockets used are used.
        placeChests(rd);
        JsonArray skullsArr = data.getAsJsonArray("skulls");
        for (SecretSite s : rd.plan.all()) {
            if (!rd.book.isFound(s)) continue;
            switch (s.kind) {
                case ESSENCE -> {
                    boolean running = false;
                    if (skullsArr != null) for (JsonElement e : skullsArr) {
                        JsonElement k = e.getAsJsonObject().get("key");
                        if (k != null && s.key().equals(k.getAsString())) { running = true; break; }
                    }
                    if (!running) setOrFix(rd, s.x, s.y, s.z, air);
                }
                case REDSTONE_KEY -> { if (s.socket != null) openSocket(rd, s, null, true); else setOrFix(rd, s.x, s.y, s.z, air); }
                case CHEST, LOCKED_CHEST -> { if (!s.key().equals(rd.trapped)) rd.lidsOwed.add(s.key()); }   // the Mimic's chest never opened
                default -> {}
            }
        }
        // Ceremonies that were under way carry on from where they were.
        JsonArray ca = data.getAsJsonArray("chests");
        if (ca != null) for (JsonElement e : ca) {
            JsonObject j = e.getAsJsonObject();
            SecretSite s = rd.plan.site(j.get("key").getAsString());
            if (s == null) continue;
            int elapsed = ceremonyElapsed(j);
            UUID by = ceremonyBy(j);
            ChestRec rec = new ChestRec(s, dropsFromRecording ? Drop.Nothing.INSTANCE : LootTables.chest(chestRandom(rd, s)), by, now - elapsed);
            rec.done.addAll(Beats.playedBy(ats(SecretChestFinale.schedule(rec.reward())), elapsed));
            rd.chests.put(s.key(), rec);
            if (elapsed >= 1 && !s.key().equals(rd.trapped)) rd.lidsOwed.add(s.key());
        }
        if (skullsArr != null) for (JsonElement e : skullsArr) {
            JsonObject j = e.getAsJsonObject();
            SecretSite s = rd.plan.site(j.get("key").getAsString());
            if (s == null) continue;
            int elapsed = ceremonyElapsed(j);
            UUID by = ceremonyBy(j);
            SkullRec rec = new SkullRec(s, by, now - elapsed);
            rec.done.addAll(Beats.playedBy(skullAts(SkullFinale.schedule(true)), elapsed));
            rd.skulls.put(s.key(), rec);
        }
        JsonElement bi = data.get("bonusBatIndex");
        rd.bat.index = bi != null ? bi.getAsInt() : rd.bat.index;
        JsonElement bc = data.get("batClaims");
        rd.bat.counted = bc != null ? bc.getAsInt() : 0;
        rd.lastSpawnPass = -SPAWN_PERIOD;
    }

    // ---- events

    @Override
    public void onEvent(Run run, RunEvent event) {
        RunData rd = d(run);
        if (rd == null) return;
        if (event instanceof MobKilled mk) {
            Integer room = mk.mob.room;
            if (room != null && mk.mob.required) rd.lastKiller.put(room, mk.by);
            if (room != null && mk.mob.starred && mk.by != null) {
                PlacedRoom pr = room >= 0 && room < run.plan.rooms.size() ? run.plan.rooms.get(room) : null;
                if (pr == null || pr.type != RoomType.BLOOD) mobKillDrop(rd, mk.by, mk.mob.id);
            }
            return;
        }
        if (event instanceof RoomCleared rc) {
            PlacedRoom room = rc.room >= 0 && rc.room < run.plan.rooms.size() ? run.plan.rooms.get(rc.room) : null;
            if (room == null) return;
            roomClearDrop(rd, rc.room);
            if (room.type == RoomType.PUZZLE || room.type == RoomType.BLOOD) return;   // a puzzle's secret is its reward chest
            if (room.tiles.isEmpty()) return;
            Tile t = room.tiles.get(0);
            rd.book.roomCleared(rc.room, null, DungeonFrame.tileCentre(t.x), 70, DungeonFrame.tileCentre(t.z));
        }
    }

    // ---- /ads dev

    @Override
    public List<AdsCommand> devCommands() { return SecretsDev.commands(); }

    /** For the dev commands: the run data's pieces without exposing the private class. */
    static DevInfo devInfo(Run run) {
        RunData rd = d(run);
        return rd != null ? new DevInfo(rd.plan, rd.groups, rd.book, rd.unlocked, rd.entities, rd.leverUsed.keySet()) : null;
    }

    static final class DevInfo {
        final SecretPlan plan;
        final CellGroups groups;
        final SecretBook book;
        final Set<String> unlocked;
        final Map<String, UUID> entities;
        final Set<Long> leversUsed;

        DevInfo(SecretPlan plan, CellGroups groups, SecretBook book, Set<String> unlocked, Map<String, UUID> entities, Set<Long> leversUsed) {
            this.plan = plan;
            this.groups = groups;
            this.book = book;
            this.unlocked = unlocked;
            this.entities = entities;
            this.leversUsed = leversUsed;
        }
    }

    static int devGroupDestroy(Run run, CellGroup g, UUID by) {
        RunData rd = d(run);
        return rd != null ? destroyGroup(rd, g, by) : 0;
    }
}
