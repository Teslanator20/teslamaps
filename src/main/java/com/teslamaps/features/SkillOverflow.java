package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.SkillXp;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

// Overflow skill levels, the way Hypixel counts them past the cap: shown in the
// "Your Skills" menu tooltip and announced in chat on an overflow level up.
public class SkillOverflow {

    private static final String PROGRESS_INDENT = "                    ";

    private static final java.util.regex.Pattern SKILL_AVG =
            java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?) Skill Avg\\.");

    public static void onTooltip(ItemStack stack, List<Component> lines) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (lines.isEmpty()) return;

        // The "Your Skills" item lives in the SkyBlock Menu, not in the skills menu itself,
        // so this one runs before the screen-title gate below.
        if (cfg.skillAvgOverflow) addOverflowAverage(stack, lines);

        if (!cfg.skillOverflow) {
            if ((cfg.allSkillsDisplay || cfg.skillGoals) && "Your Skills".equals(screenTitle())) seedOnly(stack, lines);
            return;
        }
        if (!"Your Skills".equals(screenTitle())) return;

        String name = strip(stack.getHoverName().getString()).trim();
        String[] parts = name.split(" ");
        if (parts.length < 2) return;
        String skill = parts[0];
        int level;
        try {
            level = Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return;
        }

        int maxIndex = -1;
        int progressIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = strip(lines.get(i).getString());
            if (line.contains("Max Skill level reached!")) maxIndex = i;
            else if (maxIndex >= 0 && progressIndex < 0 && line.startsWith(PROGRESS_INDENT)) progressIndex = i;
        }

        if (maxIndex < 0 || progressIndex < 0) {
            seedNotMaxed(skill, level, lines);
            SkillGoals.onTooltip(skill, lines);
            return;
        }

        String progress = strip(lines.get(progressIndex).getString());
        long totalXp = parseAmount(progress.substring(progress.lastIndexOf(' ') + 1));
        if (totalXp <= 0) return;

        SkillTracker.setTotalXp(skill, totalXp);
        SkillXp.Level lvl = SkillXp.calculate(totalXp, level);
        double pct = lvl.needed() > 0 ? 100.0 * lvl.current() / lvl.needed() : 0;

        lines.set(0, Component.literal("§a" + skill + " " + lvl.level()));
        lines.set(maxIndex, Component.literal("§7Progress to Level " + (lvl.level() + 1)
                + ": §e" + String.format("%.1f", pct) + "%"));
        lines.set(progressIndex, Component.literal(bar(lvl.current(), lvl.needed())
                + " §e" + sep(lvl.current()) + "§6/§e" + sep(lvl.needed())));
        lines.add(progressIndex + 1, Component.literal("§b§lOVERFLOW XP:"));
        lines.add(progressIndex + 2, Component.literal("§7▸ " + sep(lvl.overflowTotal())));
        SkillGoals.onTooltip(skill, lines);
    }

    // same seeding as above, without touching the tooltip, for the all-skills display and goals
    private static void seedOnly(ItemStack stack, List<Component> lines) {
        String name = strip(stack.getHoverName().getString()).trim();
        String[] parts = name.split(" ");
        if (parts.length < 2 || SkillTracker.skillOf(name) == null) return;
        String skill = parts[0];
        int level;
        try {
            level = Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return;
        }
        int maxIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = strip(lines.get(i).getString());
            if (line.contains("Max Skill level reached!")) maxIndex = i;
            else if (maxIndex >= 0 && line.startsWith(PROGRESS_INDENT)) {
                long totalXp = parseAmount(line.substring(line.lastIndexOf(' ') + 1));
                if (totalXp > 0) SkillTracker.setTotalXp(skill, totalXp);
                SkillGoals.onTooltip(skill, lines);
                return;
            }
        }
        seedNotMaxed(skill, level, lines);
        SkillGoals.onTooltip(skill, lines);
    }

    /**
     * Hypixel's line only counts up to the cap, so a maxed account sits at a flat number. This
     * adds the same average over overflow levels underneath it.
     * <p>
     * Only when every non-cosmetic skill has a known total, which means the skills menu has to
     * have been open once - a partial average would silently read too low.
     */
    private static void addOverflowAverage(ItemStack stack, List<Component> lines) {
        if (!"Your Skills".equals(strip(stack.getHoverName().getString()).trim())) return;

        int avgIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (SKILL_AVG.matcher(strip(lines.get(i).getString()).trim()).find()) {
                avgIndex = i;
                break;
            }
        }
        if (avgIndex < 0) return;

        double sum = 0;
        for (String skill : SkillTracker.NON_COSMETIC) {
            long total = SkillTracker.totalXp(skill);
            if (total <= 0) return;   // missing data, better no line than a wrong one
            SkillXp.Level lvl = SkillXp.calculate(total, SkillTracker.cap(skill));
            sum += lvl.level() + (lvl.needed() > 0 ? (double) lvl.current() / lvl.needed() : 0);
        }
        double avg = sum / SkillTracker.NON_COSMETIC.length;

        lines.add(avgIndex + 1, Component.literal(
                "§b" + String.format("%.2f", avg) + " §3Overflow Skill Avg."));
    }

    // non-maxed skills still tell us the total xp, which the tracker needs for its ETA
    private static void seedNotMaxed(String skill, int level, List<Component> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = strip(lines.get(i).getString());
            if (!line.startsWith(PROGRESS_INDENT)) continue;
            String tail = line.substring(line.lastIndexOf(' ') + 1);
            int slash = tail.indexOf('/');
            if (slash < 0) continue;
            long current = parseAmount(tail.substring(0, slash));
            if (current <= 0) return;
            SkillTracker.setTotalXp(skill, SkillXp.xpRequiredForLevel(level) + current);
            return;
        }
    }

    private static final String DIVIDER = "§3§l" + "▬".repeat(64);

    // tooltip re-seeds total xp from an abbreviated number ("114.3M"), which can dip the
    // total back under a threshold that was just crossed - never announce a level twice
    private static final java.util.Map<String, Integer> announced = new java.util.HashMap<>();

    public static void checkLevelUp(String skill, long before, long after) {
        if (!TeslaMapsConfig.get().skillOverflow) return;
        int cap = SkillTracker.cap(skill);
        int oldLevel = SkillXp.calculate(before, cap).level();
        SkillXp.Level now = SkillXp.calculate(after, cap);
        if (now.level() <= oldLevel || now.level() <= cap) return;
        if (now.level() <= announced.getOrDefault(skill, 0)) return;
        announced.put(skill, now.level());

        announce(skill, oldLevel, now, cap);
    }

    /** /tmap debug fakelevel — same block, without waiting for the real level up. */
    public static void preview(String skill) {
        int cap = SkillTracker.cap(skill);
        long total = SkillTracker.totalXp(skill);
        if (total <= 0) total = SkillXp.xpRequiredForLevel(cap + 6) + 4_200_000L;
        SkillXp.Level now = SkillXp.calculate(total, cap);
        announce(skill, Math.max(now.level() - 1, cap), now, cap);
    }

    // built to read like Hypixel's own SKILL LEVEL UP block
    private static void announce(String skill, int oldLevel, SkillXp.Level now, int cap) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.sendSystemMessage(Component.literal(DIVIDER));
        mc.player.sendSystemMessage(Component.literal("  §b§lOVERFLOW LEVEL UP §3" + skill
                + " §8" + oldLevel + "➜§3" + now.level()));
        mc.player.sendSystemMessage(Component.literal(" "));
        mc.player.sendSystemMessage(Component.literal("  §a§lOVERFLOW XP"));
        mc.player.sendSystemMessage(Component.literal("   §7§8+§b" + sep(now.overflowTotal())
                + " §7past level " + cap));
        mc.player.sendSystemMessage(Component.literal(DIVIDER));
    }

    private static String screenTitle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() == null) return "";
        return strip(mc.gui.screen().getTitle().getString()).trim();
    }

    private static String bar(long current, long needed) {
        int filled = needed > 0 ? (int) Math.round(20.0 * current / needed) : 0;
        filled = Math.max(0, Math.min(20, filled));
        return "§2§l§m" + " ".repeat(filled) + "§f§l§m" + " ".repeat(20 - filled) + "§r";
    }

    private static String sep(long v) {
        return String.format("%,d", v);
    }

    private static long parseAmount(String raw) {
        String s = raw.replace(",", "").trim();
        if (s.isEmpty()) return 0;
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k' || last == 'K') mult = 1_000;
        else if (last == 'm' || last == 'M') mult = 1_000_000;
        else if (last == 'b' || last == 'B') mult = 1_000_000_000L;
        if (mult > 1) s = s.substring(0, s.length() - 1);
        try {
            return (long) (Double.parseDouble(s) * mult);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String strip(String s) {
        return s.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }
}
