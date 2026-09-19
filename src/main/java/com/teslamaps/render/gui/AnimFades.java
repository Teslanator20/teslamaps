package com.teslamaps.render.gui;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Animated text fades. One pipeline per look, the colours are baked into its fragment shader, so a
 * line can carry several different fades at once - the shared uniform block only feeds time.
 */
public class AnimFades {

    public static final String PREFIX = "tmanim_";

    /** Preview lines need the chroma plumbing even when the config toggles are off. */
    public static boolean previewActive = false;

    private static final String[] NAMES = {
            "ember_flow", "ember_shine", "ember_pulse", "flame_wave",
            "arcane_flow", "arcane_shine", "arcane_pulse", "nebula_wave",
            "divine_flow", "divine_shine", "frost_pulse", "sun_wave",
            "max_rainbow", "max_shine", "max_pulse", "max_wave",
    };

    private static final RenderPipeline[] PIPELINES = new RenderPipeline[NAMES.length];
    private static final TextColor[] COLORS = new TextColor[NAMES.length];

    static {
        for (int i = 0; i < NAMES.length; i++) {
            PIPELINES[i] = register(NAMES[i]);
            COLORS[i] = new TextColor(0xFFFFFF, PREFIX + NAMES[i]);
        }
    }

    private static RenderPipeline register(String name) {
        return RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                        .withLocation("pipeline/teslamaps_anim_" + name)
                        .withVertexShader(Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_textured_chroma"))
                        .withFragmentShader(Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_anim_" + name))
                        .withSampler("Sampler0")
                        .withUniform(ChromaPipelines.UNIFORM, UniformType.UNIFORM_BUFFER)
                        .withColorTargetState(new ColorTargetState(
                                Optional.of(BlendFunction.TRANSLUCENT), ColorTargetState.WRITE_COLOR))
                        .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                        .build());
    }

    /** Style colour that paints text with the named fade, null when the name is unknown. */
    public static TextColor color(String name) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(name)) return COLORS[i];
        }
        return null;
    }

    public static RenderPipeline pipeline(String colorName) {
        for (int i = 0; i < NAMES.length; i++) {
            if ((PREFIX + NAMES[i]).equals(colorName)) return PIPELINES[i];
        }
        return null;
    }

    public static void init() {
    }

    private AnimFades() {}
}
