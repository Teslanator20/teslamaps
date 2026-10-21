// Ported from ADM's p3sim/run/RunSystem.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.clear.blood.BloodModule;
import com.teslamaps.adm.sim.clear.bots.BotsModule;
import com.teslamaps.adm.sim.clear.damage.DamageModule;
import com.teslamaps.adm.sim.clear.hud.HudModule;
import com.teslamaps.adm.sim.clear.layout.LayoutModule;
import com.teslamaps.adm.sim.clear.mobs.MobsModule;
import com.teslamaps.adm.sim.clear.puzzles.PuzzlesModule;
import com.teslamaps.adm.sim.clear.puzzles.hard.HardPuzzlesModule;
import com.teslamaps.adm.sim.clear.replay.ReplayModule;
import com.teslamaps.adm.sim.clear.score.ScoreModule;
import com.teslamaps.adm.sim.clear.secrets.SecretsModule;
import com.teslamaps.adm.sim.clear.state.DoorsModule;
import com.teslamaps.adm.sim.clear.traps.TrapsModule;
import com.teslamaps.adm.sim.clear.ui.UiModule;
import java.util.List;

/** Every module, in call order. The order is the contract. */
public final class AdsModules {
    private AdsModules() {}

    private static volatile List<AdsModule> all;

    public static List<AdsModule> all() {
        List<AdsModule> l = all;
        if (l != null) return l;
        synchronized (AdsModules.class) {
            if (all == null) {
                all = List.of(
                        EngineModule.INSTANCE,
                        ReplayModule.INSTANCE,
                        LayoutModule.INSTANCE,
                        DoorsModule.INSTANCE,
                        MobsModule.INSTANCE,
                        DamageModule.INSTANCE,
                        SecretsModule.INSTANCE,
                        ScoreModule.INSTANCE,
                        PuzzlesModule.INSTANCE,
                        HardPuzzlesModule.INSTANCE,
                        TrapsModule.INSTANCE,
                        BotsModule.INSTANCE,
                        BloodModule.INSTANCE,
                        HudModule.INSTANCE,
                        UiModule.INSTANCE);
            }
            return all;
        }
    }

    public static AdsModule byId(String id) {
        for (AdsModule m : all()) if (m.id().equals(id)) return m;
        return null;
    }
}
