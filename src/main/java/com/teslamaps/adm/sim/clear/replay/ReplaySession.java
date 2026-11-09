// Ported from ADM's p3sim/clear/replay/ReplaySession.kt
package com.teslamaps.adm.sim.clear.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimItems;
import com.teslamaps.adm.sim.SimNet;
import com.teslamaps.adm.sim.SimServer;
import com.teslamaps.adm.sim.clear.blood.BloodModule;
import com.teslamaps.adm.sim.clear.blood.WatcherLines;
import com.teslamaps.adm.sim.clear.blood.camp.CampLines;
import com.teslamaps.adm.sim.clear.blood.camp.CampMech;
import com.teslamaps.adm.sim.clear.blood.camp.WallStation;
import com.teslamaps.adm.sim.clear.blood.camp.WatcherCamp;
import com.teslamaps.adm.sim.clear.damage.DamageModule;
import com.teslamaps.adm.sim.clear.mobs.LevelLooks;
import com.teslamaps.adm.sim.clear.mobs.MobRolls;
import com.teslamaps.adm.sim.clear.mobs.MobsModule;
import com.teslamaps.adm.sim.clear.mobs.PlannedMob;
import com.teslamaps.adm.sim.clear.puzzles.BlazePuzzle;
import com.teslamaps.adm.sim.clear.puzzles.PuzzlesModule;
import com.teslamaps.adm.sim.clear.secrets.Blessing;
import com.teslamaps.adm.sim.clear.secrets.LootItem;
import com.teslamaps.adm.sim.clear.secrets.SecretSite;
import com.teslamaps.adm.sim.clear.secrets.SecretsModule;
import com.teslamaps.adm.sim.clear.secrets.SecretsSettings;
import com.teslamaps.adm.sim.clear.state.DoorsModule;
import com.teslamaps.adm.sim.clear.state.StateController;
import com.teslamaps.adm.sim.clear.traps.TrapsModule;
import com.teslamaps.adm.sim.mixin.ServerTeleportAccessor;
import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.pack.mobs.MobArchetype;
import com.teslamaps.adm.sim.pack.mobs.MobModifier;
import com.teslamaps.adm.sim.pack.mobs.MobPack;
import com.teslamaps.adm.sim.run.BonusKind;
import com.teslamaps.adm.sim.run.CheckMark;
import com.teslamaps.adm.sim.run.DamageSource;
import com.teslamaps.adm.sim.run.DoorPhase;
import com.teslamaps.adm.sim.run.DoorRecord;
import com.teslamaps.adm.sim.run.KeyKind;
import com.teslamaps.adm.sim.run.KeyState;
import com.teslamaps.adm.sim.run.PlacedDoor;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.RoomType;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.SecretKind;
import com.teslamaps.adm.sim.run.SecretLedger;
import com.teslamaps.adm.sim.run.api.MobRecord;
import com.teslamaps.adm.sim.run.api.Puzzle;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One replay being played (server thread): the recording's player is the host (what they sent is handed to the sim's packet handlers at
 * the tick it was sent), the teammates are ReplayBodies with party records, and what the teammates did that ADS cannot do by itself is
 * done for them when the recording says it happened and ADS has not (each one logged: diff).
 */
public class ReplaySession {
    public final Run run;
    public final ReplayFile file;
    public final ServerPlayer host;
    private final ReplayBodies bodies;
    private final LinkedHashMap<String, ReplayBodies.Mate> mateByName = new LinkedHashMap<>();
    private final HashMap<Integer, String> mateIdName;
    public final ReplayDiff diff;

    /** Recorded entities (for finding ADS's own one an attack or a death was about). */
    public static class RecEnt {
        public final int id;
        public String type;
        public double x;
        public double y;
        public double z;
        public String name;
        public boolean gone = false;

        public RecEnt(int id, String type, double x, double y, double z, String name) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
        }
    }

    private final HashMap<Integer, RecEnt> ents = new HashMap<>();

    /** Run tick of the last event applied. */
    private int lastK = Integer.MIN_VALUE;
    private int seq = 1000;
    public boolean ended = false;

    private String ownName = null;

    /** Where the recording had you at the end of the last tick. */
    private Vec3 recPos = null;

    private static final class Pending {
        final int due;
        final String what;
        final BooleanSupplier done;
        final Supplier<String> force;

        Pending(int due, String what, BooleanSupplier done, Supplier<String> force) {
            this.due = due;
            this.what = what;
            this.done = done;
            this.force = force;
        }
    }

    private final List<Pending> pending = new ArrayList<>();

    /** The hotbar, armour and offhand as recorded now (slot -> stack), kept on the host. */
    private final HashMap<Integer, ItemStack> wantInv = new HashMap<>();
    private boolean invDirty = false;
    private Vec3 lastHost = null;

    /** The server's teleport count for you when teleported last looked. */
    private int tpSeen = -1;

    /** Whether ADS teleported you since teleported last looked, from `from` (more than half a block away). */
    private boolean teleported(Vec3 from) {
        int id = ((ServerTeleportAccessor) host.connection).ec_getAwaitingTeleport();
        boolean sent = tpSeen != -1 && id != tpSeen;
        tpSeen = id;
        return sent && from.distanceTo(host.position()) > 0.5;
    }

    public ReplaySession(Run run, ReplayFile file, ServerPlayer host) {
        this.run = run;
        this.file = file;
        this.host = host;
        this.mateIdName = new HashMap<>(file.idToMate);
        this.diff = new ReplayDiff(this);
        List<ReplayBodies.Mate> mates = new ArrayList<>();
        for (ReplayFile.Person p : file.party) {
            UUID u = null;
            if (p.uuid != null) { try { u = UUID.fromString(p.uuid); } catch (Throwable ignored) {} }
            if (u == null) u = UUID.nameUUIDFromBytes(("replay:" + p.name).getBytes(StandardCharsets.UTF_8));
            mates.add(new ReplayBodies.Mate(p.name, u, p.cls, p.tex, p.sig));
        }
        for (ReplayBodies.Mate m : mates) mateByName.put(m.name, m);
        this.bodies = new ReplayBodies(mates);
    }

    private static String cls(String c) { return c != null ? c.toUpperCase(Locale.ROOT) : null; }

    private static boolean isChat(JsonArray e) { return e.size() > 2 && e.get(1).getAsString().equals("chat") && e.get(2).isJsonPrimitive(); }

    private static String gv(Matcher m, int g) {
        String s = m.group(g);
        return s != null ? s : "";
    }

    private static Integer toIntOrNull(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static Double toDoubleOrNull(String s) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    // ---- the recording's mobs, as the mobs module plans them

    private Map<Integer, List<PlannedMob>> roomPlans = null;

    /** The recording's mobs, room by room: each where it was first seen, kind and modifier from its tag, with the HP its tag showed. */
    private Map<Integer, List<PlannedMob>> roomPlans() {
        if (roomPlans != null) return roomPlans;
        recordedScale();
        MobPack pack = run.packs.mobs;
        if (pack == null) { roomPlans = Map.of(); return roomPlans; }
        List<MobArchetype> byName = new ArrayList<>(pack.archetypes);
        byName.addAll(LevelLooks.all());
        byName.sort(Comparator.<MobArchetype>comparingInt(a -> a.name.length()).reversed());
        HashMap<Integer, List<PlannedMob>> out = new HashMap<>();
        HashMap<String, Integer> unknown = new HashMap<>();
        for (ReplayFile.Mob m : file.mobs) {
            PlacedRoom room = run.plan.roomAtBlock(m.x, m.z);
            if (room == null) continue;
            if (room.type == RoomType.BLOOD || room.type == RoomType.ENTRANCE || room.type == RoomType.FAIRY) continue;
            boolean star = m.tag.contains("✯");
            String name = ReplayFileKt.trim(m.tag.replace("✯", ""));
            MobArchetype arch = null;
            for (MobArchetype a : byName) if (name.equals(a.name) || name.endsWith(" " + a.name)) { arch = a; break; }
            if (arch == null) { unknown.merge(name, 1, Integer::sum); continue; }
            String word = ReplayFileKt.trim(name.endsWith(arch.name) ? name.substring(0, name.length() - arch.name.length()) : name);
            MobModifier mod = word.isEmpty() ? null : pack.modifier(word);
            if (!word.isEmpty() && mod == null) unknown.merge(name, 1, Integer::sum);
            KRandom rng = run.subRandom("replay.mob." + m.id);
            UUID id = arch.disguise ? MobRolls.uuidV2(rng) : MobRolls.uuidV4(rng);
            // Its base HP: a mob first seen after the WITHER door into its room had scaled already.
            Double fh = firstHp.get(m.id);
            double h0 = fh != null ? fh : m.hp;
            double hp;
            if (h0 > 0) {
                Pair<Integer, Double> sc = recordedScale().get(room.index);
                Integer fk = firstHpK.get(m.id);
                if (sc != null && (fk != null ? fk : m.k) > sc.first) hp = (double) Math.round(h0 / sc.second);
                else hp = h0;
            } else {
                Long ah = arch.hpFor(run.config.floor);
                hp = ah != null ? ah.doubleValue() : 1.0;
            }
            planned.put(m.id, id);
            plannedBack.put(id, m.id);
            out.computeIfAbsent(room.index, r -> new ArrayList<>()).add(new PlannedMob(id, arch.index, star, star || "miniboss".equals(arch.cls), mod != null ? mod.id : "", hp,
                    m.x, m.y, m.z, m.yaw, !"static".equals(arch.movement), null));
        }
        for (Map.Entry<String, Integer> en : unknown.entrySet()) diff.note(0, "mobs", "the mob pack has no kind for \"" + en.getKey() + "\" (" + en.getValue() + "x): not spawned");
        roomPlans = out;
        return roomPlans;
    }

    private List<double[]> recordedBlazes = null;

    /** The Higher Or Lower blazes of the recording: each body where it was first seen, with the HP its tag gave (x, y, z, HP). */
    private List<double[]> recordedBlazes() {
        if (recordedBlazes != null) return recordedBlazes;
        HashMap<Integer, JsonArray> first = new HashMap<>();
        LinkedHashMap<String, double[]> out = new LinkedHashMap<>();
        for (JsonArray e : file.events) {
            String kind = ReplayFileKt.s(e, 1);
            if ("spawn".equals(kind)) {
                Integer id = ReplayFileKt.i(e, 2);
                if (id != null) first.putIfAbsent(id, e);
            } else if ("name".equals(kind)) {
                String tag = ReplayFileKt.s(e, 3);
                if (tag == null) continue;
                Matcher bm = BLAZE_TAG.matcher(tag);
                if (!bm.find()) continue;
                Double hp = toDoubleOrNull(gv(bm, 1).replace(",", ""));
                if (hp == null) continue;
                Integer i2 = ReplayFileKt.i(e, 2);
                if (i2 == null) continue;
                JsonArray b = first.get(i2 - 1);
                if (b == null || !"minecraft:blaze".equals(ReplayFileKt.s(b, 3))) continue;
                Double x = ReplayFileKt.d(b, 5);
                if (x == null) continue;
                Double y = ReplayFileKt.d(b, 6);
                if (y == null) continue;
                Double z = ReplayFileKt.d(b, 7);
                if (z == null) continue;
                out.putIfAbsent(x + " " + y + " " + z, new double[]{x, y, z, hp});
            }
        }
        recordedBlazes = new ArrayList<>(out.values());
        return recordedBlazes;
    }

    /** The ADS mob planned for each recorded mob. */
    private final HashMap<Integer, UUID> planned = new HashMap<>();
    private final HashMap<UUID, Integer> plannedBack = new HashMap<>();

    private Map<Integer, List<Pair<Integer, Double>>> recHp = null;

    /** Each recorded mob's HP over the run (k, HP; 0 is its death). */
    private Map<Integer, List<Pair<Integer, Double>>> recHp() {
        if (recHp != null) return recHp;
        HashMap<Integer, List<Pair<Integer, Double>>> out = new HashMap<>();
        for (JsonArray e : file.events) {
            if (e.size() > 3 && e.get(1).getAsString().equals("hp")) {
                double v = e.get(3).getAsDouble();
                List<Pair<Integer, Double>> l = out.computeIfAbsent(e.get(2).getAsInt(), q -> new ArrayList<>());
                if (v > 100.0 || (v <= 0.0 && !l.isEmpty())) l.add(Pair.of(e.get(0).getAsInt(), v));
            }
            // A mob killed outright gets no last HP update, only its death.
            if (e.size() > 3 && e.get(1).getAsString().equals("eev") && e.get(3).getAsInt() == 3) {
                List<Pair<Integer, Double>> l = out.get(e.get(2).getAsInt());
                if (l != null && !l.isEmpty()) l.add(Pair.of(e.get(0).getAsInt(), 0.0));
            }
        }
        recHp = out;
        return recHp;
    }

    private final HashMap<Integer, Integer> hpUsedK = new HashMap<>();
    /** Set while the session itself kills a mob (a recorded death), so the hit is not read as yours. */
    private boolean forcing = false;

    /** Your hit on mob does what the recording shows (the HP its recorded mob lost around now); null for anyone else's hit. */
    private Double hitAmount(UUID mob, UUID by) {
        if (forcing || ended || !Objects.equals(by, host.getUUID())) return null;
        Integer id = plannedBack.get(mob);
        if (id == null) return null;
        List<Pair<Integer, Double>> l = recHp().get(id);
        if (l == null) return 0.0;
        int now = run.t();
        Integer used = hpUsedK.get(id);
        int from = Math.max(used != null ? used : Integer.MIN_VALUE, now - HIT_BEFORE - 1);
        Pair<Integer, Double> beforeP = null;
        for (Pair<Integer, Double> p : l) if (p.first <= from) beforeP = p;
        if (beforeP == null) return 0.0;
        double before = beforeP.second;
        Pair<Integer, Double> low = null;
        for (Pair<Integer, Double> p : l) if (p.first > from && p.first <= now + HIT_AFTER && (low == null || p.second < low.second)) low = p;
        if (low == null) return 0.0;
        hpUsedK.put(id, low.first);
        if (low.second <= 0.0) return 1e15;
        return Math.max(0.0, before - low.second);
    }

    /** Recorded HP as the replay goes, and the WITHER-door jumps to check two ticks on. */
    private final HashMap<Integer, Double> liveHp = new HashMap<>();
    private final List<Triple<Integer, Integer, Double>> hpChecks = new ArrayList<>();

    /** Each recorded mob's first real HP (over 100) and the k it was seen. */
    private final HashMap<Integer, Double> firstHp = new HashMap<>();
    private final HashMap<Integer, Integer> firstHpK = new HashMap<>();

    private Map<Integer, Pair<Integer, Double>> recordedScale = null;

    /** Each room's WITHER-door HP factor as the recording shows it: the commonest ratio (to 0.05) and the first k one jumped. */
    private Map<Integer, Pair<Integer, Double>> recordedScale() {
        if (recordedScale != null) return recordedScale;
        HashMap<Integer, ReplayFile.Mob> mobs = new HashMap<>();
        for (ReplayFile.Mob m : file.mobs) mobs.put(m.id, m);
        HashMap<Integer, Double> last = new HashMap<>();
        HashSet<Integer> jumped = new HashSet<>();
        HashMap<Integer, List<Pair<Integer, Double>>> byRoom = new HashMap<>();
        for (JsonArray e : file.events) {
            if (e.size() < 4 || !e.get(1).getAsString().equals("hp")) continue;
            int id = e.get(2).getAsInt();
            double v = e.get(3).getAsDouble();
            int k = e.get(0).getAsInt();
            ReplayFile.Mob m = mobs.get(id);
            if (m == null) continue;
            if (v <= 100.0) continue;
            if (!firstHp.containsKey(id)) { firstHp.put(id, v); firstHpK.put(id, k); }
            Double l = last.put(id, v);
            if (l == null) continue;
            if (k <= 0 || v / l <= 1.04 || !jumped.add(id)) continue;
            PlacedRoom room = run.plan.roomAtBlock(m.x, m.z);
            if (room == null) continue;
            byRoom.computeIfAbsent(room.index, r -> new ArrayList<>()).add(Pair.of(k, Math.round(v / l * 20) / 20.0));
        }
        HashMap<Integer, Pair<Integer, Double>> out = new HashMap<>();
        for (Map.Entry<Integer, List<Pair<Integer, Double>>> en : byRoom.entrySet()) {
            List<Pair<Integer, Double>> l = en.getValue();
            int minK = Integer.MAX_VALUE;
            LinkedHashMap<Double, Integer> counts = new LinkedHashMap<>();
            for (Pair<Integer, Double> p : l) { minK = Math.min(minK, p.first); counts.merge(p.second, 1, Integer::sum); }
            Double best = null;
            int bestN = -1;
            for (Map.Entry<Double, Integer> c : counts.entrySet()) if (c.getValue() > bestN) { bestN = c.getValue(); best = c.getKey(); }
            out.put(en.getKey(), Pair.of(minK, best));
        }
        recordedScale = out;
        return recordedScale;
    }

    private static final class Throw {
        final int thr;
        final int land;
        final double x;
        final double y;
        final double z;

        Throw(int thr, int land, double x, double y, double z) {
            this.thr = thr;
            this.land = land;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class Stand {
        final double x;
        final double y;
        final double z;
        int thr = -1;
        int land = -1;

        Stand(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    private Integer bloodDoorK() {
        for (JsonArray e : file.events) if (isChat(e) && e.get(2).getAsString().startsWith("The BLOOD DOOR has been opened!")) return e.get(0).getAsInt();
        return null;
    }

    private static double frac(double v) { return ((v % 1.0) + 1.0) % 1.0; }

    private List<Throw> recordedThrows = null;

    /** The recording's Watcher throws, in ticks after its BLOOD door, in throw order. */
    private List<Throw> recordedThrows() {
        if (recordedThrows != null) return recordedThrows;
        Integer door = bloodDoorK();
        if (door == null) { recordedThrows = List.of(); return recordedThrows; }
        HashSet<Integer> named = new HashSet<>();
        for (JsonArray e : file.events) if (e.size() > 2 && e.get(1).getAsString().equals("name")) named.add(e.get(2).getAsInt());
        HashMap<Integer, Stand> stands = new HashMap<>();
        for (JsonArray e : file.events) {
            int k = e.get(0).getAsInt();
            String kind = e.get(1).getAsString();
            if (kind.equals("spawn") && e.size() > 7 && e.get(3).getAsString().equals("minecraft:armor_stand") && !named.contains(e.get(2).getAsInt()) && !stands.containsKey(e.get(2).getAsInt())) {
                double x = e.get(5).getAsDouble();
                double y = e.get(6).getAsDouble();
                double z = e.get(7).getAsDouble();
                // A head at rest on the wall (block centre, 0.8 over a block).
                boolean atRest = Math.abs(frac(x) - 0.5) < 0.01 && Math.abs(frac(z) - 0.5) < 0.01 && Math.abs(frac(y) - 0.8) < 0.1;
                PlacedRoom r = run.plan.roomAtBlock(x, z);
                if (atRest && r != null && r.type == RoomType.BLOOD) stands.put(e.get(2).getAsInt(), new Stand(x, y, z));
                continue;
            }
            if (k <= door || e.size() < 3 || (!kind.equals("emv") && !kind.equals("gone"))) continue;
            Stand st = stands.get(e.get(2).getAsInt());
            if (st == null) continue;
            if (kind.equals("emv") && st.thr < 0 && e.size() > 5 && Math.hypot(e.get(3).getAsDouble() - st.x, e.get(5).getAsDouble() - st.z) > 0.05) st.thr = k - door;
            if (kind.equals("gone") && st.land < 0) st.land = k - door;
        }
        // A real throw flies 38 to 80 ticks; the wall's other heads go when the room is left.
        List<Stand> ok = new ArrayList<>();
        for (Stand s : stands.values()) if (s.thr >= 0 && s.land - s.thr >= 30 && s.land - s.thr <= 90) ok.add(s);
        ok.sort(Comparator.comparingInt(s -> s.thr));
        List<Throw> out = new ArrayList<>();
        for (Stand s : ok) out.add(new Throw(s.thr, s.land, s.x, s.y, s.z));
        recordedThrows = out;
        return recordedThrows;
    }

    /** The recording's throw order as ADS's wall stations, or null when any head has no station or the camp is not whole. */
    private List<WallStation> recordedOrder(List<WallStation> wall) {
        List<Throw> t = recordedThrows();
        if (t.size() < 4 + CampMech.LATER_THROWS) {
            diff.note(run.t(), "camp", "the recording has " + t.size() + " throws, not a whole camp: ADS draws its own order");
            return null;
        }
        List<WallStation> out = new ArrayList<>();
        for (Throw r : t) {
            WallStation hit = null;
            for (WallStation w : wall) if (Math.abs(w.stand.x - r.x) < 0.6 && Math.abs(w.stand.z - r.z) < 0.6 && Math.abs(w.stand.y - r.y) < 2.0) { hit = w; break; }
            out.add(hit);
        }
        if (out.contains(null) || new HashSet<>(out).size() != out.size()) {
            diff.note(run.t(), "camp", "the recording's heads do not all match ADS's wall: ADS draws its own order");
            return null;
        }
        return out;
    }

    private Map<Integer, Integer> campMobs = null;

    /** The recording's camp mobs, each with the index of the head it came down with. */
    private Map<Integer, Integer> campMobs() {
        if (campMobs != null) return campMobs;
        Integer door = bloodDoorK();
        if (door == null) { campMobs = Map.of(); return campMobs; }
        List<Integer> lands = new ArrayList<>();
        for (Throw t : recordedThrows()) lands.add(door + t.land);
        HashSet<Integer> seen = new HashSet<>();
        HashMap<Integer, Integer> out = new HashMap<>();
        for (JsonArray e : file.events) {
            if (e.size() < 4 || !e.get(1).getAsString().equals("spawn") || !seen.add(e.get(2).getAsInt()) || e.get(3).getAsString().equals("minecraft:armor_stand")) continue;
            int k = e.get(0).getAsInt();
            int i = -1;
            for (int q = 0; q < lands.size(); q++) { int d = k - lands.get(q); if (d >= -1 && d <= 3) { i = q; break; } }
            if (i >= 0) out.put(e.get(2).getAsInt(), i);
        }
        campMobs = out;
        return campMobs;
    }

    /** The recording's camp for the Watcher: its throw order and his lines, in ticks after the BLOOD door. */
    private WatcherCamp.Replayed recordedCampOf(List<WallStation> wall) {
        Integer door = bloodDoorK();
        if (door == null) return null;
        List<Pair<CampMech.Request, String>> said = new ArrayList<>();
        for (JsonArray e : file.events) {
            if (e.size() < 3 || !e.get(1).getAsString().equals("chat") || !e.get(2).isJsonPrimitive() || e.get(0).getAsInt() < door) continue;
            String t = e.get(2).getAsString();
            CampMech.Line cl = CampMech.lineOf(t);
            if (cl == null) continue;
            String text = t.startsWith("[BOSS] The Watcher: ") ? t.substring("[BOSS] The Watcher: ".length()) : t;
            if (CampLines.INTRO_OTHER_MORE.contains(text)) continue;
            said.add(Pair.of(new CampMech.Request(e.get(0).getAsInt() - door, cl), text));
        }
        String opening = null;
        for (Pair<CampMech.Request, String> p : said) if (p.first.line == CampMech.Line.INTRO) { opening = p.second; break; }
        List<CampMech.Request> reqs = new ArrayList<>();
        for (Pair<CampMech.Request, String> p : said) reqs.add(p.first);
        List<String> texts = new ArrayList<>();
        for (Pair<CampMech.Request, String> p : said) if (p.first.line != CampMech.Line.INTRO) texts.add(p.second);
        return new WatcherCamp.Replayed(recordedOrder(wall), CampMech.requestsOf(reqs), opening, texts);
    }

    private final HashSet<UUID> campSeen = new HashSet<>();
    private boolean campSeeded = false;
    /** ADS's camp mobs in the order they came down. */
    private final List<UUID> adsCamp = new ArrayList<>();

    /** ADS's camp spawns for ReplayDiff.campAds: each mob first seen in the Blood room after ADS's BLOOD door. */
    private void watchCamp(int now) {
        Integer open = run.state.milestones.bloodOpenT;
        if (open == null) return;
        PlacedRoom blood = null;
        for (PlacedRoom r : run.plan.rooms) if (r.type == RoomType.BLOOD) { blood = r; break; }
        if (blood == null) return;
        // What is there on the first look (the Watcher, if he is a mob) is not a spawn.
        for (MobRecord m : Services.mobs().inRoom(blood.index)) if (campSeen.add(m.id) && campSeeded) { diff.campAds.add(now - open); adsCamp.add(m.id); }
        campSeeded = true;
    }

    /** Your rank bracket and name colour before the replay. */
    private Pair<Object, Object> ownLooks = null;

    private boolean recordedBoostDone = false;
    private Boolean recordedBoost = null;

    /** Whether the recording's day had Hypixel's blessing boost, from its grant lines; null when it has none. */
    private Boolean recordedBoost() {
        if (recordedBoostDone) return recordedBoost;
        Boolean r = null;
        for (JsonArray e : file.events) if (isChat(e)) {
            String t = ReplayFileKt.trim(clean(e.get(2).getAsString()));
            if (!t.startsWith("Granted you")) continue;
            if (BOOSTED_GRANT.matcher(t).find()) { r = true; break; }
            if (PLAIN_GRANT.matcher(t).find()) r = false;
        }
        recordedBoost = r;
        recordedBoostDone = true;
        return recordedBoost;
    }

    private boolean princeKillerDone = false;
    private UUID princeKiller = null;

    /** Who killed the Prince: you when Hypixel gave you its line, else a teammate (the one whose mod posted "Prince Killed!", or any). */
    private UUID princeKiller() {
        if (princeKillerDone) return princeKiller;
        UUID res = null;
        boolean found = false;
        String poster = null;
        for (JsonArray e : file.events) if (isChat(e)) {
            String t = clean(e.get(2).getAsString());
            if (t.startsWith("A Prince falls")) { res = host.getUUID(); found = true; break; }
            if (poster == null && t.endsWith(": Prince Killed!")) {
                String head = t.substring(0, t.indexOf(':'));
                poster = head.substring(head.lastIndexOf(' ') + 1);
            }
        }
        if (!found) {
            res = uuidOf(poster);
            if (res == null) for (UUID u : run.state.players.keySet()) if (!u.equals(host.getUUID())) { res = u; break; }
        }
        princeKiller = res;
        princeKillerDone = true;
        return princeKiller;
    }

    private Map<String, String> ranks = null;

    /** Each name's rank in the recording's chat. */
    private Map<String, String> ranks() {
        if (ranks != null) return ranks;
        HashMap<String, String> out = new HashMap<>();
        for (JsonArray e : file.events) if (isChat(e)) {
            Matcher m = RANKED.matcher(clean(e.get(2).getAsString()));
            if (m.find()) out.putIfAbsent(gv(m, 2), gv(m, 1));
        }
        ranks = out;
        return ranks;
    }

    private void applyRank(String name, PlayerRecord r) {
        String rank = ranks().get(name);
        if (rank == null) return;
        Pair<String, String> looks = RANK_LOOKS.get(rank);
        if (looks == null) return;
        r.extras.put("rankBracket", looks.first);
        r.extras.put("nameColour", looks.second);
    }

    public void start() {
        recordedScale();
        List<Integer> lands = new ArrayList<>();
        for (Throw t : recordedThrows()) lands.add(t.land);
        diff.campHypixel = lands;
        WatcherCamp.replay = wall -> !ended ? recordedCampOf(wall) : null;
        StateController.keysFromRecording = true;
        SecretsSettings.replayBoost = recordedBoost();
        MobsModule.doorScale = (r, room) -> {
            if (r != run || ended) return null;
            Pair<Integer, Double> p = recordedScale().get(room);
            return p != null ? p.second : null;
        };
        // Mobs go where the recording has them while it sees them (out of its sight, ADS moves them).
        MobsModule.replayPose = id -> {
            if (ended) return null;
            Integer rid = plannedBack.get(id);
            RecEnt re = rid != null ? ents.get(rid) : null;
            if (re == null || re.gone || (re.x == 0.0 && re.z == 0.0)) return null;
            return new double[]{re.x, re.y, re.z};
        };
        DamageModule.replayHit = (mob, by) -> hitAmount(mob, by);
        // The recorded player's pet: Kill Combo lines only if the recording has them.
        boolean combo = false;
        for (JsonArray e : file.events) if (isChat(e) && e.get(2).getAsString().contains("Kill Combo")) { combo = true; break; }
        DamageModule.killCombo = combo;
        SecretsModule.teamFromRecording = true;
        SecretsModule.dropsFromRecording = true;
        // You go by the recording's name in the party's lines.
        PlayerRecord me = run.state.players.get(host.getUUID());
        if (me != null) {
            ownName = me.name;
            me.name = file.self.name;
            ownLooks = Pair.of(me.extras.get("rankBracket"), me.extras.get("nameColour"));
            applyRank(file.self.name, me);
        }
        // The Watcher opens his camp the way he did in the recording.
        JsonArray intro = null;
        for (JsonArray e : file.events) {
            if (isChat(e)) {
                String s = e.get(2).getAsString();
                if (s.contains("The Watcher: ") && (s.contains(CampLines.INTRO) || s.contains(CampLines.INTRO_OTHER))) { intro = e; break; }
            }
        }
        HashMap<CampMech.Line, ArrayDeque<String>> script = new HashMap<>();
        for (JsonArray e : file.events) if (isChat(e)) {
            String c = clean(e.get(2).getAsString());
            String pre = "[BOSS] The Watcher: ";
            int at = c.indexOf(pre);
            String t = at < 0 ? "" : c.substring(at + pre.length());
            if (t.isEmpty()) continue;
            CampMech.Line cl = CampLines.classOf(t);
            if (cl != null) script.computeIfAbsent(cl, q -> new ArrayDeque<>()).addLast(t);
        }
        CampLines.script = script;
        if (intro != null) {
            String it = intro.get(2).getAsString();
            CampLines.forcedOpening = it.contains(CampLines.INTRO_OTHER) ? CampLines.INTRO_OTHER : CampLines.INTRO;
        } else CampLines.forcedOpening = null;
        TrapsModule.recordingMovesCrushers = true;
        MobsModule.roomMobs = (r, room) -> r == run && !ended ? roomPlans().get(room) : null;
        BlazePuzzle.replayBlazes = r -> r == run && !ended ? recordedBlazes() : null;
        // The teammates are party members (tab, leap window, key pickups by position, the score's deaths).
        for (ReplayBodies.Mate m : bodies.all()) {
            PlayerRecord rec = run.state.players.computeIfAbsent(m.uuid, u -> new PlayerRecord(m.uuid, m.name, cls(m.cls), true));
            rec.dungeonClass = cls(m.cls);
            rec.extras.put(MATE, true);
            // Their rank as the recording's lines show it, for ADS's lines about them.
            applyRank(m.name, rec);
        }
        SimNet.send(host, NET, "start " + file.id);
        diff.note(run.t(), "start", "replay " + file.id + " (" + file.floor + ") as " + file.self.cls + "; " + file.party.size() + " teammates; recorded k " + file.firstK + ".." + file.lastK
                + ", boss at " + file.bossK());
    }

    public void stop(String why) {
        if (ended) return;
        ended = true;
        TrapsModule.recordingMovesCrushers = false;
        SecretsModule.teamFromRecording = false;
        SecretsModule.dropsFromRecording = false;
        if (ownName != null) {
            PlayerRecord r = run.state.players.get(host.getUUID());
            if (r != null) r.name = ownName;
        }
        if (ownLooks != null) {
            PlayerRecord r = run.state.players.get(host.getUUID());
            if (r != null) {
                Map<String, Object> x = r.extras;
                Object b = ownLooks.first;
                Object c = ownLooks.second;
                if (b == null) x.remove("rankBracket"); else x.put("rankBracket", b);
                if (c == null) x.remove("nameColour"); else x.put("nameColour", c);
            }
        }
        CampLines.forcedOpening = null;
        CampLines.script = null;
        MobsModule.roomMobs = null;
        BlazePuzzle.replayBlazes = null;
        MobsModule.doorScale = null;
        DamageModule.replayHit = null;
        MobsModule.replayPose = null;
        WatcherCamp.replay = null;
        StateController.keysFromRecording = false;
        SecretsSettings.replayBoost = null;
        DamageModule.killCombo = true;
        bodies.removeAll();
        for (ReplayBodies.Mate m : bodies.all()) {
            PlayerRecord r = run.state.players.get(m.uuid);
            if (r != null) r.extras.remove(MATE);
        }
        SimNet.send(host, NET, "stop");
        diff.note(run.t(), "stop", why);
        diff.ended(why, run.t(), file.bossK(), file.lastK, why.startsWith("the recording reached") || why.equals("ADS reached the boss"));
        diff.write();
    }

    // ---- the tick

    public void tick() {
        if (ended) return;
        if (host.isRemoved()) { stop("the player left"); return; }
        int now = run.t();
        if (lastK == Integer.MIN_VALUE) {
            // The first tick: everything before it only sets state, no actions.
            for (JsonArray e : file.events) {
                int k = e.get(0).getAsInt();
                if (k >= now) break;
                apply(e, true);
            }
            lastK = now - 1;
        }
        // ADS moving you between ticks (a teleport of its own), against Hypixel's.
        if (lastHost != null && teleported(lastHost)) diff.adsTeleport(now, host.position());
        while (lastK < now) {
            lastK++;
            JsonArray pose = null;
            List<JsonArray> evs = file.at(lastK);
            for (int i = 0; i < evs.size(); i++) {
                JsonArray e = evs.get(i);
                if (e.get(1).getAsString().equals("me")) { pose = e; continue; }
                int at = i;
                Ec.safely("ads replay " + e.get(1).getAsString(), () -> apply(e, false, evs, at));
            }
            if (pose != null) {
                sendPose(pose);
                recPos = new Vec3(pose.get(2).getAsDouble(), pose.get(3).getAsDouble(), pose.get(4).getAsDouble());
            }
        }
        lastHost = host.position();
        // The teammates' party records follow their bodies.
        for (ReplayBodies.Mate m : bodies.all()) {
            PlayerRecord r = run.state.players.get(m.uuid);
            if (r != null && !Double.isNaN(m.x)) { r.x = m.x; r.y = m.y; r.z = m.z; r.extras.put("yaw", m.yaw); }
        }
        bodies.place();
        if (invDirty) syncInv();
        if (!hpChecks.isEmpty()) {
            List<Triple<Integer, Integer, Double>> dueHp = new ArrayList<>();
            for (Triple<Integer, Integer, Double> c : hpChecks) if (c.first <= now) dueHp.add(c);
            hpChecks.removeAll(dueHp);
            for (Triple<Integer, Integer, Double> c : dueHp) {
                int id = c.second;
                String label = null;
                for (ReplayFile.Mob m : file.mobs) if (m.id == id) { label = m.tag != null ? mobName(m.tag) : null; break; }
                UUID pid = planned.get(id);
                MobRecord mr = pid != null ? Services.mobs().mob(pid) : null;
                diff.hpScale(c.first - HP_CHECK_AFTER, label != null ? label : "mob " + id, c.third, mr != null ? mr.maxHp : null);
            }
        }
        if (!bloodOwed.isEmpty()) Ec.safely("ads replay camp", this::payBlood);
        watchCamp(now);
        if (witherDebt > 0 || bloodDebt) repayKeys();
        List<Pending> due = new ArrayList<>();
        for (Pending p : pending) if (p.due <= now) due.add(p);
        pending.removeAll(due);
        for (Pending p : due) Ec.safely("ads replay force", () -> {
            if (p.done.getAsBoolean()) diff.note(now, "ok", p.what);
            else {
                String r = p.force.get();
                diff.forced(now, p.what, r);
            }
        });
        selfDeaths(now);
        diff.tick(now);
        Integer bossK = file.bossK();
        if (now > file.lastK || (bossK != null && now >= bossK)) stop("the recording reached " + (now > file.lastK ? "its end" : "the boss"));
    }

    private void sendPose(JsonArray e) {
        // x y z yaw pitch slot flags
        Integer slot = ReplayFileKt.i(e, 7);
        Integer flags = ReplayFileKt.i(e, 8);
        SimNet.send(host, NET, "p " + ReplayFileKt.d(e, 2) + " " + ReplayFileKt.d(e, 3) + " " + ReplayFileKt.d(e, 4) + " " + ReplayFileKt.d(e, 5) + " " + ReplayFileKt.d(e, 6)
                + " " + (slot != null ? slot : -1) + " " + (flags != null ? flags : 0));
    }

    // ---- events

    private void apply(JsonArray e, boolean catchUp) { apply(e, catchUp, List.of(), 0); }

    private void apply(JsonArray e, boolean catchUp, List<JsonArray> same, int at) {
        int k = e.get(0).getAsInt();
        switch (e.get(1).getAsString()) {
            // ---- recorded entities
            case "spawn" -> {
                Integer id = ReplayFileKt.i(e, 2);
                if (id == null) return;
                Double x = ReplayFileKt.d(e, 5), y = ReplayFileKt.d(e, 6), z = ReplayFileKt.d(e, 7);
                RecEnt r = new RecEnt(id, ReplayFileKt.s(e, 3), x != null ? x : 0.0, y != null ? y : 0.0, z != null ? z : 0.0, ReplayFileKt.s(e, 8));
                ents.put(id, r);
                ReplayBodies.Mate m = mate(id);
                if (m != null) {
                    m.x = r.x; m.y = r.y; m.z = r.z;
                    Double yw = ReplayFileKt.d(e, 9);
                    m.yaw = yw != null ? yw.floatValue() : m.yaw;
                    m.present = true;
                }
            }
            case "hp" -> {
                Double v = ReplayFileKt.d(e, 3);
                if (v == null || !(v > 100.0)) return;
                Integer id = ReplayFileKt.i(e, 2);
                if (id == null) return;
                Double l = liveHp.put(id, v);
                if (!catchUp && k > 0 && l != null && v / l > 1.04 && planned.containsKey(id)) hpChecks.add(new Triple<>(k + HP_CHECK_AFTER, id, v));
            }
            case "emv" -> {
                RecEnt r = ents.get(ReplayFileKt.i(e, 2));
                if (r != null) {
                    Double x = ReplayFileKt.d(e, 3); if (x != null) r.x = x;
                    Double y = ReplayFileKt.d(e, 4); if (y != null) r.y = y;
                    Double z = ReplayFileKt.d(e, 5); if (z != null) r.z = z;
                }
            }
            case "name" -> {
                RecEnt r = ents.get(ReplayFileKt.i(e, 2));
                if (r != null) {
                    r.name = ReplayFileKt.s(e, 3);
                    String n = clean(r.name != null ? r.name : "");
                    if (!catchUp && (n.equals("Wither Key") || n.equals("Blood Key"))) { diff.recordedKeyStand(k, n, r.x, r.y, r.z); keyStand(r.x, r.z); }
                }
            }
            case "gone", "rm" -> {
                Integer id = ReplayFileKt.i(e, 2);
                if (id == null) return;
                RecEnt r = ents.get(id);
                if (r != null) r.gone = true;
                ReplayBodies.Mate m = mate(id);
                if (m != null) m.present = false;
            }
            // ---- the teammates' bodies
            case "mmv" -> {
                ReplayBodies.Mate m = mate(ReplayFileKt.i(e, 2));
                if (m != null) {
                    Double x = ReplayFileKt.d(e, 3); if (x != null) m.x = x;
                    Double y = ReplayFileKt.d(e, 4); if (y != null) m.y = y;
                    Double z = ReplayFileKt.d(e, 5); if (z != null) m.z = z;
                    Double yw = ReplayFileKt.d(e, 6); if (yw != null) m.yaw = yw.floatValue();
                    Double pt = ReplayFileKt.d(e, 7); if (pt != null) m.pitch = pt.floatValue();
                    m.present = true;
                }
            }
            case "mrot" -> {
                ReplayBodies.Mate m = mate(ReplayFileKt.i(e, 2));
                if (m != null) {
                    Double yw = ReplayFileKt.d(e, 3); if (yw != null) m.yaw = yw.floatValue();
                    Double pt = ReplayFileKt.d(e, 4); if (pt != null) m.pitch = pt.floatValue();
                }
            }
            case "mhead" -> {
                ReplayBodies.Mate m = mate(ReplayFileKt.i(e, 2));
                if (m != null) { Double h = ReplayFileKt.d(e, 3); if (h != null) m.head = h.floatValue(); }
            }
            case "mflags" -> {
                ReplayBodies.Mate m = mate(ReplayFileKt.i(e, 2));
                if (m != null) { Integer f = ReplayFileKt.i(e, 3); m.flags = f != null ? f : 0; }
            }
            case "manim" -> {
                if (!catchUp) {
                    ReplayBodies.Mate m = mate(ReplayFileKt.i(e, 2));
                    if (m != null && Integer.valueOf(0).equals(ReplayFileKt.i(e, 3))) m.swing = true;
                }
            }
            case "eq" -> {
                ReplayBodies.Mate m = mate(ReplayFileKt.i(e, 2));
                if (m != null) {
                    EquipmentSlot s = ReplayBodies.slot(ReplayFileKt.s(e, 3));
                    if (s != null) m.held.put(s, ReplayBodies.stack(ReplayFileKt.s(e, 4), ReplayFileKt.s(e, 6)));
                }
            }
            // ---- your inventory
            case "inv" -> inv(e);
            // ---- what the recording says happened (forced when ADS does not)
            case "eev" -> {
                if (!catchUp && Integer.valueOf(3).equals(ReplayFileKt.i(e, 3))) {
                    Integer id = ReplayFileKt.i(e, 2);
                    if (id == null) return;
                    mobDied(k, id, ReplayFileKt.o(e, 4));
                }
            }
            case "chat" -> {
                if (!catchUp) {
                    String t = ReplayFileKt.s(e, 2);
                    if (t == null) return;
                    chat(k, t);
                }
            }
            case "room" -> { if (!catchUp) room(k, e); }
            case "blk" -> {
                if (!catchUp) {
                    Integer xi = ReplayFileKt.i(e, 2);
                    if (xi == null) return;
                    Integer yi = ReplayFileKt.i(e, 3);
                    if (yi == null) return;
                    Integer zi = ReplayFileKt.i(e, 4);
                    if (zi == null) return;
                    int x = xi, y = yi, z = zi;
                    double you = host.position().distanceTo(new Vec3(x + 0.5, y + 0.5, z + 0.5));
                    Double best = null;
                    for (ReplayBodies.Mate m : bodies.all()) {
                        if (Double.isNaN(m.x) || !m.present) continue;
                        double dx = m.x - x - 0.5, dy = m.y - y, dz = m.z - z - 0.5;
                        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (best == null || d < best) best = d;
                    }
                    double mate = best != null ? best : 999.0;
                    // A crusher's blocks are the recording's, on their own tick.
                    if (TrapsModule.isCrusherCell(x, y, z)) diff.setNow(x, y, z, ReplayFileKt.s(e, 5));
                    // Away from you it is a teammate's doing or the room's: put in on its own tick.
                    else if (k > 1 && you > ReplayDiff.NEAR) diff.awayBlock(k, x, y, z, ReplayFileKt.s(e, 5), you, mate);
                    else diff.recordedBlock(k, x, y, z, ReplayFileKt.s(e, 5), you, mate);
                }
            }
            case "screen" -> { if (!catchUp && "inventory".equals(ReplayFileKt.s(e, 3))) SimNet.send(host, NET, "inv 1"); }
            // Your health as Hypixel's action bar showed it, every half second or so.
            case "bar" -> {
                if (!catchUp) {
                    String b = ReplayFileKt.s(e, 2);
                    Matcher m = HEALTH.matcher(b != null ? b : "");
                    if (m.find()) {
                        Double cur = toDoubleOrNull(gv(m, 1).replace(",", ""));
                        Double max = toDoubleOrNull(gv(m, 2).replace(",", ""));
                        if (cur != null && max != null && max > 0.0) {
                            PlayerRecord r = run.state.players.get(host.getUUID());
                            if (r != null && !r.ghost) r.hpPercent = Math.clamp(cur / max * 100.0, 1.0, 100.0);
                        }
                    }
                }
            }
            case "screenClose" -> { if (!catchUp && host.containerMenu == host.inventoryMenu) SimNet.send(host, NET, "inv 0"); }
            case "tpme" -> { if (!catchUp) diff.recordedTeleport(k, e, recPos); }
            case "dung" -> {
                diff.recordedDung(ReplayFileKt.o(e, 2));
                if (!catchUp) team(k, ReplayFileKt.o(e, 2));
            }
            // ---- what you sent
            default -> { if (!catchUp && e.get(1).getAsString().startsWith("o.")) self(e, same, at); }
        }
    }

    private ReplayBodies.Mate mate(Integer id) {
        if (id == null) return null;
        String n = mateIdName.get(id);
        return n != null ? mateByName.get(n) : null;
    }

    // ---- you

    private void self(JsonArray e, List<JsonArray> same, int at) {
        if (!run.stage.inDungeon()) return;
        ServerGamePacketListenerImpl c = host.connection;
        Vec3 before = host.position();
        String kind = e.get(1).getAsString();
        if (kind.equals("o.use") || kind.equals("o.useOn") || kind.equals("o.slot")) {
            String held = SimItems.idOf(host.getMainHandItem());
            String s2 = ReplayFileKt.s(e, 2);
            diff.action(e.get(0).getAsInt(), String.format(Locale.ROOT, "%-8s %s held %s (slot %d) at %s", kind, s2 != null ? s2 : "",
                    held != null ? held : host.getMainHandItem().getItem().toString(), host.getInventory().getSelectedSlot(), fmt(host.getX(), host.getY(), host.getZ())));
        }
        injecting = this;
        try { selfPacket(e, same, at, c); } finally { injecting = null; }
        if (!e.get(1).getAsString().equals("o.m") && teleported(before)) diff.adsTeleport(e.get(0).getAsInt(), host.position());
    }

    private static boolean is1(Integer v) { return v != null && v == 1; }

    private void selfPacket(JsonArray e, List<JsonArray> same, int at, ServerGamePacketListenerImpl c) {
        switch (e.get(1).getAsString()) {
            case "o.m" -> {
                Double x = ReplayFileKt.d(e, 2), y = ReplayFileKt.d(e, 3), z = ReplayFileKt.d(e, 4);
                Double yawD = ReplayFileKt.d(e, 5), pitchD = ReplayFileKt.d(e, 6);
                Float yaw = yawD != null ? yawD.floatValue() : null;
                Float pitch = pitchD != null ? pitchD.floatValue() : null;
                boolean ground = is1(ReplayFileKt.i(e, 7));
                boolean hcol = is1(ReplayFileKt.i(e, 8));
                ServerboundMovePlayerPacket pk;
                if (x != null && yaw != null) pk = new ServerboundMovePlayerPacket.PosRot(x, y, z, yaw, pitch, ground, hcol);
                else if (x != null) pk = new ServerboundMovePlayerPacket.Pos(x, y, z, ground, hcol);
                else if (yaw != null) pk = new ServerboundMovePlayerPacket.Rot(yaw, pitch, ground, hcol);
                else pk = new ServerboundMovePlayerPacket.StatusOnly(ground, hcol);
                // A teleport of ADS's is accepted first, as the recorded client did within the tick.
                ServerTeleportAccessor acc = (ServerTeleportAccessor) c;
                Vec3 ap = acc.ec_getAwaitingPosition();
                if (ap != null) c.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(acc.ec_getAwaitingTeleport(), ap.x, ap.y, ap.z, c.player.getYRot(), c.player.getXRot()));
                c.handleMovePlayer(pk);
                if (x != null) diff.moved(e.get(0).getAsInt(), x, y, z, host);
            }
            case "o.slot" -> {
                Integer s = ReplayFileKt.i(e, 2);
                if (s == null) return;
                c.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(s));
            }
            case "o.use" -> {
                Double yw = ReplayFileKt.d(e, 3), pt = ReplayFileKt.d(e, 4);
                c.handleUseItem(new ServerboundUseItemPacket(hand(ReplayFileKt.s(e, 2)), seq++, yw != null ? yw.floatValue() : host.getYRot(), pt != null ? pt.floatValue() : host.getXRot()));
            }
            case "o.useOn" -> {
                JsonArray pa = e.get(3).getAsJsonArray();
                BlockPos pos = new BlockPos(pa.get(0).getAsInt(), pa.get(1).getAsInt(), pa.get(2).getAsInt());
                String ds = ReplayFileKt.s(e, 4);
                Direction dir = Direction.byName(ds != null ? ds.toLowerCase(Locale.ROOT) : "up");
                if (dir == null) dir = Direction.UP;
                JsonArray la = e.get(5).getAsJsonArray();
                Vec3 loc = new Vec3(la.get(0).getAsDouble(), la.get(1).getAsDouble(), la.get(2).getAsDouble());
                c.handleUseItemOn(new ServerboundUseItemOnPacket(hand(ReplayFileKt.s(e, 2)), new BlockHitResult(loc, dir, pos, is1(ReplayFileKt.i(e, 6))), seq++));
            }
            case "o.atk" -> {
                JsonObject b = ReplayFileKt.o(e, 2);
                if (b == null) return;
                Entity t = target(b);
                if (t == null) { diff.missingTarget(e.get(0).getAsInt(), "attack", ReplayFileKt.o(e, 2)); return; }
                c.handleAttack(new ServerboundAttackPacket(t.getId()));
            }
            case "o.int" -> {
                JsonObject b = ReplayFileKt.o(e, 2);
                if (b == null) return;
                Entity t = target(b);
                if (t == null) {
                    if ("MAIN_HAND".equals(ReplayFileKt.s(e, 3))) diff.missingTarget(e.get(0).getAsInt(), "interact", b);
                    return;
                }
                JsonElement le = e.get(4);
                Vec3 loc = Vec3.ZERO;
                if (le != null && le.isJsonArray()) { JsonArray la = le.getAsJsonArray(); loc = new Vec3(la.get(0).getAsDouble(), la.get(1).getAsDouble(), la.get(2).getAsDouble()); }
                c.handleInteract(new ServerboundInteractPacket(t.getId(), hand(ReplayFileKt.s(e, 3)), loc, is1(ReplayFileKt.i(e, 5))));
            }
            case "o.act" -> {
                ServerboundPlayerActionPacket.Action action;
                try { action = ServerboundPlayerActionPacket.Action.valueOf(ReplayFileKt.s(e, 2)); } catch (Throwable t) { action = null; }
                if (action == null) return;
                JsonElement pe = e.get(3);
                BlockPos pos = BlockPos.ZERO;
                if (pe != null && pe.isJsonArray()) { JsonArray pa = pe.getAsJsonArray(); pos = new BlockPos(pa.get(0).getAsInt(), pa.get(1).getAsInt(), pa.get(2).getAsInt()); }
                String ds = ReplayFileKt.s(e, 4);
                Direction dir = Direction.byName(ds != null ? ds.toLowerCase(Locale.ROOT) : "down");
                if (dir == null) dir = Direction.DOWN;
                BlockPos fp = pos;
                Direction fd = dir;
                ServerboundPlayerActionPacket.Action fa = action;
                switch (action) {
                    // The sim's own block hit (an adventure-mode player's start_destroy never reaches it): the Dungeonbreaker, levers, Superboom.
                    case START_DESTROY_BLOCK -> Sim.asPlayer(host, () -> SimItems.serverHit(host, fp.getX() + "," + fp.getY() + "," + fp.getZ() + "," + fd.ordinal() + ",0"));
                    case DROP_ITEM, DROP_ALL_ITEMS -> Sim.asPlayer(host, () -> SimItems.serverDrop(host, fa == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS));
                    default -> {
                        Integer sq = ReplayFileKt.i(e, 5);
                        c.handlePlayerAction(new ServerboundPlayerActionPacket(action, pos, dir, sq != null ? sq : seq++));
                    }
                }
            }
            case "o.swing" -> {
                c.handlePunch(ServerboundPunchPacket.INSTANCE);
                // A swing that follows a right click in the same tick is that click's; any other is a left click.
                boolean fromUse = false;
                for (JsonArray o : same.subList(0, at)) if (RIGHT_CLICKS.contains(o.get(1).getAsString())) { fromUse = true; break; }
                if (!fromUse) Sim.asPlayer(host, () -> {
                    String id = SimItems.idOf(host.getMainHandItem());
                    SimItems.serverLeftClick(host, id != null ? id : "");
                });
            }
            case "o.cmd" -> {
                ServerboundPlayerCommandPacket.Action a;
                try { a = ServerboundPlayerCommandPacket.Action.valueOf(ReplayFileKt.s(e, 2)); } catch (Throwable t) { a = null; }
                if (a != null) c.handlePlayerCommand(new ServerboundPlayerCommandPacket(host, a));
            }
            case "o.input" -> {
                String s = ReplayFileKt.s(e, 2);
                if (s == null) return;
                boolean[] b = new boolean[s.length()];
                for (int i = 0; i < s.length(); i++) b[i] = s.charAt(i) == '1';
                if (b.length >= 7) c.handlePlayerInput(new ServerboundPlayerInputPacket(new Input(b[0], b[1], b[2], b[3], b[4], b[5], b[6])));
            }
            case "o.click" -> click(e);
            case "o.close" -> { if (host.containerMenu != host.inventoryMenu) host.closeContainer(); }
            default -> {}
        }
    }

    private static InteractionHand hand(String s) { return "OFF_HAND".equals(s) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND; }

    /**
     * A click in a window: the open window's slot holding the item of that name (the recorded slot number when it holds nothing named).
     * Clicks in your own inventory are not replayed: the recorded inventory is set as it went.
     */
    private void click(JsonArray e) {
        AbstractContainerMenu menu = host.containerMenu;
        if (menu == host.inventoryMenu) return;
        String s6 = ReplayFileKt.s(e, 6);
        String name = s6 != null ? ReplayFileKt.trim(s6) : null;
        Integer slot = ReplayFileKt.i(e, 3);
        if (slot == null) return;
        Integer byName = null;
        if (name != null && !name.isEmpty()) {
            for (int i = 0; i < menu.slots.size(); i++) {
                net.minecraft.world.inventory.Slot s = menu.slots.get(i);
                if (s.hasItem() && clean(s.getItem().getHoverName().getString()).equals(name)) { byName = i; break; }
            }
        }
        int use = byName != null ? byName : slot;
        if (byName == null && name != null) diff.note(e.get(0).getAsInt(), "click", "no \"" + name + "\" in ADS's " + host.containerMenu.getClass().getSimpleName() + "; clicked slot " + slot);
        if (use < 0 || use >= menu.slots.size()) return;
        // A leap to a teammate the recording could not see: they were where Hypixel put you.
        ReplayBodies.Mate m = name != null ? mateByName.get(name) : null;
        if (m != null) {
            Vec3 landing = leapLanding(e.get(0).getAsInt());
            if (landing != null) {
                m.x = landing.x; m.y = landing.y; m.z = landing.z;
                PlayerRecord r = run.state.players.get(m.uuid);
                if (r != null) { r.x = landing.x; r.y = landing.y; r.z = landing.z; }
            }
        }
        ContainerInput input;
        try { input = ContainerInput.valueOf(ReplayFileKt.s(e, 5)); } catch (Throwable t) { input = null; }
        if (input == null) input = ContainerInput.PICKUP;
        Integer btn = ReplayFileKt.i(e, 4);
        host.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), (short) use, (byte) (int) (btn != null ? btn : 0), input,
                new Int2ObjectOpenHashMap<>(), HashedStack.EMPTY));
    }

    /** Where Hypixel put you within a few ticks after k (a leap's landing), or null. */
    private Vec3 leapLanding(int k) {
        for (int kk = k; kk <= k + 15; kk++) {
            JsonArray tp = null;
            for (JsonArray a : file.at(kk)) if (a.get(1).getAsString().equals("tpme")) { tp = a; break; }
            if (tp == null) continue;
            JsonElement p = tp.get(2);
            if (p == null || !p.isJsonArray()) continue;
            JsonArray v = p.getAsJsonArray();
            return new Vec3(v.get(0).getAsDouble(), v.get(1).getAsDouble(), v.get(2).getAsDouble());
        }
        return null;
    }

    /** ADS's entity for a recorded one: a teammate's body, else the nearest living thing within 6 blocks of where the recorded one was. */
    private Entity target(JsonObject b) {
        JsonElement me = b.get("mate");
        if (me != null && me.isJsonPrimitive()) {
            ReplayBodies.Mate m = mateByName.get(me.getAsString());
            return m != null && m.body != null && !m.body.isRemoved() ? m.body : null;
        }
        JsonElement xe = b.get("x");
        if (xe == null || !xe.isJsonPrimitive()) return null;
        double x = xe.getAsDouble();
        double y = b.get("y").getAsDouble();
        double z = b.get("z").getAsDouble();
        JsonElement te = b.get("type");
        String type = te != null && te.isJsonPrimitive() ? te.getAsString() : null;
        JsonElement tg = b.get("tag");
        String tag = tg != null && tg.isJsonPrimitive() ? clean(tg.getAsString()) : null;
        AABB box = new AABB(x - 6, y - 4, z - 6, x + 6, y + 6, z + 6);
        // Never an item, an orb or an arrow: the server drops a player who attacks one.
        List<Entity> all = Sim.level().getEntities((Entity) null, box, it -> it != host && it.isAlive() && !(it instanceof ServerPlayer) && !(it instanceof ItemEntity)
                && !(it instanceof ExperienceOrb) && !(it instanceof AbstractArrow));
        Entity best = null;
        double bestD = 0;
        Vec3 at = new Vec3(x, y, z);
        for (Entity en : all) {
            double d = en.position().distanceTo(at);
            String et = BuiltInRegistries.ENTITY_TYPE.getKey(en.getType()).toString();
            if (!et.equals(type)) d += 4;
            if (en instanceof ArmorStand && tag != null) {
                String cn = en.getCustomName() != null ? en.getCustomName().getString() : "";
                if (!clean(cn).equals(tag)) d += 3;
            }
            if (!(en instanceof LivingEntity) && !(en instanceof ArmorStand)) d += 2;
            if (best == null || d < bestD) { best = en; bestD = d; }
        }
        return best;
    }

    // ---- inventory

    private void inv(JsonArray e) {
        JsonElement ae = e.get(3);
        if (ae == null || !ae.isJsonArray()) return;
        for (JsonElement s : ae.getAsJsonArray()) {
            JsonArray a = s.getAsJsonArray();
            int slot = a.get(0).getAsInt();
            if (!(slot >= 0 && slot <= 8) && !(slot >= 36 && slot <= 40)) continue;
            JsonElement oe = a.get(1);
            JsonObject o = oe != null && oe.isJsonObject() ? oe.getAsJsonObject() : null;
            wantInv.put(slot, o == null ? ItemStack.EMPTY : stackOf(o));
        }
        invDirty = true;
    }

    /** A recorded stack as the sim knows it: the recorded item with the SkyBlock id the sim's item handlers read. */
    private ItemStack stackOf(JsonObject o) {
        JsonElement sbe = o.get("sb");
        String sb = sbe != null && sbe.isJsonPrimitive() ? sbe.getAsString() : null;
        ItemStack st = null;
        JsonElement full = o.get("full");
        if (full != null && full.isJsonObject()) {
            try {
                st = ItemStack.CODEC.parse(Sim.level().registryAccess().createSerializationContext(JsonOps.INSTANCE), full).result().orElse(null);
            } catch (Throwable t) { st = null; }
        }
        if (st == null) {
            JsonElement ide = o.get("id");
            JsonElement ne = o.get("name");
            st = ReplayBodies.stack(ide != null ? ide.getAsString() : null, ne != null && ne.isJsonPrimitive() ? ne.getAsString() : null);
            JsonElement ce = o.get("count");
            if (ce != null) st.setCount(ce.getAsInt());
        }
        if (sb != null && !st.isEmpty()) CustomData.update(DataComponents.CUSTOM_DATA, st, t -> t.putString("id", sb));
        return st;
    }

    private static EquipmentSlot armourSlot(int slot) {
        return switch (slot) {
            case 36 -> EquipmentSlot.FEET;
            case 37 -> EquipmentSlot.LEGS;
            case 38 -> EquipmentSlot.CHEST;
            case 39 -> EquipmentSlot.HEAD;
            default -> EquipmentSlot.OFFHAND;
        };
    }

    private void syncInv() {
        invDirty = false;
        Inventory inv = host.getInventory();
        for (Map.Entry<Integer, ItemStack> en : wantInv.entrySet()) {
            int slot = en.getKey();
            ItemStack want = en.getValue();
            ItemStack have = slot <= 35 ? inv.getItem(slot) : host.getItemBySlot(armourSlot(slot));
            if (Objects.equals(SimItems.idOf(have), SimItems.idOf(want)) && have.getCount() == want.getCount() && have.getItem() == want.getItem()) continue;
            if (slot <= 35) inv.setItem(slot, want.copy());
            else host.setItemSlot(armourSlot(slot), want.copy());
        }
        host.inventoryMenu.broadcastChanges();
    }

    // ---- teammates' doings

    /** ADS mobs already matched to a recorded death. */
    private final HashSet<UUID> claimed = new HashSet<>();
    private String bloodKeyBy = null;

    /** A recorded camp death: head is the index of the recorded head the mob came down with (-1: not known). */
    private static final class BloodDeath {
        final int k;
        final String label;
        final double x;
        final double z;
        final int room;
        final int head;

        BloodDeath(int k, String label, double x, double z, int room, int head) {
            this.k = k;
            this.label = label;
            this.x = x;
            this.z = z;
            this.room = room;
            this.head = head;
        }

        BloodDeath(int k, String label, double x, double z, int room) { this(k, label, x, z, room, -1); }
    }

    /** Recorded camp deaths ADS has no camp mob for yet. */
    private final ArrayDeque<BloodDeath> bloodOwed = new ArrayDeque<>();
    private BloodDeath lastBlood = null;
    /** The recorded heads whose mob has died. */
    private final HashSet<Integer> campDied = new HashSet<>();

    /** Kills ADS's camp mobs for the recorded camp deaths owed. */
    private void payBlood() {
        watchCamp(run.t());
        Iterator<BloodDeath> it = bloodOwed.iterator();
        while (it.hasNext()) {
            BloodDeath d = it.next();
            MobRecord mob;
            if (d.head >= 0) {
                UUID id = d.head < adsCamp.size() ? adsCamp.get(d.head) : null;
                if (id == null) continue;
                it.remove();
                MobRecord m = Services.mobs().mob(id);
                if (m == null || !m.alive) continue;
                mob = m;
            } else {
                MobRecord best = null;
                double bestD = 0;
                for (MobRecord m : Services.mobs().inRoom(d.room)) {
                    if (!m.alive || claimed.contains(m.id)) continue;
                    double v = Math.hypot(m.x - d.x, m.z - d.z) + (sameMob(m.name, d.label) ? 0.0 : 12.0);
                    if (best == null || v < bestD) { best = m; bestD = v; }
                }
                if (best == null) return;
                it.remove();
                mob = best;
            }
            claimed.add(mob.id);
            int now = run.t();
            forcing = true;
            try { Services.damage().hurtMob(run, mob.id, mob.hp + 1e12, DamageSource.OTHER, nearestMate(d.x, d.z)); } finally { forcing = false; }
            diff.forced(now, "camp: recorded " + d.label + " death (k " + d.k + ") on ADS's " + mob.name + (now > d.k ? ", " + (now - d.k) + " ticks late" : ""),
                    mob.alive ? "hurtMob left it alive" : "killed");
        }
    }

    /**
     * A recorded mob died: ADS's mob for it is the one of the same name nearest where it died, not matched before. Dead already in ADS:
     * fine. Alive: it gets KILL_GRACE ticks near you (none far from you), then it is killed for the teammate nearest it.
     */
    private void mobDied(int k, int id, JsonObject brief) {
        if (mateIdName.containsKey(id)) return;
        RecEnt r = ents.get(id);
        if (r == null) return;
        if (r.type == null || r.type.equals("minecraft:armor_stand")) return;
        double rx = r.x, ry = r.y, rz = r.z;
        JsonElement tagE = brief != null ? brief.get("tag") : null;
        String tag = tagE != null && tagE.isJsonPrimitive() ? mobName(tagE.getAsString()) : null;
        // The Watcher's camp names its mobs by itself, so a recorded camp death takes ADS's nearest live camp mob whatever its name.
        PlacedRoom br = run.plan.roomAtBlock(rx, rz);
        PlacedRoom blood = br != null && br.type == RoomType.BLOOD ? br : null;
        Integer landing = campMobs().get(id);
        if (blood != null && landing != null) {
            // One death per head (a rider's two bodies are one mob).
            if (!campDied.add(landing)) return;
            BloodDeath bd = new BloodDeath(k, tag != null ? tag : "camp mob", rx, rz, blood.index, landing);
            lastBlood = bd;
            bloodOwed.add(bd);
            payBlood();
            return;
        }
        if (r.type.equals("minecraft:blaze")) {
            Integer hp = null;
            if (tagE != null && tagE.isJsonPrimitive()) {
                Matcher bm = BLAZE_TAG.matcher(clean(tagE.getAsString()));
                if (bm.find()) hp = toIntOrNull(gv(bm, 1).replace(",", ""));
            }
            if (hp != null) { blazeDied(k, hp, rx, ry, rz); return; }
        }
        // Only Hypixel's mobs have a tag (a bare body is a part of one).
        if (tag == null || tag.isEmpty()) { if (!r.type.equals("minecraft:bat")) return; }
        String label = tag != null ? tag : "Bat";
        // Without the recording's throws, any tagged death in the Blood room is a camp death.
        if (blood != null && tag != null && recordedThrows().isEmpty()) {
            // One mob can die as two tagged bodies (a rider): the same name within a tick and two blocks is one death.
            BloodDeath lb = lastBlood;
            if (lb != null && lb.label.equals(label) && k - lb.k <= 1 && Math.hypot(lb.x - rx, lb.z - rz) < 2) return;
            BloodDeath bd = new BloodDeath(k, label, rx, rz, blood.index);
            lastBlood = bd;
            bloodOwed.add(bd);
            payBlood();
            return;
        }
        MobRecord mob = null;
        double bestV = 0;
        for (MobRecord m : Services.mobs().all()) {
            if (claimed.contains(m.id) || !(Math.abs(m.y - ry) < 8) || !(Math.hypot(m.x - rx, m.z - rz) < 14)) continue;
            double v = Math.hypot(m.x - rx, m.z - rz) + (sameMob(m.name, label) ? 0.0 : 12.0) + (m.alive ? 0.0 : 1.0);
            if (mob == null || v < bestV) { mob = m; bestV = v; }
        }
        if (mob != null && !(sameMob(mob.name, label) || Math.hypot(mob.x - rx, mob.z - rz) < 4)) mob = null;
        double you = Math.hypot(host.getX() - rx, host.getZ() - rz);
        if (mob == null) { diff.mobNotFound(k, label, rx, ry, rz, you); return; }
        claimed.add(mob.id);
        if (!sameMob(mob.name, label)) diff.note(k, "mob", "recorded " + label + " at " + fmt(rx, ry, rz) + " matched to ADS's " + mob.name + " (no " + label + " within 14)");
        if (!mob.alive) { diff.mobOk(); return; }
        UUID who = nearestMate(rx, rz);
        // Yours only when you hit or used something just before; else a teammate's, done on its tick.
        boolean yours = you <= FAR && youActed(k);
        MobRecord fm = mob;
        if (yours) diff.mobPending(k, label, you, String.format(Locale.ROOT, "ADS %s %,.0f/%,.0f hp%s, %.1f blocks from where it died", fm.name, fm.hp, fm.maxHp,
                Services.mobs().isAsleep(fm.id) ? " asleep" : "", Math.hypot(fm.x - rx, fm.z - rz)));
        // Far from you it was a teammate's kill: ADS cannot make it, so it is done on the tick.
        pending.add(new Pending(!yours ? k : k + KILL_GRACE, label + " killed (you " + String.format(Locale.ROOT, "%.0f", you) + " blocks away)", () -> !fm.alive, () -> {
            forcing = true;
            try { Services.damage().hurtMob(run, fm.id, fm.hp + 1e12, DamageSource.OTHER, who); } finally { forcing = false; }
            if (fm.alive) return "hurtMob left it alive";
            PlayerRecord wr = who != null ? run.state.players.get(who) : null;
            return "killed by " + (wr != null ? wr.name : "?");
        }));
    }

    /** A recorded Higher Or Lower blaze died: ADS's blaze of the same HP in that room. */
    private void blazeDied(int k, int hp, double rx, double ry, double rz) {
        String label = String.format(Locale.ROOT, "Blaze %,d", hp);
        double you = Math.hypot(host.getX() - rx, host.getZ() - rz);
        PlacedRoom room = run.plan.roomAtBlock(rx, rz);
        Puzzle p = room != null ? PuzzlesModule.INSTANCE.puzzleOf(run, room.index) : null;
        BlazePuzzle pz = p instanceof BlazePuzzle bp ? bp : null;
        if (pz == null) { diff.mobNotFound(k, label, rx, ry, rz, you); return; }
        boolean yours = you <= FAR && youActed(k);
        if (!yours && pz.awake && !pz.standing(hp)) { diff.mobOk(); return; }
        if (yours) diff.mobPending(k, label, you, String.format(Locale.ROOT, "ADS blaze %,d %s", hp, pz.standing(hp) ? "standing" : "not standing"));
        UUID who = nearestMate(rx, rz);
        pending.add(new Pending(!yours ? k : k + KILL_GRACE, label + " killed (you " + String.format(Locale.ROOT, "%.0f", you) + " blocks away)",
                () -> pz.awake && !pz.standing(hp), () -> {
            if (pz.killRecorded(hp, who)) {
                PlayerRecord wr = who != null ? run.state.players.get(who) : null;
                return "killed by " + (wr != null ? wr.name : "?");
            }
            return "no ADS blaze of " + hp + " standing";
        }));
    }

    // ---- the team's score parts

    private int recSecrets = 0;
    private int recCrypts = 0;
    private boolean recMimic = false;
    private boolean recPrince = false;

    private Map<Integer, Integer> greenAt = null;

    /** The first tick the recording marks each room green. */
    private Map<Integer, Integer> greenAt() {
        if (greenAt != null) return greenAt;
        HashMap<Integer, Integer> m = new HashMap<>();
        for (JsonArray e : file.events) {
            if (e.size() > 5 && e.get(1).getAsString().equals("room") && "GREEN".equals(ReplayFileKt.s(e, 3))) {
                JsonElement te = e.get(5);
                if (te == null || !te.isJsonArray() || te.getAsJsonArray().isEmpty()) continue;
                JsonArray t0 = te.getAsJsonArray().get(0).getAsJsonArray();
                PlacedRoom r = run.plan.roomAt(t0.get(0).getAsInt(), t0.get(1).getAsInt());
                if (r == null) continue;
                m.putIfAbsent(r.index, e.get(0).getAsInt());
            }
        }
        greenAt = m;
        return greenAt;
    }

    private static Integer primInt(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : null;
    }

    /** The party's secrets, crypts, Mimic and Prince as the recording counted them: credited TEAM_GRACE ticks later when ADS has not. */
    private void team(int k, JsonObject o) {
        if (o == null || k <= 0) return;
        SecretLedger led = run.state.secrets;
        Integer s = primInt(o, "secretsFound");
        if (s != null && s > recSecrets) {
            recSecrets = s;
            int want = s;
            pending.add(new Pending(k + TEAM_GRACE, "secrets to " + want, () -> led.teamFound >= want, () -> {
                int n = creditSecrets(want);
                return "credited " + n + ", ADS now " + led.teamFound;
            }));
        }
        Integer c = primInt(o, "crypts");
        if (c != null && c > recCrypts) {
            recCrypts = c;
            int want = c;
            pending.add(new Pending(k + TEAM_GRACE, "crypts to " + want, () -> led.crypts >= want, () -> {
                int n = creditCrypts(want);
                return "blew " + n + ", ADS now " + led.crypts;
            }));
        }
        JsonElement mi = o.get("mimic");
        if (mi != null && mi.getAsBoolean() && !recMimic) {
            recMimic = true;
            pending.add(new Pending(k + TEAM_GRACE, "mimic killed", () -> led.mimicKilled, () -> {
                Services.secrets().bonus(run, BonusKind.MIMIC, null);
                return "ADS mimic " + led.mimicKilled;
            }));
        }
        JsonElement pr = o.get("prince");
        if (pr != null && pr.getAsBoolean() && !recPrince) {
            recPrince = true;
            pending.add(new Pending(k + TEAM_GRACE, "prince killed", () -> led.princeKilled, () -> {
                Services.secrets().bonus(run, BonusKind.PRINCE, princeKiller());
                return "ADS prince " + led.princeKilled;
            }));
        }
    }

    /** The rooms to credit a teammate's find in, best first: where teammates stand, then by the recording's next green mark. */
    private List<Integer> teamRooms() {
        int now = run.t();
        List<Integer> withMate = new ArrayList<>();
        for (ReplayBodies.Mate m : bodies.all()) {
            if (Double.isNaN(m.x) || !m.present) continue;
            RoomRecord r = run.state.roomAt(m.x, m.z);
            if (r != null) withMate.add(r.index());
        }
        List<Integer> rest = new ArrayList<>();
        for (PlacedRoom r : run.plan.rooms) if (!withMate.contains(r.index)) rest.add(r.index);
        Map<Integer, Integer> ga = greenAt();
        rest.sort(Comparator.comparingInt(r -> {
            Integer g = ga.get(r);
            if (g == null) return Integer.MAX_VALUE;
            return g >= now - TEAM_GRACE ? g : Integer.MAX_VALUE / 2 + g;
        }));
        List<Integer> out = new ArrayList<>(new LinkedHashSet<>(withMate));
        out.addAll(rest);
        return out;
    }

    private UUID mateIn(int room) {
        for (ReplayBodies.Mate m : bodies.all()) {
            if (Double.isNaN(m.x) || !m.present) continue;
            RoomRecord r = run.state.roomAt(m.x, m.z);
            if (r != null && r.index() == room) return m.uuid;
        }
        return bodies.all().isEmpty() ? null : bodies.all().get(0).uuid;
    }

    private int creditSecrets(int want) {
        int n = 0;
        for (int room : teamRooms()) {
            for (SecretSite site : SecretsModule.pendingSecrets(run, room)) {
                if (run.state.secrets.teamFound >= want) return n;
                // A Wither Essence a teammate takes later in the recording is theirs, on its tick (mateEssence).
                if (site.kind == SecretKind.ESSENCE) {
                    boolean later = false;
                    for (int t : mateEssences()) if (t > run.t()) { later = true; break; }
                    if (later) continue;
                }
                // A chest or skull you click later in the recording is yours to find then, not a teammate's now.
                Integer yc = yourClicks().get(BlockPos.asLong(site.x, site.y, site.z));
                if ((yc != null ? yc : Integer.MIN_VALUE) > run.t()) continue;
                if (Services.secrets().found(run, room, site.kind, mateIn(room), site.x, site.y, site.z)) n++;
            }
            if (run.state.secrets.teamFound >= want) return n;
        }
        return n;
    }

    private int creditCrypts(int want) {
        int n = 0;
        for (int room : teamRooms()) for (Pos p : SecretsModule.standingCrypts(run, room)) {
            if (run.state.secrets.crypts >= want) return n;
            Services.secrets().cryptDestroyed(run, room, mateIn(room), p.x, p.y, p.z);
            n++;
        }
        return n;
    }

    private UUID nearestMate(double x, double z) {
        UUID best = null;
        double bestD = 0;
        boolean any = false;
        for (ReplayBodies.Mate m : bodies.all()) {
            if (Double.isNaN(m.x) || !m.present) continue;
            double d = Math.hypot(m.x - x, m.z - z);
            if (!any || d < bestD) { best = m.uuid; bestD = d; any = true; }
        }
        double hd = Math.hypot(host.getX() - x, host.getZ() - z);
        if (!any || hd < bestD) best = host.getUUID();
        return best;
    }

    /** The recording's key stand showed up: ADS drops the room's key there. */
    private void keyStand(double x, double z) {
        PlacedRoom r = run.plan.roomAtBlock(x, z);
        if (r == null) return;
        int room = r.index;
        if (Services.doors().keyForRoom(run, room) != null) Services.doors().dropKey(run, room, x, z);
    }

    /** The recording says name took a kind key ("Wither" or "Blood"): they take ADS's, on this tick. */
    private void keyTaken(String name, String kind) {
        UUID by = null;
        for (Map.Entry<UUID, PlayerRecord> en : run.state.players.entrySet()) if (Objects.equals(en.getValue().name, name)) { by = en.getKey(); break; }
        if (by == null) return;
        PlayerRecord p = run.state.players.get(by);
        if (p == null) return;
        KeyKind k = kind.equals("Blood") ? KeyKind.BLOOD : KeyKind.WITHER;
        PlacedRoom r = run.plan.roomAtBlock(p.x, p.z);
        if (!Services.doors().pickUpKey(run, by, k, r != null ? r.index : null, p.x, p.z)) diff.note(run.t(), "keys", name + " took a " + kind + " Key, ADS had none to give");
    }

    private void chat(int k, String raw) {
        String t = clean(raw);
        diff.recordedChat(k, t);
        Matcher m = WITHER_OPENED.matcher(t);
        if (m.find()) { door(k, DoorKind.WITHER, gv(m, 1)); return; }
        if (t.startsWith("The BLOOD DOOR has been opened!")) { door(k, DoorKind.BLOOD, null); return; }
        // His camp out of the recorded player's sight has no throws or kills to replay: the pass line says when it ended.
        if (t.equals("[BOSS] The Watcher: " + WatcherLines.PASS) && BloodModule.passNow()) diff.forced(run.t(), "the Watcher let the party pass (recorded k " + k + ")", "ADS's camp had not ended");
        if (t.startsWith("Party >")) return;
        m = KEY.matcher(t);
        if (m.find()) {
            diff.recordedKeyPick(k, gv(m, 2) + " Key", gv(m, 1));
            if (gv(m, 2).equals("Blood")) bloodKeyBy = gv(m, 1);
            keyTaken(gv(m, 1), gv(m, 2));
            return;
        }
        m = DIED.matcher(t);
        if (m.find()) { death(k, gv(m, 1)); return; }
        m = REVIVED.matcher(t);
        if (m.find()) { revive(k, gv(m, 1), gv(m, 2)); return; }
        drop(k, t);
    }

    /** The recording's drops (blessings, Superboom TNT, Revive Stones, chest essence), done by the secrets module on their tick. */
    private void drop(int k, String t) {
        Matcher m = BUFF.matcher(t);
        if (m.find()) {
            Blessing b = null;
            for (Blessing x : Blessing.values()) if (x.displayName.equals(gv(m, 3))) { b = x; break; }
            if (b == null) return;
            int level = ROMAN.indexOf(gv(m, 4)) + 1;
            UUID finder;
            if (gv(m, 1).equals("A")) finder = null;
            else {
                finder = uuidOf(gv(m, 2));
                if (finder == null) return;
            }
            String want = "has obtained Blessing of " + b.displayName + "!";
            boolean obtained = false;
            for (JsonArray a : file.at(k)) {
                if (a.size() > 2 && a.get(1).getAsString().equals("chat")) {
                    String l = ReplayFileKt.s(a, 2);
                    if (l != null && clean(l).endsWith(want)) { obtained = true; break; }
                }
            }
            SecretsModule.recordedBlessing(run, finder, b, level, !gv(m, 5).isEmpty(), obtained);
            return;
        }
        m = ITEM.matcher(t);
        if (m.find()) {
            LootItem item = null;
            for (LootItem x : LootItem.values()) if (x.displayName.equals(gv(m, 2))) { item = x; break; }
            if (item == null) return;
            UUID u = uuidOf(gv(m, 1));
            if (u == null) return;
            Integer n = toIntOrNull(gv(m, 3));
            SecretsModule.recordedItem(run, u, item, n != null ? n : 1);
            return;
        }
        m = WITHER_ESSENCE.matcher(t);
        if (m.find()) {
            UUID u = uuidOf(gv(m, 1));
            if (u != null && !u.equals(host.getUUID())) mateEssence(u);
            return;
        }
        m = ESSENCE.matcher(t);
        if (m.find()) {
            UUID u = uuidOf(gv(m, 1));
            if (u == null) return;
            Integer n = toIntOrNull(gv(m, 2));
            if (n == null) return;
            SecretsModule.recordedEssence(run, u, gv(m, 3), n);
        }
    }

    private int[] yourActs = null;

    /** The ticks you attacked or used an item (main hand) in the recording. */
    private int[] yourActs() {
        if (yourActs != null) return yourActs;
        List<Integer> l = new ArrayList<>();
        for (JsonArray e : file.events) {
            if (e.size() <= 2) continue;
            String kind = e.get(1).getAsString();
            if (kind.equals("o.atk") || ((kind.equals("o.use") || kind.equals("o.useOn")) && "MAIN_HAND".equals(ReplayFileKt.s(e, 2)))) l.add(e.get(0).getAsInt());
        }
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        Arrays.sort(a);
        yourActs = a;
        return yourActs;
    }

    /** Whether you attacked or used an item in the YOURS_WITHIN ticks up to k. */
    private boolean youActed(int k) {
        int[] acts = yourActs();
        int bs = Arrays.binarySearch(acts, k);
        int i = bs < 0 ? -bs - 2 : bs;
        while (i + 1 < acts.length && acts[i + 1] <= k) i++;
        return i >= 0 && k - acts[i] <= YOURS_WITHIN;
    }

    private Map<Long, Integer> yourClicks = null;

    /** Each block you right-click in the recording, with the last tick you do. */
    private Map<Long, Integer> yourClicks() {
        if (yourClicks != null) return yourClicks;
        HashMap<Long, Integer> out = new HashMap<>();
        for (JsonArray e : file.events) {
            if (e.size() > 3 && e.get(1).getAsString().equals("o.useOn") && e.get(3).isJsonArray()) {
                JsonArray p = e.get(3).getAsJsonArray();
                out.put(BlockPos.asLong(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt()), e.get(0).getAsInt());
            }
        }
        yourClicks = out;
        return yourClicks;
    }

    private List<Integer> mateEssences = null;

    /** The ticks of the recording's "<teammate> found a Wither Essence!" lines. */
    private List<Integer> mateEssences() {
        if (mateEssences != null) return mateEssences;
        List<Integer> out = new ArrayList<>();
        for (JsonArray e : file.events) {
            if (!isChat(e)) continue;
            Matcher m = WITHER_ESSENCE.matcher(clean(e.get(2).getAsString()));
            if (!m.find()) continue;
            UUID u = uuidOf(gv(m, 1));
            if (u != null && !u.equals(host.getUUID())) out.add(e.get(0).getAsInt());
        }
        mateEssences = out;
        return mateEssences;
    }

    /** A teammate took a Wither Essence: ADS's nearest one still lying is theirs now. */
    private void mateEssence(UUID by) {
        Vec3 at = posOf(by);
        if (at == null) return;
        Integer bestRoom = null;
        SecretSite bestSite = null;
        double bestD = Double.MAX_VALUE;
        for (int room = 0; room < run.state.rooms.size(); room++) for (SecretSite site : SecretsModule.pendingSecrets(run, room)) {
            if (site.kind != SecretKind.ESSENCE) continue;
            double d = Math.hypot(site.x + 0.5 - at.x, site.z + 0.5 - at.z);
            if (d < bestD) { bestD = d; bestRoom = room; bestSite = site; }
        }
        if (bestSite == null) { diff.note(run.t(), "secrets", "a teammate's Wither Essence: ADS has none lying"); return; }
        Services.secrets().found(run, bestRoom, bestSite.kind, by, bestSite.x, bestSite.y, bestSite.z);
    }

    private UUID uuidOf(String name) {
        if (name == null) return null;
        if (name.equals(file.self.name) || name.equals("You")) return host.getUUID();
        ReplayBodies.Mate m = mateByName.get(name);
        return m != null ? m.uuid : null;
    }

    private Vec3 posOf(UUID u) {
        if (u == null) return null;
        if (u.equals(host.getUUID())) return host.position();
        for (ReplayBodies.Mate m : bodies.all()) if (m.uuid.equals(u)) return Double.isNaN(m.x) ? null : new Vec3(m.x, m.y, m.z);
        return null;
    }

    /** Keys lent to open a recorded door before ADS had one, owed back from ADS's next key. */
    private int witherDebt = 0;
    private boolean bloodDebt = false;

    private void repayKeys() {
        KeyState keys = run.state.keys;
        while (witherDebt > 0 && keys.witherHeld > 0) { keys.witherHeld--; witherDebt--; diff.note(run.t(), "keys", "ADS's Wither Key paid back the one lent"); }
        if (bloodDebt && keys.bloodHeld) { keys.bloodHeld = false; bloodDebt = false; diff.note(run.t(), "keys", "ADS's Blood Key paid back the one lent"); }
    }

    private void door(int k, DoorKind kind, String who) {
        UUID by = uuidOf(who != null ? who : (kind == DoorKind.BLOOD ? bloodKeyBy : null));
        Vec3 p = posOf(by);
        Vec3 at = p != null ? p : host.position();
        PlacedDoor door = null;
        double bestD = 0;
        for (PlacedDoor d : run.plan.doors) {
            if (d.kind != kind) continue;
            Pair<Integer, Integer> c = d.spot();
            double v = Math.hypot(c.first + 0.5 - at.x, c.second + 0.5 - at.z);
            if (door == null || v < bestD) { door = d; bestD = v; }
        }
        if (door == null) return;
        DoorRecord rec = run.state.doors.get(door.index);
        int index = door.index;
        // A teammate's door opens on the tick of its line; yours had better be ADS's own.
        pending.add(new Pending(by != null && !by.equals(host.getUUID()) ? k : k + DOOR_GRACE,
                kind.name().toLowerCase(Locale.ROOT) + " door " + index + " opened by " + (who != null ? who : "?"), () -> rec.phase != DoorPhase.CLOSED, () -> {
            KeyState keys = run.state.keys;
            if (kind == DoorKind.WITHER ? keys.witherHeld <= 0 : !keys.bloodHeld) {
                // ADS had no key for it: lent, then opened. The next key ADS gives of that kind pays it back.
                if (kind == DoorKind.WITHER) { keys.witherHeld++; witherDebt++; } else { keys.bloodHeld = true; bloodDebt = true; }
                return "no key in ADS (lent one): " + Services.doors().tryOpen(run, index, by);
            }
            return Services.doors().tryOpen(run, index, by).toString();
        }));
    }

    private List<Integer> selfDeathKs = null;

    /** Recorded deaths of yours (k), to tell ADS's own kills of you from the recording's. */
    private List<Integer> selfDeathKs() {
        if (selfDeathKs != null) return selfDeathKs;
        List<Integer> out = new ArrayList<>();
        for (JsonArray e : file.all("chat")) {
            String s2 = ReplayFileKt.s(e, 2);
            String t = clean(s2 != null ? s2 : "");
            Matcher m = DIED.matcher(t);
            if (m.find() && !t.startsWith("Party >") && Objects.equals(uuidOf(gv(m, 1)), host.getUUID())) out.add(e.get(0).getAsInt());
        }
        selfDeathKs = out;
        return selfDeathKs;
    }

    private boolean wasGhost = false;

    /** ADS killing you where the recording did not die: written down, and you are revived so the replay goes on as recorded. */
    private void selfDeaths(int now) {
        PlayerRecord rec = run.state.players.get(host.getUUID());
        if (rec == null) return;
        if (rec.ghost && !wasGhost) {
            boolean recorded = false;
            for (int k : selfDeathKs()) if (Math.abs(k - now) <= 40) { recorded = true; break; }
            if (!recorded) {
                diff.forced(now, "ADS killed you (the recording did not die here)", "revived");
                Services.damage().revive(run, host.getUUID(), null);
            }
        }
        wasGhost = rec.ghost;
    }

    private void death(int k, String name) {
        UUID u = uuidOf(name);
        if (u == null) return;
        PlayerRecord rec = run.state.players.get(u);
        if (rec == null) return;
        pending.add(new Pending(k + 2, name + " died", () -> rec.ghost, () -> {
            Services.damage().kill(run, u, DamageSource.OTHER);
            return rec.ghost ? "killed" : "kill refused (no deaths?)";
        }));
    }

    private void revive(int k, String name, String by) {
        UUID u = uuidOf(name);
        if (u == null) return;
        PlayerRecord rec = run.state.players.get(u);
        if (rec == null) return;
        pending.add(new Pending(k + 2, name + " revived by " + by, () -> !rec.ghost, () -> {
            Services.damage().revive(run, u, uuidOf(by));
            return !rec.ghost ? "revived" : "revive refused";
        }));
    }

    /**
     * A recorded room changing mark: for the differ, and ADS's room is brought to it when ADS has not got there by itself in a while:
     * found (on the map), cleared (white) or done (green, its secrets credited; a puzzle reported solved).
     */
    private void room(int k, JsonArray e) {
        String mark = ReplayFileKt.s(e, 3);
        if (mark == null) return;
        JsonElement te = e.get(5);
        if (te == null || !te.isJsonArray()) return;
        JsonArray tiles = te.getAsJsonArray();
        if (tiles.isEmpty()) return;
        JsonArray t0 = tiles.get(0).getAsJsonArray();
        PlacedRoom placed = run.plan.roomAt(t0.get(0).getAsInt(), t0.get(1).getAsInt());
        if (placed == null) { diff.note(k, "room", "no ADS room at tile " + t0.get(0) + "," + t0.get(1) + " (" + ReplayFileKt.s(e, 2) + ")"); return; }
        diff.recordedRoom(k, placed.index, ReplayFileKt.s(e, 2), mark);
        Integer want = RANK.get(mark);
        if (want == null) return;
        if (want == 0) return;
        RoomRecord rr = run.state.room(placed.index);
        if (rr == null) return;
        RoomRecord hr = run.state.roomAt(host.getX(), host.getZ());
        boolean inIt = hr != null && hr.index() == placed.index;
        Tile ft = placed.tiles.get(0);
        double cx = DungeonFrame.tileCentre(ft.x), cz = DungeonFrame.tileCentre(ft.z);
        UUID by = inIt ? host.getUUID() : nearestMate(cx, cz);
        int grace = want == 1 ? 10 : GRACE;
        int w = want;
        int idx = placed.index;
        pending.add(new Pending(k + grace, placed.name + " " + mark.toLowerCase(Locale.ROOT) + (inIt ? " (you were in it)" : ""), () -> {
            Integer have = RANK.get(odin(rr.check));
            return (have != null ? have : 0) >= w;
        }, () -> {
            StateController ctl = DoorsModule.controller;
            switch (w) {
                case 1 -> { if (ctl != null) ctl.discover(idx); }
                case 2 -> {
                    if (ctl != null) ctl.discover(idx);
                    DoorsModule.completeRoom(run, idx);
                }
                case 3 -> {
                    if (ctl != null) ctl.discover(idx);
                    Puzzle pz = PuzzlesModule.INSTANCE.puzzleOf(run, idx);
                    if (pz != null) Services.puzzles().reportSolved(pz, by);
                    else {
                        DoorsModule.completeRoom(run, idx);
                        Services.secrets().creditRoom(run, idx, by);
                    }
                }
                default -> {}
            }
            return "ADS now " + rr.check;
        }));
    }

    private static String odin(CheckMark c) {
        return switch (c) {
            case UNDISCOVERED -> "UNDISCOVERED";
            case NONE -> "NONE";
            case WHITE -> "WHITE";
            case GREEN -> "GREEN";
            default -> "FAILED";
        };
    }

    // ---- statics

    /** The session whose recorded packet the server is handling right now (server thread). */
    private static volatile ReplaySession injecting = null;

    /** A kick while a recorded packet is handled (AdsReplayKickMixin): the replay keeps going and the report lists it. */
    public static boolean swallowKick(String reason) {
        ReplaySession s = injecting;
        if (s == null) return false;
        s.diff.kick(s.run.t(), reason);
        return true;
    }

    public static final String NET = "ads.replay";

    /** Ticks the replay waits for ADS to count a find of the party's itself before crediting it. */
    public static final int TEAM_GRACE = 20;
    private static final Map<String, Integer> RANK = Map.of("UNDISCOVERED", 0, "NONE", 1, "WHITE", 2, "GREEN", 3);
    public static final String MATE = "replayMate";
    /** Ticks ADS gets to do by itself what the recording did before it is done for it. */
    public static final int GRACE = 20;
    public static final int DOOR_GRACE = 6;
    /** Ticks after a recorded WITHER-door HP jump that ADS's mob is checked. */
    public static final int HP_CHECK_AFTER = DOOR_GRACE + 2;
    /** The window around your hit in which the recording's HP loss of that mob is yours. */
    public static final int HIT_BEFORE = 2;
    public static final int HIT_AFTER = 3;
    /** A recorded death near you waits this long for your own kill. */
    public static final int KILL_GRACE = HIT_AFTER + 1;
    /** Farther than this from you, a recorded kill was not yours. */
    public static final double FAR = 12.0;
    /** A death within this many ticks of your hit or use may be yours. */
    public static final int YOURS_WITHIN = 10;

    /** The health part of Hypixel's action bar: current/max, red or gold. */
    private static final Pattern HEALTH = Pattern.compile("^§[c6]([\\d,]+)/([\\d,]+)");
    /** A grant line's first term at levels I, II and V: boosted (x1.25) or plain. */
    private static final Pattern BOOSTED_GRANT = Pattern.compile("^Granted you \\+(?:7\\.3|14\\.5|36\\.3) &|^Granted you \\+1\\.(?:05|11|27)x HP");
    private static final Pattern PLAIN_GRANT = Pattern.compile("^Granted you \\+(?:5\\.8|11\\.6|29) &|^Granted you \\+1\\.(?:04|09|22)x HP");
    private static final Set<String> RIGHT_CLICKS = Set.of("o.use", "o.useOn", "o.int");
    /** "DUNGEON BUFF! X found a / You found a / A Blessing of Life V(! / was found!) (03s)". */
    private static final Pattern BUFF = Pattern.compile("^DUNGEON BUFF! (A|(?:(\\w{1,16}|You) found a)) Blessing of (\\w+) (I|II|III|IV|V)(?: was found)?!( \\(.+\\))?$");
    private static final Pattern RANKED = Pattern.compile("^(?:Party > )?\\[(VIP|VIP\\+|MVP|MVP\\+|MVP\\+\\+)] (\\w{1,16})[ :]");
    /** A rank's bracket and name colour as Hypixel writes them. */
    private static final Map<String, Pair<String, String>> RANK_LOOKS = Map.of("VIP", Pair.of("§a[VIP]", "§a"), "VIP+", Pair.of("§a[VIP§6+§a]", "§a"),
            "MVP", Pair.of("§b[MVP]", "§b"), "MVP+", Pair.of("§b[MVP§c+§b]", "§b"), "MVP++", Pair.of("§6[MVP§c++§6]", "§6"));
    private static final List<String> ROMAN = List.of("I", "II", "III", "IV", "V");
    private static final Pattern ITEM = Pattern.compile("^(?:\\[[^\\]]+] )?(\\w{1,16}) has obtained (Superboom TNT|Revive Stone)(?: x(\\d+))?!$");
    private static final Pattern WITHER_ESSENCE = Pattern.compile("^(\\S+) found a Wither Essence! Everyone gains an extra essence!$");
    private static final Pattern ESSENCE = Pattern.compile("^ESSENCE! (\\w{1,16}) found x(\\d+) (\\w+) Essence!$");
    private static final Pattern WITHER_OPENED = Pattern.compile("^(\\w{1,16}) opened a WITHER door!");
    private static final Pattern DIED = Pattern.compile("☠ (\\w{1,16}) .*became a ghost");
    private static final Pattern KEY = Pattern.compile("^(?:\\[[^\\]]+] )?(\\w{1,16}) has obtained (Wither|Blood) Key!");
    private static final Pattern LV_PREFIX = Pattern.compile("^\\[Lv\\d+]");
    private static final Pattern HP_SUFFIX = Pattern.compile("\\s*[0-9][0-9.,]*[kMB]?(/[0-9][0-9.,]*[kMB]?)?❤.*$");

    /** A Hypixel nametag as ADS names the mob: no star, no HP, no colour. */
    public static String mobName(String tag) {
        String s = clean(tag).replace("✯", "");
        s = LV_PREFIX.matcher(s).replaceAll("");
        s = HP_SUFFIX.matcher(s).replaceAll("");
        return ReplayFileKt.trim(s);
    }

    public static boolean sameMob(String ads, String rec) {
        String a = ReplayFileKt.trim(clean(ads).replace("✯", ""));
        return a.equalsIgnoreCase(rec) || rec.endsWith(" " + a) || a.endsWith(" " + rec);
    }

    private static final Pattern REVIVED = Pattern.compile("❣ (\\w{1,16}) was revived by (\\w{1,16})");
    private static final Pattern CODES = Pattern.compile("§.");

    public static String clean(String s) { return ReplayFileKt.trim(CODES.matcher(s).replaceAll("")); }

    /** A Higher Or Lower blaze's tag, `[Lv11] <icon> Blaze 3,916/3,916❤`: its full HP. */
    private static final Pattern BLAZE_TAG = Pattern.compile("\\bBlaze [\\d,]+/([\\d,]+)❤");

    public static String fmt(double x, double y, double z) { return String.format(Locale.ROOT, "%.1f %.1f %.1f", x, y, z); }

    public static ServerPlayer hostPlayer() { return SimServer.player(); }
}
