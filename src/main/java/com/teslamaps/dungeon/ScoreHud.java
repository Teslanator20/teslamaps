package com.teslamaps.dungeon;

// Skyblocker DungeonScoreHUD, WpcMod ScoreCalculation, Devonian ScoreDisplay/DeathsDisplay/CryptsDisplay
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ScoreHud {

    private static final String SECTION = "Score & Splits";
    private static final String[] MODES = {"Full", "Minimized", "Score Only"};
    private static final int ROW = 10;
    private static final List<String> SAMPLE = List.of(
            "§7Score: §a287 §8(§eS§8)",
            "§7Deaths: §c1 §8| §7Puzzles: §a0 missing",
            "§7Secrets: §b62.4% §8/ §a70.0%",
            "§7Crypts: §a5§7/5 §8| §7Mimic: §a✔ §8| §7Prince: §c✖ §8| §7Bat: §a✔",
            "§7Skill §a98 §8| §7Explore §a92 §8| §7Speed §a100 §8| §7Bonus §a8");

    private static volatile List<String> lines = List.of();
    private static int linesWidth;
    private static int tickCounter;
    private static HudEditElement el;

    private ScoreHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        el = HudEditElement.of("scoreHud", "Score HUD").ctx(HudEditElement.HudCtx.DUNGEON).section(SECTION)
                .enabled(() -> cfg().scoreHud)
                .size(() -> width(SAMPLE), () -> SAMPLE.size() * ROW)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, width(SAMPLE)));
        Feature.of("ScoreHud").section(SECTION).when(() -> cfg().scoreHud)
                .tick(ScoreHud::tick)
                .hud("scorehud", ScoreHud::render)
                .config(SECTION, b -> {
                    b.label("Score HUD");
                    b.toggle("Score HUD", () -> cfg().scoreHud, v -> cfg().scoreHud = v)
                            .desc("Score with rank, deaths, secrets, crypts and the four part scores, move it in /tmap gui");
                    b.dropdown("  Mode", MODES, () -> cfg().scoreHudMode, v -> cfg().scoreHudMode = v);
                    b.toggle("  Hide In Boss", () -> cfg().scoreHudHideInBoss, v -> cfg().scoreHudHideInBoss = v);
                });
    }

    private static void tick() {
        if (++tickCounter % 10 != 0) return;
        TeslaMapsConfig c = cfg();
        if (!DungeonManager.isInDungeon() || !DungeonScore.isStarted()
                || (c.scoreHudHideInBoss && DungeonManager.isInBossArea())) {
            lines = List.of();
            return;
        }
        int score = DungeonScore.calculateScore();
        String rank = DungeonScore.rank(score);
        String rc = DungeonScore.rankColor(rank);
        String head = "§7Score: " + rc + score + " §8(" + rc + rank + "§8)";
        int deaths = DungeonScore.getDeathCount();
        int crypts = DungeonScore.getCryptCount();
        List<String> out = new ArrayList<>();
        switch (c.scoreHudMode) {
            case "Score Only" -> out.add(head);
            case "Minimized" -> out.add(head + " §8| §7Crypts: " + (crypts >= 5 ? "§a" : "§c") + crypts
                    + " §8| §7Deaths: " + (deaths > 0 ? "§c" : "§a") + deaths);
            default -> {
                out.add(head);
                int missing = DungeonScore.getMissingPuzzles();
                out.add("§7Deaths: " + (deaths > 0 ? "§c" : "§a") + deaths
                        + " §8| §7Puzzles: " + (missing > 0 ? "§c" : "§a") + missing + " missing");
                double have = DungeonScore.getSecretsPercent();
                double need = DungeonScore.getNeededSecretsPercentFor300(crypts);
                out.add("§7Secrets: " + (have >= need ? "§a" : "§b") + pct(have) + " §8/ §a" + pct(need));
                StringBuilder sb = new StringBuilder("§7Crypts: ").append(crypts >= 5 ? "§a" : "§c").append(crypts).append("§7/5");
                if (DungeonScore.floorHasMimics()) sb.append(" §8| §7Mimic: ").append(DungeonScore.isMimicKilled() ? "§a✔" : "§c✖");
                sb.append(" §8| §7Prince: ").append(DungeonScore.isPrinceKilled() ? "§a✔" : "§c✖");
                sb.append(" §8| §7Bat: ").append(DungeonScore.isBatKilled() ? "§a✔" : "§c✖");
                out.add(sb.toString());
                out.add("§7Skill " + part(DungeonScore.getSkillScore(), 100) + " §8| §7Explore " + part(DungeonScore.getExploreScore(), 100)
                        + " §8| §7Speed " + part(DungeonScore.getTimeScore(), 100) + " §8| §7Bonus §a" + DungeonScore.getBonusScore());
            }
        }
        linesWidth = width(out);
        lines = out;
    }

    private static String part(int v, int max) {
        return (v >= max ? "§a" : v >= max * 0.8 ? "§e" : "§c") + v;
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.1f%%", v);
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
