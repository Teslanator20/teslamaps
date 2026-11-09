package com.teslamaps.adm;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.FeatureRegistry;

/** ADM port (undonecoffee/adm). Own sidebar group, shown by /tmap adg. */
public final class Adm {
    public static final String SECTION = "ADM";

    private Adm() {}

    public static boolean on() {
        return TeslaMapsConfig.get().section(SECTION);
    }

    /** True while ADM is on and its version of a feature replaces ours. */
    public static boolean overrides(boolean admToggle) {
        return admToggle && on();
    }

    public static void register() {
        FeatureRegistry.category(SECTION, SECTION, true);
        FeatureRegistry.category("ADM Timers", SECTION, false);
        FeatureRegistry.category("ADM Map", SECTION, false);
        FeatureRegistry.category("ADM Splits", SECTION, false);
        FeatureRegistry.category("ADM Leap & Recore", SECTION, false);
        FeatureRegistry.category("ADM Terminals & Practice", SECTION, false);
        FeatureRegistry.category("ADM Highlight & BR", SECTION, false);
        FeatureRegistry.category("ADM Simulator", SECTION, false);
        com.teslamaps.adm.misc.AdmMisc.register();
        com.teslamaps.adm.map.AdmMap.register();
        com.teslamaps.adm.leap.AdmLeap.register();
        com.teslamaps.adm.practice.AdmPractice.register();
        com.teslamaps.adm.sim.AdmSim.register();
    }
}
