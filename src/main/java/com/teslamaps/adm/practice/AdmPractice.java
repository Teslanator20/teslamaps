package com.teslamaps.adm.practice;

import com.teslamaps.debug.Perf;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public final class AdmPractice {
    private AdmPractice() {}

    public static void register() {
        AdmSimonSays.register();
        SsPractice.register();
        AdmTerms.register();
        BladeHighlight.register();
        BrBoxes.register();
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            if (!SsPractice.practicing()) return;
            Perf.begin("AdmSsPractice");
            try {
                SsPractice.render(context);
            } finally {
                Perf.end();
            }
        });
    }
}
