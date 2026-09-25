package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.ChromaColors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Repaints the [TeslaMaps] tag of the mod's own chat lines with a static per-pixel gradient
 * between the two configured colours. The rest of the line keeps its colours.
 */
public class ChromaPrefix {

    private static final String TAG = "[TeslaMaps]";

    public static final String[] STYLES = {"Long", "Short", "Custom"};

    private static String cachedRaw, cachedTag = TAG;

    /** Prefix text in use: [TeslaMaps], [TM] or the custom one with & turned into colour codes. */
    public static String tag() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if ("Short".equals(c.chatPrefixStyle)) return "[TM]";
        if (!"Custom".equals(c.chatPrefixStyle) || c.chatPrefixCustom == null || c.chatPrefixCustom.isBlank()) return TAG;
        if (!c.chatPrefixCustom.equals(cachedRaw)) {
            cachedRaw = c.chatPrefixCustom;
            cachedTag = cachedRaw.replaceAll("&(?=[0-9a-fk-orA-FK-OR])", "§");
        }
        return cachedTag;
    }

    /** Rewritten line, or null when the message is not one of ours. */
    public static Component rewrite(Component message) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        boolean restyle = !"Long".equals(config.chatPrefixStyle) && config.chatPrefixStyle != null;
        if ((!config.chromaPrefix && !restyle) || !config.section("Chat")) return null;

        List<Component> parts = message.toFlatList();
        if (parts.isEmpty()) return null;

        Component first = parts.get(0);
        String text = first.getString();
        int idx = text.indexOf(TAG);
        // the tag sits at the start, at most one legacy colour code in front
        if (idx < 0 || idx > 2) return null;

        MutableComponent out = Component.empty();
        String tag = tag();
        String rest;
        if (tag.indexOf('§') >= 0) {
            out.append(Component.literal(tag + "§r"));
            rest = text.substring(0, idx) + text.substring(idx + TAG.length());
        } else if (config.chromaPrefix) {
            out.append(Component.literal(tag).withStyle(style -> style.withColor(ChromaColors.FADE)));
            // keep the old colour code so the text after the tag stays coloured
            rest = text.substring(0, idx) + text.substring(idx + TAG.length());
        } else {
            rest = text.substring(0, idx) + tag + text.substring(idx + TAG.length());
        }
        if (!rest.isEmpty()) out.append(Component.literal(rest).setStyle(first.getStyle()));
        for (int i = 1; i < parts.size(); i++) out.append(parts.get(i));
        return out;
    }
}
