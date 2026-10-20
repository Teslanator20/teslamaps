package com.teslamaps.adm.map;

// Ported from Odin's dungeon/map DungeonMap.kt + MapRenderer.kt (the ADM map draws on its own, ADM's look on top)
import com.teslamaps.adm.map.AdmMapScan.Check;
import com.teslamaps.adm.map.AdmMapScan.DType;
import com.teslamaps.adm.map.AdmMapScan.Door;
import com.teslamaps.adm.map.AdmMapScan.RType;
import com.teslamaps.adm.map.AdmMapScan.Room;
import com.teslamaps.adm.map.pred.MapStats;
import com.teslamaps.adm.map.pred.Pred.RoomName;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.ClassColors;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.render.PlayerHeadRenderer;
import com.teslamaps.utils.ItemUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

public final class AdmMapRender {
    static final int ROOM = 16, TILE = 20, MAP_PX = 128;

    private static final Identifier MARKER = Identifier.withDefaultNamespace("textures/map/decorations/frame.png");
    private static final Identifier CROSS = Identifier.fromNamespaceAndPath("teslamaps", "adm/odinmap/cross.png");
    private static final Identifier GREEN = Identifier.fromNamespaceAndPath("teslamaps", "adm/odinmap/green_check.png");
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("teslamaps", "adm/odinmap/white_check.png");
    static final Identifier QUESTION = Identifier.fromNamespaceAndPath("teslamaps", "adm/odinmap/question.png");

    static int lastW = MAP_PX, lastH = MAP_PX;

    private AdmMapRender() {}

    static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    static int col(String hex) { return TeslaMapsConfig.parseColor(hex); }

    /** Odin's Color.darker: brightness times the factor, alpha kept. */
    static int darker(int c, float f) {
        return (c & 0xFF000000) | ((int) (((c >> 16) & 255) * f) << 16) | ((int) (((c >> 8) & 255) * f) << 8) | (int) ((c & 255) * f);
    }

    static int typeColor(RType t) {
        TeslaMapsConfig c = cfg();
        return switch (t) {
            case NORMAL, RARE -> col(c.admMapNormalRoom);
            case PUZZLE -> col(c.admMapPuzzleRoom);
            case TRAP -> col(c.admMapTrapRoom);
            case BLOOD -> col(c.admMapBloodRoom);
            case ENTRANCE -> col(c.admMapEntranceRoom);
            case FAIRY -> col(c.admMapFairyRoom);
            case CHAMPION -> col(c.admMapChampionRoom);
            default -> 0xFF3C3C3C;
        };
    }

    public static void hud(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || AdmMap.hud == null) return;
        g.pose().pushMatrix();
        g.pose().translate(AdmMap.hud.x(), AdmMap.hud.y());
        float s = AdmMap.hud.scale();
        g.pose().scale(s, s);
        draw(g, false);
        g.pose().popMatrix();
    }

    public static void preview(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        draw(g, true);
        g.pose().popMatrix();
    }

    /** Draws the whole map HUD at 0,0; its size is kept for /tmap gui. */
    static void draw(GuiGraphicsExtractor g, boolean example) {
        if (LcmStyle.active()) {
            int[] size = LcmStyle.render(g, example);
            if (size != null) {
                if (size[0] > 0) { lastW = size[0]; lastH = size[1]; }
                return;
            }
        }
        if (!example && (!DungeonManager.isInDungeon() || (cfg().admMapDisableInBoss && DungeonManager.isInBossArea()))) return;
        lastW = MAP_PX; lastH = MAP_PX;
        g.fill(0, 0, MAP_PX, MAP_PX, col(cfg().admMapBgColor));
        hollow(g, 0, 0, MAP_PX, MAP_PX, col(cfg().admMapBgOutline));
        g.pose().pushMatrix();
        if (example) {
            g.pose().translate(5f, 5f);
            renderMap(g, Example.rooms(), Example.doors(), List.of(), true);
        } else {
            g.pose().translate(AdmMapScan.startX, AdmMapScan.startY);
            float s = AdmMapScan.roomSize / 16f;
            g.pose().scale(s, s);
            renderMap(g, AdmMapScan.rooms, AdmMapScan.doors.values(), AdmMapScan.pathHints, false);
            if (!DungeonManager.isInBossArea()) renderPlayers(g);
        }
        g.pose().popMatrix();
    }

    private static void hollow(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y + 1, x + 1, y + h - 1, c);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    private static void renderMap(GuiGraphicsExtractor g, List<Room> rooms, Iterable<Door> doors, List<Integer> hints, boolean example) {
        for (Door d : doors) renderDoor(g, d, doorColor(d, example));
        for (Room r : rooms) if (r.viewable()) fillTiles(g, r.tiles, typeColor(r.type));
        for (Room r : rooms) renderRoomText(g, r);
        if (example || !MapPrediction.render(g, false)) renderPathHints(g, hints);
    }

    static void fillTiles(GuiGraphicsExtractor g, List<int[]> tiles, int color) {
        for (int[] t : tiles) {
            int x = t[0] * TILE, z = t[1] * TILE;
            g.fill(x, z, x + ROOM, z + ROOM, color);
            boolean right = has(tiles, t[0] + 1, t[1]), down = has(tiles, t[0], t[1] + 1);
            if (right) g.fill(x + ROOM, z, x + TILE, z + ROOM, color);
            if (down) g.fill(x, z + ROOM, x + ROOM, z + TILE, color);
            if (right && down && has(tiles, t[0] + 1, t[1] + 1)) g.fill(x + ROOM, z + ROOM, x + TILE, z + TILE, color);
        }
    }

    static boolean has(List<int[]> tiles, int x, int z) {
        for (int[] t : tiles) if (t[0] == x && t[1] == z) return true;
        return false;
    }

    static Room tileRoom(int i) { return i >= 0 && i < 36 ? AdmMapScan.tiles[i] : null; }

    /** Odin's door colours (DungeonScan.updateViewableDoors). */
    private static int doorColor(Door d, boolean example) {
        TeslaMapsConfig c = cfg();
        if (example) return d.exampleColor;
        Room o = tileRoom(d.origin()), t = tileRoom(d.dest());
        boolean ov = o != null && o.viewable(), tv = t != null && t.viewable();
        if (!ov && !tv) return 0xFFFFFFFF;
        return switch (d.type) {
            case WITHER -> col(c.admMapWitherDoor);
            case BLOOD -> !ov || !tv ? darker(col(c.admMapBloodDoor), 0.5f) : col(c.admMapBloodDoor);
            case FAIRY -> col(c.admMapFairyDoor);
            default -> {
                if (!ov || !tv) yield col(c.admMapUnknownDoor);
                for (Room r : new Room[]{o, t}) if (r.type != RType.NORMAL && r.type != RType.FAIRY) yield typeColor(r.type);
                yield col(c.admMapNormalDoor);
            }
        };
    }

    private static void renderDoor(GuiGraphicsExtractor g, Door d, int color) {
        int ox = d.horizontal ? 1 : 0, oz = d.horizontal ? 0 : 1;
        float half = (ROOM - 8) / 2f;
        g.pose().pushMatrix();
        g.pose().translate(d.x * TILE + ox * ROOM + oz * half, d.z * TILE + oz * ROOM + ox * half);
        g.fill(0, 0, 4 + 4 * oz, 4 + 4 * ox, color);
        g.pose().popMatrix();
    }

    static void icon(GuiGraphicsExtractor g, int x, int z, Identifier id) {
        int size = ROOM - 4;
        g.blit(RenderPipelines.GUI_TEXTURED, id, x + 2, z + 2, 0f, 0f, size, size, size, size);
    }

    private static void renderRoomText(GuiGraphicsExtractor g, Room room) {
        RType t = room.type;
        if (t == RType.UNDISCOVERED || t == RType.FAIRY || t == RType.ENTRANCE || t == RType.BLOOD) return;
        String mode = cfg().admMapRoomText;
        if (!room.walkedInto || ("Room Secrets".equals(mode) && room.maxSecrets == 0)) {
            Identifier tex = switch (room.checkmark) {
                case GREEN -> GREEN;
                case WHITE -> WHITE;
                case RED -> CROSS;
                default -> null;
            };
            if (tex != null) icon(g, room.tlx * TILE, room.tlz * TILE, tex);
            return;
        }
        int[] c = room.center();
        if (c == null) return;
        var font = Minecraft.getInstance().font;
        int fontH = font.lineHeight;
        int color = switch (room.checkmark) {
            case GREEN -> 0xFF55FF55;
            case WHITE -> 0xFFFFFFFF;
            case RED -> 0xFFFF5555;
            default -> 0xFF646464;
        };
        String secrets = room.maxSecrets > 0 ? " " + (room.foundSecrets >= 0 ? String.valueOf(room.foundSecrets) : "?") + "/" + room.maxSecrets : "";
        String text = switch (mode) {
            case "Room Name" -> room.name;
            case "Room Secrets" -> secrets;
            default -> room.name == null ? null : room.name + secrets;
        };
        if (text == null) return;
        String[] lines = text.trim().split(" ");
        float scale = cfg().admMapTextScaling;
        float totalH = (lines.length - 1) * fontH * scale;
        for (int i = 0; i < lines.length; i++) {
            g.pose().pushMatrix();
            g.pose().translate(c[0], c[1] - totalH / 2f + i * fontH * scale);
            g.pose().scale(scale, scale);
            g.centeredText(font, lines[i], 0, -fontH / 2, color);
            g.pose().popMatrix();
        }
    }

    private static void renderPathHints(GuiGraphicsExtractor g, List<Integer> hints) {
        TeslaMapsConfig c = cfg();
        for (int t : hints) {
            Room room = tileRoom(t);
            if (room != null && room.walkedInto) continue;
            int x = (t % 6) * TILE, y = (t / 6) * TILE;
            int[] colors;
            if (room != null && room.type == RType.BLOOD) colors = new int[]{darker(col(c.admMapBloodRoom), 0.5f)};
            else if (!c.admMapDisablePred && room != null && room.known1x1) colors = guessColors(t % 6);
            else colors = new int[]{col(c.admMapUnknownRoom)};
            if (colors.length == 1) g.fill(x, y, x + ROOM, y + ROOM, colors[0]);
            else if (colors.length == 2) {
                int half = ROOM / 2;
                g.fill(x, y, x + half, y + ROOM, darker(colors[0], 0.5f));
                g.fill(x + half, y, x + ROOM, y + ROOM, darker(colors[1], 0.5f));
            } else {
                int third = ROOM / 3;
                g.fill(x, y, x + third, y + ROOM, darker(colors[0], 0.5f));
                g.fill(x + third, y, x + third * 2, y + ROOM, darker(colors[1], 0.5f));
                g.fill(x + third * 2, y, x + ROOM, y + ROOM, darker(colors[2], 0.5f));
            }
            icon(g, x, y, QUESTION);
        }
    }

    private static int[] guessColors(int column) {
        RType[] types = AdmMapScan.guessForUnknown(column);
        int[] out = new int[types.length];
        for (int i = 0; i < types.length; i++) out[i] = typeColor(types[i]);
        return out;
    }

    // ---- players ----

    record MapPlayer(String name, UUID uuid, String cls, Player entity, int[] mapPos, float yaw, boolean self) {}

    static boolean holdingLeap() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String id = ItemUtil.skyblockId(mc.player.getMainHandItem());
        return "INFINITE_SPIRIT_LEAP".equals(id) || "SPIRIT_LEAP".equals(id);
    }

    /** Teammates (not you), alive, each with its entity or map icon, in tab order as Odin pairs them. */
    static List<MapPlayer> teammates() {
        Minecraft mc = Minecraft.getInstance();
        List<MapPlayer> out = new ArrayList<>();
        if (mc.player == null || mc.level == null) return out;
        String self = mc.player.getGameProfile().name();
        int icon = 0;
        for (PlayerTracker.DungeonPlayer p : PlayerTracker.getPlayers()) {
            if (p.getName().equals(self) || !p.isAlive()) continue;
            int[] pos = icon < AdmMapScan.mapPlayers.size() ? AdmMapScan.mapPlayers.get(icon) : null;
            icon++;
            UUID uuid = p.getUuid();
            Player e = uuid != null ? mc.level.getPlayerByUUID(uuid) : null;
            float yaw = e != null ? e.getYRot() : pos != null ? pos[2] * 360 / 16f : 0f;
            out.add(new MapPlayer(p.getName(), uuid, p.getDungeonClass(), e, pos, yaw, false));
        }
        return out;
    }

    static float partial() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }

    static double renderX(Player e) { return e.xo + (e.getX() - e.xo) * partial(); }
    static double renderZ(Player e) { return e.zo + (e.getZ() - e.zo) * partial(); }

    /** A map icon (decoration coords) in map space, 20 per tile. */
    static float[] iconPos(int[] p) {
        float gap = AdmMapScan.roomGap();
        return new float[]{((p[0] + 128) / 2f - AdmMapScan.startX) * TILE / gap, ((p[1] + 128) / 2f - AdmMapScan.startY) * TILE / gap};
    }

    static int odinClassColor(String cls) {
        String k = cls == null ? "" : cls.toLowerCase();
        int def = switch (k) {
            case "archer" -> 0xFFFFAA00;
            case "berserk" -> 0xFFAA0000;
            case "healer" -> 0xFFFF55FF;
            case "mage" -> 0xFF55FFFF;
            case "tank" -> 0xFF00AA00;
            default -> 0xFFFFFFFF;
        };
        return ClassColors.or(cls, def);
    }

    private static void renderPlayers(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        TeslaMapsConfig c = cfg();
        boolean names = holdingLeap();
        var font = mc.font;
        for (MapPlayer p : teammates()) {
            float px, pz;
            if (p.entity() != null) {
                px = (float) (renderX(p.entity()) + 200) * TILE / 32f;
                pz = (float) (renderZ(p.entity()) + 200) * TILE / 32f;
            } else if (p.mapPos() != null) {
                float[] ip = iconPos(p.mapPos());
                px = ip[0]; pz = ip[1];
            } else continue;
            g.pose().pushMatrix();
            g.pose().translate(px, pz);
            if (names) {
                g.pose().pushMatrix();
                g.pose().scale(c.admMapPlayerNamesScaling, c.admMapPlayerNamesScaling);
                g.centeredText(font, p.name(), 0, 8, col(c.admMapPlayerNameColor));
                g.pose().popMatrix();
            }
            g.pose().rotate((float) Math.toRadians(180.0 + p.yaw()));
            if (p.uuid() != null) {
                g.fill(-5, -5, 5, 5, odinClassColor(p.cls()));
                PlayerHeadRenderer.drawPlayerHead(g, -4, -4, 8, p.uuid());
            }
            g.pose().popMatrix();
        }
        g.pose().pushMatrix();
        g.pose().translate((float) (renderX(mc.player) + 200) * TILE / 32f, (float) (renderZ(mc.player) + 200) * TILE / 32f);
        g.pose().rotate((float) Math.toRadians(180.0 + mc.player.getYRot()));
        if (c.admMapOwnHead) {
            g.fill(-5, -5, 5, 5, odinClassColor(PlayerTracker.getLocalClass()));
            PlayerHeadRenderer.drawPlayerHead(g, -4, -4, 8, mc.player.getUUID());
        } else g.blit(RenderPipelines.GUI_TEXTURED, MARKER, -2, -3, 2f, 0f, 5, 7, 8, 8);
        g.pose().popMatrix();
    }

    // ---- Odin's example map (buildExampleRooms / buildExampleDoors) ----

    static final class Example {
        private static List<Room> rooms;

        static List<Room> rooms() {
            if (rooms == null) rooms = build();
            return rooms;
        }

        private static Room room(RType type, Check check, boolean walked, String name, int... xz) {
            Room r = new Room(type, xz[0], xz[1]);
            for (int i = 0; i < xz.length; i += 2) r.addTile(xz[i], xz[i + 1]);
            r.checkmark = check;
            r.walkedInto = walked;
            r.name = name;
            if (name != null) for (RoomName n : MapStats.NAMES) if (n.name().equals(name)) r.maxSecrets = n.secrets();
            return r;
        }

        private static List<Room> build() {
            return List.of(
                    room(RType.ENTRANCE, Check.GREEN, true, null, 2, 0),
                    room(RType.NORMAL, Check.NONE, true, "Hallway", 0, 0, 0, 1, 0, 2),
                    room(RType.PUZZLE, Check.NONE, true, "Water Board", 1, 0),
                    room(RType.NORMAL, Check.WHITE, true, "Waterfall", 3, 0, 3, 1, 3, 2),
                    room(RType.NORMAL, Check.NONE, true, "Bridges", 4, 0, 5, 0),
                    room(RType.NORMAL, Check.WHITE, true, "Museum", 1, 1, 2, 1, 1, 2, 2, 2),
                    room(RType.NORMAL, Check.NONE, true, "Cathedral", 4, 1, 5, 1, 4, 2, 5, 2),
                    room(RType.NORMAL, Check.WHITE, true, "Water", 1, 3),
                    room(RType.FAIRY, Check.GREEN, true, null, 2, 3),
                    room(RType.CHAMPION, Check.GREEN, false, "Dragon", 4, 3),
                    room(RType.TRAP, Check.GREEN, false, "Old Trap", 5, 3),
                    room(RType.PUZZLE, Check.NONE, true, "Teleport Maze", 0, 4),
                    room(RType.NORMAL, Check.WHITE, true, "Spikes", 1, 4),
                    room(RType.NORMAL, Check.WHITE, true, "Beams", 2, 4),
                    room(RType.NORMAL, Check.NONE, false, "Mirror", 3, 4),
                    room(RType.NORMAL, Check.NONE, false, "Silver Sword", 4, 4),
                    room(RType.NORMAL, Check.WHITE, false, "Staircase", 5, 4),
                    room(RType.NORMAL, Check.WHITE, true, "Archway", 0, 5, 1, 5),
                    room(RType.BLOOD, Check.NONE, true, null, 2, 5),
                    room(RType.NORMAL, Check.NONE, false, "Wizard", 3, 5, 4, 5, 5, 5));
        }

        private static Door door(int x, int z, boolean horizontal, DType type, int color) {
            Door d = new Door(x, z, horizontal, type);
            d.exampleColor = color;
            return d;
        }

        static List<Door> doors() {
            TeslaMapsConfig c = cfg();
            int normal = col(c.admMapNormalDoor);
            return List.of(
                    door(2, 0, false, DType.NORMAL, col(c.admMapEntranceRoom)),
                    door(1, 0, false, DType.NORMAL, col(c.admMapPuzzleRoom)),
                    door(0, 1, true, DType.NORMAL, normal),
                    door(2, 2, true, DType.NORMAL, normal),
                    door(1, 2, false, DType.NORMAL, normal),
                    door(4, 0, false, DType.NORMAL, normal),
                    door(3, 1, true, DType.NORMAL, normal),
                    door(3, 3, false, DType.NORMAL, normal),
                    door(1, 3, true, DType.FAIRY, col(c.admMapFairyDoor)),
                    door(2, 3, false, DType.NORMAL, normal),
                    door(4, 3, false, DType.NORMAL, col(c.admMapChampionRoom)),
                    door(5, 3, false, DType.NORMAL, col(c.admMapTrapRoom)),
                    door(0, 4, true, DType.NORMAL, col(c.admMapPuzzleRoom)),
                    door(1, 4, true, DType.NORMAL, normal),
                    door(1, 4, false, DType.NORMAL, normal),
                    door(3, 4, true, DType.NORMAL, normal),
                    door(3, 4, false, DType.NORMAL, normal),
                    door(5, 4, false, DType.NORMAL, normal),
                    door(1, 5, true, DType.BLOOD, col(c.admMapBloodDoor)));
        }
    }
}
