package com.teslamaps.render.gui;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
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
    public static final RenderPipeline RARITY_CONTOUR = gui()
            .withLocation("pipeline/teslamaps_rarity_contour")
            .withVertexShader(Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_sdf"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_rarity_contour"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .build();

    // Frosted HUD panel: samples the world copy of PanelBlur.
    public static final RenderPipeline PANEL_BLUR = gui()
            .withLocation("pipeline/teslamaps_panel_blur")
            .withVertexShader(Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_sdf"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_panel_blur"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .build();

    private static RenderPipeline.Builder gui() {
        return RenderPipeline.builder()
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, TeslaVertexFormats.SDF)
                .withPrimitiveTopology(PrimitiveTopology.QUADS);
    }

    private static RenderPipeline register(String name, String fragmentShader) {
        return gui()
                .withLocation("pipeline/teslamaps_" + name)
                .withVertexShader(Identifier.fromNamespaceAndPath("teslamaps", "core/teslamaps_sdf"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("teslamaps", fragmentShader))
                .build();
    }

    public static void init() {
    }
}
