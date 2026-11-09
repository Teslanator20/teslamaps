package com.teslamaps.profileviewer.data;

import com.teslamaps.utils.LegacyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Hypixel still sends 1.8 paragraph codes; this turns them into real components. */
public class LegacyText {

    public static MutableComponent parse(String legacy) {
        MutableComponent result = Component.empty();
        if (legacy == null || legacy.isEmpty()) return result;

        Style style = Style.EMPTY.withItalic(false);
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < legacy.length()) {
                char code = Character.toLowerCase(legacy.charAt(++i));
                ChatFormatting formatting = ChatFormatting.getByCode(code);
                if (formatting == null) {
                    current.append(c).append(code);
                    continue;
                }
                if (!current.isEmpty()) {
                    result.append(Component.literal(current.toString()).setStyle(style));
                    current.setLength(0);
                }
                style = formatting == ChatFormatting.RESET
                        ? Style.EMPTY.withItalic(false)
                        : LegacyFormat.isColor(formatting)
                        ? Style.EMPTY.withItalic(false).withColor(formatting)
                        : style.applyFormat(formatting);
                continue;
            }
            current.append(c);
        }

        if (!current.isEmpty()) {
            result.append(Component.literal(current.toString()).setStyle(style));
        }
        return result;
    }
}
