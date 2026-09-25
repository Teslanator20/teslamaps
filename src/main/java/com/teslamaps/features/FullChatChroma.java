package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.ChromaColors;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.IdentityHashMap;
import java.util.Map;

/** Paints every visible chat glyph in the chroma colour; the shader animates it. */
public class FullChatChroma {

    private static final Map<Style, Style> STYLES = new IdentityHashMap<>();

    public static FormattedCharSequence apply(FormattedCharSequence text) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.fullChatChroma || !c.section("Chat")) return text;
        return sink -> text.accept((index, style, cp) -> sink.accept(index, chroma(style), cp));
    }

    private static Style chroma(Style style) {
        Style out = STYLES.get(style);
        if (out == null) {
            if (STYLES.size() > 1024) STYLES.clear();
            out = style.withColor(ChromaColors.CHROMA);
            STYLES.put(style, out);
        }
        return out;
    }
}
