// Ported from ADM's p3sim/pack/PackSource.kt
package com.teslamaps.adm.sim.pack;

import java.io.InputStream;

public interface BundledPacks {
    InputStream open(String name);

    /** The data packs inside the running mod jar ({@code assets/teslamaps/adm/ads/packs/}). */
    BundledPacks JAR = new BundledPacks() {
        @Override public InputStream open(String name) {
            return BundledPacks.class.getClassLoader().getResourceAsStream(PackFiles.RESOURCE_ROOT + "/" + name);
        }
    };

    /** Nothing bundled (tests of the override folder alone). */
    BundledPacks NONE = new BundledPacks() {
        @Override public InputStream open(String name) { return null; }
    };
}
