package com.teslamaps.profileviewer;

import com.teslamaps.config.TeslaMapsConfig;

/** The optional /pv sections, all behind the "More Profile Data" master. */
public final class PvExtras {
    private PvExtras() {}

    private static boolean on(boolean sub) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.pvExtras && sub;
    }

    public static boolean gear() { return on(TeslaMapsConfig.get().pvGearScan); }
    public static boolean greenhouse() { return on(TeslaMapsConfig.get().pvGreenhouse); }
    public static boolean fishing() { return on(TeslaMapsConfig.get().pvFishingStats); }
    public static boolean minions() { return on(TeslaMapsConfig.get().pvMinions); }
    public static boolean miscBags() { return on(TeslaMapsConfig.get().pvMiscBags); }
    public static boolean deepLinks() { return on(TeslaMapsConfig.get().pvDeepLinks); }
    public static boolean levelPage() { return on(TeslaMapsConfig.get().pvLevelHoppity); }
}
