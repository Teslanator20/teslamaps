package com.teslamaps.adm.leap;

// Ported from ADM's practice/Recore.kt
import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
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

    private static boolean on() { return cfg().admRecore; }

    public static void register() {
        Feature.of("AdmRecore").section(Adm.SECTION).when(Recore::on)
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
                    b.toggle("Highlight All Players In Core", () -> cfg().admRecoreAllInCore, v -> cfg().admRecoreAllInCore = v)
                            .desc("Highlights every teammate who made it to the core before you, each where they'll be in the leap menu, not just the first one.");
                    b.label("Core Leap Look");
                    b.toggle("Preview", () -> cfg().admRecorePreview, v -> cfg().admRecorePreview = v)
                            .desc("Shows the highlight now, top right, as if someone were in the core (also over this menu), to try the looks below; with Highlight All Players In Core, bottom left too. Turn it off after.");
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
                    b.toggle("Outline", () -> cfg().admRecoreOutline, v -> cfg().admRecoreOutline = v)
                            .desc("A line around the highlighted area. Quadrant, Side, Top / Bottom and Leap Box.");
                    b.slider("Outline Width", 1, 8, () -> (float) cfg().admRecoreOutlineWidth, v -> cfg().admRecoreOutlineWidth = Math.round(v), 0);
                    b.color("Outline Color", () -> cfg().admRecoreOutlineColor, v -> cfg().admRecoreOutlineColor = v);
                    b.toggle("Show Name", () -> cfg().admRecoreShowName, v -> cfg().admRecoreShowName = v).desc("Their name on the highlight.");
                    b.toggle("Show Class", () -> cfg().admRecoreShowClass, v -> cfg().admRecoreShowClass = v).desc("Their class under the name.");
                    b.dropdown("Name Position", NAME_POS, () -> cfg().admRecoreNameAt, v -> cfg().admRecoreNameAt = v)
                            .desc("In the middle of the highlighted area, or just off the crosshair toward it.");
                    b.slider("Name Scale", 0.5f, 4f, () -> cfg().admRecoreNameScale, v -> cfg().admRecoreNameScale = Math.round(v * 4f) / 4f, 2);
                    b.color("Name Color", () -> cfg().admRecoreNameColor, v -> cfg().admRecoreNameColor = v);
                    b.toggle("Pulse", () -> cfg().admRecorePulse, v -> cfg().admRecorePulse = v).desc("Breathes between the colour's opacity and a fraction of it.");
                    b.slider("Pulse Speed", 0.25f, 6f, () -> cfg().admRecorePulseSpeed, v -> cfg().admRecorePulseSpeed = Math.round(v * 4f) / 4f, 2).desc("Pulses a second.");
                    b.slider("Pulse Low", 0, 100, () -> (float) cfg().admRecorePulseLow, v -> cfg().admRecorePulseLow = Math.round(v / 5f) * 5, 0)
                            .desc("The faintest point of the pulse, in % of the colour's opacity.");
                    b.slider("Fade In", 0, 1000, () -> (float) cfg().admRecoreFadeIn, v -> cfg().admRecoreFadeIn = Math.round(v / 50f) * 50, 0)
                            .desc("Fades in over this long (ms) once someone is in the core.");
                    b.toggle("Over Screens", () -> cfg().admRecoreOverScreens, v -> cfg().admRecoreOverScreens = v)
                            .desc("Also shown while a screen is open (terminals, chat, your inventory), in the background behind it: the screen always draws over it. Off: only with no screen open.");
                    b.toggle("Over Leap Menu", () -> cfg().admRecoreOverLeap, v -> cfg().admRecoreOverLeap = v)
                            .desc("Keeps showing once the leap menu is open, behind it.");
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

    /** Term Info counted S3 done. */
    public static void s3Done() { if (Adm.on() && on()) coreWatch = true; }

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
        for (AdmLeapMenu.Mate m : AdmLeapMenu.teammates()) {
            if (m == null) continue;
            Player p = null;
            for (Player x : mc.level.players()) if (!x.isRemoved() && x.getName().getString().equals(m.name())) { p = x; break; }
            if (p == null) continue;
            if (AdmWorld.isDead(m.name()) || !inCore(p)) inCoreSince.remove(m.name());
            else if (!inFlight(p)) inCoreSince.putIfAbsent(m.name(), now);
        }
    }

    private static boolean isLeapScreen(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> s)) return false;
        String t = s.getTitle().getString();
        return t.equals("Spirit Leap") || t.equals("Teleport to Player");
    }

    private record Target(int i, String name, String clazz, PlayerSkin skin, long since) {}

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        trackCore();
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        if (!c.admRecoreCoreLeap || mc.options.hideGui) return;
        Player self = mc.player;
        if (self == null) return;
        Screen screen = mc.screen;
        if (screen != null) {
            if (!(c.admRecoreOverScreens || c.admRecorePreview)) return;
            if (!c.admRecoreOverLeap && isLeapScreen(screen)) return;
        }
        List<Target> targets = new ArrayList<>();
        if (c.admRecorePreview) {
            long since = System.currentTimeMillis() / 3000 * 3000;
            targets.add(new Target(1, "Teammate", "mage", AdmLeapOrder.skin(self.getName().getString()), since));
            if (c.admRecoreAllInCore) targets.add(new Target(2, "Teammate", "archer", AdmLeapOrder.skin(self.getName().getString()), since));
        } else {
            if (!coreWatch) return;
            if (c.admRecoreHideInCore && selfBeenInCore) return;
            List<Map.Entry<String, Long>> inOrder = new ArrayList<>(inCoreSince.entrySet());
            inOrder.sort(Map.Entry.comparingByValue());
            if (!c.admRecoreAllInCore && inOrder.size() > 1) inOrder = inOrder.subList(0, 1);
            AdmLeapMenu.Mate[] mates = AdmLeapMenu.teammates();
            for (Map.Entry<String, Long> e : inOrder) {
                for (int i = 0; i < 4; i++) {
                    AdmLeapMenu.Mate m = mates[i];
                    if (m == null || !m.name().equals(e.getKey())) continue;
                    targets.add(new Target(i, m.name(), m.clazz() == null ? "" : m.clazz().toLowerCase(), AdmLeapMenu.skin(m.name()), e.getValue()));
                    break;
                }
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
        if (c.admRecorePulse) {
            float p = (float) ((Math.sin(now / 1000.0 * c.admRecorePulseSpeed * 2 * Math.PI) + 1) / 2);
            float low = c.admRecorePulseLow / 100f;
            fade *= low + (1 - low) * p;
        }
        if (fade <= 0f) return;
        int col = faded(TeslaMapsConfig.parseColor(c.admRecoreColor), fade);
        int outlineCol = faded(TeslaMapsConfig.parseColor(c.admRecoreOutlineColor), fade);

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
                    if (c.admRecoreOutline) frame(g, x0, y0, x1, y1, c.admRecoreOutlineWidth, outlineCol);
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
                int bw = AdmLeapMenu.BOX_WIDTH, bh = AdmLeapMenu.BOX_HEIGHT;
                int lx = right ? 0 : -bw, ly = bottom ? 0 : -bh;
                float scale = c.admLeapScale;
                g.pose().pushMatrix();
                g.pose().translate(cx + sx * 24, cy + sy * 24);
                g.pose().scale(scale, scale);
                AdmLeapMenu.roundedFill(g, lx, ly, lx + bw, ly + bh, col, 9);
                if (c.admRecoreOutline) frame(g, lx, ly, lx + bw, ly + bh, c.admRecoreOutlineWidth, outlineCol);
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

        if (!c.admRecoreShowName) return;
        List<String> lines = new ArrayList<>();
        lines.add(t.name);
        if (c.admRecoreShowClass && !t.clazz.isEmpty()) lines.add(Character.toUpperCase(t.clazz.charAt(0)) + t.clazz.substring(1));
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

    private static void frame(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int t, int rgba) {
        g.fill(x0, y0, x1, y0 + t, rgba);
        g.fill(x0, y1 - t, x1, y1, rgba);
        g.fill(x0, y0 + t, x0 + t, y1 - t, rgba);
        g.fill(x1 - t, y0 + t, x1, y1 - t, rgba);
    }
}
