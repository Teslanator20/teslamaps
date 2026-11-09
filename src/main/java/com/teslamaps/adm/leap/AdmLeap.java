package com.teslamaps.adm.leap;

import com.teslamaps.adm.Adm;
import com.teslamaps.adm.leap.splits.DungeonSplits;
import com.teslamaps.registry.Feature;

public final class AdmLeap {
    private AdmLeap() {}

    public static void register() {
        Feature.of("AdmLeapWorld").section(Adm.SECTION)
                .tickAlways(AdmWorld::tick)
                .serverChat(AdmWorld::onChat);
        Recore.register();
        DungeonSplits.register();
    }

    static void onWorldChange() {
        Recore.onWorldChange();
        DungeonSplits.onWorldChange();
    }
}
