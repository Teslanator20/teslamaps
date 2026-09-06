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

    public static void onTooltip(ItemStack stack, List<Component> lines) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.skillOverflow || lines.isEmpty()) return;
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

    public static void checkLevelUp(String skill, long before, long after) {
        if (!TeslaMapsConfig.get().skillOverflow) return;
        int cap = SkillTracker.cap(skill);
        int oldLevel = SkillXp.calculate(before, cap).level();
        SkillXp.Level now = SkillXp.calculate(after, cap);
        if (now.level() <= oldLevel || now.level() <= cap) return;

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
        if (mc.screen == null) return "";
        return strip(mc.screen.getTitle().getString()).trim();
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
