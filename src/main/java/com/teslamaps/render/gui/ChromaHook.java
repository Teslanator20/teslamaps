package com.teslamaps.render.gui;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.network.chat.TextColor;

/**
 * Drives the chroma shader. Text whose style colour carries the name {@link #COLOR_NAME} gets its
 * pipeline swapped for {@link ChromaPipelines#CHROMA_TEXT}, and the uniform block is filled once
 * per draw range. The hue itself is computed in the shader from gl_FragCoord, which is what makes
 * every chroma glyph on screen share one rainbow.
 */
public class ChromaHook {

    /** Style colours are matched by name, exactly like SkyHanni does it. */
    public static final String COLOR_NAME = "chroma";
    public static final String FADE_NAME = "teslafade";

    private static final ChromaUniform UNIFORM = new ChromaUniform();
    private static GpuBufferSlice slice;

    public static TextColor color() {
        return ChromaColors.CHROMA;
    }

    private static boolean enabled() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.enchantChromaMax || c.chromaPrefix || c.chromaFormatCode || c.fullChatChroma || AnimFades.previewActive;
    }

    private static boolean everything() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.chromaFormatCode && c.chromaEverything;
    }

    private static String cachedHexA = "", cachedHexB = "";
    private static int fadeA = 0xB300FF, fadeB = 0x3355FF;

    public static void computeBufferSlice() {
        if (!enabled()) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();

        // "30" means 30% of the screen width, not 30 pixels
        float chromaSize = c.chromaSize * (mc.getWindow().getWidth() / 100f);

        float ticks = mc.gui.getGuiTicks()
                + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        if (!c.chromaForward) ticks = -ticks;

        if (!cachedHexA.equals(c.prefixFadeColorA) || !cachedHexB.equals(c.prefixFadeColorB)) {
            cachedHexA = c.prefixFadeColorA;
            cachedHexB = c.prefixFadeColorB;
            fadeA = TeslaMapsConfig.parseColor(cachedHexA) & 0xFFFFFF;
            fadeB = TeslaMapsConfig.parseColor(cachedHexB) & 0xFFFFFF;
        }

        float timeOffset = ticks * (c.chromaSpeed / 360f);

        // static prefix gradient: pixel span of "[TeslaMaps]" at the chat's left edge
        float chatScale = mc.options.chatScale().get().floatValue();
        float guiScale = mc.getWindow().getGuiScale();
        float fadeStartX = 4f * chatScale * guiScale;
        float fadeWidth = mc.font.width("[TeslaMaps]") * chatScale * guiScale;

        slice = UNIFORM.writeWith(chromaSize, timeOffset, c.chromaSaturation, c.chromaForward ? 1 : 0,
                fadeStartX, fadeWidth, fadeA, fadeB, ticks, chatScale * guiScale);
    }

    /**
     * Binding a uniform the bound pipeline does not declare is a no-op, so this can be inserted
     * for every draw range without checking which pipeline is active.
     */
    public static void insertUniform(RenderPass pass) {
        if (!enabled() || slice == null) return;
        pass.setUniform(ChromaPipelines.UNIFORM, slice);
    }

    /** Called once a frame; without it the uniform storage keeps growing. */
    public static void clearUniforms() {
        UNIFORM.clear();
        slice = null;
    }

    /** Swaps in the chroma pipeline for glyphs painted in the chroma colour. */
    public static RenderPipeline replacePipeline(GuiElementRenderState state, RenderPipeline original) {
        if (!enabled()) return original;
        if (!(state instanceof GlyphRenderState glyph)) return original;
        if (!(glyph.renderable() instanceof BakedSheetGlyph.GlyphInstance instance)) return original;

        TextColor color = instance.style().getColor();
        if (color == null) return everything() ? ChromaPipelines.CHROMA_TEXT : original;
        String name = color.serialize();
        if (COLOR_NAME.equals(name)) return ChromaPipelines.CHROMA_TEXT;
        if (FADE_NAME.equals(name)) return ChromaPipelines.FADE_TEXT;
        if (name.startsWith(AnimFades.PREFIX)) {
            RenderPipeline anim = AnimFades.pipeline(name);
            if (anim != null) return anim;
        }
        return everything() ? ChromaPipelines.CHROMA_TEXT : original;
    }
}
