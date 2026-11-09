package com.teslamaps.adm.map;

// Ported from ADM's mappred/OdinMapPrediction.kt: Room Prediction (F7/M7), drawn in map space
import com.teslamaps.TeslaMaps;
import com.teslamaps.adm.map.AdmMapScan.Door;
import com.teslamaps.adm.map.AdmMapScan.Room;
import com.teslamaps.adm.map.pred.MapPredictor;
import com.teslamaps.adm.map.pred.MapStats;
import com.teslamaps.adm.map.pred.Pred.BigRoom;
import com.teslamaps.adm.map.pred.Pred.Cls;
import com.teslamaps.adm.map.pred.Pred.Edge;
import com.teslamaps.adm.map.pred.Pred.Kind;
import com.teslamaps.adm.map.pred.Pred.MapView;
import com.teslamaps.adm.map.pred.Pred.NamedRoom;
import com.teslamaps.adm.map.pred.Pred.PredictedDoor;
import com.teslamaps.adm.map.pred.Pred.Prediction;
import com.teslamaps.adm.map.pred.Pred.Split;
import com.teslamaps.adm.map.pred.SecretNames;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.utils.TabListUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class MapPrediction {
    private static final int W = 6, SAMPLES = 2000, BATCH = 100, TILE = 20, ROOM = 16;
    private static final int ALWAYS_DEAD_END = 95;
    private static final float EXACT = 0.999f;
    private static final int BIG_GREY = 0xFF555555;
    private static final Identifier QUESTION = Identifier.fromNamespaceAndPath("teslamaps", "adm/odinmap/question.png");
    private static final int[] SPECIALS = {Cls.PUZZLE, Cls.TRAP, Cls.YELLOW, Cls.BLOOD, Cls.FAIRY, Cls.RARE};

    private static volatile Prediction shown;
    private static volatile MapView shownView;
    private static String lastKey = "";
    private static final AtomicInteger generation = new AtomicInteger();
    private static final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "teslamaps-adm-map-prediction");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final Map<Integer, Byte> pathDoors = new HashMap<>();
    private static Object lastLevel;

    private MapPrediction() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean on() {
        return cfg().admRoomPrediction && !LegitMode.blocksCheats();
    }

    private static int col(String hex) { return TeslaMapsConfig.parseColor(hex); }

    private static int darker(int c, float f) {
        return (c & 0xFF000000) | ((int) (((c >> 16) & 255) * f) << 16) | ((int) (((c >> 8) & 255) * f) << 8) | (int) ((c & 255) * f);
    }

    private static void icon(GuiGraphicsExtractor g, int x, int z, Identifier id) {
        int size = ROOM - 4;
        g.blit(RenderPipelines.GUI_TEXTURED, id, x + 2, z + 2, 0f, 0f, size, size, size, size);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        if (mc.player == null || mc.level == null || mc.level.getGameTime() % 5L != 0L) return;
        try {
            update();
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[adm] map prediction failed", e);
        }
    }

    private static void reset() {
        generation.incrementAndGet();
        shown = null;
        shownView = null;
        lastKey = "";
        pathDoors.clear();
    }

    private static boolean supported() {
        return AdmMapScan.inClear() && AdmMapScan.floorNumber() == 7;
    }

    private static MapView view() {
        int puzzles = TabListUtils.getPuzzleCount();
        if (puzzles <= 0) return null;
        MapView v = new MapView(W, W, puzzles);
        for (Room room : AdmMapScan.rooms) {
            if (!room.viewable() || room.tiles.isEmpty()) continue;
            Kind kind = switch (room.type) {
                case ENTRANCE -> Kind.ENTRANCE;
                case NORMAL -> Kind.NORMAL;
                case RARE -> Kind.RARE;
                case PUZZLE -> Kind.PUZZLE;
                case TRAP -> Kind.TRAP;
                case CHAMPION -> Kind.YELLOW;
                case BLOOD -> Kind.BLOOD;
                case FAIRY -> Kind.FAIRY;
                default -> null;
            };
            if (kind == null) continue;
            int[] tiles = room.tiles.stream().mapToInt(t -> t[1] * W + t[0]).distinct().toArray();
            if (v.addRoom(kind, tiles, room.walkedInto ? room.name : null) == null) return null;
        }
        boolean entrance = false;
        for (Kind k : v.kinds) if (k == Kind.ENTRANCE) { entrance = true; break; }
        if (!entrance) return null;
        v.secretTotals = SecretNames.totals(TabListUtils.getSecretsFound(), (float) TabListUtils.getSecretsPercentage());
        Map<Integer, Byte> doors = new HashMap<>();
        for (Door door : AdmMapScan.doors.values()) {
            int a = door.z * W + door.x;
            int b = (door.z + (door.horizontal ? 0 : 1)) * W + door.x + (door.horizontal ? 1 : 0);
            if (a < 0 || a >= W * W || b < 0 || b >= W * W) continue;
            byte k = switch (door.type) {
                case WITHER -> Edge.WITHER;
                case BLOOD -> Edge.BLOOD;
                default -> Edge.DOOR;
            };
            doors.put(pair(a, b), k);
            if (k != Edge.DOOR) pathDoors.put(pair(a, b), k);
        }
        v.closeEdges((a, b) -> {
            Byte p = pathDoors.get(pair(a, b));
            return p != null ? p : doors.get(pair(a, b));
        });
        return v;
    }

    private static int pair(int a, int b) {
        return Math.min(a, b) * 64 + Math.max(a, b);
    }

    private static void update() {
        if (!on() || !supported()) return;
        MapView v = view();
        if (v == null) return;
        String key = v.key();
        if (key.equals(lastKey)) return;
        lastKey = key;
        int gen = generation.incrementAndGet();
        worker.execute(() -> {
            try {
                MapPredictor predictor = new MapPredictor(v);
                Random rnd = new Random(System.nanoTime());
                while (generation.get() == gen && predictor.accepted < SAMPLES) {
                    int kept = predictor.run(BATCH, 40_000, rnd);
                    if (generation.get() != gen) return;
                    Prediction p = predictor.prediction();
                    if (p != null) { shown = p; shownView = v; }
                    if (kept == 0 && (predictor.accepted > 0 || predictor.level > 2)) break;
                }
            } catch (Throwable t) {
                TeslaMaps.LOGGER.warn("[adm] map prediction worker failed", t);
            }
        });
    }

    private static boolean replacesOdin() {
        return on() && supported() && shown != null;
    }

    private static Set<Integer> bloodTiles(boolean[] known) {
        Set<Integer> out = new HashSet<>();
        for (Door door : AdmMapScan.doors.values()) {
            if (door.type != AdmMapScan.DType.BLOOD) continue;
            int a = door.z * W + door.x;
            int b = (door.z + (door.horizontal ? 0 : 1)) * W + door.x + (door.horizontal ? 1 : 0);
            if (a < 0 || a >= W * W || b < 0 || b >= W * W) continue;
            if (known[a] && !known[b]) out.add(b);
            if (known[b] && !known[a]) out.add(a);
        }
        return out;
    }

    private static boolean[] knownTiles() {
        boolean[] known = new boolean[W * W];
        for (Room r : AdmMapScan.rooms) if (r.viewable()) for (int[] t : r.tiles) if (t[0] >= 0 && t[0] < W && t[1] >= 0 && t[1] < W) known[t[1] * W + t[0]] = true;
        return known;
    }

    private static void bloodRoom(GuiGraphicsExtractor g, int t) {
        int x = (t % W) * TILE, y = (t / W) * TILE;
        g.fill(x, y, x + ROOM, y + ROOM, col(cfg().admMapBloodRoom));
    }

    private static boolean drawWithoutPrediction(GuiGraphicsExtractor g) {
        if (!on() || !supported()) return false;
        boolean[] known = knownTiles();
        Set<Integer> blood = bloodTiles(known);
        if (blood.isEmpty()) return false;
        for (int t : AdmMapScan.pathHints) {
            if (t < 0 || t >= W * W || known[t] || blood.contains(t)) continue;
            int x = (t % W) * TILE, y = (t / W) * TILE;
            g.fill(x, y, x + ROOM, y + ROOM, col(cfg().admMapUnknownRoom));
            icon(g, x, y, QUESTION);
        }
        for (int t : blood) bloodRoom(g, t);
        return true;
    }

    private static int colorOf(int c) {
        TeslaMapsConfig k = cfg();
        return col(switch (c) {
            case Cls.PUZZLE -> k.admMapPuzzleRoom;
            case Cls.TRAP -> k.admMapTrapRoom;
            case Cls.YELLOW -> k.admMapChampionRoom;
            case Cls.BLOOD -> k.admMapBloodRoom;
            case Cls.FAIRY -> k.admMapFairyRoom;
            case Cls.RARE -> k.admMapNormalRoom;
            default -> k.admMapUnknownRoom;
        });
    }

    private static int dim(int c) {
        return darker(colorOf(c), 0.5f);
    }

    private static int withAlpha(int c, float a) {
        return (c & 0xFFFFFF) | (Math.round(a * 255) << 24);
    }

    /** Draws the prediction in map space (20 per tile); false when there is none to draw. */
    public static boolean render(GuiGraphicsExtractor g) {
        try {
            return draw(g);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean draw(GuiGraphicsExtractor g) {
        if (!replacesOdin()) return drawWithoutPrediction(g);
        Prediction p = shown;
        MapView v = shownView;
        if (p == null || v == null) return drawWithoutPrediction(g);
        TeslaMapsConfig c = cfg();
        boolean[] known = knownTiles();
        Set<Integer> blood = bloodTiles(known);
        for (int t : blood) bloodRoom(g, t);
        float min = c.admPredMinChance;
        float deadEndAt = Math.min(Math.round(c.admPredDeadEndChance), ALWAYS_DEAD_END) / 100f;
        float oneAt = Math.round(c.admPredOneChance) / 100f;
        float bloodFairyAt = Math.round(c.admPredBloodFairyChance) / 100f;
        Map<Integer, Split> splits = new HashMap<>();
        for (Split s : p.splits()) splits.put(s.tile(), s);
        boolean[] exact = new boolean[W * W];
        if (c.admPredExactRooms) {
            Map<String, List<NamedRoom>> groups = new LinkedHashMap<>();
            for (NamedRoom nr : p.namedRooms()) groups.computeIfAbsent(Arrays.toString(nr.tiles()), k -> new ArrayList<>()).add(nr);
            for (List<NamedRoom> names : groups.values()) {
                int[] tiles = names.get(0).tiles();
                double sum = 0;
                for (NamedRoom nr : names) sum += nr.chance();
                boolean skip = sum < EXACT;
                for (int t : tiles) if (known[t] || exact[t] || blood.contains(t)) skip = true;
                if (skip) continue;
                for (int t : tiles) exact[t] = true;
                exactRoom(g, tiles, names);
            }
        }
        BigRoom[] inBig = new BigRoom[W * W];
        float bigAt = Math.round(c.admPredBigChance) / 100f;
        for (BigRoom room : p.bigRooms()) {
            if (room.chance() < bigAt || room.chance() <= 0f) continue;
            boolean skip = false;
            for (int t : room.tiles()) if (known[t] || exact[t] || blood.contains(t) || inBig[t] != null) skip = true;
            if (skip) continue;
            for (int t : room.tiles()) inBig[t] = room;
            fillTiles(g, room.tiles(), BIG_GREY);
        }
        for (int t = 0; t < W * W; t++) {
            float[] probs = p.probs()[t];
            if (probs == null) continue;
            if (known[t] || v.roomAt[t] >= 0 || exact[t] || blood.contains(t)) continue;
            int x = (t % W) * TILE, y = (t / W) * TILE;
            Split split = splits.get(t);
            if (inBig[t] != null) {
                if (split != null) overlay(g, x, y, split, inBig[t].chance(), true);
                continue;
            }
            List<Integer> shownCls = new ArrayList<>();
            for (int s : SPECIALS) if (probs[s] >= min) shownCls.add(s);
            shownCls.sort((a, b) -> Float.compare(probs[b], probs[a]));
            if (shownCls.size() > 3) shownCls = shownCls.subList(0, 3);
            float deadEnd = probs[Cls.PUZZLE] + probs[Cls.TRAP] + probs[Cls.YELLOW] + probs[Cls.RARE];
            float one = probs[Cls.NORMAL];
            int bfCls = probs[Cls.BLOOD] >= probs[Cls.FAIRY] ? Cls.BLOOD : Cls.FAIRY;
            float bf = probs[bfCls];
            boolean isDeadEnd = deadEnd > 0f && deadEnd >= deadEndAt;
            boolean isOne = one > 0f && one >= oneAt;
            boolean isBloodFairy = bf > 0f && bf >= bloodFairyAt;
            float best = Math.max(isDeadEnd ? deadEnd : 0f, Math.max(isOne ? one : 0f, isBloodFairy ? bf : 0f));
            if (isBloodFairy && bf >= best) {
                g.fill(x, y, x + ROOM, y + ROOM, dim(bfCls));
                overlay(g, x, y, null, bf, true);
            } else if (isDeadEnd && deadEnd >= best) {
                deadEndGuess(g, x, y, probs, deadEnd);
                overlay(g, x, y, null, deadEnd, true);
            } else if (isOne) {
                fillTiles(g, new int[]{t}, BIG_GREY);
                overlay(g, x, y, split, one, split != null);
            } else if (split != null) {
                g.fill(x, y, x + ROOM, y + ROOM, col(c.admMapUnknownRoom));
                float at = x;
                for (int cl : shownCls) {
                    float wdt = probs[cl] * ROOM;
                    g.fill(Math.round(at), y, Math.min(Math.round(at + wdt), x + ROOM), y + ROOM, dim(cl));
                    at += wdt;
                }
                overlay(g, x, y, split, shownCls.isEmpty() ? null : probs[shownCls.get(0)], true);
            } else if (c.admPredHiddenHints && !shownCls.isEmpty()) {
                int cl = shownCls.get(0);
                float a = Math.min(0.35f + 0.65f * probs[cl], 1f);
                int inset = 5;
                int color = withAlpha(darker(colorOf(cl), 0.6f), a);
                g.fill(x + inset, y + inset, x + ROOM - inset, y + ROOM - inset, color);
            }
        }
        float doorAt = Math.round(c.admPredDoorChance) / 100f;
        for (PredictedDoor d : p.doors()) {
            if (d.chance() < doorAt || d.chance() <= 0f || known[d.a()] || known[d.b()]) continue;
            door(g, d);
        }
        return true;
    }

    private static void door(GuiGraphicsExtractor g, PredictedDoor d) {
        TeslaMapsConfig c = cfg();
        String hex;
        if (d.blood() >= 0.5f * d.chance()) hex = c.admMapBloodDoor;
        else if (d.fairy() >= 0.5f * d.chance()) hex = c.admMapFairyDoor;
        else if (d.wither() >= 0.5f * d.chance()) hex = c.admMapWitherDoor;
        else hex = c.admMapNormalDoor;
        int color = col(hex);
        int x = (d.a() % W) * TILE, y = (d.a() / W) * TILE;
        int half = (ROOM - 8) / 2;
        if (d.b() == d.a() + 1) g.fill(x + ROOM, y + half, x + TILE, y + half + 8, color);
        else g.fill(x + half, y + ROOM, x + half + 8, y + TILE, color);
    }

    private static void exactRoom(GuiGraphicsExtractor g, int[] tiles, List<NamedRoom> names) {
        NamedRoom best = names.get(0);
        for (NamedRoom n : names) if (n.chance() > best.chance()) best = n;
        int pool = best.name().pool();
        int type = pool == MapStats.POOL_PUZZLE ? Cls.PUZZLE : pool == MapStats.POOL_TRAP ? Cls.TRAP : pool == MapStats.POOL_YELLOW ? Cls.YELLOW
                : pool == MapStats.POOL_BLOOD ? Cls.BLOOD : pool == MapStats.POOL_FAIRY ? Cls.FAIRY : -1;
        int color = withAlpha(type < 0 ? col(cfg().admMapNormalRoom) : colorOf(type), 0.75f);
        fillTiles(g, tiles, color);
        float cx = 0, cz = 0;
        for (int t : tiles) { cx += (t % W) * TILE + ROOM / 2f; cz += (t / W) * TILE + ROOM / 2f; }
        cx /= tiles.length;
        cz /= tiles.length;
        Integer secrets = null;
        boolean same = true;
        for (NamedRoom n : names) {
            if (secrets == null) secrets = n.name().secrets();
            else if (secrets != n.name().secrets()) same = false;
        }
        if (!same) secrets = null;
        String name = best.chance() >= EXACT ? best.name().name() : "?";
        String text = (name + (secrets != null && secrets > 0 ? " 0/" + secrets : "")).trim();
        List<String> lines = new ArrayList<>();
        for (String s : text.split(" ")) if (!s.isEmpty()) lines.add(s);
        var font = Minecraft.getInstance().font;
        int fontH = font.lineHeight;
        float scale = cfg().admMapTextScaling;
        float totalH = (lines.size() - 1) * fontH * scale;
        for (int i = 0; i < lines.size(); i++) {
            g.pose().pushMatrix();
            g.pose().translate(cx, cz - totalH / 2f + i * fontH * scale);
            g.pose().scale(scale, scale);
            g.centeredText(font, lines.get(i), 0, -fontH / 2, 0xFFB4B4B4);
            g.pose().popMatrix();
        }
    }

    private static void fillTiles(GuiGraphicsExtractor g, int[] tiles, int c) {
        Set<Integer> set = new HashSet<>();
        for (int t : tiles) set.add(t);
        for (int t : tiles) {
            int x = (t % W) * TILE, y = (t / W) * TILE;
            boolean right = t % W + 1 < W && set.contains(t + 1), down = set.contains(t + W);
            g.fill(x, y, x + ROOM, y + ROOM, c);
            if (right) g.fill(x + ROOM, y, x + TILE, y + ROOM, c);
            if (down) g.fill(x, y + ROOM, x + ROOM, y + TILE, c);
            if (right && down && set.contains(t + W + 1)) g.fill(x + ROOM, y + ROOM, x + TILE, y + TILE, c);
        }
    }

    private static void deadEndGuess(GuiGraphicsExtractor g, int x, int y, float[] probs, float deadEnd) {
        List<Integer> types = new ArrayList<>();
        for (int t : new int[]{Cls.YELLOW, Cls.TRAP, Cls.PUZZLE, Cls.RARE}) if (probs[t] >= 0.05f * deadEnd) types.add(t);
        List<Integer> sorted = new ArrayList<>(types);
        sorted.sort((a, b) -> Float.compare(probs[b], probs[a]));
        Set<Integer> keep = new HashSet<>(sorted.subList(0, Math.min(3, sorted.size())));
        List<Integer> colors = new ArrayList<>();
        for (int t : types) if (keep.contains(t)) colors.add(colorOf(t));
        if (colors.size() == 1) g.fill(x, y, x + ROOM, y + ROOM, colors.get(0));
        else if (colors.size() == 2) {
            int half = ROOM / 2;
            g.fill(x, y, x + half, y + ROOM, darker(colors.get(0), 0.5f));
            g.fill(x + half, y, x + ROOM, y + ROOM, darker(colors.get(1), 0.5f));
        } else if (colors.size() >= 3) {
            int third = ROOM / 3;
            g.fill(x, y, x + third, y + ROOM, darker(colors.get(0), 0.5f));
            g.fill(x + third, y, x + third * 2, y + ROOM, darker(colors.get(1), 0.5f));
            g.fill(x + third * 2, y, x + ROOM, y + ROOM, darker(colors.get(2), 0.5f));
        }
    }

    private static void overlay(GuiGraphicsExtractor g, int x, int y, Split split, Float chance, boolean question) {
        boolean label = split != null && cfg().admPredSplitContents;
        if (label) splitLabel(g, x, y, split);
        else if (question) icon(g, x, y, QUESTION);
        if (chance != null && cfg().admPredShowChances)
            small(g, Math.round(chance * 100) + "%", x + ROOM / 2, y + 13, -1);
    }

    private static void splitLabel(GuiGraphicsExtractor g, int x, int y, Split s) {
        List<String> parts = new ArrayList<>();
        int pz = Math.round(s.expect()[Cls.PUZZLE]);
        if (pz > 0) parts.add("§d" + (pz > 1 ? pz : "") + "P");
        if (s.expect()[Cls.TRAP] >= 0.5f) parts.add("§6T");
        if (s.expect()[Cls.YELLOW] >= 0.5f) parts.add("§eY");
        if (s.expect()[Cls.BLOOD] >= 0.5f) parts.add("§cB");
        String top = parts.isEmpty() ? "§7-" : String.join(" ", parts);
        small(g, top, x + ROOM / 2, y + 3, -1);
        small(g, "§f~" + Math.round(s.rooms()), x + ROOM / 2, y + 8, -1);
    }

    private static void small(GuiGraphicsExtractor g, String text, int cx, int cy, int color) {
        var font = Minecraft.getInstance().font;
        float scale = Math.min(0.45f, (ROOM - 1f) / Math.max(1, font.width(text)));
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.centeredText(font, text, 0, -font.lineHeight / 2, color);
        g.pose().popMatrix();
    }
}
