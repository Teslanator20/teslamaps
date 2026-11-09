package com.teslamaps.features;

// SkyHanni SkillProgress all-skill display
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkillXp;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AllSkillsDisplay {

    private static final String[] SAMPLE = {
            "§a§lFarming §e60 §7100% §8(MAX)",
            "§9Mining §e52 §734.1% §8(1.02M/3.40M)",
            "§cCombat §e48 §771.9% §8(2.16M/3.00M) §8→§b50 §882%",
            "§2Foraging §e30 §8(open /skills)"
    };

    private static List<FormattedCharSequence> rows = List.of();
    private static int width;
    private static List<FormattedCharSequence> sampleRows;
    private static int sampleWidth;
    private static int timer;
    private static HudEditElement hud;

    private AllSkillsDisplay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("allSkills", "All Skills").section("Skills")
                .enabled(() -> cfg().allSkillsDisplay)
                .size(() -> rows.isEmpty() ? sampleW() : width, () -> (rows.isEmpty() ? SAMPLE.length : rows.size()) * 10 + 6)
                .preview((g, mc, x, y, s) -> {
                    if (rows.isEmpty()) draw(g, mc, x, y, s, sample(), sampleW());
                    else draw(g, mc, x, y, s, rows, width);
                });
        Feature.of("AllSkillsDisplay").section("Skills").when(() -> cfg().allSkillsDisplay)
                .tick(AllSkillsDisplay::tick)
                .hudHiddenInInventory("allskills", AllSkillsDisplay::render)
                .config("Skills", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("All Skills");
                    b.toggle("All Skills Display", () -> c.allSkillsDisplay, v -> c.allSkillsDisplay = v)
                            .desc("Every skill at once: level, progress and xp, the one you are training highlighted. Open /skills once for exact xp. Position in /tmap gui.");
                    b.toggle("  Show XP Numbers", () -> c.allSkillsXp, v -> c.allSkillsXp = v);
                    b.toggle("  Overflow Levels", () -> c.allSkillsOverflow, v -> c.allSkillsOverflow = v);
                    b.searchList("  Hidden Skills", () -> Arrays.asList(SkillTracker.skills()),
                            c.allSkillsHidden::contains,
                            s -> { if (!c.allSkillsHidden.remove(s)) c.allSkillsHidden.add(s); });
                });
    }

    private static void tick() {
        if (++timer % 10 != 1) return;
        TeslaMapsConfig c = cfg();
        Minecraft mc = Minecraft.getInstance();
        String active = SkillTracker.activeSkill();
        List<FormattedCharSequence> out = new ArrayList<>();
        int w = 0;
        for (String skill : SkillTracker.skills()) {
            if (c.allSkillsHidden.contains(skill)) continue;
            String line = row(c, skill, skill.equals(active));
            if (line == null) continue;
            FormattedCharSequence seq = Component.literal(line).getVisualOrderText();
            out.add(seq);
            w = Math.max(w, mc.font.width(seq));
        }
        rows = out;
        width = w + 8;
    }

    private static String row(TeslaMapsConfig c, String skill, boolean active) {
        String name = color(skill) + (active ? "§l" : "") + skill + "§r";
        long total = SkillTracker.totalXp(skill);
        if (total <= 0) {
            Integer lvl = SkillTracker.tabLevel(skill);
            return lvl == null ? null : name + " §e" + lvl + " §8(open /skills)";
        }
        int cap = SkillTracker.cap(skill);
        SkillXp.Level lvl = SkillXp.calculate(total, cap);
        StringBuilder sb = new StringBuilder(name).append(" §e");
        boolean maxed = !c.allSkillsOverflow && lvl.level() >= cap;
        sb.append(maxed ? cap : lvl.level());
        if (!maxed && lvl.level() > cap) sb.append(" §6✦");
        if (maxed) {
            sb.append(" §7100%");
            if (c.allSkillsXp) sb.append(" §8(MAX)");
        } else {
            double pct = lvl.needed() > 0 ? 100.0 * lvl.current() / lvl.needed() : 0;
            sb.append(" §7").append(String.format("%.1f", pct)).append('%');
            if (c.allSkillsXp) sb.append(" §8(").append(fmt(lvl.current())).append('/').append(fmt(lvl.needed())).append(')');
        }
        sb.append(SkillGoals.suffix(skill, total));
        return sb.toString();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<FormattedCharSequence> r = rows;
        if (hud == null || r.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), r, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<FormattedCharSequence> r, int w) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, r.size() * 10 + 6);
        for (int i = 0; i < r.size(); i++) g.text(mc.font, r.get(i), 4, 4 + i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    private static List<FormattedCharSequence> sample() {
        if (sampleRows == null) {
            List<FormattedCharSequence> l = new ArrayList<>();
            int w = 0;
            for (String s : SAMPLE) {
                FormattedCharSequence seq = Component.literal(s).getVisualOrderText();
                l.add(seq);
                w = Math.max(w, Minecraft.getInstance().font.width(seq));
            }
            sampleWidth = w + 8;
            sampleRows = l;
        }
        return sampleRows;
    }

    private static int sampleW() {
        sample();
        return sampleWidth;
    }

    private static String color(String skill) {
        return switch (skill) {
            case "Foraging" -> "§2";
            case "Fishing" -> "§b";
            case "Combat" -> "§c";
            case "Farming" -> "§a";
            case "Mining" -> "§9";
            case "Alchemy", "Enchanting" -> "§d";
            default -> "§6";
        };
    }

    private static String fmt(double v) {
        if (v >= 1_000_000_000) return String.format("%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.1fk", v / 1_000);
        return String.valueOf((long) v);
    }
}
