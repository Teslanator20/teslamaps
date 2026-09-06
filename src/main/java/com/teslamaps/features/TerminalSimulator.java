package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.termsim.MelodySim;
import com.teslamaps.dungeon.termsim.NumbersSim;
import com.teslamaps.dungeon.termsim.PanesSim;
import com.teslamaps.dungeon.termsim.RubixSim;
import com.teslamaps.dungeon.termsim.SelectAllSim;
import com.teslamaps.dungeon.termsim.StartGUI;
import com.teslamaps.dungeon.termsim.StartsWithSim;
import com.teslamaps.dungeon.terminals.TerminalTypes;

import java.util.List;
import java.util.Map;

/** Simulates a floor 7 phase 3 terminal. */
public class TerminalSimulator {

    public static int ping() {
        return TeslaMapsConfig.get().termSimPing;
    }

    public static boolean disableFirstClickProtection() {
        return TeslaMapsConfig.get().termSimDisableFirstClickProt;
    }

    public static Map<String, Float> termSimPBs() {
        return TeslaMapsConfig.get().termSimPbs;
    }

    public static void resetPBs() {
        termSimPBs().clear();
        TeslaMapsConfig.save();
    }

    public static void open() {
        open(ping());
    }

    public static void open(long ping) {
        if (net.minecraft.client.Minecraft.getInstance().player == null) return;
        StartGUI.get().open(ping);
    }

    public static void openRandomTerminal(long ping) {
        List<TerminalTypes> pool = List.of(TerminalTypes.PANES, TerminalTypes.RUBIX, TerminalTypes.NUMBERS,
                TerminalTypes.STARTS_WITH, TerminalTypes.SELECT);
        switch (pool.get((int) (Math.random() * pool.size()))) {
            case STARTS_WITH -> new StartsWithSim().open(ping);
            case PANES -> PanesSim.get().open(ping);
            case SELECT -> new SelectAllSim().open(ping);
            case NUMBERS -> NumbersSim.get().open(ping);
            case MELODY -> MelodySim.get().open(ping);
            case RUBIX -> RubixSim.get().open(ping);
        }
    }
}
