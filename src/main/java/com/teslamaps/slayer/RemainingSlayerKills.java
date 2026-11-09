package com.teslamaps.slayer;

// Concept from SkyHanni's RemainingSlayerKills.kt; xp per kill is measured instead of taken from repo tables
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RemainingSlayerKills {

    private static final Pattern PROGRESS = Pattern.compile("\\(([\\d,.]+[kKmM]?)/([\\d,.]+[kKmM]?)\\)");
    private static final List<String> SAMPLE = List.of("§e§lRemaining Revenant IV kills",
            " §7- §e26x §8(§3~14.6 xp/kill§8)", " §7Missing: §c380§7/§a1,500 XP");
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    private static final ArrayDeque<Double> gains = new ArrayDeque<>();
    private static double lastCur = -1, lastMax = -1;
    private static String lastKey;
    private static List<String> lines = List.of();
    private static int width;
    private static int ticks;
    private static HudEditElement hud;

    private RemainingSlayerKills() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("remainingSlayerKills", "Remaining Slayer Kills").section("Combat")
                .enabled(() -> cfg().remainingSlayerKills)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines), () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("RemainingSlayerKills").section("Combat").when(() -> cfg().remainingSlayerKills)
                .tick(RemainingSlayerKills::tick)
                .hudHiddenInInventory("remainingslayerkills", RemainingSlayerKills::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Slayer Helpers");
                    b.toggle("Remaining Slayer Kills", () -> c.remainingSlayerKills, v -> c.remainingSlayerKills = v)
                            .desc("HUD with the kills left until the boss spawns. XP per kill is measured from the scoreboard progress, so wisdom, combo, pets and mayor are already in it. Position in /tmap gui")
                            .tags("slayer", "kills", "remaining", "xp");
                });
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        String boss = SlayerQuest.boss();
        if (boss == null || SlayerQuest.bossActive() || !SkyblockUtils.isInSkyblock()) {
            if (boss == null) reset();
            lines = List.of();
            return;
        }
        double cur = -1, max = -1;
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            String line = ScoreboardUtils.cleanLine(raw);
            if (!line.contains("/") || !line.contains("(")) continue;
            Matcher m = PROGRESS.matcher(line);
            if (m.find()) {
                cur = num(m.group(1));
                max = num(m.group(2));
                break;
            }
        }
        if (cur < 0 || max <= 0) {
            lines = List.of();
            return;
        }
        String key = boss + SlayerQuest.tier();
        if (!key.equals(lastKey) || max != lastMax) {
            gains.clear();
            lastKey = key;
        } else if (lastCur >= 0 && cur > lastCur) {
            gains.addLast(cur - lastCur);
            while (gains.size() > 8) gains.pollFirst();
        }
        lastCur = cur;
        lastMax = max;
        build(boss, cur, max);
    }

    private static void build(String boss, double cur, double max) {
        int t = SlayerQuest.tier();
        String name = boss.split(" ")[0];
        List<String> l = new ArrayList<>();
        l.add("§e§lRemaining " + name + (t > 0 && t < ROMAN.length ? " " + ROMAN[t] : "") + " kills");
        double missing = Math.max(0, max - cur);
        if (gains.isEmpty()) {
            l.add(" §7- §8kill a mob to measure xp");
        } else {
            double xp = median();
            if (xp >= missing) l.add(" §7- §aNext kill spawns the boss!");
            else l.add(" §7- §e" + fmt(Math.ceil(missing / xp)) + "x §8(§3~" + (Math.round(xp * 10) / 10.0) + " xp/kill§8)");
        }
        l.add(" §7Missing: §c" + fmt(missing) + "§7/§a" + fmt(max) + " XP");
        lines = l;
        width = TextHud.width(l);
    }

    private static double median() {
        double[] a = new double[gains.size()];
        int i = 0;
        for (double d : gains) a[i++] = d;
        Arrays.sort(a);
        return a[a.length / 2];
    }

    private static String fmt(double d) {
        return String.format("%,d", Math.round(d));
    }

    private static double num(String s) {
        s = s.replace(",", "");
        double mul = 1;
        char last = Character.toLowerCase(s.charAt(s.length() - 1));
        if (last == 'k') mul = 1_000;
        else if (last == 'm') mul = 1_000_000;
        if (mul > 1) s = s.substring(0, s.length() - 1);
        try {
            return Double.parseDouble(s) * mul;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void reset() {
        gains.clear();
        lastCur = -1;
        lastMax = -1;
        lastKey = null;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
