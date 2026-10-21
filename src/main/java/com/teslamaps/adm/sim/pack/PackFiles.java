// Ported from ADM's p3sim/pack/PackTypes.kt
package com.teslamaps.adm.sim.pack;

import java.nio.file.Path;
import java.util.List;

public final class PackFiles {
    private PackFiles() {}

    public static final String ROOMS_INDEX = "rooms.index.json.gz";
    public static final String ROOMS_BLOCKS = "rooms.blocks.bin";
    public static final String LAYOUTS = "layouts.json.gz";
    public static final String MOBS = "mobs.json.gz";
    public static final String RUNSTATE = "runstate.json.gz";
    public static final String TRAPS = "traps.json";

    public static final List<String> BUNDLED = List.of(ROOMS_INDEX, ROOMS_BLOCKS, LAYOUTS, MOBS, RUNSTATE, TRAPS);

    public static final String RESOURCE_ROOT = "assets/teslamaps/adm/ads/packs";

    /** {@code <gameDir>/config/teslamaps/adm/ads/packs-bundled/}, one sub-folder per content hash. */
    public static Path cacheRootFor(Path packsDir) { return packsDir.resolveSibling("packs-bundled"); }

    /** {@code <gameDir>/config/teslamaps/adm/ads/packs/}. */
    public static Path dirFor(Path gameDir) {
        return gameDir.resolve("config").resolve("teslamaps").resolve("adm").resolve("ads").resolve("packs");
    }

    public static Path defaultDir() { return dirFor(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()); }
}
