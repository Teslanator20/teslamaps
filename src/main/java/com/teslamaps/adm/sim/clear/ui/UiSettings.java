package com.teslamaps.adm.sim.clear.ui;
// Ported from ADM's p3sim/clear/ui/UiSettings.kt

import com.mojang.blaze3d.platform.InputConstants;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.run.LagPreset;
import com.teslamaps.adm.sim.settings.BoolSetting;
import com.teslamaps.adm.sim.settings.NumSetting;
import com.teslamaps.adm.sim.settings.SelSetting;
import com.teslamaps.adm.sim.settings.SimSettings;
import com.teslamaps.adm.sim.settings.StrSetting;

public final class UiSettings {
    private UiSettings() {}

    public enum MobHp { BASE, PER_RUN }

    private static BoolSetting bots;
    private static NumSetting partySize;
    private static SelSetting lag;
    private static BoolSetting noDeaths;
    private static SelSetting mobHp;
    private static StrSetting strategy;

    private static volatile boolean ready = false;

    public static void register() {
        bots = SimSettings.bool("Run Bots", true, "Teammate bots in a full run: they walk the dungeon and their rooms check off on the measured schedule. Off: you clear alone and the party list is just you.");
        partySize = SimSettings.num("Run Party Size", 5, 1, 5, 1, "", "Players in a full run, you included. The bots are the classes nobody plays. 1 is the same as bots off.");
        lag = SimSettings.sel("Run Lag", LagPreset.TYPICAL, "Server lag in a full run, as net ticks lost per minute: Good 14.5, Typical 30.3, Bad 56.6, High slot (an instance slot above 200) 54.6. Off: no lag at all.");
        noDeaths = SimSettings.bool("Run No Deaths", false, "You cannot die in the clear: your health stops at its last point instead of the death and the revive.");
        mobHp = SimSettings.sel("Run Mob HP", MobHp.BASE, "Base: every mob has its base HP. Per run: each run draws its HP scale from the recorded per-run table, as the real dungeon's mobs vary.");
        strategy = SimSettings.str("Run Party Strategy", StrategyChoice.AUTO, 64, "Which party strategy the bots follow (auto, neutral or a plug-in preset's id). Chosen with the Party Strategy button on the start screen.");
        if (Ec.BETA) {
            SimSettings.key("Run Start Screen Keybind", InputConstants.UNKNOWN.getValue(), "Opens the full run start screen (layout, party, lag).").onPress(UiModule::openStartScreen);
            SimSettings.key("Run Restart Room Keybind", InputConstants.UNKNOWN.getValue(), "In a full run: go back to the savepoint taken when the party entered the room you are in, as /ads run restart room does.").onPress(() -> UiServer.send("restart", "room"));
            SimSettings.key("Run Restart Keybind", InputConstants.UNKNOWN.getValue(), "In a full run: go back to the very start (the countdown), as /ads run restart does. The same dungeon, no rebuild.").onPress(() -> UiServer.send("restart"));
            SimSettings.key("Run Savepoint Keybind", InputConstants.UNKNOWN.getValue(), "In a full run: take a savepoint right now (/ads run restore <number> goes back to it).").onPress(() -> UiServer.send("savepoint"));
        }
        ready = true;
    }

    public static RunDefaults defaults() {
        if (!ready) return new RunDefaults();
        String s = strategy.get();
        return new RunDefaults(bots.get(), partySize.getInt(), LagPreset.values()[lag.index()], noDeaths.get(), MobHp.values()[mobHp.index()] == MobHp.PER_RUN, s.isBlank() ? StrategyChoice.AUTO : s);
    }

    public static void store(RunDefaults d) {
        if (!ready) return;
        bots.set(d.bots);
        partySize.set(Math.clamp(d.partySize, 1, 5));
        lag.setIndex(d.lag.ordinal());
        noDeaths.set(d.noDeaths);
        mobHp.setIndex((d.mobHpFromPack ? MobHp.PER_RUN : MobHp.BASE).ordinal());
        strategy.set(d.strategy);
    }
}
