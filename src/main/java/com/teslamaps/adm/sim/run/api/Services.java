// Ported from ADM's p3sim/run/api/Services.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.clear.bots.BotsModule;
import com.teslamaps.adm.sim.clear.damage.DamageModule;
import com.teslamaps.adm.sim.clear.hud.HudModule;
import com.teslamaps.adm.sim.clear.layout.LayoutModule;
import com.teslamaps.adm.sim.clear.mobs.MobsModule;
import com.teslamaps.adm.sim.clear.puzzles.PuzzlesModule;
import com.teslamaps.adm.sim.clear.score.ScoreModule;
import com.teslamaps.adm.sim.clear.secrets.SecretsModule;
import com.teslamaps.adm.sim.clear.state.DoorsModule;
import com.teslamaps.adm.sim.clear.traps.TrapsModule;

/** The fixed wiring: each service is implemented by exactly one module object. `Services.mobs().setHp(run, id, 0.0, by)`. */
public final class Services {
    private Services() {}

    public static LayoutService layout() { return LayoutModule.INSTANCE; }

    public static DoorService doors() { return DoorsModule.INSTANCE; }

    public static MobService mobs() { return MobsModule.INSTANCE; }

    public static DamageService damage() { return DamageModule.INSTANCE; }

    public static SecretService secrets() { return SecretsModule.INSTANCE; }

    public static ScoreService score() { return ScoreModule.INSTANCE; }

    public static PuzzleHost puzzles() { return PuzzlesModule.INSTANCE; }

    public static BotService bots() { return BotsModule.INSTANCE; }

    public static TrapService traps() { return TrapsModule.INSTANCE; }

    public static ChannelService channels() { return HudModule.INSTANCE; }
}
