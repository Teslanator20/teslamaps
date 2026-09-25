package com.teslamaps.utils;

// After Bax's AutomationEngine (buildContext / putRegexGroups / substituteVars)
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {x} {y} {z} {player} {island} {location} {server} {health} {maxHealth} {mana} {maxMana}, plus regex groups. */
public final class Placeholders {

    private static final Pattern NAMED_GROUP = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");
    private static final Pattern HEALTH = Pattern.compile("([\\d,]+)/([\\d,]+)❤");
    private static final Pattern MANA = Pattern.compile("([\\d,]+)/([\\d,]+)✎");
    private static final Pattern LOCATION = Pattern.compile("^\\s*[⏣ф]\\s*(.+)$");
    private static final Pattern SERVER = Pattern.compile("(m\\d+[A-Za-z]+|mini\\d+[A-Za-z]*|mega\\d+[A-Za-z]*)");

    /** Last raw action bar text, set by PlayerStats. */
    public static volatile String actionBar = "";

    private Placeholders() {}

    public static String apply(String text) {
        return apply(text, null, null);
    }

    /** {@code m}/{@code regex}: a trigger's match, its groups become {1} {2} … and {name}. */
    public static String apply(String text, Matcher m, String regex) {
        if (text == null || text.indexOf('{') < 0) return text;
        Map<String, String> ctx = context();
        if (m != null) {
            for (int g = 1; g <= m.groupCount(); g++) {
                String v = m.group(g) == null ? "" : m.group(g);
                ctx.put(String.valueOf(g), v);
                ctx.put("group" + g, v);
            }
            if (regex != null) {
                Matcher nm = NAMED_GROUP.matcher(regex);
                while (nm.find()) {
                    try {
                        String v = m.group(nm.group(1));
                        ctx.put(nm.group(1), v == null ? "" : v);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            int end = ch == '{' ? text.indexOf('}', i + 1) : -1;
            if (end < 0) {
                sb.append(ch);
                i++;
                continue;
            }
            String val = ctx.get(text.substring(i + 1, end));
            sb.append(val != null ? val : text.substring(i, end + 1));
            i = end + 1;
        }
        return sb.toString();
    }

    private static Map<String, String> context() {
        Map<String, String> ctx = new HashMap<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            BlockPos p = mc.player.blockPosition();
            ctx.put("x", String.valueOf(p.getX()));
            ctx.put("y", String.valueOf(p.getY()));
            ctx.put("z", String.valueOf(p.getZ()));
            ctx.put("player", mc.player.getName().getString());
        }
        ctx.put("island", SkyblockUtils.getArea());
        String location = "", server = "";
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            String s = StringUtil.strip(line).trim();
            Matcher l = LOCATION.matcher(s);
            if (location.isEmpty() && l.matches()) location = l.group(1).trim();
            Matcher sv = SERVER.matcher(s);
            if (server.isEmpty() && sv.find()) server = sv.group(1);
        }
        ctx.put("location", location);
        ctx.put("server", server);
        String bar = StringUtil.strip(actionBar);
        Matcher h = HEALTH.matcher(bar);
        boolean hp = h.find();
        ctx.put("health", hp ? h.group(1).replace(",", "") : "");
        ctx.put("maxHealth", hp ? h.group(2).replace(",", "") : "");
        Matcher mm = MANA.matcher(bar);
        boolean mana = mm.find();
        ctx.put("mana", mana ? mm.group(1).replace(",", "") : "");
        ctx.put("maxMana", mana ? mm.group(2).replace(",", "") : "");
        return ctx;
    }
}
