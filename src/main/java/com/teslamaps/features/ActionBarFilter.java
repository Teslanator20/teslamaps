package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
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
        boolean cutSkill = SkillExpDisplay.onActionBar(raw);
        if ((!cfg.actionBarFilter && !cutSkill) || raw.isEmpty()) return message;

        List<int[]> segs = new ArrayList<>();
        Matcher sep = SEP.matcher(raw);
        int pos = 0;
        while (sep.find()) {
            if (sep.start() > pos) segs.add(new int[]{pos, sep.start()});
            pos = sep.end();
        }
        if (pos < raw.length()) segs.add(new int[]{pos, raw.length()});
        if (segs.isEmpty()) return message;

        boolean[] cut = new boolean[raw.length()];
        boolean changed = false;
        int keptEnd = -1;
        for (int j = 0; j < segs.size(); j++) {
            int from = segs.get(j)[0], to = segs.get(j)[1];
            String s = raw.substring(from, to).trim();
            boolean drop = (cutSkill && SKILL_XP.matcher(StringUtil.strip(s)).find())
                    || (cfg.actionBarFilter && dropSegment(cfg, s));
            if (!drop && cfg.actionBarFilter && !cutStats(cfg, raw, from, to, cut)) drop = true;
            if (drop) {
                changed = true;
                mark(cut, from, to);
                continue;
            }
            if (keptEnd >= 0) mark(cut, keptEnd, segs.get(j - 1)[1]);
            else mark(cut, segs.get(0)[0], from);
            keptEnd = to;
        }
        if (keptEnd < 0) return Component.empty();
        mark(cut, keptEnd, segs.get(segs.size() - 1)[1]);
        if (!changed) {
            for (boolean b : cut) if (b) { changed = true; break; }
            if (!changed) return message;
        }
        return withoutRanges(message, cut);
    }

    private static void mark(boolean[] cut, int from, int to) {
        for (int i = from; i < to; i++) cut[i] = true;
    }

    private static Component withoutRanges(Component message, boolean[] cut) {
        MutableComponent out = Component.empty();
        int[] offset = {0};
        message.visit((style, text) -> {
            StringBuilder kept = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                int at = offset[0] + i;
                if (at >= cut.length || !cut[at]) kept.append(text.charAt(i));
            }
            if (!kept.isEmpty()) out.append(Component.literal(kept.toString()).setStyle(style));
            offset[0] += text.length();
            return Optional.empty();
        }, Style.EMPTY);
        return out;
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

    /** Several stats share one segment (mana + overflow mana), so cut them stat by stat. False = nothing left. */
    private static boolean cutStats(TeslaMapsConfig cfg, String raw, int from, int to, boolean[] cut) {
        int keptEnd = -1;
        int i = from;
        while (i < to) {
            if (raw.charAt(i) == ' ') { i++; continue; }
            int end = raw.indexOf(' ', i);
            if (end < 0 || end > to) end = to;
            if (dropStat(cfg, raw.substring(i, end))) {
                mark(cut, i, end);
            } else {
                mark(cut, keptEnd >= 0 ? keptEnd : from, keptEnd >= 0 ? Math.max(keptEnd, i - 1) : i);
                keptEnd = end;
            }
            i = end;
        }
        if (keptEnd < 0) return false;
        mark(cut, keptEnd, to);
        return true;
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
