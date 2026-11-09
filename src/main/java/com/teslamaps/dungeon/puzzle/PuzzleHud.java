package com.teslamaps.dungeon.puzzle;

// Ported from Odin's PuzzleHud.kt and Devonian's PuzzlesDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.DungeonState;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PuzzleHud {

    private static final String SECTION = "Puzzles & Terminals";
    private static final Pattern PUZZLE = Pattern.compile("^([\\w ?']+): \\[([✦✔✖])] ?(?:\\((\\w*)\\))?$");
    private static final Pattern COUNT = Pattern.compile("^Puzzles: \\((\\d+)\\)$");
    private static final int ROW = 10;
    private static final List<String> SAMPLE = List.of(
            "§5Puzzles §8(§34§8)", "§eIce Path§8: [§c✖§8] §7(§6Steve§7)", "§eCreeper Beams§8: [§a✔§8]",
            "§e???§8: [§6✦§8]", "§e???§8: [§6✦§8]");

    private static volatile List<String> lines = List.of();
    private static int linesWidth = 0;
    private static int tickCounter = 0;
    private static HudEditElement el;

    private PuzzleHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        el = HudEditElement.of("puzzleHud", "Puzzle HUD").ctx(HudEditElement.HudCtx.DUNGEON).section(SECTION)
                .enabled(() -> cfg().puzzleHud)
                .size(() -> width(SAMPLE), () -> SAMPLE.size() * ROW)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, width(SAMPLE)));
        Feature.of("PuzzleHud").section(SECTION).when(() -> cfg().puzzleHud)
                .tick(PuzzleHud::tick)
                .hud("puzzlehud", PuzzleHud::render);
    }

    private static void tick() {
        if (++tickCounter % 10 != 0) return;
        TeslaMapsConfig c = cfg();
        if (!DungeonManager.isInDungeon() || DungeonManager.isInBossArea()
                || (c.puzzleHudPreRunOnly && DungeonManager.getCurrentState() != DungeonState.STARTING)) {
            lines = List.of();
            return;
        }
        int count = -1, failed = 0, done = 0, unknown = 0;
        List<String> rows = new ArrayList<>();
        for (String raw : TabListUtils.getTabListLines()) {
            String line = StringUtil.stripTrim(raw);
            if (line.length() < 6) continue;
            Matcher m = COUNT.matcher(line);
            if (m.matches()) { count = Integer.parseInt(m.group(1)); continue; }
            m = PUZZLE.matcher(line);
            if (!m.matches()) continue;
            String name = m.group(1).trim(), state = m.group(2), player = m.group(3);
            if (state.equals("✖")) failed++;
            else if (state.equals("✔")) done++;
            if (name.equals("???")) { unknown++; continue; }
            String color = state.equals("✔") ? "§a" : state.equals("✖") ? "§c" : "§6";
            String who = c.puzzleHudPlayers && player != null && !player.isEmpty() ? " §7(§6" + player + "§7)" : "";
            rows.add("§e" + name + "§8: [" + color + state + "§8]" + who);
        }
        if (count <= 0) {
            lines = List.of();
            return;
        }
        for (int i = 0; i < unknown && rows.size() < count; i++) rows.add("§e???§8: [§6✦§8]");
        int missing = Math.max(0, count - done);
        List<String> out = new ArrayList<>();
        if (!c.puzzleHudCountsOnly) {
            out.add("§5Puzzles §8(§3" + count + "§8)");
            out.addAll(rows);
        }
        if (c.puzzleHud && (c.puzzleHudCounts || c.puzzleHudCountsOnly)) {
            out.add("§cFailed§f: " + (failed > 0 ? "§c" : "§a") + failed);
            out.add("§eMissing§f: " + (missing > 0 ? "§c" : "§a") + missing);
        }
        linesWidth = width(out);
        lines = out;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || el == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, el.x(), el.y(), el.scale(), l, linesWidth);
    }

    private static int width(List<String> l) {
        var font = Minecraft.getInstance().font;
        int w = 0;
        for (String s : l) w = Math.max(w, font.width(s));
        return w;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * ROW + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * ROW, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
