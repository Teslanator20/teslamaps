package com.teslamaps.features;

// Ported from Skysoft's ChatTimestamps.kt (formats), colour like HypixelSimpleChat's withTimestamp
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** "[HH:mm] " in front of every stored chat line. */
public final class ChatTimestamps {

    public static final String[] FORMATS = {"24h", "24h + Seconds", "12h", "12h + Seconds"};
    private static final DateTimeFormatter[] PATTERNS = {
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH)};

    private ChatTimestamps() {}

    public static Component decorate(Component line) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.chatTimestamps || !c.section("Chat")) return line;
        int f = 0;
        for (int i = 0; i < FORMATS.length; i++) if (FORMATS[i].equals(c.chatTimestampFormat)) f = i;
        int color;
        try {
            color = Integer.parseInt(c.chatTimestampColor, 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            color = 0x555555;
        }
        int rgb = color;
        String stamp = "[" + LocalTime.now().format(PATTERNS[f]) + "] ";
        MutableComponent out = Component.empty()
                .append(Component.literal(stamp).withStyle(s -> s.withColor(rgb)))
                .append(line);
        SharedImageChat.aliasStamped(StringUtil.strip(line.getString()).trim(), StringUtil.strip(out.getString()).trim());
        return out;
    }

    /** The line without our stamp. */
    public static Component body(Component c) {
        List<Component> sib = c.getSiblings();
        if (sib.size() < 2 || !(c.getContents() instanceof PlainTextContents p) || !p.text().isEmpty()) return c;
        if (isStamp(sib.get(0).getString(), 0) < 0) return c;
        if (sib.size() == 2) return sib.get(1);
        MutableComponent rest = Component.empty();
        for (int i = 1; i < sib.size(); i++) rest.append(sib.get(i));
        return rest;
    }

    /** Plain text without a leading stamp. */
    public static String cut(String s) {
        int end = isStamp(s, 0);
        return end < 0 ? s : s.substring(end);
    }

    /** End index of "[12:34] " / "[1:02:03 PM] " at {@code from}, or -1. */
    private static int isStamp(String s, int from) {
        if (s.length() < from + 8 || s.charAt(from) != '[') return -1;
        boolean colon = false;
        int max = Math.min(s.length() - 1, from + 14);
        for (int i = from + 1; i < max; i++) {
            char ch = s.charAt(i);
            if (ch == ']') return colon && s.charAt(i + 1) == ' ' ? i + 2 : -1;
            if (ch == ':') colon = true;
            else if (!(ch >= '0' && ch <= '9') && ch != ' ' && ch != 'A' && ch != 'P' && ch != 'M') return -1;
        }
        return -1;
    }
}
