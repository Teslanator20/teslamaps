package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/Spike.kt

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Fight;
import com.teslamaps.adm.sim.P3Sim;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimServer;
import com.teslamaps.adm.sim.SimWorld;
import com.teslamaps.adm.sim.pack.PackSet;
import com.teslamaps.adm.sim.pack.Packs;
import com.teslamaps.adm.sim.pack.Rot;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomDesign;
import com.teslamaps.adm.sim.pack.rooms.RoomPack;
import com.teslamaps.adm.sim.pack.rooms.RoomVolume;
import com.teslamaps.adm.sim.pack.rooms.SkullTexture;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.PlacedDoor;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.RunConfig;
import com.teslamaps.adm.sim.run.WorldRegions;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;

public final class Spike {
    private Spike() {}

    public static volatile StandSpot pendingSpawn = null;

    public static volatile List<ExpectedRoom> expected = List.of();

    public static volatile LayoutRegion lastRegion = null;
    public static volatile String lastLabel = "";

    public static final int GIVE_UP_TICKS = 400;
    private static final int TELEPORT_DELAY = 40;

    // ---- building

    public static class Prepared {
        public final LayoutBuilder.Built built;
        public final List<ExpectedRoom> expected;
        public final StandSpot spawn;
        public final String label;
        public final List<SkullTexture> textures;

        public Prepared(LayoutBuilder.Built built, List<ExpectedRoom> expected, StandSpot spawn, String label, List<SkullTexture> textures) {
            this.built = built;
            this.expected = expected;
            this.spawn = spawn;
            this.label = label;
            this.textures = textures;
        }
    }

    public static void launch(Consumer<String> reply, BiFunction<PackSet, Consumer<String>, Prepared> prepare) {
        Packs.loadAsync().thenAccept(set -> {
            ArrayList<String> lines = new ArrayList<>();
            Prepared prepared;
            try {
                if (!set.canBuildDungeon()) {
                    List<String> ul = set.userLines();
                    lines.addAll(ul.isEmpty() ? List.of("The room library and the layout pool are needed.") : ul);
                    prepared = null;
                } else prepared = prepare.apply(set, lines::add);
            } catch (Throwable t) {
                Ec.LOG.error("[ads] layout spike failed", t);
                lines.add("That failed: " + t.getMessage());
                prepared = null;
            }
            Prepared fp = prepared;
            Minecraft.getInstance().execute(() -> {
                lines.forEach(reply);
                if (fp != null) start(fp, reply);
            });
        });
    }

    private static void start(Prepared p, Consumer<String> reply) {
        LayoutRegion region = new LayoutRegion("dungeon", p.built.instances, p.built.edits, p.textures);
        WorldRegions.register(region);
        lastRegion = region;
        lastLabel = p.label;
        pendingSpawn = p.spawn;
        expected = p.expected;
        watch();
        for (String w : p.built.warnings) reply.accept(w);
        reply.accept("Building " + p.label + ": " + p.built.instances.size() + " rooms, " + p.built.edits.size() + " door and wall edits. Rebuilding the ADS world...");
        SimWorld.rebuild();
    }

    public static Prepared prepareRooms(PackSet set, Consumer<String> say) {
        RoomPack rooms = set.rooms;
        if (rooms == null) return null;
        RoomDesign andesite = design(rooms, "Andesite", "Andesite");
        RoomDesign skulls = design(rooms, "Blue_Skulls", "Blue Skulls");
        RoomDesign pirate = design(rooms, "Pirate", "Pirate");
        if (andesite == null || skulls == null || pirate == null) {
            List<String> missing = new ArrayList<>();
            if (andesite == null) missing.add("Andesite");
            if (skulls == null) missing.add("Blue Skulls");
            if (pirate == null) missing.add("Pirate");
            say.accept("The room library lacks " + String.join(", ", missing) + ".");
            return null;
        }
        List<Triple<RoomDesign, Rot, List<Tile>>> picks = List.of(
                new Triple<>(andesite, Rot.SOUTH, Placement.tilesFor(andesite.shape, Rot.SOUTH, 1, 1)),
                new Triple<>(skulls, Rot.WEST, Placement.tilesFor(skulls.shape, Rot.WEST, 2, 1)),
                new Triple<>(pirate, Rot.NORTH, Placement.tilesFor(pirate.shape, Rot.NORTH, 1, 2))
        );
        ArrayList<RoomInstance> instances = new ArrayList<>();
        ArrayList<ExpectedRoom> expect = new ArrayList<>();
        for (Triple<RoomDesign, Rot, List<Tile>> pk : picks) {
            RoomDesign d = pk.first;
            Rot rot = pk.second;
            List<Tile> tiles = pk.third;
            RoomVolume vol = rooms.volume(d);
            if (vol == null) {
                say.accept("The blocks of " + d.name + " could not be read from the room library.");
                return null;
            }
            Placement placement = d.place(rot, tiles);
            instances.add(new RoomInstance(d, vol, placement));
            expect.add(new ExpectedRoom(d.name, rot.name(), tileSet(tiles)));
        }
        // a wither door between Andesite and Blue Skulls, a plain doorway between Blue Skulls and Pirate
        LinkedHashMap<Slot, DoorKind> doors = new LinkedHashMap<>();
        doors.put(new Slot('E', 1, 1), DoorKind.WITHER);
        doors.put(new Slot('S', 2, 1), DoorKind.NORMAL);
        LayoutBuilder.Built built = LayoutBuilder.assemble(instances, doors, rooms);
        StandSpot spawn = SpawnFinder.find(instances.get(0), 1, 1);
        return new Prepared(built, expect, spawn, "the three-room spike", rooms.textures);
    }

    private static RoomDesign design(RoomPack rooms, String id, String name) {
        RoomDesign d = rooms.design(id);
        if (d != null) return d;
        List<RoomDesign> byName = rooms.designsByName(name);
        return byName.isEmpty() ? null : byName.get(0);
    }

    private static Set<Pair<Integer, Integer>> tileSet(List<Tile> tiles) {
        Set<Pair<Integer, Integer>> out = new LinkedHashSet<>();
        for (Tile t : tiles) out.add(Pair.of(t.x, t.z));
        return out;
    }

    public static BiFunction<PackSet, Consumer<String>, Prepared> prepareLayout(RunConfig config) {
        return (set, say) -> {
            RoomPack rooms = Objects.requireNonNull(set.rooms);
            DungeonPlan.Result planned = LayoutModule.INSTANCE.plan(config, set);
            if (planned instanceof DungeonPlan.Result.Failed f) {
                say.accept(f.failure.message);
                return null;
            }
            DungeonPlan plan = ((DungeonPlan.Result.Ok) planned).plan;
            LayoutBuilder.Result b = LayoutBuilder.build(plan, rooms);
            if (b instanceof LayoutBuilder.Result.Failed bf) {
                say.accept(bf.message);
                return null;
            }
            LayoutBuilder.Built built = ((LayoutBuilder.Result.Ok) b).built;
            PlacedRoom entrance = plan.entrance();
            RoomInstance inst = null;
            if (entrance != null) for (RoomInstance i : built.instances) if (i.placement.tiles().equals(entrance.tiles)) { inst = i; break; }
            StandSpot spawn = entrance != null && inst != null ? SpawnFinder.find(inst, entrance.tiles.get(0).x, entrance.tiles.get(0).z) : null;
            List<ExpectedRoom> expect = new ArrayList<>();
            for (PlacedRoom r : plan.rooms) if (r.design != null) expect.add(new ExpectedRoom(r.design.name, r.rotation.name(), tileSet(r.tiles)));
            int doorways = 0;
            for (PlacedDoor d : plan.doors) if (d.kind.connected()) doorways++;
            say.accept("Layout " + plan.layoutId + " (seed " + plan.seed + "): " + plan.rooms.size() + " rooms, " + doorways + " doorways.");
            return new Prepared(built, expect, spawn, plan.layoutId, rooms.textures);
        };
    }

    // ---- server side

    public static void installServerHooks() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            StandSpot spot = pendingSpawn;
            if (spot == null) return;
            if (!SimServer.isSim(server)) return;
            ServerPlayer player = handler.player;
            if (!Sim.isHost(player)) return;
            pendingSpawn = null;
            Fight.later(TELEPORT_DELAY, "layout spike teleport", () -> {
                Sim.asPlayer(player, () -> Sim.tp(player, spot.x, spot.y, spot.z, player.getYRot(), 0f));
                Sim.note("Standing in the first room. §f/ads dev probe§7 asks Odin what it sees.");
            });
        });
        // the dungeon region belongs to the world it was registered for
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            if (s instanceof IntegratedServer && s.getWorldData().getLevelName().equals(SimWorld.NAME)) WorldRegions.worldStopped();
        });
    }

    // ---- client side: the probe

    private static boolean watching = false;
    private static int watchTicks = 0;
    private static String lastCurrent = null;
    private static final ArrayList<String> entered = new ArrayList<>();
    private static int retried = 0;

    private static final int MAX_ENTERED = 64;
    private static int rescanned = 0;

    private static void watch() {
        watching = true;
        watchTicks = 0;
        lastCurrent = null;
        entered.clear();
        retried = 0;
        rescanned = 0;
    }

    public static void installClientHooks() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Ec.safely("ads layout probe", Spike::clientTick));
    }

    private static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        // nothing to do unless a probe is watching
        if (!watching || mc.level == null || !P3Sim.inSim()) return;
        String cur = OdinProbe.currentRoom();
        if (!Objects.equals(cur, lastCurrent)) {
            lastCurrent = cur;
            if (cur != null) {
                entered.add(cur);
                if (entered.size() > MAX_ENTERED) entered.remove(0);
            }
        }
        watchTicks++;
        if (watchTicks % 20 != 0) return;
        rescanned += OdinProbe.rescanUnnamed();
        retried += OdinProbe.retryRotations();
        List<ProbeLine> lines = ProbeReport.compare(expected, OdinProbe.rooms());
        if (ProbeReport.allOk(lines) || watchTicks >= GIVE_UP_TICKS) {
            watching = false;
            for (String l : report(lines)) Ec.msg(l);
        }
    }

    public static List<String> report() { return report(null); }

    public static List<String> report(List<ProbeLine> given) {
        if (given == null) {
            rescanned += OdinProbe.rescanUnnamed();
            retried += OdinProbe.retryRotations();
        }
        List<ProbeLine> lines = given != null ? given : ProbeReport.compare(expected, OdinProbe.rooms());
        ArrayList<String> out = new ArrayList<>();
        if (expected.isEmpty()) out.add("Nothing was built this session; use /ads dev rooms or /ads dev layout first. Odin holds " + OdinProbe.rooms().size() + " rooms.");
        int ok = 0;
        for (ProbeLine l : lines) if (l.ok) ok++;
        out.add(!lines.isEmpty() && ok == lines.size() ? "§aOdin recognises all " + ok + " built rooms." : "§eOdin recognises " + ok + " of " + lines.size() + " built rooms.");
        // a full layout prints only the failures and the first few good ones
        List<ProbeLine> show;
        if (lines.size() <= 6) show = lines;
        else {
            show = new ArrayList<>();
            for (ProbeLine l : lines) if (!l.ok) show.add(l);
            int good = 0;
            for (ProbeLine l : lines) if (l.ok && good < 3) { show.add(l); good++; }
        }
        for (ProbeLine l : show) out.add((l.ok ? "§a ok §7" : "§c no §7") + l.text);
        String cur = OdinProbe.currentRoom();
        String ent = String.join(", ", entered);
        out.add("§7Odin: in dungeon " + OdinProbe.inDungeons() + ", in boss " + OdinProbe.inBoss() + ", standing in " + (cur != null ? cur : "no room") + "; rooms entered: " + (ent.isEmpty() ? "none" : ent) + ".");
        if (rescanned > 0) out.add("§7The probe offered " + rescanned + " tile centre chunk(s) to Odin's world scan again: they had loaded before the sim told Odin it was in a dungeon, and Odin looks at a chunk only once.");
        if (retried > 0) out.add("§7The probe asked Odin's own rotation check again " + retried + " time(s) for rooms it had named but not turned (their corner marker was in a chunk that had not loaded yet).");
        if (OdinProbe.inBoss()) out.add("§7Odin reports room entries only outside the boss, and the sim's bridge still says it is in the boss until a run sets its stage; names and rotations do not depend on that.");
        LayoutRegion r = lastRegion;
        if (r != null) out.add("§7Last build (" + lastLabel + "): " + r.stats() + ".");
        return out;
    }
}
