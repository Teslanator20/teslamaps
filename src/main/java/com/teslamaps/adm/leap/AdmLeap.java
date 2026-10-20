package com.teslamaps.adm.leap;

import com.teslamaps.adm.Adm;
import com.teslamaps.adm.leap.pov.PovPreviews;
import com.teslamaps.adm.leap.splits.AdmSplitsHud;
import com.teslamaps.adm.leap.splits.DungeonSplits;
import com.teslamaps.registry.Feature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class AdmLeap {
    private AdmLeap() {}

    public static void register() {
        Feature.of("AdmLeapWorld").section(Adm.SECTION)
                .tickAlways(() -> { AdmWorld.tick(); AdmLeapOrder.tick(); })
                .serverChat(AdmWorld::onChat);
        AdmLeapMenu.register();
        AdmLeapOrder.register();
        PovPreviews.register();
        Recore.register();
        TermInfo.register();
        AdmSplitsHud.register();
        DungeonSplits.register();
        ClientTickEvents.END_CLIENT_TICK.register(c -> PovPreviews.tickOff());
    }

    static void onWorldChange() {
        Recore.onWorldChange();
        TermInfo.onWorldChange();
        DungeonSplits.onWorldChange();
    }
}
