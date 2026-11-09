package com.teslamaps.features;

// Section tracking ported from Odin's InactiveWaypoints.kt (Term Info), compact line from Devonian's
// TerminalDisplay.kt, breakdown from Devonian's TerminalBreakdown.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TerminalProgress {

    public static final Pattern COMPLETED = Pattern.compile(
            "^(.{1,16}) (activated|completed) a (terminal|lever|device)! \\((\\d)/(\\d)\\)$");
    private static final String GOLDOR = "[BOSS] Goldor: Who dares trespass into my domain?";
    private static final String CORE = "The Core entrance is opening!";
    private static final String GATE = "The gate has been destroyed!";

    private static final String[] SAMPLE = {"§6Levers §c1§8/§a2", "§6Terms §c3§8/§a4", "§6Device §a✔", "§6Gate §c✘"};
    private static final String[] SAMPLE_COMPACT = {"§c5/7"};

    private static boolean active = false;
    private static boolean firstInSection, isComplete, device, gate;
    private static int lastCompleted, terminals, levers, section = 1;
    private static final Map<String, int[]> perPlayer = new LinkedHashMap<>();

    private static String[] lines = null;
    private static int linesW = 0;
    private static Object lastLevel = null;
    private static HudEditElement hud;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("termInfoHud", "Term Info").ctx(HudEditElement.HudCtx.DUNGEON)
                .enabled(() -> cfg().termInfoHud)
                .size(() -> width(sample()), () -> sample().length * 10)
                .scaleRange(0.5f, 4.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, sample(), width(sample())));
        Feature.of("TerminalProgress").when(() -> cfg().termInfoHud || cfg().terminalBreakdown || cfg().melodyDisplay)
                .tick(TerminalProgress::tick)
                .serverChat(TerminalProgress::onChat);
        Feature.of("TermInfoHud").when(() -> cfg().termInfoHud)
                .hud("terminfohud", TerminalProgress::render);
    }

    /** Goldor's terminal phase: between his first line and the core opening. */
    public static boolean inP3() {
        return active && DungeonManager.isInDungeon();
    }

    public static String classColor(String name) {
        String cls = PlayerTracker.getPlayer(name).map(PlayerTracker.DungeonPlayer::getDungeonClass).orElse("");
        return switch (cls == null ? "" : cls) {
            case "Archer" -> "§6";
            case "Berserk" -> "§4";
            case "Healer" -> "§d";
            case "Mage" -> "§b";
            case "Tank" -> "§2";
            default -> "§7";
        };
    }

    private static String[] sample() {
        return cfg().termInfoCompact ? SAMPLE_COMPACT : SAMPLE;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            active = false;
            resetState();
            perPlayer.clear();
        }
        if (!inP3() || !cfg().termInfoHud) { lines = null; return; }
        int maxTerms = section == 2 ? 5 : 4;
        if (cfg().termInfoCompact) {
            int done = terminals + levers + (device ? 1 : 0);
            lines = new String[]{(gate ? "§a" : "§c") + done + "/" + (maxTerms + 3)};
        } else {
            lines = new String[]{
                    "§6Levers " + (levers == 2 ? "§a" : "§c") + levers + "§8/§a2",
                    "§6Terms " + (terminals == maxTerms ? "§a" : "§c") + terminals + "§8/§a" + maxTerms,
                    "§6Device " + (device ? "§a✔" : "§c✘"),
                    "§6Gate " + (gate ? "§a✔" : "§c✘")};
        }
        linesW = width(lines);
    }

    private static void onChat(String raw) {
        if (!DungeonManager.isInDungeon()) return;
        String s = StringUtil.stripTrim(raw);
        if (s.equals(GOLDOR)) {
            active = true;
            resetState();
            perPlayer.clear();
            return;
        }
        if (s.equals(CORE)) {
            if (active && cfg().terminalBreakdown) sendBreakdown();
            active = false;
            resetState();
            return;
        }
        if (s.equals(GATE)) {
            gate = true;
            if (isComplete) newSection();
            return;
        }
        if (s.indexOf('(') < 0) return;
        Matcher m = COMPLETED.matcher(s);
        if (!m.matches()) return;

        String type = m.group(3);
        int[] counts = perPlayer.computeIfAbsent(m.group(1), k -> new int[3]);
        counts[type.equals("terminal") ? 0 : type.equals("lever") ? 1 : 2]++;

        int completed = Integer.parseInt(m.group(4));
        if (completed == 1) firstInSection = true;
        if (completed == Integer.parseInt(m.group(5))) {
            if (gate) newSection(); else isComplete = true;
            return;
        }
        switch (type) {
            case "lever" -> levers++;
            case "terminal" -> terminals++;
            default -> { if (!firstInSection || lastCompleted != completed) device = true; }
        }
        lastCompleted = completed;
    }

    private static void sendBreakdown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || perPlayer.isEmpty()) return;
        Map<String, int[]> all = new LinkedHashMap<>(perPlayer);
        for (PlayerTracker.DungeonPlayer p : PlayerTracker.getPlayers()) all.putIfAbsent(p.getName(), new int[3]);
        List<Map.Entry<String, int[]>> rows = new ArrayList<>(all.entrySet());
        rows.sort(Comparator.<Map.Entry<String, int[]>>comparingInt(e -> -e.getValue()[0])
                .thenComparingInt(e -> -e.getValue()[2])
                .thenComparingInt(e -> -e.getValue()[1]));
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fTerminals Breakdown:"));
        for (Map.Entry<String, int[]> e : rows) {
            int[] c = e.getValue();
            mc.player.sendSystemMessage(Component.literal(classColor(e.getKey()) + e.getKey() + "§f: Terminal x§a" + c[0]
                    + "§7 | §fLever x§a" + c[1] + "§7 | §fDevice x§a" + c[2]));
        }
    }

    private static void resetState() {
        firstInSection = false;
        lastCompleted = 0;
        isComplete = false;
        device = false;
        terminals = 0;
        gate = false;
        section = 1;
        levers = 0;
    }

    private static void newSection() {
        firstInSection = false;
        isComplete = false;
        device = false;
        terminals = 0;
        gate = false;
        levers = 0;
        section++;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String[] l = lines;
        if (l == null || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l, linesW);
    }

    private static int width(String[] l) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String s : l) w = Math.max(w, mc.font.width(s));
        return w;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String[] l, int w) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.length * 10 + 2);
        for (int i = 0; i < l.length; i++) g.text(mc.font, l[i], 0, i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
