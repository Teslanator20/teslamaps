package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.IrisCompat;
import com.teslamaps.render.MapTexture;
import com.teslamaps.utils.HypixelModApi;

/** Config rows for the performance / compat switches of w12-03. */
public final class PerfOptions {

    private PerfOptions() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        if (cfg().irisCompat) IrisCompat.apply();
        HypixelModApi.register();

        Feature.of("PerfOptions")
                .config("Dev & Debug", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Performance & Compat");
                    b.button("Toggle All Rendering (temporary)", GlobalRender::toggle)
                            .desc("Turns every TeslaMaps HUD, ESP, glow, entity hider and slot overlay off until pressed again or restart. Same as /tmap rendertoggle. Useful to check if a problem comes from this mod.")
                            .tags("render", "disable", "conflict", "rendertoggle");
                    b.toggle("Quiet Hypixel Log Spam", () -> c.suppressLogSpam, v -> c.suppressLogSpam = v)
                            .desc("On Hypixel: drops the skin signature, scoreboard/team, passenger and player-info warnings from latest.log and skips the invalid entity data exceptions.")
                            .tags("log", "spam", "console");
                    b.toggle("Use Hypixel Mod API", () -> c.hypixelModApi, v -> c.hypixelModApi = v)
                            .desc(HypixelModApi.present()
                                    ? "Server type and party come straight from Hypixel instead of scoreboard/chat scraping."
                                    : "Needs the Hypixel Mod API mod installed (not found). Without it nothing changes.")
                            .tags("modapi", "location", "party");
                    b.toggle("Iris Shader Compat", () -> c.irisCompat, v -> {
                        c.irisCompat = v;
                        if (v) IrisCompat.apply();
                    }).desc(IrisCompat.irisLoaded()
                            ? "Registers the ESP pipelines with Iris so boxes and lines still draw with a shader pack. Turning it off needs a restart."
                            : "Only does something with Iris installed (not found).")
                            .tags("iris", "shader");
                })
                .config("ESP", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("GPU Instanced Boxes", () -> c.espInstancing, v -> c.espInstancing = v)
                            .desc("Filled ESP boxes are drawn as instances of one cube, one draw call per 341 boxes. Experimental: turn off if boxes vanish or look wrong.")
                            .tags("instancing", "performance", "fps");
                })
                .config("Map", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Cache Map As Texture", () -> c.mapTextureCache, v -> {
                        c.mapTextureCache = v;
                        MapTexture.invalidate();
                    }).desc("Rooms and doors are painted into a texture 4 times a second instead of every frame. Room colours can lag up to 0.25 s.")
                            .tags("performance", "fps", "texture");
                });
    }
}
