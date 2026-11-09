package com.teslamaps.utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;

public final class LegacyFormat {
    private static final ChatFormatting[] VALUES = ChatFormatting.values();
    private static final Map<String, ChatFormatting> BY_NAME = new HashMap<>();
    private static final String[] NAMES = new String[VALUES.length];

    static {
        for (ChatFormatting f : VALUES) {
            NAMES[f.ordinal()] = f.name().toLowerCase(Locale.ROOT);
            BY_NAME.put(clean(f.name()), f);
        }
    }

    private LegacyFormat() {}

    private static String clean(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (c >= 'a' && c <= 'z') sb.append(c);
        }
        return sb.toString();
    }

    public static boolean isColor(ChatFormatting f) { return f.ordinal() < 16; }

    public static boolean isFormat(ChatFormatting f) { return f.ordinal() >= 16 && f != ChatFormatting.RESET; }

    public static Integer getColor(ChatFormatting f) {
        TextColor c = isColor(f) ? TextColor.fromLegacyFormat(f) : null;
        return c == null ? null : c.getValue();
    }

    public static char getChar(ChatFormatting f) { return f.toString().charAt(1); }

    public static String getName(ChatFormatting f) { return NAMES[f.ordinal()]; }

    public static ChatFormatting getByName(String name) { return name == null ? null : BY_NAME.get(clean(name)); }
}
