package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Drops parts out of the Hypixel action bar. /tmap debug actionbar shows what is in it. */
public class ActionBarFilter {

    // 5 spaces between segments, 1 space between stats inside a segment
    private static final Pattern SEP = Pattern.compile("\\s{2,}");
    private static final Pattern SKILL_XP = Pattern.compile("\\+[\\d.,]+ [A-Za-z]+ \\(");
    private static final Pattern ARMOR_STACK = Pattern.compile("\\d+[\u1750\u2051|\u0489\u046A\u26B6]");

    private static boolean debug;
    private static String lastDump = "";
    private static String lastRaw = "";

    public static boolean toggleDebug() {
        debug = !debug;
        lastDump = "";
        return debug;
    }

    public static String lastRaw() {
        return lastRaw;
    }

    public static Component filter(Component message) {
        String raw = message.getString();
        lastRaw = raw;
        if (debug) dump(raw);

        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.actionBarFilter || raw.isEmpty()) return message;

        String[] segments = SEP.split(raw);
        List<String> keep = new ArrayList<>(segments.length);
        boolean changed = false;
        for (String segment : segments) {
            String s = segment.trim();
            if (s.isEmpty()) continue;
            if (dropSegment(cfg, s)) {
                changed = true;
                continue;
            }
            String filtered = dropStats(cfg, s);
            if (!filtered.equals(s)) changed = true;
            if (!filtered.isEmpty()) keep.add(filtered);
        }
        if (!changed) return message;
        return Component.literal(String.join("     ", keep));
    }

    private static boolean dropSegment(TeslaMapsConfig cfg, String segment) {
        String s = StringUtil.strip(segment);
        for (String custom : cfg.actionBarHidden) {
            if (!custom.isEmpty() && s.toLowerCase().contains(custom.toLowerCase())) return true;
        }
        if (cfg.abHideLocation && (s.indexOf('\uE067') >= 0 || s.indexOf('\u23E3') >= 0)) return true;
        if (cfg.abHideSkillXp && SKILL_XP.matcher(s).find()) return true;
        if (cfg.abHideAbilityMana && s.contains("Mana (")) return true;
        if (cfg.abHideDrillFuel && s.contains("Drill Fuel")) return true;
        if (cfg.abHideSecrets && s.contains("Secrets")) return true;
        return false;
    }

    /** Several stats share one segment (mana + overflow mana), so drop them stat by stat. */
    private static String dropStats(TeslaMapsConfig cfg, String segment) {
        if (segment.indexOf(' ') < 0) return dropStat(cfg, segment) ? "" : segment;
        StringBuilder sb = new StringBuilder(segment.length());
        for (String stat : segment.split(" ")) {
            if (stat.isEmpty() || dropStat(cfg, stat)) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(stat);
        }
        return sb.toString();
    }

    private static boolean dropStat(TeslaMapsConfig cfg, String stat) {
        if (cfg.abHideHealth && (stat.indexOf('\uE010') >= 0 || stat.indexOf('\u2764') >= 0)) return true;
        if (cfg.abHideDefense && (stat.indexOf('\uE008') >= 0 || stat.indexOf('\u2748') >= 0)) return true;
        if (cfg.abHideMana && (stat.indexOf('\uE003') >= 0 || stat.indexOf('\u270E') >= 0)) return true;
        if (cfg.abHideOverflowMana && stat.indexOf('\uE017') >= 0) return true;
        if (cfg.abHideVitality && stat.indexOf('\uE028') >= 0) return true;
        if (cfg.abHideRiftTime && (stat.indexOf('\uE020') >= 0 || stat.indexOf('\u0444') >= 0)) return true;
        if (cfg.abHideArmorStacks && ARMOR_STACK.matcher(StringUtil.strip(stat)).find()) return true;
        return false;
    }

    private static void dump(String raw) {
        if (raw.equals(lastDump)) return;
        lastDump = raw;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§b[AB] §r" + raw));
        String[] segments = SEP.split(raw);
        for (int i = 0; i < segments.length; i++) {
            mc.player.sendSystemMessage(Component.literal(
                    "§8 [" + i + "] §7" + escape(segments[i].trim())));
        }
    }

    public static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') sb.append('&');
            else if (c < 32 || c > 126) sb.append(String.format("\\u%04X", (int) c));
            else sb.append(c);
        }
        return sb.toString();
    }
}
