// Ported from ADM's p3sim/clear/mobs/MobTags.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.teslamaps.adm.sim.pack.mobs.MobModifier;
import com.teslamaps.adm.sim.pack.mobs.MobTag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

public final class MobTags {
    private MobTags() {}

    public static final Pattern STARRED_REGEX = Pattern.compile("^.*✯ .*\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?.?❤$");

    public static String formatHp(double hp) {
        double v = hp < 0.0 ? 0.0 : hp;
        if (v >= 1_000_000_000.0) return trim(v / 1e9) + "B";
        if (v >= 1_000_000.0) return rounds1(v / 1e6) >= 1000.0 ? trim(v / 1e9) + "B" : trim(v / 1e6) + "M";
        if (v >= 100_000.0) return rounds1(v / 1e3) >= 1000.0 ? trim(v / 1e6) + "M" : trim(v / 1e3) + "k";
        return String.format(java.util.Locale.ROOT, "%,d", Math.round(v));
    }

    private static double rounds1(double x) { return new BigDecimal(x).setScale(1, RoundingMode.HALF_EVEN).doubleValue(); }

    private static String trim(double x) {
        String r = new BigDecimal(x).setScale(1, RoundingMode.HALF_EVEN).toPlainString();
        return r.endsWith(".0") ? r.substring(0, r.length() - 2) : r;
    }

    public static String code(String c) {
        if (c == null || c.isEmpty()) return "";
        if (c.startsWith("§")) return c;
        return "§" + c;
    }

    public static String unstarredHead(String head) {
        if (!head.contains("✯")) return head;
        if (head.contains("§6✯ §r")) return head.replace("§6✯ §r", "");
        return head.replace("✯ ", "").replace("§6§r", "");
    }

    private static String removeSection(String s) { return s.startsWith("§") ? s.substring(1) : s; }

    public static String hpColor(MobModifier modifier, double hp, double maxHp) {
        if (modifier == null) return maxHp > 0 && hp <= 0.5 * maxHp ? "e" : "a";
        String hurt = modifier.hpColorHurt;
        Double limit = modifier.hpThreshold;
        if (hurt != null && limit != null && maxHp > 0 && hp <= limit * maxHp) return removeSection(hurt);
        return removeSection(modifier.hpColor != null ? modifier.hpColor : "a");
    }

    public static String nametagLegacy(MobTag tag, String name, MobModifier modifier, double hp, double maxHp, boolean starred) {
        String word = modifier != null && modifier.id != null ? modifier.id : "";
        String head = starred ? tag.head : unstarredHead(tag.head);
        String archColour = code(tag.nameColor);
        String bold = tag.bold ? "§l" : "";
        String tail = "§r §" + hpColor(modifier, hp, maxHp) + formatHp(hp) + "§r§c❤";
        if (word.isEmpty()) return head + archColour + bold + name + tail;
        if (tag.modSplit) {
            String wordColour = code(modifier != null ? modifier.nameColor : null);
            if (wordColour.isEmpty()) wordColour = word.equals("Flaming") ? "§6" : "";
            return head + wordColour + word + " §r" + archColour + bold + name + tail;
        }
        if (modifier != null && !modifier.headReset && head.endsWith("§r")) head = head.substring(0, head.length() - 2);
        String colour = modifier == null || modifier.nameColor == null ? archColour : code(modifier.nameColor);
        return head + colour + bold + word + " " + name + tail;
    }

    public static String plain(String legacy) {
        StringBuilder sb = new StringBuilder(legacy.length());
        int i = 0;
        while (i < legacy.length()) {
            if (legacy.charAt(i) == '§' && i + 1 < legacy.length()) { i += 2; continue; }
            sb.append(legacy.charAt(i)); i++;
        }
        return sb.toString();
    }

    public static boolean looksStarred(String legacy) { return STARRED_REGEX.matcher(plain(legacy)).matches(); }
}
