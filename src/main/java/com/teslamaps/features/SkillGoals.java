package com.teslamaps.features;

// SkyHanni SkillProgress custom goal: per-skill target level with progress, ETA and a reached alert
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkillXp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

public final class SkillGoals {

    private static String picked = "Farming";

    private SkillGoals() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SkillGoals").section("Skills").when(() -> cfg().skillGoals)
                .config("Skills", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Skill Goals");
                    b.toggle("Custom Skill Goals", () -> c.skillGoals, v -> c.skillGoals = v)
                            .desc("A target level per skill: progress and ETA in the Skill Tracker, the All Skills Display and the /skills tooltip.");
                    b.dropdown("  Skill", SkillTracker.skills(), () -> picked, v -> picked = v);
                    b.slider("  Goal Level (0 = none)", 0f, 70f,
                            () -> (float) goal(picked),
                            v -> {
                                int lvl = Math.round(v);
                                if (lvl <= 0) c.skillGoalLevels.remove(picked);
                                else c.skillGoalLevels.put(picked, lvl);
                            }, 0);
                    b.toggle("  Chat + Sound On Reach", () -> c.skillGoalAlert, v -> c.skillGoalAlert = v);
                });
    }

    public static int goal(String skill) {
        Integer g = cfg().skillGoalLevels.get(skill);
        return g == null ? 0 : g;
    }

    private static int activeGoal(String skill) {
        TeslaMapsConfig c = cfg();
        return c.skillGoals && c.section("Skills") ? goal(skill) : 0;
    }

    /** "Goal 60: 45.2% (12.3M left) 4h 10m", null without a goal or data. */
    public static String line(String skill, long total, double xph) {
        int goal = activeGoal(skill);
        if (goal <= 0 || total <= 0) return null;
        long need = SkillXp.xpRequiredForLevel(goal);
        if (total >= need) return "§7Goal §b" + goal + "§7: §a✔ reached";
        String out = "§7Goal §b" + goal + "§7: §e" + String.format("%.1f", 100.0 * total / need) + "% §8(" + fmt(need - total) + " left)";
        if (xph > 0) out += " §a" + eta((need - total) / xph);
        return out;
    }

    /** Short suffix for the all-skills rows. */
    public static String suffix(String skill, long total) {
        int goal = activeGoal(skill);
        if (goal <= 0 || total <= 0) return "";
        long need = SkillXp.xpRequiredForLevel(goal);
        if (total >= need) return " §8→§a" + goal + "✔";
        return " §8→§b" + goal + " §8" + String.format("%.0f", 100.0 * total / need) + "%";
    }

    public static void check(String skill, long before, long after) {
        TeslaMapsConfig c = cfg();
        int goal = activeGoal(skill);
        if (goal <= 0 || !c.skillGoalAlert) return;
        long need = SkillXp.xpRequiredForLevel(goal);
        if (before >= need || after < need) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §aSkill goal reached: §e" + skill + " " + goal + "§a!"));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1f));
    }

    public static void onTooltip(String skill, List<Component> lines) {
        int goal = activeGoal(skill);
        if (goal <= 0) return;
        long total = SkillTracker.totalXp(skill);
        if (total <= 0) return;
        long need = SkillXp.xpRequiredForLevel(goal);
        double pct = Math.min(1.0, (double) total / need);
        int filled = (int) Math.round(pct * 20);
        lines.add(Component.literal(""));
        lines.add(Component.literal("§7Goal: §b" + skill + " " + goal + " §e" + String.format("%.1f", pct * 100) + "%"));
        lines.add(Component.literal("§a" + "-".repeat(filled) + "§f" + "-".repeat(20 - filled)
                + " §e" + fmt(Math.min(total, need)) + "§6/§e" + fmt(need)));
    }

    private static String fmt(double v) {
        if (v >= 1_000_000_000) return String.format("%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.1fk", v / 1_000);
        return String.valueOf((long) v);
    }

    private static String eta(double hours) {
        long minutes = Math.round(hours * 60);
        if (minutes < 60) return minutes + "m";
        if (minutes < 60 * 48) return (minutes / 60) + "h " + (minutes % 60) + "m";
        return (minutes / 1440) + "d " + (minutes % 1440 / 60) + "h";
    }
}
