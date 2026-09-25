package com.teslamaps.render.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class TeslaGuiPipelines {

    // All three share the vertex shader; only the fragment stage differs.
    public static final RenderPipeline SDF_FILL = register("sdf_fill", "core/teslamaps_sdf");
    public static final RenderPipeline SDF_OUTLINE = register("sdf_outline", "core/teslamaps_sdf_outline");
    public static final RenderPipeline SDF_SHADOW = register("sdf_shadow", "core/teslamaps_sdf_shadow");

    // Procedural menu backgrounds. Same vertex stage as the SDF shapes — the
    // vertex shader's outputs (localPos/shapeParams/vertexColor) are what these
    // fragment shaders read their resolution, time and mouse from.
    public static final RenderPipeline BG_SNOW = register("bg_snow", "core/teslamaps_bg_snow");
    public static final RenderPipeline BG_SHIMMER = register("bg_shimmer", "core/teslamaps_bg_shimmer");
    public static final RenderPipeline BG_LIQUID = register("bg_liquid", "core/teslamaps_bg_liquid");
    public static final RenderPipeline BG_SURGE = register("bg_surge", "core/teslamaps_bg_surge");
    public static final RenderPipeline BG_STARFIELD = register("bg_starfield", "core/teslamaps_bg_starfield");
    public static final RenderPipeline BG_NEBULA = register("bg_nebula", "core/teslamaps_bg_nebula");
    public static final RenderPipeline BG_MESH = register("bg_mesh", "core/teslamaps_bg_mesh");
    public static final RenderPipeline BG_DEEPSPACE = register("bg_deepspace", "core/teslamaps_bg_deepspace");
    public static final RenderPipeline BG_LIQUIDMETAL = register("bg_liquidmetal", "core/teslamaps_bg_liquidmetal");
    public static final RenderPipeline BG_WAVES = register("bg_waves", "core/teslamaps_bg_waves");

    // Rarity silhouette: samples the gui item atlas, bounds of the atlas cell ride in SdfParams.
    public static final RenderPipeline RARITY_CONTOUR = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                    .withLocation("pipeline/teslamaps_rarity_contour")
                    .withVertexShader(Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_sdf"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_rarity_contour"))
                    .withSampler("Sampler0")
                    .withVertexFormat(TeslaVertexFormats.SDF, VertexFormat.Mode.QUADS)
                    .build()
    );

    private static RenderPipeline register(String name, String fragmentShader) {
        return RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                        .withLocation("pipeline/teslamaps_" + name)
                        .withVertexShader(Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_sdf"))
                        .withFragmentShader(Identifier.fromNamespaceAndPath("teslamaps", fragmentShader))
                        .withVertexFormat(TeslaVertexFormats.SDF, VertexFormat.Mode.QUADS)
                        .build()
        );
    }

    public static void init() {
    }
}
