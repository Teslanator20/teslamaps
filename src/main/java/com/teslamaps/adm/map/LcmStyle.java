package com.teslamaps.adm.map;

// Ported from ADM's lcm/LcmStyle.kt: the ADM map drawn like LCM's map (dtMap) in its Legit Mode
import com.teslamaps.adm.map.AdmMapScan.Check;
import com.teslamaps.adm.map.AdmMapScan.DType;
import com.teslamaps.adm.map.AdmMapScan.Door;
import com.teslamaps.adm.map.AdmMapScan.RType;
import com.teslamaps.adm.map.AdmMapScan.Room;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.DungeonScore;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.render.PlayerHeadRenderer;
import com.teslamaps.TeslaMaps;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class LcmStyle {
    private static final int ROOM = 16, TILE = 20, PAD = 5, DOOR = 9;
    private static final float DARKEN = 0.4f;

    private static final int BACKGROUND = 0x46000000;
    static final int UNOPENED = 0xFF1E1E1E;
    static final int BLOOD = 0xFFFF0000;
    static final int NORMAL = 0xFF6B3A11;
    static final int PUZZLE = 0xFF750085;
    static final int CHAMPION = 0xFFFEDF00;
    static final int TRAP = 0xFFD87F33;
    private static final int ENTRANCE = 0xFF148500;
    static final int FAIRY = 0xFFF4138B;
    static final int RARE = 0xFFFFCB59;
    static final int WITHER = 0xFF000000;
    private static final int NO_CLASS = 0xB2000000;

    private static final int TEXT_GREEN = 0xFF00FF00, TEXT_WHITE = 0xFFFFFFFF, TEXT_GREY = 0xFF646464, TEXT_RED = 0xFFFF0000;

    private static final Identifier GREEN_CHECK = id("green_check"), WHITE_CHECK = id("white_check"), CROSS = id("cross"),
            CROWN = id("prince_crown_ziyno"), SELF_MARKER = id("self_marker");

    private static final Set<String> PRINCE_ROOMS = Set.of("Big Red Flag", "Chambers", "Doors", "Flags", "Grass Ruin", "Leaves", "Market",
            "Pirate", "Quartz Knight", "Red Blue", "Skull", "Sloth", "Supertall", "Waterfall", "Withermancer");
    private static final Set<String> LEAP_MENUS = Set.of("Spirit Leap", "Teleport to Player");
    private static final int[] GUESS_ORDER = {PUZZLE, TRAP, CHAMPION};

    private static boolean failed;

    private LcmStyle() {}

    private static Identifier id(String n) {
        return Identifier.fromNamespaceAndPath("teslamaps", "adm/dtmap/" + n + ".png");
    }

    public static boolean active() {
        return AdmMapRender.cfg().admLcmStyle;
    }

    /** Draws the whole map HUD; its size ({0,0} when hidden), or null to leave the frame to the Odin look. */
    static int[] render(GuiGraphicsExtractor g, boolean example) {
        if (!example && (!DungeonManager.isInDungeon() || (AdmMapRender.cfg().admMapDisableInBoss && DungeonManager.isInBossArea()))) return new int[]{0, 0};
        int[] cr = mapSize(example);
        int width = cr[0] * TILE - (TILE - ROOM) + PAD * 2;
        int height = cr[1] * TILE - (TILE - ROOM) + PAD * 2;
        g.pose().pushMatrix();
        try {
            g.fill(0, 0, width, height, BACKGROUND);
            g.pose().translate(PAD, PAD);
            if (example) drawMap(g, AdmMapRender.Example.rooms(), AdmMapRender.Example.doors(), List.of(), true);
            else {
                drawMap(g, AdmMapScan.rooms, AdmMapScan.doors.values(), AdmMapScan.pathHints, false);
                if (!DungeonManager.isInBossArea()) drawPlayers(g);
            }
        } catch (Throwable t) {
            if (!failed) {
                failed = true;
                TeslaMaps.LOGGER.error("[adm] dtMap style failed, the Odin look draws the map", t);
            }
            return null;
        } finally {
            g.pose().popMatrix();
        }
        return new int[]{width, height};
    }

    private static int[] mapSize(boolean example) {
        if (example) return new int[]{6, 6};
        return switch (AdmMapScan.floorNumber()) {
            case 0 -> new int[]{4, 4};
            case 1 -> new int[]{4, 5};
            case 2, 3 -> new int[]{5, 5};
            default -> new int[]{AdmMapScan.startX == 5 ? 6 : 5, AdmMapScan.startY == 5 ? 6 : 5};
        };
    }

    private static void drawMap(GuiGraphicsExtractor g, List<Room> rooms, Iterable<Door> doors, List<Integer> hints, boolean example) {
        for (Room room : rooms) {
            if (!room.viewable()) continue;
            int color = room.checkmark == Check.QUESTION_MARK ? (room.type == RType.BLOOD ? darker(BLOOD) : UNOPENED) : roomColor(room.type);
            AdmMapRender.fillTiles(g, room.tiles, color);
            if (opened(room) && room.walkedInto && room.name != null && PRINCE_ROOMS.contains(room.name) && !DungeonScore.isPrinceKilled()) drawCrown(g, room);
        }
        if (!example && !MapPrediction.render(g, true)) drawUnopened(g, hints);
        for (Door d : doors) drawDoor(g, d, example);
        for (Room room : rooms) drawName(g, room);
    }

    private static int roomColor(RType type) {
        return switch (type) {
            case BLOOD -> BLOOD;
            case NORMAL -> NORMAL;
            case PUZZLE -> PUZZLE;
            case CHAMPION -> CHAMPION;
            case TRAP -> TRAP;
            case ENTRANCE -> ENTRANCE;
            case FAIRY -> FAIRY;
            case RARE -> RARE;
            default -> UNOPENED;
        };
    }

    static int darker(int c) { return darker(c, DARKEN); }

    static int darker(int c, float m) { return AdmMapRender.darker(c, m); }

    private static boolean opened(Room room) {
        return room != null && room.viewable() && room.checkmark != Check.QUESTION_MARK;
    }

    private static int[] center(Room room) {
        List<int[]> tiles = room.tiles;
        if (tiles.isEmpty()) return null;
        int minX = 9, maxX = -1, minZ = 9, maxZ = -1;
        for (int[] t : tiles) { minX = Math.min(minX, t[0]); maxX = Math.max(maxX, t[0]); minZ = Math.min(minZ, t[1]); maxZ = Math.max(maxZ, t[1]); }
        if (tiles.size() == 3 && maxX - minX == 1 && maxZ - minZ == 1) {
            int top = 0;
            for (int[] t : tiles) if (t[1] == minZ) top++;
            int row = top == 2 ? minZ : maxZ;
            return new int[]{minX * TILE + TILE / 2 + ROOM / 2, row * TILE + ROOM / 2};
        }
        return new int[]{(minX + maxX) * TILE / 2 + ROOM / 2, (minZ + maxZ) * TILE / 2 + ROOM / 2};
    }

    private static void drawCrown(GuiGraphicsExtractor g, Room room) {
        int[] last = null;
        for (int[] t : room.tiles) if (last == null || t[0] * 1000 + t[1] > last[0] * 1000 + last[1]) last = t;
        if (last == null) return;
        g.pose().pushMatrix();
        g.pose().translate(last[0] * TILE + 9f, last[1] * TILE + 10f);
        g.pose().scale(0.7f, 0.7f);
        g.blit(RenderPipelines.GUI_TEXTURED, CROWN, 0, 0, 0f, 0f, 10, 10, 10, 10);
        g.pose().popMatrix();
    }

    private static void drawUnopened(GuiGraphicsExtractor g, List<Integer> hints) {
        boolean pred = !AdmMapRender.cfg().admMapDisablePred;
        for (int t : hints) {
            Room room = AdmMapRender.tileRoom(t);
            if (room != null && room.walkedInto) continue;
            int[] colors;
            if (room != null && room.type == RType.BLOOD) colors = new int[]{darker(BLOOD)};
            else if (pred && room != null && room.known1x1) colors = specialGuess(t % 6);
            else colors = new int[]{UNOPENED};
            guessTile(g, (t % 6) * TILE, (t / 6) * TILE, colors);
        }
        if (!pred) return;
        for (Room room : AdmMapScan.rooms) {
            if (!room.known1x1 || room.viewable()) continue;
            for (int[] p : room.tiles) {
                if (hints.contains(p[0] + p[1] * 6)) continue;
                guessTile(g, p[0] * TILE, p[1] * TILE, specialGuess(p[0]));
            }
        }
    }

    private static int dtColor(RType t) {
        return switch (t) {
            case PUZZLE -> PUZZLE;
            case TRAP -> TRAP;
            case CHAMPION -> CHAMPION;
            case ENTRANCE -> ENTRANCE;
            case BLOOD -> BLOOD;
            case FAIRY -> FAIRY;
            default -> NORMAL;
        };
    }

    private static int orderOf(int c) {
        for (int i = 0; i < GUESS_ORDER.length; i++) if (GUESS_ORDER[i] == c) return i;
        return GUESS_ORDER.length;
    }

    private static int[] specialGuess(int column) {
        RType[] types = AdmMapScan.guessForUnknown(column);
        Integer[] cs = new Integer[types.length];
        for (int i = 0; i < types.length; i++) cs[i] = dtColor(types[i]);
        Arrays.sort(cs, (a, b) -> Integer.compare(orderOf(a), orderOf(b)));
        int[] out = new int[cs.length];
        for (int i = 0; i < cs.length; i++) out[i] = darker(cs[i]);
        return out;
    }

    /** dtMap's guess tile: one colour, two as halves, three as an L, its opposite corner and a middle square. */
    static void guessTile(GuiGraphicsExtractor g, int x, int z, int[] colors) {
        switch (colors.length) {
            case 0 -> {}
            case 1 -> g.fill(x, z, x + ROOM, z + ROOM, colors[0]);
            case 2 -> {
                g.fill(x, z, x + ROOM, z + 8, colors[0]);
                g.fill(x, z + 8, x + ROOM, z + ROOM, colors[1]);
            }
            default -> {
                g.fill(x, z, x + ROOM, z + 5, colors[0]);
                g.fill(x, z, x + 5, z + 10, colors[0]);
                g.fill(x + 10, z + 5, x + ROOM, z + ROOM, colors[1]);
                g.fill(x, z + 10, x + ROOM, z + ROOM, colors[1]);
                g.fill(x + 5, z + 5, x + 11, z + 11, colors[2]);
            }
        }
    }

    private static void drawDoor(GuiGraphicsExtractor g, Door d, boolean example) {
        Room a = example ? null : AdmMapRender.tileRoom(d.origin());
        Room b = example ? null : AdmMapRender.tileRoom(d.dest());
        if (!example && !opened(a) && !opened(b)) return;
        int color;
        if (example) color = switch (d.type) {
            case WITHER -> WITHER;
            case BLOOD -> BLOOD;
            case FAIRY -> FAIRY;
            default -> exampleDoorColor(d.exampleColor);
        };
        else color = doorColor(d, a, b);
        doorAt(g, d.x, d.z, d.horizontal, color);
    }

    private static int exampleDoorColor(int c) {
        var cfg = AdmMapRender.cfg();
        if (c == AdmMapRender.col(cfg.admMapPuzzleRoom)) return PUZZLE;
        if (c == AdmMapRender.col(cfg.admMapTrapRoom)) return TRAP;
        if (c == AdmMapRender.col(cfg.admMapChampionRoom)) return CHAMPION;
        if (c == AdmMapRender.col(cfg.admMapEntranceRoom)) return ENTRANCE;
        return NORMAL;
    }

    static void doorAt(GuiGraphicsExtractor g, int x, int z, boolean right, int color) {
        g.pose().pushMatrix();
        if (right) g.pose().translate(x * TILE + ROOM, z * TILE + (ROOM - DOOR) / 2f);
        else g.pose().translate(x * TILE + (ROOM - DOOR) / 2f, z * TILE + ROOM);
        g.fill(0, 0, right ? TILE - ROOM : DOOR, right ? DOOR : TILE - ROOM, color);
        g.pose().popMatrix();
    }

    private static int doorColor(Door d, Room a, Room b) {
        boolean noInfo = !opened(a) || !opened(b);
        List<Room> rooms = new ArrayList<>();
        if (a != null) rooms.add(a);
        if (b != null) rooms.add(b);
        return switch (d.type) {
            case BLOOD -> noInfo ? darker(BLOOD) : BLOOD;
            case WITHER -> WITHER;
            case FAIRY -> {
                if (noInfo) yield WITHER;
                for (Room r : rooms) if (r.type != RType.NORMAL) yield r.type == RType.FAIRY ? FAIRY : NORMAL;
                yield NORMAL;
            }
            default -> {
                if (noInfo) yield UNOPENED;
                for (Room r : rooms) if (r.type != RType.NORMAL && r.type != RType.FAIRY) yield switch (r.type) {
                    case ENTRANCE -> ENTRANCE;
                    case BLOOD -> BLOOD;
                    case CHAMPION -> CHAMPION;
                    case PUZZLE -> PUZZLE;
                    case RARE -> RARE;
                    case TRAP -> TRAP;
                    default -> NORMAL;
                };
                yield NORMAL;
            }
        };
    }

    private static void drawName(GuiGraphicsExtractor g, Room room) {
        if (!opened(room) || room.type == RType.ENTRANCE || room.type == RType.BLOOD || room.type == RType.FAIRY || room.type == RType.UNDISCOVERED) return;
        int[] c = center(room);
        if (c == null) return;
        String name = room.walkedInto ? room.name : null;
        if (name == null) {
            Identifier icon = switch (room.checkmark) {
                case GREEN -> GREEN_CHECK;
                case WHITE -> WHITE_CHECK;
                case RED -> CROSS;
                default -> null;
            };
            if (icon != null) g.blit(RenderPipelines.GUI_TEXTURED, icon, c[0] - ROOM / 2 + 2, c[1] - ROOM / 2 + 2, 0f, 0f, 12, 12, 12, 12, -1);
            return;
        }
        int color = switch (room.checkmark) {
            case GREEN -> TEXT_GREEN;
            case NONE, UNDISCOVERED -> TEXT_GREY;
            case RED -> TEXT_RED;
            default -> TEXT_WHITE;
        };
        var font = Minecraft.getInstance().font;
        float scale = AdmMapRender.cfg().admMapTextScaling;
        float lineH = font.lineHeight * scale;
        String[] words = name.split(" ");
        float top = ROOM / 2f - lineH / 2f - (int) ((words.length - 1) / 2.0 * lineH);
        for (int i = 0; i < words.length; i++) {
            g.pose().pushMatrix();
            g.pose().translate(c[0], c[1] - ROOM / 2f + i * lineH + top);
            g.pose().scale(scale, scale);
            g.centeredText(font, words[i], 0, 0, color);
            g.pose().popMatrix();
        }
    }

    private static void drawPlayers(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String title = mc.screen != null ? mc.screen.getTitle().getString() : null;
        boolean names = AdmMapRender.holdingLeap() || (title != null && LEAP_MENUS.contains(title));
        for (AdmMapRender.MapPlayer p : AdmMapRender.teammates()) {
            float x, z;
            if (p.entity() != null) {
                x = worldPos(AdmMapRender.renderX(p.entity()));
                z = worldPos(AdmMapRender.renderZ(p.entity()));
            } else if (p.mapPos() != null) {
                float[] ip = AdmMapRender.iconPos(p.mapPos());
                x = ip[0]; z = ip[1];
            } else continue;
            drawHead(g, x, z, p.yaw(), p.uuid(), classColor(p.cls()), p.name(), names, false);
        }
        drawHead(g, worldPos(AdmMapRender.renderX(mc.player)), worldPos(AdmMapRender.renderZ(mc.player)), mc.player.getYRot(),
                mc.player.getUUID(), classColor(PlayerTracker.getLocalClass()), mc.player.getGameProfile().name(), names, true);
    }

    private static float worldPos(double v) {
        return (float) ((v + 201.0) / (32.0 / TILE));
    }

    private static int classColor(String cls) {
        String k = cls == null ? "" : cls.toLowerCase();
        return switch (k) {
            case "archer" -> 0xFFFFAA00;
            case "berserk" -> 0xFFFF5555;
            case "healer" -> 0xFFFF55FF;
            case "mage" -> 0xFF55FFFF;
            case "tank" -> 0xFF00AA00;
            default -> NO_CLASS;
        };
    }

    private static void drawHead(GuiGraphicsExtractor g, float x, float z, float yaw, java.util.UUID uuid, int color, String name, boolean showName, boolean own) {
        var c = AdmMapRender.cfg();
        g.pose().pushMatrix();
        g.pose().translate(x - 2f, z - 2f);
        if (showName) {
            g.pose().pushMatrix();
            g.pose().scale(c.admMapPlayerNamesScaling, c.admMapPlayerNamesScaling);
            g.centeredText(Minecraft.getInstance().font, name, 0, 8, AdmMapRender.col(c.admMapPlayerNameColor));
            g.pose().popMatrix();
        }
        g.pose().rotate((float) Math.toRadians(180.0 + yaw));
        if (own) g.blit(RenderPipelines.GUI_TEXTURED, SELF_MARKER, 5, 5, 10f, 10f, -10, -10, 10, 10);
        else {
            g.fill(-6, -6, 6, 6, color);
            if (uuid != null) PlayerHeadRenderer.drawPlayerHead(g, -5, -5, 10, uuid);
        }
        g.pose().popMatrix();
    }
}
