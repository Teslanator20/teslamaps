package com.teslamaps.render.gui;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Animated main-menu background. One fullscreen quad per frame, the procedural
 * work happens in the fragment shader. Resolution/time/mouse ride along in the
 * vertex attributes.
 * <p>
 * These started life as post-process chains, but a post chain run from the GUI
 * extract phase is wiped by the frame render that follows it; drawing through
 * the gui render state puts it in the right place in the draw order.
 */
public class MenuBackgroundRenderer {

    /**
     * @param accent brand-title colour for this preset. Picked against how bright
     *               the shader actually is: dark text on the washed-out ones,
     *               glowing text on the near-black ones.
     */
    private record Preset(RenderPipeline pipeline, int accent, String chain) {}

    private static final Map<String, Preset> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("Snow", new Preset(TeslaGuiPipelines.BG_SNOW, 0xFF0B2C4A, null));
        PRESETS.put("Shimmer", new Preset(TeslaGuiPipelines.BG_SHIMMER, 0xFF0D2B3A, null));
        PRESETS.put("Liquid", new Preset(TeslaGuiPipelines.BG_LIQUID, 0xFF2B0F1C, "sky_liquid"));
        PRESETS.put("Surge", new Preset(TeslaGuiPipelines.BG_SURGE, 0xFF8BE9FF, null));
        PRESETS.put("Starfield", new Preset(TeslaGuiPipelines.BG_STARFIELD, 0xFF9CC7FF, "sky_starfield"));
        PRESETS.put("Nebula", new Preset(TeslaGuiPipelines.BG_NEBULA, 0xFFD2A8FF, "sky_nebula"));
        PRESETS.put("Mesh", new Preset(TeslaGuiPipelines.BG_MESH, 0xFF12122A, "sky_mesh"));
        PRESETS.put("Deep Space", new Preset(TeslaGuiPipelines.BG_DEEPSPACE, 0xFFBFD4FF, "sky_deepspace"));
        PRESETS.put("Liquid Metal", new Preset(TeslaGuiPipelines.BG_LIQUIDMETAL, 0xFFDCE6F5, "sky_liquidmetal"));
        PRESETS.put("Waves", new Preset(TeslaGuiPipelines.BG_WAVES, 0xFF241A46, "sky_waves"));
    }

    /** Post-chain file for a preset, used by the in-world skybox. */
    public static String chainFor(String preset) {
        Preset p = PRESETS.get(preset);
        // snow/shimmer/surge only exist as menu backgrounds
        return p == null || p.chain() == null ? "sky_starfield" : p.chain();
    }

    /** Preset one step along from {@code name}; wraps at both ends. */
    public static String cycle(String name, int direction) {
        return step(names(), name, direction);
    }

    /** Same, but only over the procedural presets — the sky cannot show a still image. */
    public static String cycleShader(String name, int direction) {
        return step(shaderNames(), name, direction);
    }

    private static String step(String[] all, String name, int direction) {
        int index = 0;
        for (int i = 0; i < all.length; i++) if (all[i].equals(name)) index = i;
        return all[Math.floorMod(index + direction, all.length)];
    }

    /**
     * Tint for the randomiser: a bright hue, or plain white a fifth of the time so
     * the presets' own colouring shows up too. Kept light — a dark tint multiplies
     * the shader down to nothing.
     */
    public static String randomTint() {
        if (Math.random() < 0.2) return "FFFFFF";

        float hue = (float) Math.random();
        float saturation = 0.35f + (float) Math.random() * 0.5f;
        float value = 0.85f + (float) Math.random() * 0.15f;

        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, value) & 0xFFFFFF;
        return String.format("%06X", rgb);
    }

    public static String random(String except) {
        String[] all = names();
        if (all.length < 2) return all[0];
        String pick;
        do {
            pick = all[(int) (Math.random() * all.length)];
        } while (pick.equals(except));
        return pick;
    }

    private static final Preset FALLBACK = PRESETS.get("Liquid Metal");

    /** Procedural presets only. */
    public static String[] shaderNames() { return PRESETS.keySet().toArray(new String[0]); }

    /** Presets that also have an in-world sky shader. */
    public static String[] skyNames() {
        return PRESETS.entrySet().stream().filter(e -> e.getValue().chain() != null).map(java.util.Map.Entry::getKey).toArray(String[]::new);
    }

    /** Everything the menu can show: the procedural presets plus the player's own images. */
    public static String[] names() {
        ImageBackgrounds.rescan();
        java.util.List<String> all = new java.util.ArrayList<>(PRESETS.keySet());
        all.addAll(ImageBackgrounds.names());
        return all.toArray(new String[0]);
    }

    /** Last background actually put on screen, so a decoding image can hold it. */
    private static String lastDrawn;

    private static Preset current() {
        Preset preset = PRESETS.get(TeslaMapsConfig.get().titleBackground);
        if (preset != null) return preset;
        // An image is selected: keep showing the shader it replaced rather than the fallback.
        Preset held = lastDrawn == null ? null : PRESETS.get(lastDrawn);
        return held != null ? held : FALLBACK;
    }

    /** Brand-title colour: the preset's own accent, or the configured override. */
    public static int accentColor() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.titleAccentAuto) return TeslaMapsConfig.parseColor(config.titleAccentColor);
        // No sensible auto colour for a photo — the dim overlay makes near-white safe.
        if (ImageBackgrounds.has(config.titleBackground)) return 0xFFF0F3FA;
        return current().accent();
    }

    private static final ShaderClock CLOCK = new ShaderClock();

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();

        // A picked image wins. While one is still decoding we hold whatever was on
        // screen before instead of snapping to the fallback preset, which used to
        // flash Snow for a frame or two on every switch.
        String picked = TeslaMapsConfig.get().titleBackground;
        if (ImageBackgrounds.has(picked)) {
            if (ImageBackgrounds.draw(ctx, picked)) {
                lastDrawn = picked;
                return;
            }
            if (lastDrawn != null && !lastDrawn.equals(picked)
                    && ImageBackgrounds.has(lastDrawn) && ImageBackgrounds.draw(ctx, lastDrawn)) return;
        } else {
            lastDrawn = picked;
        }

        // The shaders sample gl_FragCoord, which is in native framebuffer pixels,
        // so the resolution they get must be native too — not gui units.
        float pixelWidth = mc.getWindow().getWidth();
        float pixelHeight = mc.getWindow().getHeight();
        float time = CLOCK.advance(TeslaMapsConfig.get().titleBackgroundSpeed);

        ctx.guiRenderState.addGuiElement(new ShaderQuadRenderState(
                current().pipeline(), ctx.pose(),
                0, 0, ctx.guiWidth(), ctx.guiHeight(),
                pixelWidth, pixelHeight, time, 1.0f,
                0.5f, 0.5f,   // no cursor parallax
                TeslaMapsConfig.parseColor(TeslaMapsConfig.get().titleBackgroundColor)));
    }
}
