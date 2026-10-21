// Ported from ADM's p3sim/clear/secrets/RedstoneKeys.kt
package com.teslamaps.adm.sim.clear.secrets;

public class RevealChest {
    public final SecretSite site;
    public final boolean counts;

    public RevealChest(SecretSite site, boolean counts) {
        this.site = site;
        this.counts = counts;
    }
}
