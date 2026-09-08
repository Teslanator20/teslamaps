package com.teslamaps.utils;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Component text with the styles rendered back into § codes. Hypixel sends the guild
 * list as styled components, so a plain {@code getString()} drops exactly the markers
 * the list parser needs ("§6Guild Name: ").
 */
public class ChatText {

    private static final Map<Integer, ChatFormatting> COLORS = new HashMap<>();
    static {
        for (ChatFormatting formatting : ChatFormatting.values()) {
            TextColor color = TextColor.fromLegacyFormat(formatting);
            if (color != null) COLORS.put(color.getValue(), formatting);
        }
    }

    public static String formatted(Component component) {
        if (component == null) return "";
        StringBuilder out = new StringBuilder(50);
        append(component, out, new boolean[1]);

        String s = out.toString();
        if (s.endsWith("§r")) s = s.substring(0, s.length() - 2);
        if (s.startsWith("§r")) s = s.substring(2);
        return s;
    }

    // self, then every sibling depth first
    private static void append(Component component, StringBuilder out, boolean[] wasFormatted) {
        String style = style(component.getStyle());
        boolean lastWasReset = out.length() == 2 && out.charAt(0) == '§' && out.charAt(1) == 'r';
        if (!style.isEmpty() && ((wasFormatted[0] && !lastWasReset) || !style.equals("§f"))) {
            out.append(style);
            wasFormatted[0] = true;
        }
        out.append(plain(component));
        out.append("§r");
        wasFormatted[0] = true;

        for (Component sibling : component.getSiblings()) append(sibling, out, wasFormatted);
    }

    private static String plain(Component component) {
        if (component.getContents() instanceof TranslatableContents) return component.getString();
        if (component.getContents() instanceof PlainTextContents text) return text.text();
        return "";
    }

    public static String style(Style style) {
        StringBuilder out = new StringBuilder();
        TextColor color = style.getColor();
        if (color != null) {
            ChatFormatting formatting = COLORS.get(color.getValue());
            out.append(formatting != null ? formatting.toString() : "<" + color.serialize() + ">");
        }
        if (style.isBold()) out.append("§l");
        if (style.isItalic()) out.append("§o");
        if (style.isUnderlined()) out.append("§n");
        if (style.isStrikethrough()) out.append("§m");
        if (style.isObfuscated()) out.append("§k");
        return out.toString();
    }

    /** Hypixel wraps its lines in resets. */
    public static String stripHypixelMessage(String message) {
        String s = message;
        while (s.startsWith("§r")) s = s.substring(2);
        while (s.endsWith("§r")) s = s.substring(0, s.length() - 2);
        return s;
    }

    /** "[MVP+] Notch" -> "Notch", "Notch's" -> "Notch". */
    public static String cleanPlayerName(String raw) {
        String[] split = raw.trim().split(" ");
        String name = StringUtil.strip(split.length > 1 ? split[1] : split[0]);
        return name.endsWith("'s") ? name.substring(0, name.length() - 2) : name;
    }
}
