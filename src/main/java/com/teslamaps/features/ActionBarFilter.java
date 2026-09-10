package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Drops segments out of the Hypixel action bar. /tmap debug actionbar shows what is in it. */
public class ActionBarFilter {

    private static final Pattern SEP = Pattern.compile("\\s{3,}");
    private static final Pattern SKILL_XP = Pattern.compile("\\+[\\d.,]+ [A-Za-z]+ \\(");

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

        String[] parts = SEP.split(raw);
        List<String> keep = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!hidden(cfg, part)) keep.add(part.trim());
        }
        if (keep.size() == parts.length) return message;
        return Component.literal(String.join("     ", keep));
    }

    private static boolean hidden(TeslaMapsConfig cfg, String part) {
        String s = StringUtil.strip(part);
        for (String custom : cfg.actionBarHidden) {
            if (!custom.isEmpty() && s.toLowerCase().contains(custom.toLowerCase())) return true;
        }
        if (cfg.abHideHealth && (s.indexOf('❤') >= 0 || s.indexOf('') >= 0)) return true;
        if (cfg.abHideDefense && (s.indexOf('❈') >= 0 || s.indexOf('') >= 0)) return true;
        if (cfg.abHideMana && s.contains("/")
                && (s.indexOf('✎') >= 0 || s.indexOf('') >= 0)) return true;
        if (cfg.abHideVitality && (s.contains("Vitality") || s.indexOf('') >= 0)) return true;
        if (cfg.abHideRiftTime && (s.indexOf('ф') >= 0 || s.indexOf('') >= 0)) return true;
        if (cfg.abHideAbilityMana && s.contains("Mana (")) return true;
        if (cfg.abHideDrillFuel && s.contains("Drill Fuel")) return true;
        if (cfg.abHideSecrets && s.contains("Secrets")) return true;
        if (cfg.abHideSkillXp && SKILL_XP.matcher(s).find()) return true;
        return false;
    }

    private static void dump(String raw) {
        if (raw.equals(lastDump)) return;
        lastDump = raw;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§b[AB] §r" + raw));
        String[] parts = SEP.split(raw);
        for (int i = 0; i < parts.length; i++) {
            mc.player.sendSystemMessage(Component.literal(
                    "§8 [" + i + "] §7" + escape(parts[i].trim())));
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
