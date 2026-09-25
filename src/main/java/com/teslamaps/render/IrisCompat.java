package com.teslamaps.render;

// Ported from Skyblocker's compatibility/IrisCompatibility.java (Odin's IrisCompat.kt does the same)
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.teslamaps.TeslaMaps;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/** Assigns the ESP pipelines to Iris programs so shader packs keep drawing them. No-op without Iris. */
public final class IrisCompat {

    private static boolean done;

    private IrisCompat() {}

    public static boolean irisLoaded() {
        return FabricLoader.getInstance().isModLoaded("iris");
    }

    public static void apply() {
        if (done || !irisLoaded()) return;
        done = true;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<Enum> program = (Class<Enum>) Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object instance = api.getMethod("getInstance").invoke(null);
            Method assign = api.getMethod("assignPipeline", RenderPipeline.class, program);
            assign(assign, instance, TeslaRenderPipelines.LINES_ESP, Enum.valueOf(program, "LINES"));
            assign(assign, instance, TeslaRenderPipelines.LINES_DEPTH, Enum.valueOf(program, "LINES"));
            assign(assign, instance, TeslaRenderPipelines.FILLED_ESP, Enum.valueOf(program, "BASIC"));
            assign(assign, instance, TeslaRenderPipelines.FILLED_DEPTH, Enum.valueOf(program, "BASIC"));
            assign(assign, instance, TeslaRenderPipelines.FILLED_ESP_INSTANCED, Enum.valueOf(program, "BASIC"));
            assign(assign, instance, TeslaRenderPipelines.FILLED_DEPTH_INSTANCED, Enum.valueOf(program, "BASIC"));
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("Iris compat failed", t);
        }
    }

    private static void assign(Method m, Object api, RenderPipeline p, Object program) {
        try {
            m.invoke(api, p, program);
        } catch (Throwable t) {
            // IllegalStateException = already assigned
            if (!(t.getCause() instanceof IllegalStateException)) TeslaMaps.LOGGER.warn("Iris: could not assign {}", p.getLocation(), t);
        }
    }
}
