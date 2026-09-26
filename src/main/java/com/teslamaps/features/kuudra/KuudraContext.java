package com.teslamaps.features.kuudra;

import net.minecraft.world.entity.monster.MagmaCube;

/** Thin view on {@link Kuudra} for the w16-08 helpers: phase as 0-4 (none, supplies, build, stun/dps, lair). */
public final class KuudraContext {

    public static final int NONE = 0, SUPPLIES = 1, BUILD = 2, STUN = 3, LAIR = 4;

    private KuudraContext() {}

    public static void register() {
        Kuudra.LISTENERS.add((prev, now) -> {
            if (now == Kuudra.Phase.SUPPLIES) {
                KuudraSupplyHelper.reset();
                KuudraSupplySplits.reset();
            } else if (now == Kuudra.Phase.NONE) {
                KuudraSupplyHelper.reset();
            }
        });
    }

    public static boolean inKuudra() { return Kuudra.inKuudra(); }

    public static int phase() {
        if (!Kuudra.inKuudra()) return NONE;
        return switch (Kuudra.phase()) {
            case SUPPLIES -> SUPPLIES;
            case BUILD -> BUILD;
            case EATEN, STUN, DPS, SKIP -> STUN;
            case BOSS -> LAIR;
            default -> NONE;
        };
    }

    public static long runStart() { return Kuudra.runStartMs(); }
    public static MagmaCube boss() { return Kuudra.boss(); }
}
