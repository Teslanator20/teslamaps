package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.ChromaColors;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Repaints the [TeslaMaps] tag of the mod's own chat lines with a static per-pixel gradient
 * between the two configured colours. The rest of the line keeps its colours.
 */
public class ChromaPrefix {

    private static final String TAG = "[TeslaMaps]";

    /** Rewritten line, or null when the message is not one of ours. */
    public static Component rewrite(Component message) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.section("Chat")) return null;
        boolean chroma = config.chromaPrefix, click = config.clickablePrefix;
        if (!chroma && !click) return null;

        List<Component> parts = message.toFlatList();
        if (parts.isEmpty()) return null;

        Component first = parts.get(0);
        String text = first.getString();
        int idx = text.indexOf(TAG);
        // the tag sits at the start, at most one legacy colour code in front
        if (idx < 0 || idx > 2) return null;

        MutableComponent out = Component.empty();
        String lead = text.substring(0, idx);
        MutableComponent tag = chroma
                ? Component.literal(TAG).withStyle(style -> style.withColor(ChromaColors.FADE))
                : Component.literal(lead + TAG).setStyle(first.getStyle());
        if (click) tag = tag.withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand("/tmap"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Click to open the TeslaMaps config"))));
        out.append(tag);
        // keep the old colour code so the text after the tag stays coloured
        String rest = lead + text.substring(idx + TAG.length());
        if (!rest.isEmpty()) out.append(Component.literal(rest).setStyle(first.getStyle()));
        for (int i = 1; i < parts.size(); i++) out.append(parts.get(i));
        return out;
    }
}
