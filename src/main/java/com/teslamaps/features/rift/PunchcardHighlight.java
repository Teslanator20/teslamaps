package com.teslamaps.features.rift;

// Ported from SkyHanni's PunchcardHighlight.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PunchcardHighlight {

    private static final Pattern PUNCHED = Pattern.compile("^PUNCHCARD! You punched (?:\\[[^]]+] )?(\\w+) and both regained");
    private static final Pattern SERVER = Pattern.compile("\\b(m\\d+[A-Za-z]+|mini\\d+[A-Za-z]*|mega\\d+[A-Za-z]*)\\b");
    private static final List<String> SAMPLE = List.of("§7Punchcard Artifact: §d7");

    private static final Set<String> punched = new HashSet<>();
    private static String queued;
    private static long queuedAt;
    private static boolean inRift;
    private static String lastServer = "";
    private static List<String> lines = List.of();
    private static int width;
    private static int ticks;
    private static Feature feature;
    private static HudEditElement hud;

    private PunchcardHighlight() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("punchcardOverlay", "Punchcard Artifact").section("Rift")
                .enabled(() -> cfg().punchcardHighlight && cfg().punchcardOverlay)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        feature = Feature.of("PunchcardHighlight").section("Rift").legit().when(() -> cfg().punchcardHighlight)
                .tick(PunchcardHighlight::tick)
                .serverChat(PunchcardHighlight::onChat)
                .hudHiddenInInventory("punchcard", PunchcardHighlight::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Punchcard Highlight", () -> c.punchcardHighlight, v -> c.punchcardHighlight = v)
                            .desc("Rift: players you have not punched yet with the Punchcard Artifact glow. "
                                    + "Stops at 20 punches. The list resets when you join another Rift server.")
                            .tags("punchcard", "artifact", "rift", "punch", "players").legit();
                    b.toggle("  Reverse (Only Punched)", () -> c.punchcardReverse, v -> c.punchcardReverse = v);
                    b.color("  Color", () -> c.punchcardColor, v -> c.punchcardColor = v);
                    b.toggle("  Overlay", () -> c.punchcardOverlay, v -> c.punchcardOverlay = v)
                            .desc("Punched count x/20. Move it in /tmap gui.");
                    b.toggle("  Compact Overlay", () -> c.punchcardCompact, v -> c.punchcardCompact = v);
                    b.toggle("  Show Remaining", () -> c.punchcardCountdown, v -> c.punchcardCountdown = v);
                    b.button("  Reset Punched List", PunchcardHighlight::reset);
                });
    }

    private static void reset() {
        punched.clear();
        queued = null;
        buildLines();
    }

    /** From the attack hook. */
    public static void onAttack(Entity target) {
        if (!inRift || !(target instanceof Player p) || p.getUUID().version() != 4 || feature == null || !feature.active()) return;
        String name = p.getName().getString();
        if (punched.contains(name)) return;
        queued = name;
        queuedAt = System.currentTimeMillis();
    }

    private static void onChat(String raw) {
        String q = queued;
        if (q == null || System.currentTimeMillis() - queuedAt > 1000) return;
        String t = StringUtil.stripTrim(raw);
        String name = null;
        Matcher m = PUNCHED.matcher(t);
        if (m.find()) name = m.group(1);
        else if (t.startsWith("AWKWARD! This player has already been punched by you")
                || t.startsWith("UH OH! You reached the limit of 20 players")) name = q;
        if (name == null) return;
        String n = name;
        Minecraft.getInstance().execute(() -> {
            punched.add(n);
            if (n.equals(queued)) queued = null;
            buildLines();
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks % 40 == 0) {
            inRift = RiftSpot.inRift();
            if (inRift) {
                String server = "";
                for (String line : ScoreboardUtils.getScoreboardLines()) {
                    Matcher m = SERVER.matcher(StringUtil.strip(line));
                    if (m.find()) { server = m.group(1); break; }
                }
                if (!server.isEmpty() && !server.equals(lastServer)) {
                    if (!lastServer.isEmpty()) punched.clear();
                    lastServer = server;
                }
            }
            buildLines();
        }
        if (!inRift) return;
        TeslaMapsConfig c = cfg();
        boolean reverse = c.punchcardReverse;
        if (!reverse && punched.size() >= 20) return;
        int rgb = TeslaMapsConfig.parseColor(c.punchcardColor) & 0xFFFFFF;
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.getUUID().version() != 4) continue;
            if (punched.contains(p.getName().getString()) == reverse) EntityGlow.mark(p, rgb);
        }
    }

    private static void buildLines() {
        TeslaMapsConfig c = cfg();
        if (!inRift || !c.punchcardOverlay) {
            lines = List.of();
            return;
        }
        int n = c.punchcardCountdown ? 20 - punched.size() : punched.size();
        lines = List.of((c.punchcardCompact ? "" : "§7Punchcard Artifact: ") + "§d" + n + (c.punchcardCountdown ? "" : "§7/20"));
        width = TextHud.width(lines);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty() || hud == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
