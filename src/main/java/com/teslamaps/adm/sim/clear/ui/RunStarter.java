package com.teslamaps.adm.sim.clear.ui;
// Ported from ADM's p3sim/clear/ui/RunStarter.kt

import com.teslamaps.adm.sim.P3Sim;
import com.teslamaps.adm.sim.pack.PackSet;
import com.teslamaps.adm.sim.pack.Packs;
import com.teslamaps.adm.sim.pack.layouts.Layout;
import com.teslamaps.adm.sim.pack.layouts.LayoutPack;
import com.teslamaps.adm.sim.run.LayoutPick;
import com.teslamaps.adm.sim.run.LayoutSource;
import com.teslamaps.adm.sim.run.RunConfig;
import com.teslamaps.adm.sim.run.RunLauncher;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

public final class RunStarter {
    private RunStarter() {}

    public static void start(RunConfig config, Consumer<String> reply) {
        if (P3Sim.inSim() && !P3Sim.localSim()) { reply.accept("Only the host of the party can start a run."); return; }
        withPacks(reply, set -> launch(config, set, reply));
    }

    public static void withPacks(Consumer<String> reply, Consumer<PackSet> then) {
        PackSet have = Packs.current;
        if (have != null && have.canBuildDungeon()) { then.accept(have); return; }
        Packs.loadAsync().whenComplete((set, err) -> Minecraft.getInstance().execute(() -> {
            if (err != null || set == null) {
                reply.accept("ADS could not read its data packs: " + (err != null && err.getMessage() != null ? err.getMessage() : "unknown error") + ".");
                return;
            }
            if (!set.canBuildDungeon()) { set.userLines().forEach(reply); return; }
            then.accept(set);
        }));
    }

    private static void launch(RunConfig config0, PackSet set, Consumer<String> reply) {
        LayoutPack layouts = set.layouts;
        if (layouts == null) return;
        RunConfig config = config0;
        LayoutSource src = config.layout;
        if (src instanceof LayoutSource.Recorded rec) {
            List<String> ids = new ArrayList<>();
            for (Layout l : layouts.layouts) ids.add(l.id);
            LayoutBrowserModel.Resolved r = LayoutBrowserModel.resolve(ids, rec.id);
            if (r instanceof LayoutBrowserModel.Resolved.One one) {
                config = new RunConfig(config.floor, new LayoutSource.Recorded(one.id), config.seed, config.bots, config.partySize, config.lag,
                        config.noDeaths, config.mobHpScale, config.statsPreset, config.strategy, config.replay);
            } else if (r instanceof LayoutBrowserModel.Resolved.Many many) {
                reply.accept("\"" + rec.id + "\" matches " + many.ids.size() + " layouts, for example " + String.join(", ", many.ids.subList(0, Math.min(3, many.ids.size()))) + ". Type more of the id.");
                return;
            } else {
                reply.accept("There is no recorded layout called " + rec.id + ". The start screen's layout browser lists them.");
                return;
            }
        }
        if (!(src instanceof LayoutSource.Practice)) {
            LayoutPick.Result picked = LayoutPick.pick(config, layouts);
            if (picked instanceof LayoutPick.Result.Failed f) { reply.accept(f.message); return; }
            if (picked instanceof LayoutPick.Result.Ok ok && ok.picked.warning != null) reply.accept(ok.picked.warning);
        }
        reply.accept(RunArgs.describe(config));
        RunLauncher.launch(config, reply);
    }
}
