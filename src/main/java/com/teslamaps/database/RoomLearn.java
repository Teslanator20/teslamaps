package com.teslamaps.database;

// IllegalMap NewRoomCommand.js (/newroom, /newcore), FunnyMapExtras /fm roomdata
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.scanner.CoreHasher;
import com.teslamaps.scanner.DoorScanner;
import com.teslamaps.scanner.RoomScanner;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class RoomLearn {

    private static final String[] TYPES = {"normal", "puzzle", "trap", "yellow", "blood", "fairy", "rare", "entrance"};

    public static LiteralArgumentBuilder<FabricClientCommandSource> newCoreCommand() {
        return ClientCommands.literal("newcore")
                .then(ClientCommands.argument("room", StringArgumentType.greedyString())
                        .suggests((c, b) -> {
                            String rem = b.getRemaining().toLowerCase();
                            for (RoomData r : RoomDatabase.getInstance().getAllRooms()) {
                                if (r.getName().toLowerCase().startsWith(rem) && !r.getName().equalsIgnoreCase(rem)) b.suggest(r.getName());
                            }
                            return b.buildFuture();
                        })
                        .executes(c -> newCore(c.getSource(), StringArgumentType.getString(c, "room"))));
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> newRoomCommand() {
        return ClientCommands.literal("newroom")
                .then(ClientCommands.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (String t : TYPES) if (t.startsWith(b.getRemaining().toLowerCase())) b.suggest(t);
                            return b.buildFuture();
                        })
                        .then(ClientCommands.argument("secrets", IntegerArgumentType.integer(0, 20))
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .executes(c -> newRoom(c.getSource(), StringArgumentType.getString(c, "type"),
                                                IntegerArgumentType.getInteger(c, "secrets"), StringArgumentType.getString(c, "name"))))));
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> roomDataCommand() {
        return ClientCommands.literal("roomdata").executes(c -> roomData(c.getSource()));
    }

    private static int newCore(FabricClientCommandSource src, String name) {
        RoomData target = RoomDatabase.getInstance().findByName(name.trim());
        if (target == null) {
            src.sendFeedback(Component.literal("§c[TeslaMaps] No room named '" + name + "'. Use /tmap newroom for a new one."));
            return 0;
        }
        List<int[]> cells = currentCells(src);
        if (cells == null) return 0;
        List<Integer> cores = cores(src, cells, target);
        if (cores == null) return 0;
        if (cores.isEmpty()) {
            src.sendFeedback(Component.literal("§e[TeslaMaps] All cores here already belong to " + target.getName()));
            relabel(target, cells);
            return 1;
        }
        RoomDatabase.getInstance().learn(new RoomData(target.getName(), null, 0, cores, -1, null));
        relabel(target, cells);
        src.sendFeedback(Component.literal("§a[TeslaMaps] Added " + cores.size() + " core(s) to " + target.getName()
                + " §7(config/teslamaps/rooms_custom.json)"));
        return 1;
    }

    private static int newRoom(FabricClientCommandSource src, String type, int secrets, String name) {
        name = name.trim();
        type = type.toLowerCase();
        if (RoomType.fromString(type) == RoomType.UNKNOWN) {
            src.sendFeedback(Component.literal("§c[TeslaMaps] Unknown type '" + type + "'"));
            return 0;
        }
        if (RoomDatabase.getInstance().findByName(name) != null) {
            src.sendFeedback(Component.literal("§c[TeslaMaps] '" + name + "' already exists. Use /tmap newcore " + name));
            return 0;
        }
        List<int[]> cells = currentCells(src);
        if (cells == null) return 0;
        List<Integer> cores = cores(src, cells, null);
        if (cores == null) return 0;
        RoomDatabase db = RoomDatabase.getInstance();
        RoomData entry = new RoomData(name, type, secrets, cores, db.nextId(), shape(cells));
        db.learn(entry);
        relabel(entry, cells);
        src.sendFeedback(Component.literal("§a[TeslaMaps] New room " + name + " §7(id " + entry.getRoomID() + ", "
                + entry.getShape() + ", " + cores.size() + " core(s)) §asaved to rooms_custom.json"));
        return 1;
    }

    private static int roomData(FabricClientCommandSource src) {
        List<int[]> cells = currentCells(src);
        if (cells == null) return 0;
        StringBuilder sb = new StringBuilder();
        for (int[] c : cells) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(CoreHasher.scanRoomCore(c[0], c[1]));
        }
        DungeonRoom room = DungeonManager.getCurrentRoom();
        String name = room != null && room.getRoomData() != null ? room.getRoomData().getName() : "Unknown";
        String text = "{\"name\": \"" + name + "\", \"shape\": \"" + shape(cells) + "\", \"cores\": [" + sb + "]}";
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        src.sendFeedback(Component.literal("§a[TeslaMaps] Copied: §7" + text));
        return 1;
    }

    // cells of the room the player stands in; an unknown room is flood-filled over its open connectors
    private static List<int[]> currentCells(FabricClientCommandSource src) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !DungeonManager.isInDungeon()) {
            src.sendFeedback(Component.literal("§c[TeslaMaps] Stand inside a dungeon room"));
            return null;
        }
        int[] start = ComponentGrid.worldToGrid(mc.player.getX(), mc.player.getZ());
        if (start == null) {
            src.sendFeedback(Component.literal("§c[TeslaMaps] Not inside the room grid"));
            return null;
        }
        DungeonRoom room = DungeonManager.getGrid().getRoom(start[0], start[1]);
        List<int[]> cells = new ArrayList<>();
        if (room != null && room.isIdentified() && !"Unknown".equals(room.getName())) cells.addAll(room.getComponents());
        else cells.add(start);
        for (int i = 0; i < cells.size() && cells.size() < 4; i++) {
            int[] c = cells.get(i);
            int[][] nb = {{c[0] + 1, c[1]}, {c[0] - 1, c[1]}, {c[0], c[1] + 1}, {c[0], c[1] - 1}};
            for (int[] n : nb) {
                if (!DungeonManager.getGrid().isValidGridPos(n[0], n[1]) || contains(cells, n)) continue;
                DungeonRoom other = DungeonManager.getGrid().getRoom(n[0], n[1]);
                if (other != null && other.isIdentified() && !"Unknown".equals(other.getName())) continue;
                if (cells.size() < 4 && connected(c, n)) cells.add(n);
            }
        }
        return cells;
    }

    private static boolean contains(List<int[]> cells, int[] p) {
        for (int[] c : cells) if (c[0] == p[0] && c[1] == p[1]) return true;
        return false;
    }

    // same room = the gap between the two cells is built up to the roof, no door
    private static boolean connected(int[] a, int[] b) {
        if (DoorScanner.hasDoorBetween(a[0], a[1], b[0], b[1])) return false;
        Minecraft mc = Minecraft.getInstance();
        int[] ca = ComponentGrid.gridToWorld(a[0], a[1]);
        int[] cb = ComponentGrid.gridToWorld(b[0], b[1]);
        int mx = (ca[0] + cb[0]) / 2, mz = (ca[1] + cb[1]) / 2;
        int roofA = top(mc, ca[0], ca[1]), roofB = top(mc, cb[0], cb[1]), gap = top(mc, mx, mz);
        return roofA > 0 && roofB > 0 && gap >= Math.min(roofA, roofB) - 4;
    }

    private static int top(Minecraft mc, int x, int z) {
        for (int y = 255; y > 0; y--) {
            var st = mc.level.getBlockState(new BlockPos(x, y, z));
            if (st.isAir() || st.is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK)) continue;
            return y;
        }
        return 0;
    }

    private static List<Integer> cores(FabricClientCommandSource src, List<int[]> cells, RoomData target) {
        List<Integer> out = new ArrayList<>();
        for (int[] c : cells) {
            int core = CoreHasher.scanRoomCore(c[0], c[1]);
            if (core == 0) {
                src.sendFeedback(Component.literal("§c[TeslaMaps] Cell [" + c[0] + "," + c[1] + "] is not loaded"));
                return null;
            }
            RoomData known = RoomDatabase.getInstance().findByCore(core);
            if (known != null && known == target) continue;
            if (known != null) {
                src.sendFeedback(Component.literal("§e[TeslaMaps] Core " + core + " was matched to " + known.getName() + ", now reassigned"));
            }
            if (!out.contains(core)) out.add(core);
        }
        return out;
    }

    private static String shape(List<int[]> cells) {
        int minX = 99, maxX = -1, minZ = 99, maxZ = -1;
        for (int[] c : cells) {
            minX = Math.min(minX, c[0]); maxX = Math.max(maxX, c[0]);
            minZ = Math.min(minZ, c[1]); maxZ = Math.max(maxZ, c[1]);
        }
        int w = maxX - minX + 1, h = maxZ - minZ + 1, n = cells.size();
        if (n == 3 && w == 2 && h == 2) return "L";
        if (n == 4 && w == 2 && h == 2) return "2x2";
        return "1x" + n;
    }

    // redraw right away: one identified room over the learned cells
    private static void relabel(RoomData data, List<int[]> cells) {
        DungeonRoom room = new DungeonRoom(cells.get(0)[0], cells.get(0)[1]);
        for (int[] c : cells) room.addComponent(c[0], c[1]);
        room.loadFromRoomData(data);
        DungeonRoom old = DungeonManager.getGrid().getRoom(cells.get(0)[0], cells.get(0)[1]);
        if (old != null) {
            room.setCheckmarkState(old.getCheckmarkState());
            room.setExplored(old.isExplored());
        }
        DungeonManager.addRoom(room);
        RoomScanner.detectAndSetRotation(room);
    }
}
