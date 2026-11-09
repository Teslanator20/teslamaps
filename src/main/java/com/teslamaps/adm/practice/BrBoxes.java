package com.teslamaps.adm.practice;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.teslamaps.TeslaMaps;
import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.DungeonWaypoints;
import com.teslamaps.features.LegitMode;
import com.teslamaps.map.CheckmarkState;
import com.teslamaps.map.DoorType;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.scanner.DoorScanner;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

// Ported from ADM's BrWaypoints2.kt ("BR Roles"): boxes that group a room's starred mobs, with blood rush
// roles from party chat. Boxes and roles are the site's (undonecoffee.com/brroles), bundled; the wand
// editor (from ADM's earlier versions) edits a local copy in config/teslamaps/adm/brboxes.json.
public final class BrBoxes {
    private BrBoxes() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static final String[] MY_ROLES = {"All Boxes", "Door", "Role 1", "Role 2", "Role 3", "Role 4"};
    private static final String[] KILLERS = {"Duo", "Trio", "Quad"};
    private static final String[] DOOR_KILLS = {"Never", "Always", "After Fairy"};

    // --- boxes ---

    static final class Box {
        final int[] c;
        final String room;
        boolean saved;
        Box(int[] c, String room) { this.c = c; this.room = room; }
        AABB aabb() { return new AABB(c[0], c[1], c[2], c[3], c[4], c[5]); }
    }

    private static final List<Box> boxes = new ArrayList<>();
    private static Map<String, List<int[]>> saved;
    private static final Map<String, String> loadedRooms = new HashMap<>();

    // --- starred mobs ---

    private static final class Mob {
        final AABB spawn;
        final double x, y, z;
        final String room;
        Entity entity, tag;
        boolean dead;
        Integer tagGoneAt;
        Box claim;
        long claimSig;
        boolean claimed;
        Mob(AABB spawn, double x, double y, double z, String room, Entity entity, Entity tag) {
            this.spawn = spawn; this.x = x; this.y = y; this.z = z; this.room = room; this.entity = entity; this.tag = tag;
        }
    }

    private static final List<Mob> mobs = new ArrayList<>();
    private static final Map<Integer, Mob> byId = new HashMap<>();
    private static int ticks = 0;

    private static final List<String> MOB_NAMES = List.of("Lurker", "Dreadlord", "Souleater", "Zombie", "Skeleton", "Skeletor", "Sniper", "Super Archer", "Spider", "Fels", "Withermancer", "Lost Adventurer", "Angry Archaeologist", "Frozen Adventurer");
    private static final Pattern STARRED = Pattern.compile("^.*✯ .*\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?.?❤$");

    private static final int PURPLE = 0xFFAA00AA;
    private static final int GOLD = 0xFFFFAA00;
    private static final int GREEN = 0xFF55FF55;

    private static final String MORT = "[NPC] Mort: Here, I found this map when I first entered the dungeon.";
    private static final String BLOOD_DOOR = "The BLOOD DOOR has been opened!";
    private static final Pattern WITHER_DOOR = Pattern.compile("^\\w+ opened a WITHER door!$");

    private static final double CLAIM_REACH = 5.0;
    private static final double DIES_WITH_TAG = 48.0;
    private static final double DIES_ALONE = 32.0;
    private static final double REACH = 48.0;
    private static final int DOOR_BLOCKS = 30;

    private static Object lastLevel;
    private static boolean editKeyDown;
    private static boolean useHeld;

    public static void register() {
        Feature.of("AdmBrRoles").section(Adm.SECTION).legit().when(() -> cfg().admBrRoles)
                .tickAlways(BrBoxes::tick)
                .world(BrBoxes::render)
                .chat(BrBoxes::onChatLine)
                .config("ADM Highlight & BR", b -> {
                    b.label("BR Roles");
                    b.toggle("BR Roles", () -> cfg().admBrRoles, v -> cfg().admBrRoles = v).cheat()
                            .desc("Boxes that group a room's starred mobs, shown while any of them is alive, through walls. Boxes and roles from undonecoffee.com/brroles (bundled).");
                    b.dropdown("  My Role", MY_ROLES, () -> cfg().admBrMyRole, v -> cfg().admBrMyRole = v)
                            .desc("Your blood rush role: only your boxes show, numbered in kill order, the next one filled in; your stack once yours are dead. Door shows none. All Boxes (or a role past the number of killers) turns roles off. In master mode the M7 roles are used and your class picks the role (Archer 1, Mage 2, Berserk 3, Tank 4, Healer the door). Party chat (!br 2, !br d) overrides it for a run.");
                    b.dropdown("  Killers", KILLERS, () -> cfg().admBrKillers, v -> cfg().admBrKillers = v)
                            .desc("How many kill on blood rush, not counting the door runner. Party chat (!3br 2) overrides it for a run.");
                    b.dropdown("  Door Kills", DOOR_KILLS, () -> cfg().admBrDoorKills, v -> cfg().admBrDoorKills = v)
                            .desc("When the door runner kills too, with the Doorer kills roles: one more role than Killers (the door runner's, the last), and each room's plan goes by its wither door as well as the door the rush came in by. Never, Always, or After Fairy: only in the rooms the rush reaches after fairy.");
                    b.toggle("  Drop Bottom After Fairy", () -> cfg().admBrDropBottom, v -> cfg().admBrDropBottom = v)
                            .desc("Door Kills After Fairy: once the rush reaches fairy, the killer with the highest role number gets a Clear! alert and leaves the rush to clear. The rest go on with one killer fewer and the door runner killing.");
                    b.toggle("  Recolor Done Boxes", () -> cfg().admBrRecolorDone, v -> cfg().admBrRecolorDone = v)
                            .desc("A done box stays up as a normal box in Done Color, its number greyed, instead of disappearing.");
                    b.color("  Done Color", () -> cfg().admBrDoneColor, v -> cfg().admBrDoneColor = v)
                            .desc("Colour of a done box with Recolor Done Boxes on. Its alpha fades the outline; Fill Opacity still sets the faces.");
                    b.slider("  Fill Opacity", 0f, 1f, () -> cfg().admBrOpacity, v -> cfg().admBrOpacity = Math.round(v * 100f) / 100f, 2)
                            .desc("How solid the boxes' faces are. The next of yours to kill is filled in more.");
                    b.toggle("  Starred Mobs Spawn", () -> cfg().admBrSpawnMarkers, v -> cfg().admBrSpawnMarkers = v)
                            .desc("Marks where each starred mob was first seen, flat on the floor in the starred mob colour.");
                    b.toggle("  All Rooms", () -> cfg().admBrAllRooms, v -> cfg().admBrAllRooms = v)
                            .desc("Shows boxes in every room. Off, only in rooms the blood rush went through. Edit Mode always shows them.");
                    b.toggle("  Debug", () -> cfg().admBrDebug, v -> cfg().admBrDebug = v)
                            .desc("Says in chat, for each room the rush comes into: the door it came in by, your role, and the boxes it shows you.");
                    b.toggle("  Edit Mode", () -> cfg().admBrEditMode, v -> cfg().admBrEditMode = v)
                            .desc("Box editor with the wand: drop places a box at your feet (or deletes the one you look at), left click / scroll up pushes the selected face out, right click / scroll down pulls it in, sneak moves the whole box up or down. Edits are saved locally.");
                    b.keybind("    Edit Mode Keybind", () -> cfg().admBrEditKey, v -> cfg().admBrEditKey = v)
                            .desc("Toggles Edit Mode.");
                    b.buttonRow(new String[]{"Make Held Item Wand", "Clear Room"}, new Runnable[]{BrBoxes::makeWand, BrBoxes::clearRoom});
                });

        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("brrole")
                .executes(c -> { BrRoles.status(); return 1; })
                .then(ClientCommands.literal("door").executes(c -> { BrRoles.claimDoor(); return 1; }))
                .then(ClientCommands.argument("killing", IntegerArgumentType.integer(2, BrRoles.MAX_KILLING))
                        .then(ClientCommands.argument("role", IntegerArgumentType.integer(1, BrRoles.MAX_KILLING)).executes(c -> {
                            BrRoles.claim(IntegerArgumentType.getInteger(c, "killing"), IntegerArgumentType.getInteger(c, "role"));
                            return 1;
                        })))));
    }

    private static boolean on() {
        return Adm.on() && cfg().admBrRoles && !LegitMode.blocksCheats();
    }

    private static int index(String[] options, String v, int fallback) {
        for (int i = 0; i < options.length; i++) if (options[i].equals(v)) return i;
        return fallback;
    }

    // --- tick ---

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            worldLoad();
        }
        int killers = index(KILLERS, cfg().admBrKillers, 0);
        int role = index(MY_ROLES, cfg().admBrMyRole, 3);
        BrRoles.settingKilling = killers + 2;
        BrRoles.settingRole = role == 0 ? null : role == 1 ? 0 : role - 1;
        BrRoles.doorKills = BrRoles.DoorKills.values()[index(DOOR_KILLS, cfg().admBrDoorKills, 0)];
        BrRoles.dropBottom = cfg().admBrDropBottom;

        boolean down = mc.gui.screen() == null && KeyNames.isDown(cfg().admBrEditKey);
        if (down && !editKeyDown) {
            cfg().admBrEditMode = !cfg().admBrEditMode;
            TeslaMapsConfig.save();
            PracticeUtil.msg("§dBR Roles §7edit mode " + (cfg().admBrEditMode ? "§aon" : "§coff"));
        }
        editKeyDown = down;
        if (!mc.options.keyUse.isDown()) useHeld = false;

        if (mc.player == null || mc.level == null || !DungeonManager.isInDungeon()) return;
        wasInDungeon = true;
        ticks++;
        if (barriers.size() >= DOOR_BLOCKS) for (int[][] door : doors(barriers)) doorFalling(tileRoom(door[0]), tileRoom(door[1]), door);
        barriers.clear();
        if (ticks % 10 == 0) mapPath();
        trackMyDoor();
        if (rushing && beforeFairy == null) {
            DungeonRoom cur = DungeonManager.getCurrentRoom();
            if (cur != null && cur.getType() == RoomType.FAIRY && onRush(cur.getName())) reachFairy();
        }
        if (cfg().admBrDebug && (rushing || cfg().admBrAllRooms)) {
            DungeonRoom cur = DungeonManager.getCurrentRoom();
            String n = cur == null ? null : cur.getName();
            if (n != null && !n.equals(lastRoom)) { lastRoom = n; debugRoom(n); }
        }
        loadRooms();
        if (!DungeonManager.isInBossArea()) findStarred();
        watchDeaths();
    }

    private static void worldLoad() {
        BrRoles.load();
        BrRoles.newRun(wasInDungeon);
        wasInDungeon = false;
        entryDoors.clear(); noPlanSaid.clear(); walkedIn.clear(); myTile = null;
        boxes.clear(); loadedRooms.clear(); mobs.clear(); byId.clear(); clayCache.clear();
        rushing = false; rushRoom = null; rushed.clear(); startDoor = false; barriers.clear(); witherSides.clear();
        path = Map.of(); exits = Map.of(); entrances = Map.of(); lastRoom = null;
        beforeFairy = null; witherSeen = false;
    }

    // --- chat ---

    private static void onChatLine(String raw) {
        String text = StringUtil.strip(raw).trim();
        if (BrRoles.onChat(text)) return;
        if (!DungeonManager.isInDungeon()) return;
        if (text.equals(MORT)) {
            rushing = true; rushRoom = null; rushed.clear(); startDoor = true; beforeFairy = null; witherSeen = false;
        } else if (rushing && WITHER_DOOR.matcher(text).matches()) {
            startDoor = false;
            witherSeen = true;
            if (beforeFairy == null) {
                for (DungeonRoom r : rooms()) if (Objects.equals(r.getName(), rushRoom) && r.getType() == RoomType.FAIRY) { reachFairy(); break; }
            }
        } else if (text.equals(BLOOD_DOOR)) {
            rushing = false; rushRoom = null;
        }
    }

    /** Client thread, before a block change is applied. */
    public static void onBlockUpdate(BlockPos pos, BlockState old, BlockState updated) {
        if (rushing && on() && updated.getBlock() == Blocks.BARRIER && old.getBlock() != Blocks.BARRIER) barriers.add(new int[]{pos.getX(), pos.getZ()});
    }

    // --- starred mobs ---

    private static void findStarred() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Entity tag : level.entitiesForRendering()) {
            if (!(tag instanceof ArmorStand) || !tag.isAlive()) continue;
            String name = tag.getName().getString();
            boolean known = false;
            for (String n : MOB_NAMES) if (name.contains(n)) { known = true; break; }
            if (!known || !STARRED.matcher(name).matches()) continue;
            List<Entity> under = level.getEntities(tag, tag.getBoundingBox().move(0.0, -1.0, 0.0), BrBoxes::isMob);
            if (under.isEmpty()) continue;
            Entity mob = under.get(0);
            Mob k = byId.get(mob.getId());
            if (k != null) {
                if (k.entity == null && !k.dead) k.entity = mob;
                if (k.tag != tag) { k.tag = tag; k.tagGoneAt = null; }
                continue;
            }
            DungeonRoom r = roomAt(mob.getX(), mob.getZ());
            Mob m = new Mob(mob.getBoundingBox(), mob.getX(), mob.getY(), mob.getZ(), r == null ? null : r.getName(), mob, tag);
            mobs.add(m);
            byId.put(mob.getId(), m);
        }
    }

    private static void watchDeaths() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        for (Mob mob : mobs) {
            if (mob.tag != null && mob.tag.isRemoved() && mob.tagGoneAt == null) mob.tagGoneAt = ticks;
        }
        for (Mob mob : mobs) {
            Entity e = mob.entity;
            if (e == null || !e.isRemoved()) continue;
            double d = Math.hypot(player.getX() - e.getX(), player.getZ() - e.getZ());
            boolean withTag = mob.tagGoneAt != null && ticks - mob.tagGoneAt <= 1;
            if (d < DIES_WITH_TAG && (withTag || d < DIES_ALONE)) mob.dead = true;
            mob.entity = null;
            mob.tag = null;
        }
    }

    private static boolean isMob(Entity e) {
        if (e instanceof ArmorStand || e instanceof WitherBoss) return false;
        if (e instanceof Enderman) return true;
        if (e instanceof Player p) return p.getUUID().version() == 2 && p != Minecraft.getInstance().player;
        return !e.isInvisible();
    }

    private static List<Box> shown() {
        List<DungeonRoom> rooms = shownRooms();
        long sig = boxesSig();
        Set<Box> claimed = new HashSet<>(), alive = new HashSet<>();
        for (Mob mob : mobs) {
            Box box = cachedClaim(mob, sig);
            if (box == null) continue;
            claimed.add(box);
            if (!mob.dead) alive.add(box);
        }
        Set<String> open = new HashSet<>();
        for (DungeonRoom r : rooms) {
            CheckmarkState s = r.getCheckmarkState();
            if (s != CheckmarkState.WHITE && s != CheckmarkState.GREEN) open.add(r.getName());
        }
        List<Box> out = new ArrayList<>();
        for (Box b : boxes) if (b.room != null && open.contains(b.room) && (!claimed.contains(b) || alive.contains(b))) out.add(b);
        return out;
    }

    private static List<DungeonRoom> shownRooms() {
        List<DungeonRoom> out = new ArrayList<>();
        Set<String> names = new HashSet<>();
        DungeonRoom cur = DungeonManager.getCurrentRoom();
        if (cur != null && cur.getName() != null && names.add(cur.getName())) out.add(cur);
        if (rushing && rushRoom != null) {
            for (DungeonRoom r : rooms()) if (rushRoom.equals(r.getName()) && names.add(r.getName())) { out.add(r); break; }
        }
        boolean all = cfg().admBrAllRooms || editing();
        out.removeIf(r -> !all && !onRush(r.getName()));
        return out;
    }

    // --- blood rush ---

    private static boolean rushing = false;
    private static String rushRoom = null;
    private static final Set<String> rushed = new HashSet<>();
    private static boolean startDoor = false;
    private static final List<int[]> barriers = new ArrayList<>();
    private static boolean witherSeen = false;
    private static Set<String> beforeFairy = null;

    private static boolean late(String name) {
        return beforeFairy != null && !beforeFairy.contains(name);
    }

    private static void reachFairy() {
        beforeFairy = new HashSet<>(rushed);
        if (cfg().admBrDebug) PracticeUtil.msg("§dBR debug §7the rush reached fairy §8(after " + rushed.size() + " rooms)");
        if (BrRoles.youDropped(true)) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.hud.setTimes(0, 40, 10);
            mc.gui.hud.setTitle(Component.literal("§cClear!"));
            if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
            PracticeUtil.msg("§dBR §7past fairy: you leave the rush §8— §fClear!");
        }
    }

    private static void doorFalling(DungeonRoom a, DungeonRoom b, int[][] door) {
        List<DungeonRoom> sides = new ArrayList<>();
        if (a != null) sides.add(a);
        if (b != null) sides.add(b);
        if (startDoor) {
            DungeonRoom entrance = null;
            for (DungeonRoom r : sides) if (r.getType() == RoomType.ENTRANCE) { entrance = r; break; }
            if (entrance == null || entrance.getName() == null) return;
            rushed.add(entrance.getName());
            startDoor = false;
        }
        if (witherSeen && beforeFairy == null) {
            for (DungeonRoom r : sides) if (r.getType() == RoomType.FAIRY) { reachFairy(); break; }
        }
        DungeonRoom next = null;
        for (DungeonRoom r : sides) {
            if (r.getName() == null || rushed.contains(r.getName())) continue;
            if (next == null || (next.getType() == RoomType.FAIRY && r.getType() != RoomType.FAIRY)) next = r;
        }
        if (next == null) return;
        rushRoom = next.getName();
        rushed.add(next.getName());
        entryDoors.put(next.getName(), new int[]{-185 + 16 * (door[0][0] + door[1][0]), -185 + 16 * (door[0][1] + door[1][1])});
    }

    /** Ported from ADM's DoorBlocks.kt: the doors in one tick's barrier burst, as their two tiles. */
    private static List<int[][]> doors(List<int[]> blocks) {
        List<List<int[]>> groups = new ArrayList<>();
        for (int[] p : blocks) {
            List<int[]> g = null;
            for (List<int[]> gr : groups) if (Math.abs(gr.get(0)[0] - p[0]) <= 4 && Math.abs(gr.get(0)[1] - p[1]) <= 4) { g = gr; break; }
            if (g != null) g.add(p); else { List<int[]> n = new ArrayList<>(); n.add(p); groups.add(n); }
        }
        List<int[][]> out = new ArrayList<>();
        for (List<int[]> g : groups) {
            if (g.size() < DOOR_BLOCKS) continue;
            double sx = 0, sz = 0;
            for (int[] p : g) { sx += p[0]; sz += p[1]; }
            double tx = (sx / g.size() + 185) / 32;
            double tz = (sz / g.size() + 185) / 32;
            if (Math.abs(tx - Math.round(tx)) < 0.1 && Math.abs(tz - Math.round(tz)) < 0.1) continue;
            out.add(new int[][]{
                    {(int) Math.floor(tx + 0.01), (int) Math.floor(tz + 0.01)},
                    {(int) Math.ceil(tx - 0.01), (int) Math.ceil(tz - 0.01)}});
        }
        return out;
    }

    private static final Set<DungeonRoom> witherSides = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private static Map<String, int[]> path = Map.of();
    private static Map<String, int[]> exits = Map.of();
    private static Map<String, int[]> entrances = Map.of();
    private static String lastRoom;

    private static boolean onRush(String name) {
        return name != null && (path.containsKey(name) || rushed.contains(name));
    }

    private record Link(DungeonRoom to, int[] at) {}

    private static void mapPath() {
        Map<DungeonRoom, List<Link>> links = new IdentityHashMap<>();
        worldDoors(links);
        for (int tz = 0; tz <= 5; tz++) for (int tx = 0; tx <= 5; tx++) for (int[] dir : new int[][]{{1, 0}, {0, 1}}) {
            int bx = tx + dir[0], bz = tz + dir[1];
            if (bx > 5 || bz > 5) continue;
            DungeonRoom a = DungeonManager.getRoomAt(tx, tz), b = DungeonManager.getRoomAt(bx, bz);
            if (a == null || b == null || a == b) continue;
            if (!DoorScanner.hasDoorBetween(tx, tz, bx, bz)) continue;
            DoorType t = DoorScanner.getDoorType(tx, tz, bx, bz);
            if (t == DoorType.WITHER || t == DoorType.BLOOD || DoorScanner.isKeyDoor(tx, tz, bx, bz)) { witherSides.add(a); witherSides.add(b); }
            int[] at = {-185 + 16 * (tx + bx), -185 + 16 * (tz + bz)};
            links.computeIfAbsent(a, k -> new ArrayList<>()).add(new Link(b, at));
            links.computeIfAbsent(b, k -> new ArrayList<>()).add(new Link(a, at));
        }
        DungeonRoom entrance = null;
        for (DungeonRoom r : rooms()) if (r.getType() == RoomType.ENTRANCE) { entrance = r; break; }
        if (entrance == null) return;
        Map<DungeonRoom, Link> back = new IdentityHashMap<>();
        ArrayDeque<DungeonRoom> queue = new ArrayDeque<>();
        queue.add(entrance);
        while (!queue.isEmpty()) {
            DungeonRoom r = queue.removeFirst();
            for (Link l : links.getOrDefault(r, List.of())) {
                if (l.to != entrance && !back.containsKey(l.to)) { back.put(l.to, new Link(r, l.at)); queue.add(l.to); }
            }
        }
        Map<String, int[]> out = new HashMap<>();
        Map<String, int[]> outOf = new HashMap<>();
        if (entrance.getName() != null) out.put(entrance.getName(), null);
        Set<DungeonRoom> ends = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        ends.addAll(witherSides);
        for (DungeonRoom r : rooms()) if (r.getType() == RoomType.BLOOD || (r.getName() != null && rushed.contains(r.getName()))) ends.add(r);
        for (DungeonRoom end : ends) {
            DungeonRoom r = end;
            int guard = 0;
            while (r != entrance && guard++ < 64) {
                Link prev = back.get(r);
                if (prev == null) break;
                if (r.getName() != null) out.put(r.getName(), prev.at);
                if (prev.to.getName() != null) outOf.put(prev.to.getName(), prev.at);
                r = prev.to;
            }
        }
        path = out;
        exits = outOf;
        Map<String, int[]> ent = new HashMap<>();
        for (Map.Entry<DungeonRoom, Link> e : back.entrySet()) if (e.getKey().getName() != null) ent.put(e.getKey().getName(), e.getValue().at);
        entrances = ent;
    }

    private static void worldDoors(Map<DungeonRoom, List<Link>> links) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        for (int tz = 0; tz <= 5; tz++) for (int tx = 0; tx <= 5; tx++) for (int[] dir : new int[][]{{1, 0}, {0, 1}}) {
            int dx = dir[0], dz = dir[1];
            if (tx + dx > 5 || tz + dz > 5) continue;
            DungeonRoom a = DungeonManager.getRoomAt(tx, tz), b = DungeonManager.getRoomAt(tx + dx, tz + dz);
            if (a == null || b == null || a == b) continue;
            int gx = -185 + 16 * (2 * tx + dx), gz = -185 + 16 * (2 * tz + dz);
            List<BlockPos> cells = new ArrayList<>();
            for (int d : new int[]{-2, -1, 1, 2}) for (int y : new int[]{70, 71}) cells.add(new BlockPos(gx + d * dx, y, gz + d * dz));
            boolean loaded = true;
            for (BlockPos c : cells) if (!level.isLoaded(c)) { loaded = false; break; }
            if (!loaded) continue;
            boolean open = true, wither = false;
            for (BlockPos c : cells) {
                BlockState s = level.getBlockState(c);
                boolean w = s.getBlock() == Blocks.COAL_BLOCK || s.getBlock() == Blocks.DYED_TERRACOTTA.red();
                if (w) wither = true;
                if (!(s.isAir() || s.getBlock() == Blocks.BARRIER || w)) { open = false; break; }
            }
            if (!open) continue;
            if (wither) { witherSides.add(a); witherSides.add(b); }
            int[] at = {gx, gz};
            links.computeIfAbsent(a, k -> new ArrayList<>()).add(new Link(b, at));
            links.computeIfAbsent(b, k -> new ArrayList<>()).add(new Link(a, at));
        }
    }

    private static void debugRoom(String name) {
        int[] door = entryOf(name);
        if (door == null) {
            PracticeUtil.msg("§dBR debug §f" + name + " §8— " + (onRush(name) ? "on the rush, door not known yet" : "not on the rush path"));
            return;
        }
        String how = walkedIn.containsKey(name) ? "you walked in" : path.get(name) != null ? "rush path"
                : entryDoors.containsKey(name) ? "wither door seen falling" : "off the rush, Entrance side";
        DungeonRoom room = placed(name);
        int[] rel = room == null ? null : relative(room, door[0], door[1]);
        String at = "§f" + name + " §7by door §f" + door[0] + ", " + door[1] + (rel != null ? " §8(room " + rel[0] + ", " + rel[1] + ")" : " §8(room not placed yet)") + " §8· " + how;
        boolean late = late(name);
        String role = "§7you: §f" + BrRoles.describe(late) + (late ? " §8(past fairy)" : "");
        List<Box> inRoom = new ArrayList<>();
        for (Box b : boxes) if (name.equals(b.room)) inRoom.add(b);
        int[] wither = room == null ? null : witherOf(name, room);
        BrRoles.Plan plan = !BrRoles.active() || rel == null ? null : BrRoles.planFor(name, rel[0], rel[1], wither, late);
        String shows;
        if (inRoom.isEmpty()) shows = "§8no boxes in this room";
        else if (!BrRoles.active()) shows = "§7showing §fall " + inRoom.size();
        else if (BrRoles.youDropped(late)) shows = "§7you left the rush to clear §8— showing §fall " + inRoom.size();
        else if (plan == null) shows = "§7no plan" + (BrRoles.doorerKills(late) && wither == null ? " §8(wither door not known)" : "") + " §8— showing §fall " + inRoom.size();
        else if (BrRoles.playing(late) == null) shows = "§7on the door §8— showing §fnone";
        else {
            List<int[]> mine = new ArrayList<>();
            List<String> stack = new ArrayList<>(), loose = new ArrayList<>();
            for (Box b : inRoom) {
                int n = number(b);
                BrRoles.Look l = BrRoles.look(plan, n, late);
                if (l instanceof BrRoles.Mine m) mine.add(new int[]{n, m.order()});
                else if (l instanceof BrRoles.Stack) stack.add(String.valueOf(n));
                else if (l == null) loose.add(String.valueOf(n));
            }
            mine.sort((x, y) -> Integer.compare(x[1], y[1]));
            List<String> ms = new ArrayList<>();
            for (int[] m : mine) ms.add(String.valueOf(m[0]));
            shows = "§7kill §a" + (ms.isEmpty() ? "none" : String.join(" → ", ms)) + " §7stack §6" + (stack.isEmpty() ? "none" : String.join(", ", stack))
                    + (loose.isEmpty() ? "" : " §7unassigned §d" + String.join(", ", loose));
        }
        PracticeUtil.msg("§dBR debug §7" + at + "\n  " + role + "\n  " + shows);
    }

    private static final Map<String, int[]> entryDoors = new HashMap<>();

    private static int[] entryOf(String name) {
        int[] d = walkedIn.get(name);
        if (d == null) d = path.get(name);
        if (d == null) d = entryDoors.get(name);
        if (d == null) d = entrances.get(name);
        return d;
    }

    private static final Map<String, int[]> walkedIn = new HashMap<>();
    private static int[] myTile;

    private static void trackMyDoor() {
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        int[] t = {Math.floorDiv(p.getBlockX() + 201, 32), Math.floorDiv(p.getBlockZ() + 201, 32)};
        int[] was = myTile;
        if (was != null && was[0] == t[0] && was[1] == t[1]) return;
        DungeonRoom room = tileRoom(t);
        if (room == null || room.getName() == null) return;
        myTile = t;
        if (was == null) return;
        DungeonRoom fromRoom = tileRoom(was);
        if (fromRoom == null || fromRoom.getName() == null) return;
        if (fromRoom.getName().equals(room.getName()) || Math.abs(was[0] - t[0]) + Math.abs(was[1] - t[1]) != 1) return;
        walkedIn.put(room.getName(), new int[]{-185 + 16 * (was[0] + t[0]), -185 + 16 * (was[1] + t[1])});
    }

    private record Drawn(Box box, int colour, String label, boolean next, boolean done) {}

    private static final Set<String> noPlanSaid = new HashSet<>();
    private static boolean wasInDungeon = false;

    private static List<Drawn> drawn() {
        List<Drawn> out = new ArrayList<>();
        List<Box> live = shown();
        Set<Box> liveSet = new HashSet<>(live);
        List<Box> all;
        if (cfg().admBrRecolorDone) {
            Set<String> names = new HashSet<>();
            for (DungeonRoom r : shownRooms()) names.add(r.getName());
            all = new ArrayList<>();
            for (Box b : boxes) if (b.room != null && names.contains(b.room)) all.add(b);
        } else all = live;
        Map<String, List<Box>> byRoom = new LinkedHashMap<>();
        for (Box b : all) byRoom.computeIfAbsent(b.room, k -> new ArrayList<>()).add(b);
        for (Map.Entry<String, List<Box>> e : byRoom.entrySet()) {
            String room = e.getKey();
            List<Box> list = e.getValue();
            boolean late = late(room);
            BrRoles.Plan plan = !BrRoles.active() || BrRoles.youDropped(late) ? null : planOf(room);
            if (plan == null) {
                for (Box b : list) out.add(new Drawn(b, PURPLE, "§d" + number(b), false, !liveSet.contains(b)));
                continue;
            }
            if (BrRoles.playing(late) == null) continue;
            List<Object[]> mine = new ArrayList<>();
            List<Box> stack = new ArrayList<>(), loose = new ArrayList<>();
            for (Box b : list) {
                BrRoles.Look l = BrRoles.look(plan, number(b), late);
                if (l instanceof BrRoles.Mine m) mine.add(new Object[]{b, m.order()});
                else if (l instanceof BrRoles.Stack) stack.add(b);
                else if (l == null) loose.add(b);
            }
            mine.sort((x, y) -> Integer.compare((Integer) x[1], (Integer) y[1]));
            Box next = null;
            for (Object[] m : mine) if (liveSet.contains((Box) m[0])) { next = (Box) m[0]; break; }
            for (Object[] m : mine) {
                Box b = (Box) m[0];
                out.add(new Drawn(b, GREEN, "§a" + m[1], b == next, !liveSet.contains(b)));
            }
            for (Box b : stack) out.add(new Drawn(b, GOLD, "§6stack", false, !liveSet.contains(b)));
            for (Box b : loose) out.add(new Drawn(b, PURPLE, "§d" + number(b), false, !liveSet.contains(b)));
        }
        return out;
    }

    private static BrRoles.Plan planOf(String name) {
        if (name == null) return null;
        boolean late = late(name);
        if (BrRoles.isMini(name)) {
            List<Integer> all = new ArrayList<>();
            for (Box b : boxes) if (name.equals(b.room)) all.add(number(b));
            int n = Math.max(1, BrRoles.planCount(late));
            List<List<Integer>> roles = new ArrayList<>(), stacks = new ArrayList<>();
            for (int i = 0; i < n; i++) { roles.add(all); stacks.add(List.of()); }
            return new BrRoles.Plan(roles, stacks);
        }
        int[] door = entryOf(name);
        if (door == null) return null;
        DungeonRoom room = placed(name);
        if (room == null) return null;
        int[] rel = relative(room, door[0], door[1]);
        if (rel == null) return null;
        BrRoles.Plan plan = BrRoles.planFor(name, rel[0], rel[1], witherOf(name, room), late);
        if (plan == null && cfg().admBrDebug && noPlanSaid.add(name + " " + door[0] + "," + door[1] + " " + late))
            PracticeUtil.msg("§dBR §7no roles for §f" + name + " §7from this door with §f" + BrRoles.planCount(late) + " §7on the plan yet §8— every box shows");
        return plan;
    }

    private static int[] witherOf(String name, DungeonRoom room) {
        int[] e = exits.get(name);
        return e == null ? null : relative(room, e[0], e[1]);
    }

    private static DungeonRoom tileRoom(int[] t) {
        if (t[0] < 0 || t[0] > 5 || t[1] < 0 || t[1] > 5) return null;
        return DungeonManager.getRoomAt(t[0], t[1]);
    }

    private static long boxesSig() {
        long h = boxes.size();
        for (Box b : boxes) h = h * 31 + System.identityHashCode(b) * 17L + Arrays.hashCode(b.c);
        return h;
    }

    private static Box cachedClaim(Mob mob, long sig) {
        if (!mob.claimed || mob.claimSig != sig) { mob.claim = claimOf(mob); mob.claimSig = sig; mob.claimed = true; }
        return mob.claim;
    }

    private static Box claimOf(Mob mob) {
        Box best = null;
        double most = 0.0;
        for (Box box : boxes) {
            double o = overlap(mob.spawn, box.aabb());
            if (o > most) { most = o; best = box; }
        }
        if (best != null || mob.room == null) return best;
        Box near = null;
        double nearD = Double.MAX_VALUE;
        for (Box b : boxes) {
            if (!mob.room.equals(b.room)) continue;
            double g = gap(mob.spawn, b.aabb());
            if (g <= CLAIM_REACH * CLAIM_REACH && g < nearD) { nearD = g; near = b; }
        }
        return near;
    }

    private static double gap(AABB a, AABB b) {
        double x = Math.max(0.0, Math.max(a.minX, b.minX) - Math.min(a.maxX, b.maxX));
        double y = Math.max(0.0, Math.max(a.minY, b.minY) - Math.min(a.maxY, b.maxY));
        double z = Math.max(0.0, Math.max(a.minZ, b.minZ) - Math.min(a.maxZ, b.maxZ));
        return x * x + y * y + z * z;
    }

    private static double overlap(AABB a, AABB b) {
        double x = Math.min(a.maxX, b.maxX) - Math.max(a.minX, b.minX);
        double y = Math.min(a.maxY, b.maxY) - Math.max(a.minY, b.minY);
        double z = Math.min(a.maxZ, b.maxZ) - Math.max(a.minZ, b.minZ);
        return x > 0 && y > 0 && z > 0 ? x * y * z : 0.0;
    }

    // --- render ---

    private static void render(PoseStack pose, Vec3 cam) {
        if (!DungeonManager.isInDungeon() || LegitMode.blocksCheats()) return;
        float opacity = cfg().admBrOpacity;
        int doneColor = TeslaMapsConfig.parseColor(cfg().admBrDoneColor);
        for (Drawn d : drawn()) {
            AABB bb = d.box.aabb();
            Vec3 label = new Vec3((bb.minX + bb.maxX) / 2, bb.maxY + 0.6, (bb.minZ + bb.maxZ) / 2);
            if (d.done) {
                if (!cfg().admBrRecolorDone) continue;
                ESPRenderer.drawFilledBox(pose, bb, alpha(doneColor, opacity), cam, true);
                ESPRenderer.drawBoxOutline(pose, bb, doneColor, 2f, cam, true);
                ESPRenderer.drawText(pose, "§7" + StringUtil.strip(d.label), label, 1.5f, cam, true, true);
                continue;
            }
            float a = d.next ? Math.max(opacity + 0.15f, Math.min(1f, opacity * 3)) : opacity;
            if (d.next) a = Math.min(1f, a);
            ESPRenderer.drawFilledBox(pose, bb, alpha(d.colour, a), cam, true);
            ESPRenderer.drawBoxOutline(pose, bb, d.colour, 2f, cam, true);
            ESPRenderer.drawText(pose, d.label, label, 1.5f, cam, true, true);
        }
        if (editing()) {
            Object[] t = target(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
            if (t != null) ESPRenderer.drawFilledBox(pose, faceSlab(((Box) t[0]).aabb(), (BoxFaces.Face) t[1]), alpha(PURPLE, 0.35f), cam, true);
        }
        if (cfg().admBrSpawnMarkers) {
            int c = TeslaMapsConfig.parseColor(cfg().colorESPStarred) | 0xFF000000;
            for (Mob mob : mobs) {
                double y = mob.y + 0.02;
                ESPRenderer.drawBoxOutline(pose, new AABB(mob.x - 0.5, y, mob.z - 0.5, mob.x + 0.5, y, mob.z + 0.5), c, 2f, cam, false);
            }
        }
    }

    private static int alpha(int rgb, float a) {
        return (Math.round(Math.max(0f, Math.min(1f, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    // --- wand editor (input from the mixins) ---

    public static boolean onDrop() {
        if (!editing()) return false;
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        Object[] t = target(1f);
        if (t != null) {
            Box box = (Box) t[0];
            boxes.remove(box);
            save(box.room);
            return true;
        }
        BlockPos feet = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        int[] c = {feet.getX(), feet.getY(), feet.getZ(), feet.getX() + 1, feet.getY() + 1, feet.getZ() + 1};
        for (Box b : boxes) if (Arrays.equals(b.c, c)) return true;
        DungeonRoom r = roomAt(player.getX(), player.getZ());
        Box box = new Box(c, r == null ? null : r.getName());
        boxes.add(box);
        if (!save(box.room)) PracticeUtil.msg("§eThis room isn't worked out yet; the box saves once it is.");
        return true;
    }

    public static boolean onAttack() {
        return move(1);
    }

    public static boolean blocksContinueAttack() {
        return editing() && target(1f) != null;
    }

    public static boolean onUse() {
        if (!editing() || target(1f) == null) return false;
        if (!useHeld) { useHeld = true; move(-1); }
        return true;
    }

    public static boolean onScroll(double y) {
        return y != 0.0 && move(y > 0 ? 1 : -1);
    }

    private static boolean move(int by) {
        if (!editing()) return false;
        Object[] t = target(1f);
        if (t == null) return false;
        Box box = (Box) t[0];
        Player p = Minecraft.getInstance().player;
        if (p != null && p.isShiftKeyDown()) { box.c[1] += by; box.c[4] += by; save(box.room); return true; }
        if (BoxFaces.move(box.c, (BoxFaces.Face) t[1], by)) save(box.room);
        return true;
    }

    private static boolean editing() {
        if (!on() || !cfg().admBrEditMode || cfg().admBrWand.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null || mc.player == null || !DungeonManager.isInDungeon()) return false;
        return identity(mc.player.getMainHandItem()).equals(cfg().admBrWand);
    }

    private static Object[] target(float partial) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return null;
        Vec3 e = player.getEyePosition(partial);
        Vec3 v = player.getViewVector(partial);
        double[] eye = {e.x, e.y, e.z};
        double[] dir = {v.x, v.y, v.z};
        double[] feet = {player.getX(), player.getY(), player.getZ()};
        float pitch = player.getViewXRot(partial);
        List<Box> shown = shown();
        for (Box box : shown) {
            double[] min = {box.c[0], box.c[1], box.c[2]};
            double[] max = {box.c[3], box.c[4], box.c[5]};
            if (BoxFaces.editsTop(feet, pitch, min, max)) return new Object[]{box, BoxFaces.Face.UP};
        }
        Object[] best = null;
        double bestT = REACH;
        for (Box box : shown) {
            double[] min = {box.c[0], box.c[1], box.c[2]};
            double[] max = {box.c[3], box.c[4], box.c[5]};
            Double t = BoxFaces.distance(eye, dir, min, max);
            if (t == null || t > bestT) continue;
            BoxFaces.Face face = BoxFaces.select(eye, dir, min, max);
            if (face == null) continue;
            best = new Object[]{box, face};
            bestT = t;
        }
        return best;
    }

    private static AABB faceSlab(AABB bb, BoxFaces.Face face) {
        double e = 0.02;
        return switch (face) {
            case EAST -> new AABB(bb.maxX - e, bb.minY, bb.minZ, bb.maxX + e, bb.maxY, bb.maxZ);
            case WEST -> new AABB(bb.minX - e, bb.minY, bb.minZ, bb.minX + e, bb.maxY, bb.maxZ);
            case SOUTH -> new AABB(bb.minX, bb.minY, bb.maxZ - e, bb.maxX, bb.maxY, bb.maxZ + e);
            case NORTH -> new AABB(bb.minX, bb.minY, bb.minZ - e, bb.maxX, bb.maxY, bb.minZ + e);
            case UP -> new AABB(bb.minX, bb.maxY - e, bb.minZ, bb.maxX, bb.maxY + e, bb.maxZ);
        };
    }

    private static void makeWand() {
        Player p = Minecraft.getInstance().player;
        ItemStack held = p == null ? null : p.getMainHandItem();
        if (held == null || held.isEmpty()) { PracticeUtil.msg("§cHold the item you want as the wand first."); return; }
        cfg().admBrWand = identity(held);
        TeslaMapsConfig.save();
        PracticeUtil.msg("§aWand set: §f" + held.getHoverName().getString());
    }

    private static void clearRoom() {
        DungeonRoom cur = DungeonManager.getCurrentRoom();
        String room = cur == null ? null : cur.getName();
        if (room == null) { PracticeUtil.msg("§cYou are not in a dungeon room."); return; }
        savedBoxes();
        int gone = 0;
        for (Box b : boxes) if (room.equals(b.room)) gone++;
        boxes.removeIf(b -> room.equals(b.room));
        saved.remove(room);
        write();
        PracticeUtil.msg("§aCleared §f" + gone + " §abox" + (gone == 1 ? "" : "es") + " from §f" + room + "§a.");
    }

    private static String identity(ItemStack stack) {
        if (stack.isEmpty()) return "";
        String uuid = ItemUtil.itemUuid(stack);
        if (uuid != null && !uuid.isEmpty()) return "uuid:" + uuid;
        String id = ItemUtil.skyblockId(stack);
        if (id != null && !id.isEmpty()) return "id:" + id;
        return "item:" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + stack.getHoverName().getString();
    }

    // --- rooms and saving ---

    private static java.util.Collection<DungeonRoom> rooms() {
        var grid = DungeonManager.getGrid();
        return grid == null ? List.of() : grid.getAllRooms();
    }

    private static DungeonRoom roomAt(double x, double z) {
        int tx = Math.floorDiv((int) Math.floor(x) + 200, 32);
        int tz = Math.floorDiv((int) Math.floor(z) + 200, 32);
        if (tx < 0 || tx > 5 || tz < 0 || tz > 5) return null;
        return DungeonManager.getRoomAt(tx, tz);
    }

    private record Clay(int x, int z, int rot, int comps) {}
    private static final Map<DungeonRoom, Clay> clayCache = new IdentityHashMap<>();
    private static final Map<DungeonRoom, Long> clayRetry = new IdentityHashMap<>();

    private static int tileCount(String shape) {
        if (shape == null) return -1;
        if (shape.equalsIgnoreCase("L")) return 3;
        String[] p = shape.toLowerCase().split("x");
        if (p.length != 2) return -1;
        try { return Integer.parseInt(p[0].trim()) * Integer.parseInt(p[1].trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    private static Clay clay(DungeonRoom room) {
        int comps = room.getComponents().size();
        Clay c = clayCache.get(room);
        if (c != null && c.comps == comps) return c;
        long now = System.currentTimeMillis();
        Long retry = clayRetry.get(room);
        if (retry != null && now < retry && (c == null || c.comps == comps)) return null;
        int[] s = DungeonWaypoints.scanClayPos(room);
        if (s == null) { clayRetry.put(room, now + 1000); return null; }
        c = new Clay(s[0], s[1], s[2], comps);
        clayCache.put(room, c);
        return c;
    }

    /** A room worked out well enough to turn room coordinates into world ones (Odin's convention). */
    private static DungeonRoom placed(String name) {
        for (DungeonRoom r : rooms()) {
            if (!name.equals(r.getName()) || r.getRoomData() == null) continue;
            int want = tileCount(r.getRoomData().getShape());
            if (want > 0 && r.getComponents().size() != want) continue;
            if (clay(r) == null) continue;
            return r;
        }
        return null;
    }

    private static String transform(DungeonRoom room) {
        Clay c = clay(room);
        return c == null ? "" : c.rot + "|" + c.x + "," + c.z;
    }

    private static int[] relative(DungeonRoom room, int x, int z) {
        Clay c = clay(room);
        return c == null ? null : DungeonWaypoints.worldToRelative(new int[]{c.x, c.z, c.rot}, x, z);
    }

    private static int[] real(DungeonRoom room, int x, int z) {
        Clay c = clay(room);
        return c == null ? null : DungeonWaypoints.relativeToWorld(new int[]{c.x, c.z, c.rot}, x, z);
    }

    private static void loadRooms() {
        Map<String, List<int[]>> s = savedBoxes();
        Set<String> names = new HashSet<>(s.keySet());
        for (Box b : boxes) if (!b.saved && b.room != null) names.add(b.room);
        for (String name : names) {
            DungeonRoom room = placed(name);
            if (room == null) continue;
            String t = transform(room);
            if (t.equals(loadedRooms.get(name))) continue;
            boxes.removeIf(b -> name.equals(b.room) && b.saved);
            List<int[]> list = new ArrayList<>(s.getOrDefault(name, List.of()));
            list.sort((x, y) -> Integer.compare(x.length > 6 ? x[6] : 0, y.length > 6 ? y[6] : 0));
            for (int[] r : list) {
                int[] a = real(room, r[0], r[2]);
                int[] b = real(room, r[3], r[5]);
                if (a == null || b == null) continue;
                Box box = new Box(new int[]{
                        Math.min(a[0], b[0]), Math.min(r[1], r[4]), Math.min(a[1], b[1]),
                        Math.max(a[0], b[0]) + 1, Math.max(r[1], r[4]) + 1, Math.max(a[1], b[1]) + 1}, name);
                box.saved = true;
                boxes.add(box);
            }
            loadedRooms.put(name, t);
            for (Box b : boxes) if (name.equals(b.room) && !b.saved) { save(name); break; }
        }
    }

    private static boolean save(String name) {
        if (name == null) return false;
        DungeonRoom room = placed(name);
        if (room == null) return false;
        if (!transform(room).equals(loadedRooms.get(name))) return false;
        List<int[]> list = new ArrayList<>();
        int i = 0;
        for (Box box : boxes) {
            if (!name.equals(box.room)) continue;
            int[] a = relative(room, box.c[0], box.c[2]);
            int[] b = relative(room, box.c[3] - 1, box.c[5] - 1);
            if (a == null || b == null) return false;
            list.add(new int[]{a[0], box.c[1], a[1], b[0], box.c[4] - 1, b[1], ++i});
            box.saved = true;
        }
        Map<String, List<int[]>> s = savedBoxes();
        if (list.isEmpty()) s.remove(name); else s.put(name, list);
        write();
        return true;
    }

    private static int number(Box box) {
        int n = 0;
        for (Box b : boxes) {
            if (!Objects.equals(b.room, box.room)) continue;
            n++;
            if (b == box) return n;
        }
        return 0;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("adm").resolve("brboxes.json");
    }

    /** The boxes: the local copy once edited, else the bundled site copy. */
    private static Map<String, List<int[]>> savedBoxes() {
        if (saved != null) return saved;
        Map<String, List<int[]>> out = null;
        Path f = file();
        if (Files.exists(f)) {
            try {
                out = rooms(JsonParser.parseString(Files.readString(f)));
            } catch (Exception e) {
                TeslaMaps.LOGGER.info("[adm] brboxes: file unreadable: {}", e.toString());
            }
        }
        if (out == null) {
            try (InputStream in = BrBoxes.class.getResourceAsStream("/assets/teslamaps/adm/brboxes.json")) {
                if (in != null) {
                    JsonObject site = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    out = siteBoxes(rooms(site.get("rooms")));
                }
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[adm] brboxes: bundled boxes unreadable: {}", e.toString());
            }
        }
        saved = out != null ? out : new HashMap<>();
        return saved;
    }

    private static Map<String, List<int[]>> rooms(JsonElement json) {
        Map<String, List<int[]>> out = new HashMap<>();
        if (json == null || !json.isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
            if (!e.getValue().isJsonArray()) continue;
            List<int[]> list = new ArrayList<>();
            for (JsonElement b : e.getValue().getAsJsonArray()) {
                if (!b.isJsonArray()) continue;
                try {
                    var a = b.getAsJsonArray();
                    int[] r = new int[a.size()];
                    for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsInt();
                    list.add(r);
                } catch (Exception ignored) {
                }
            }
            out.put(e.getKey(), list);
        }
        return out;
    }

    private static Map<String, List<int[]>> siteBoxes(Map<String, List<int[]>> rooms) {
        Map<String, List<int[]>> out = new HashMap<>();
        int n = 0;
        for (Map.Entry<String, List<int[]>> e : rooms.entrySet()) {
            if (n >= 5000) break;
            String name = e.getKey();
            if (name.isEmpty() || name.length() > 64) continue;
            List<int[]> ok = new ArrayList<>();
            for (int[] b : e.getValue()) {
                if (n + ok.size() >= 5000) break;
                if (b.length != 7) continue;
                boolean fits = true;
                for (int i = 0; i < 3; i++) if (Math.abs((long) b[i + 3] - b[i]) > 64) fits = false;
                if (fits) ok.add(b);
            }
            n += ok.size();
            if (!ok.isEmpty()) out.put(name, ok);
        }
        return out;
    }

    private static void write() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, new GsonBuilder().setPrettyPrinting().create().toJson(savedBoxes()));
        } catch (Exception e) {
            PracticeUtil.msg("§cCould not save BR Roles boxes: " + e.getMessage());
        }
    }
}
