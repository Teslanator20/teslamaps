package com.teslamaps.esp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.CheckmarkState;
import com.teslamaps.map.DoorType;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EspStyle;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.scanner.DoorScanner;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Door ESP helpers: wither/blood doors in route order toward blood (IllegalMap WitherDoorEsp)
 * and the normal doorways of the current room with "hide useless" (Devonian BoxDoors).
 */
public final class DoorRoute {
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public record Door(DungeonRoom a, DungeonRoom b, double x, double z, DoorType type) {}

    private static final List<double[]> routeAhead = new ArrayList<>();
    private static boolean routeKnown = false;
    private static final List<AABB> normalDoors = new ArrayList<>();
    private static int tickCounter = 0;

    private DoorRoute() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DoorRoute").section("ESP").legit()
                .when(() -> cfg().doorESP && (cfg().doorEspRoute || cfg().doorEspNormalDoors))
                .tick(DoorRoute::tick)
                .world(DoorRoute::render);
    }

    public static void tick() {
        if (!DungeonManager.isInDungeon()) {
            routeKnown = false;
            routeAhead.clear();
            normalDoors.clear();
            return;
        }
        if (tickCounter++ % 5 != 0) return;
        DungeonRoom current = DungeonManager.getCurrentRoom();
        if (cfg().doorEspRoute) buildRoute(current);
        else { routeKnown = false; routeAhead.clear(); }
        if (cfg().doorEspNormalDoors) buildNormalDoors(current);
        else normalDoors.clear();
    }

    /** Route mode active and a path entrance -> blood was found. */
    public static boolean routeActive() {
        return cfg().doorEspRoute && routeKnown;
    }

    public static boolean isAhead(AABB box) {
        Vec3 c = box.getCenter();
        for (double[] p : routeAhead) {
            if (Math.abs(p[0] - c.x) <= 3 && Math.abs(p[1] - c.z) <= 3) return true;
        }
        return false;
    }

    /** The door box touches the room the player stands in. */
    public static boolean atCurrentRoom(AABB box) {
        DungeonRoom current = DungeonManager.getCurrentRoom();
        if (current == null) return false;
        Vec3 c = box.getCenter();
        int[][] probes = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        for (int[] p : probes) {
            int[] g = ComponentGrid.worldToGrid(c.x + p[0], c.z + p[1]);
            if (g != null && DungeonManager.getGrid().getRoom(g[0], g[1]) == current) return true;
        }
        return false;
    }

    private static List<Door> doorsOf(DungeonRoom room) {
        List<Door> out = new ArrayList<>();
        ComponentGrid grid = DungeonManager.getGrid();
        for (int[] c : room.getComponents()) {
            for (int[] d : DIRS) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (!grid.isValidGridPos(nx, nz)) continue;
                DungeonRoom other = grid.getRoom(nx, nz);
                if (other == null || other == room) continue;
                DoorType t = DoorScanner.getDoorType(c[0], c[1], nx, nz);
                if (t == DoorType.NONE) continue;
                double x = ComponentGrid.DUNGEON_MIN_X + ComponentGrid.HALF_ROOM_SIZE + (c[0] + nx) * 16 + 0.5;
                double z = ComponentGrid.DUNGEON_MIN_Z + ComponentGrid.HALF_ROOM_SIZE + (c[1] + nz) * 16 + 0.5;
                out.add(new Door(room, other, x, z, t));
            }
        }
        return out;
    }

    // IllegalMap searchForDoors: path entrance -> blood, then the next N wither/blood doors from the player's room
    private static void buildRoute(DungeonRoom current) {
        routeAhead.clear();
        routeKnown = false;
        DungeonRoom entrance = null, blood = null;
        for (DungeonRoom r : DungeonManager.getGrid().getAllRooms()) {
            if (r.getType() == RoomType.ENTRANCE) entrance = r;
            else if (r.getType() == RoomType.BLOOD) blood = r;
        }
        if (entrance == null || blood == null) return;

        Map<DungeonRoom, Door> via = new IdentityHashMap<>();
        ArrayDeque<DungeonRoom> queue = new ArrayDeque<>();
        via.put(entrance, null);
        queue.add(entrance);
        while (!queue.isEmpty() && !via.containsKey(blood)) {
            DungeonRoom r = queue.poll();
            for (Door d : doorsOf(r)) {
                if (via.containsKey(d.b())) continue;
                via.put(d.b(), d);
                queue.add(d.b());
            }
        }
        if (!via.containsKey(blood)) return;

        List<Door> path = new ArrayList<>();
        for (DungeonRoom r = blood; via.get(r) != null; r = via.get(r).a()) path.add(via.get(r));
        Collections.reverse(path);

        List<Door> keyDoors = new ArrayList<>();
        boolean onPath = false;
        for (Door d : path) {
            if (d.a() == current) onPath = true;
            if (d.type() == DoorType.WITHER || d.type() == DoorType.BLOOD) keyDoors.add(d);
        }
        routeKnown = true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int ahead = Math.round(cfg().doorEspAhead);
        boolean reached = !onPath;
        for (Door d : path) {
            if (d.a() == current) reached = true;
            if (!reached || !keyDoors.contains(d)) continue;
            if (d.b().isExplored()) continue;
            double dx = mc.player.getX() - d.x(), dz = mc.player.getZ() - d.z();
            if (dx * dx + dz * dz <= 25) continue;
            if (routeAhead.size() >= ahead) break;
            routeAhead.add(new double[]{d.x(), d.z()});
        }
    }

    // Devonian BoxDoors: normal doorways of the current room, minus ones that only lead to green rooms
    private static void buildNormalDoors(DungeonRoom current) {
        normalDoors.clear();
        if (current == null) return;
        for (Door d : doorsOf(current)) {
            if (d.type() != DoorType.NORMAL && d.type() != DoorType.ENTRANCE) continue;
            if (d.b().getType() == RoomType.FAIRY && !d.b().isExplored()) continue;
            if (cfg().doorEspHideUseless) {
                Set<DungeonRoom> visited = Collections.newSetFromMap(new IdentityHashMap<>());
                visited.add(current);
                visited.add(d.b());
                if (!isRoomUseful(d.b(), visited)) continue;
            }
            normalDoors.add(new AABB(d.x() - 1.5, 69, d.z() - 1.5, d.x() + 1.5, 73, d.z() + 1.5));
        }
    }

    private static boolean isRoomUseful(DungeonRoom room, Set<DungeonRoom> visited) {
        if (room.getType() == RoomType.ENTRANCE) return false;
        if (room.getCheckmarkState() != CheckmarkState.GREEN) return true;
        for (Door d : doorsOf(room)) {
            if (visited.add(d.b()) && isRoomUseful(d.b(), visited)) return true;
        }
        return false;
    }

    public static void render(PoseStack pose, Vec3 cam) {
        if (!cfg().doorEspNormalDoors || normalDoors.isEmpty() || !cfg().doorEspClasses.allowsLocal()) return;
        EspStyle style = EspStyle.of(cfg().doorEspNormalStyle);
        for (AABB box : normalDoors) style.drawBox(pose, box, cam);
    }
}
