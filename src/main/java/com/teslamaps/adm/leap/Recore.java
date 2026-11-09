package com.teslamaps.adm.leap;

// Ported from ADM's practice/Recore.kt
import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The core after S4: Core Leap Highlight (where the teammates already in the core are in the leap menu) and Paste Last In Core. */
public final class Recore {

    private Recore() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static final String GOLDOR_START = "[BOSS] Goldor: Who dares trespass into my domain?";
    private static final String CORE_OPEN = "The Core entrance is opening!";
    private static final String GATE = "The gate has been destroyed!";
    private static final Pattern TASK_COUNT = Pattern.compile("^\\w+ (?:activated|completed) a (?:terminal|lever|device)! \\((\\d+)/(\\d+)\\)$");
    private static final double FLIGHT_PER_TICK = 2.0;

    private static boolean terms, coreWatch, selfBeenInCore, lastIn, gateIn;
    private static int sectionsOver;
    private static final LinkedHashMap<String, Long> inCoreSince = new LinkedHashMap<>();

    private static final String[] STYLES = {"Quadrant", "Side", "Top / Bottom", "Edge Glow", "Corner Bracket", "Leap Box"};
    private static final String[] FILLS = {"Solid", "Fade To Crosshair", "Fade To Edge"};
    private static final String[] NAME_POS = {"Area Middle", "By Crosshair"};
    private static final int BOX_WIDTH = 200, BOX_HEIGHT = 75;

    private record Mate(String name, String clazz) {}
    private static Mate[] mates = new Mate[4];

    private static boolean on() { return cfg().admRecore; }

    public static void register() {
        Feature.of("AdmRecore").section(Adm.SECTION).when(Recore::on)
                .tick(Recore::tick)
                .serverChat(Recore::onChat)
                .hud("admrecore", Recore::render)
                .config("ADM Leap & Recore", b -> {
                    b.label("Recore");
                    b.toggle("Recore", () -> cfg().admRecore, v -> { cfg().admRecore = v; reset(); })
                            .desc("The core after S4: the Core Leap Highlight (where the teammates already in the core are in the leap menu, so you flick there as it opens) and Paste Last In Core.");
                    b.toggle("Paste Last In Core", () -> cfg().admRecorePasteLast, v -> cfg().admRecorePasteLast = v)
                            .desc("Says in party chat who was last into the core after S4 and their time (e.g. \"Last in core: Name 1.35s\"), once all five are in. Only when every time is exact and nobody has died.");
                    b.toggle("Core Leap Highlight", () -> cfg().admRecoreCoreLeap, v -> cfg().admRecoreCoreLeap = v)
                            .desc("Once S3 is done, the side of the screen where the first teammate inside the core will be in the leap menu, in gold. Shown behind terminals too, never over them.");
                    b.label("Core Leap Look");
                    b.dropdown("Style", STYLES, () -> cfg().admRecoreStyle, v -> cfg().admRecoreStyle = v)
                            .desc("Quadrant: the quarter of the screen with their box. Side: the whole left or right half. Top / Bottom: the whole top or bottom half. Edge Glow: a glow along that quarter's two outer edges. Corner Bracket: an L near the crosshair pointing at it. Leap Box: their leap menu box.");
                    b.color("Color", () -> cfg().admRecoreColor, v -> cfg().admRecoreColor = v).desc("The highlight's colour and opacity.");
                    b.dropdown("Fill", FILLS, () -> cfg().admRecoreFill, v -> cfg().admRecoreFill = v)
                            .desc("Solid, or fading out toward the crosshair (strongest at the screen's edge), or the other way round. Quadrant, Side and Top / Bottom.");
                    b.slider("Centre Gap", 0, 120, () -> (float) cfg().admRecoreGap, v -> cfg().admRecoreGap = Math.round(v / 2f) * 2, 0)
                            .desc("Leaves this much room around the crosshair clear (px). Quadrant, Side, Top / Bottom, Corner Bracket.");
                    b.slider("Glow Depth", 4, 50, () -> (float) cfg().admRecoreGlowDepth, v -> cfg().admRecoreGlowDepth = Math.round(v), 0)
                            .desc("How far in from the edge the Edge Glow reaches, in % of the screen.");
                    b.slider("Bracket Size", 6, 120, () -> (float) cfg().admRecoreBracketSize, v -> cfg().admRecoreBracketSize = Math.round(v / 2f) * 2, 0)
                            .desc("Length of the Corner Bracket's arms (px).");
                    b.slider("Bracket Width", 1, 12, () -> (float) cfg().admRecoreBracketWidth, v -> cfg().admRecoreBracketWidth = Math.round(v), 0)
                            .desc("Thickness of the Corner Bracket's arms (px).");
                    b.dropdown("Name Position", NAME_POS, () -> cfg().admRecoreNameAt, v -> cfg().admRecoreNameAt = v)
                            .desc("In the middle of the highlighted area, or just off the crosshair toward it.");
                    b.slider("Name Scale", 0.5f, 4f, () -> cfg().admRecoreNameScale, v -> cfg().admRecoreNameScale = Math.round(v * 4f) / 4f, 2);
                    b.color("Name Color", () -> cfg().admRecoreNameColor, v -> cfg().admRecoreNameColor = v);
                    b.slider("Fade In", 0, 1000, () -> (float) cfg().admRecoreFadeIn, v -> cfg().admRecoreFadeIn = Math.round(v / 50f) * 50, 0)
                            .desc("Fades in over this long (ms) once someone is in the core.");
                    b.toggle("Hide When In Core", () -> cfg().admRecoreHideInCore, v -> cfg().admRecoreHideInCore = v)
                            .desc("Off for the rest of the run once you've been in the core yourself (after S3), even if you step back out.");
                });
    }

    public static void onWorldChange() { reset(); }

    private static void reset() {
        terms = false;
        coreWatch = false;
        inCoreSince.clear();
        sectionsOver = 0; lastIn = false; gateIn = false;
        selfBeenInCore = false;
    }

    /** P3 sim starting at section 1-4: counts from there, as if the run so far had been seen. */
    public static void simStart(int section) {
        reset();
        if (section < 1 || section > 4) return;
        terms = true;
        sectionsOver = section - 1;
        coreWatch = section == 4;
    }

    private static void countSectionEnd(String msg) {
        if (sectionsOver >= 3) return;
        if (msg.equals(GATE)) gateIn = true;
        else {
            Matcher m = TASK_COUNT.matcher(msg);
            if (!m.matches()) return;
            if (!m.group(1).equals(m.group(2))) return;
            lastIn = true;
        }
        if (!lastIn || !gateIn) return;
        sectionsOver++; lastIn = false; gateIn = false;
        if (sectionsOver == 3) coreWatch = true;
    }

    private static void onChat(String raw) {
        String msg = StringUtil.strip(raw);
        if (msg.equals(GOLDOR_START)) { if (!terms) { reset(); terms = true; } }
        else if (msg.equals(CORE_OPEN)) { terms = false; coreWatch = true; }
        else if (msg.startsWith("[BOSS] Necron:")) coreWatch = false;
        else {
            if (!terms && TASK_COUNT.matcher(msg).matches()) { reset(); terms = true; }
            if (terms) countSectionEnd(msg);
        }
    }

    private static boolean inCore(Entity p) { return p.getX() >= 39 && p.getX() < 71 && p.getY() < 155.5 && p.getZ() >= 54 && p.getZ() < 118; }

    private static boolean inFlight(Entity p) {
        double dx = p.getX() - p.xOld, dy = p.getY() - p.yOld, dz = p.getZ() - p.zOld;
        return dx * dx + dy * dy + dz * dz > FLIGHT_PER_TICK * FLIGHT_PER_TICK;
    }

    private static void trackCore() {
        if (!cfg().admRecoreCoreLeap || !coreWatch) { inCoreSince.clear(); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.player != null && inCore(mc.player) && !inFlight(mc.player)) selfBeenInCore = true;
        long now = System.currentTimeMillis();
        for (Mate m : mates) {
            if (m == null) continue;
            Player p = null;
            for (Player x : mc.level.players()) if (!x.isRemoved() && x.getName().getString().equals(m.name())) { p = x; break; }
            if (p == null) continue;
            if (AdmWorld.isDead(m.name()) || !inCore(p)) inCoreSince.remove(m.name());
            else if (!inFlight(p)) inCoreSince.putIfAbsent(m.name(), now);
        }
    }

    private static void tick() {
        refreshMates();
        trackCore();
    }

    private static int priority(String c) {
        if (c == null) return 99;
        return switch (c) { case "Berserk" -> 0; case "Tank" -> 1; case "Archer", "Healer", "Mage" -> 2; default -> 99; };
    }

    private static int defaultQuadrant(String c) {
        if (c == null) return 0;
        return switch (c) { case "Berserk" -> 1; case "Healer" -> 2; case "Mage", "Tank" -> 3; default -> 0; };
    }

    private static int classOrder(String c) {
        if (c == null) return 99;
        return switch (c) { case "Archer" -> 0; case "Berserk" -> 1; case "Healer" -> 2; case "Mage" -> 3; case "Tank" -> 4; default -> 98; };
    }

    /** Teammates where the leap overlay puts them, same sorting. */
    private static void refreshMates() {
        Minecraft mc = Minecraft.getInstance();
        String self = mc.player == null ? null : mc.player.getName().getString();
        List<Mate> list = new ArrayList<>();
        for (PlayerTracker.DungeonPlayer dp : PlayerTracker.getPlayers()) {
            if (dp.getName() == null || dp.getName().equals(self)) continue;
            list.add(new Mate(dp.getName(), "EMPTY".equals(dp.getDungeonClass()) ? null : dp.getDungeonClass()));
        }
        Mate[] out = new Mate[4];
        String mode = cfg().leapSortMode == null ? "Odin" : cfg().leapSortMode;
        if (mode.equals("Odin")) {
            list.sort(Comparator.comparingInt(m -> priority(m.clazz)));
            List<Mate> second = new ArrayList<>();
            for (Mate m : list) {
                int q = defaultQuadrant(m.clazz);
                if (out[q] == null) out[q] = m; else second.add(m);
            }
            for (int i = 0; i < 4 && !second.isEmpty(); i++) if (out[i] == null) out[i] = second.remove(0);
        } else {
            switch (mode) {
                case "Class A-Z" -> list.sort(Comparator.comparingInt((Mate m) -> classOrder(m.clazz)).thenComparing(m -> m.name.toLowerCase()));
                case "Name A-Z" -> list.sort(Comparator.comparing(m -> m.name.toLowerCase()));
                case "Custom" -> {
                    List<String> order = cfg().leapCustomOrder == null ? List.of() : cfg().leapCustomOrder;
                    list.sort(Comparator.comparingInt(m -> {
                        int i = order.indexOf(m.name.toLowerCase());
                        return i < 0 ? Integer.MAX_VALUE : i;
                    }));
                }
                default -> { }
            }
            for (int i = 0; i < Math.min(4, list.size()); i++) out[i] = list.get(i);
        }
        mates = out;
    }

    private static PlayerSkin skin(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfoIgnoreCase(name);
            if (info != null) return info.getSkin();
        }
        return DefaultPlayerSkin.getDefaultSkin();
    }

    private record Target(int i, String name, String clazz, PlayerSkin skin, long since) {}

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        if (!c.admRecoreCoreLeap || mc.gui.hud.isHidden() || mc.player == null) return;
        if (!coreWatch) return;
        if (c.admRecoreHideInCore && selfBeenInCore) return;
        List<Map.Entry<String, Long>> inOrder = new ArrayList<>(inCoreSince.entrySet());
        inOrder.sort(Map.Entry.comparingByValue());
        List<Target> targets = new ArrayList<>();
        Mate[] ms = mates;
        for (Map.Entry<String, Long> e : inOrder) {
            for (int i = 0; i < 4; i++) {
                Mate m = ms[i];
                if (m == null || !m.name().equals(e.getKey())) continue;
                targets.add(new Target(i, m.name(), m.clazz() == null ? "" : m.clazz().toLowerCase(), skin(m.name()), e.getValue()));
                break;
            }
        }
        Set<Integer> drawn = new HashSet<>();
        for (Target t : targets) drawTarget(g, t, drawn, targets.size() > 1);
    }

    private static int faded(int argb, float fade) {
        int a = Math.max(0, Math.min(255, (int) ((argb >>> 24) * fade)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int withAlpha(int argb, float alpha) {
        return (Math.max(0, Math.min(255, (int) (alpha * 255))) << 24) | (argb & 0xFFFFFF);
    }

    private static void drawTarget(GuiGraphicsExtractor g, Target t, Set<Integer> drawnAreas, boolean several) {
        TeslaMapsConfig c = cfg();
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        float fade = 1f;
        if (c.admRecoreFadeIn > 0) fade *= Math.max(0f, Math.min(1f, (now - t.since) / (float) c.admRecoreFadeIn));
        if (fade <= 0f) return;
        int col = faded(TeslaMapsConfig.parseColor(c.admRecoreColor), fade);

        int i = t.i;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int cx = w / 2, cy = h / 2;
        boolean right = i % 2 == 1, bottom = i / 2 == 1;
        int sx = right ? 1 : -1, sy = bottom ? 1 : -1;
        int gap = c.admRecoreGap;
        int qx0 = right ? cx + gap : 0, qx1 = right ? w : cx - gap;
        int qy0 = bottom ? cy + gap : 0, qy1 = bottom ? h : cy - gap;
        int midX = (qx0 + qx1) / 2, midY = (qy0 + qy1) / 2;
        String style = c.admRecoreStyle == null ? "Edge Glow" : c.admRecoreStyle;

        switch (style) {
            case "Quadrant", "Side", "Top / Bottom" -> {
                boolean vertical = style.equals("Top / Bottom"), side = style.equals("Side");
                int x0 = vertical ? 0 : qx0, x1 = vertical ? w : qx1;
                int y0 = side ? 0 : qy0, y1 = side ? h : qy1;
                if (!several || style.equals("Quadrant")) { midX = (x0 + x1) / 2; midY = (y0 + y1) / 2; }
                int area = side ? (right ? 1 : 0) : vertical ? (bottom ? 2 : 0) : i;
                if (drawnAreas.add(area)) {
                    int ax = vertical ? 0 : sx, ay = side ? 0 : sy;
                    switch (c.admRecoreFill == null ? "Solid" : c.admRecoreFill) {
                        case "Fade To Crosshair" -> fadeRect(g, x0, y0, x1, y1, col, ax, ay);
                        case "Fade To Edge" -> fadeRect(g, x0, y0, x1, y1, col, -ax, -ay);
                        default -> g.fill(x0, y0, x1, y1, col);
                    }
                }
            }
            case "Corner Bracket" -> {
                int l = c.admRecoreBracketSize, bt = c.admRecoreBracketWidth;
                int px = cx + sx * (gap + l), py = cy + sy * (gap + l);
                g.fill(Math.min(px, px - sx * l), Math.min(py, py - sy * bt), Math.max(px, px - sx * l), Math.max(py, py - sy * bt), col);
                g.fill(Math.min(px, px - sx * bt), Math.min(py - sy * bt, py - sy * l), Math.max(px, px - sx * bt), Math.max(py - sy * bt, py - sy * l), col);
                midX = px + sx * 12; midY = py + sy * 12;
            }
            case "Leap Box" -> {
                int bw = BOX_WIDTH, bh = BOX_HEIGHT;
                int lx = right ? 0 : -bw, ly = bottom ? 0 : -bh;
                g.pose().pushMatrix();
                g.pose().translate(cx + sx * 24, cy + sy * 24);
                roundedFill(g, lx, ly, lx + bw, ly + bh, col, 9);
                int face = (int) (bh * 0.76);
                PlayerFaceExtractor.extractRenderState(g, t.skin, lx + 9, ly + 9, face, withAlpha(0xFFFFFF, 64 / 255f * fade));
                g.text(mc.font, t.name, lx + 15 + face, ly + (int) (bh / 2.5), col, false);
                g.text(mc.font, t.clazz, lx + 15 + face, ly + (int) (bh / 1.7), col, false);
                g.pose().popMatrix();
                return;
            }
            default -> {
                int dep = Math.max(2, (int) (Math.min(w, h) * c.admRecoreGlowDepth / 100f));
                int x0 = right ? cx : 0, x1 = right ? w : cx;
                int y0 = bottom ? cy : 0, y1 = bottom ? h : cy;
                if (bottom) fadeRect(g, x0, h - dep, x1, h, col, 0, 1); else fadeRect(g, x0, 0, x1, dep, col, 0, -1);
                if (right) fadeRect(g, w - dep, y0, w, y1, col, 1, 0); else fadeRect(g, 0, y0, dep, y1, col, -1, 0);
                midX = (x0 + x1) / 2; midY = (y0 + y1) / 2;
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add(t.name);
        if (!t.clazz.isEmpty()) lines.add(Character.toUpperCase(t.clazz.charAt(0)) + t.clazz.substring(1));
        float s = c.admRecoreNameScale;
        int tw = 0;
        for (String l : lines) tw = Math.max(tw, mc.font.width(l));
        tw = (int) (tw * s);
        int th = (int) (lines.size() * 10 * s);
        int tx, ty;
        if ("By Crosshair".equals(c.admRecoreNameAt)) { tx = right ? cx + 10 : cx - 10 - tw; ty = bottom ? cy + 10 : cy - 10 - th; }
        else { tx = midX - tw / 2; ty = midY - th / 2; }
        g.pose().pushMatrix();
        g.pose().translate(tx, ty);
        g.pose().scale(s, s);
        int nc = faded(TeslaMapsConfig.parseColor(c.admRecoreNameColor), fade);
        for (int n = 0; n < lines.size(); n++) g.text(mc.font, lines.get(n), 0, n * 10, nc, false);
        g.pose().popMatrix();
    }

    private static void fadeRect(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color, int ax, int ay) {
        if (ax == 0 && ay == 0) { g.fill(x0, y0, x1, y1, color); return; }
        int steps = 24;
        float alpha = (color >>> 24) / 255f;
        int layer = withAlpha(color, 1f - (float) Math.pow(1.0 - alpha, 1.0 / steps));
        for (int k = 0; k < steps; k++) {
            float f = (steps - k) / (float) steps;
            int rw = (int) ((x1 - x0) * f), rh = (int) ((y1 - y0) * f);
            int rx0 = ax == 1 ? x1 - rw : x0;
            int rx1 = ax == -1 ? x0 + rw : x1;
            int ry0 = ay == 1 ? y1 - rh : y0;
            int ry1 = ay == -1 ? y0 + rh : y1;
            g.fill(rx0, ry0, rx1, ry1, layer);
        }
    }

    private static void roundedFill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color, int r) {
        r = Math.max(0, Math.min(r, Math.min((x1 - x0) / 2, (y1 - y0) / 2)));
        g.fill(x0 + r, y0, x1 - r, y1, color);
        for (int k = 0; k < r; k++) {
            double dy = r - k - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(x0 + inset, y0 + k, x0 + r, y0 + k + 1, color);
            g.fill(x1 - r, y0 + k, x1 - inset, y0 + k + 1, color);
            g.fill(x0 + inset, y1 - k - 1, x0 + r, y1 - k, color);
            g.fill(x1 - r, y1 - k - 1, x1 - inset, y1 - k, color);
        }
        g.fill(x0, y0 + r, x0 + r, y1 - r, color);
        g.fill(x1 - r, y0 + r, x1, y1 - r, color);
    }
}
