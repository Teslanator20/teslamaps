package com.teslamaps.render.gui;

import net.minecraft.network.chat.TextColor;

/**
 * Text painted in this colour is picked up by {@link ChromaHook#replacePipeline}. The name is the
 * marker; the rgb value only matters if the shader never runs, so it stays plain white.
 */
public class ChromaColors {

    public static final TextColor CHROMA = new TextColor(0xFFFFFF, ChromaHook.COLOR_NAME);
    public static final TextColor FADE = new TextColor(0xFFFFFF, ChromaHook.FADE_NAME);
    // §z: off-white so Style.equals does not merge it with plain white text
    public static final TextColor CHROMA_CODE = new TextColor(0xFFFFFE, ChromaHook.COLOR_NAME);

    private ChromaColors() {}
}
