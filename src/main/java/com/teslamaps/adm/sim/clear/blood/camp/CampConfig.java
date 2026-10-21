// Ported from ADM's p3sim/clear/blood/camp/CampEngine.kt
package com.teslamaps.adm.sim.clear.blood.camp;

import java.util.List;

/** Switches for how much of the chat queue is simulated and which optional legs happen. */
public class CampConfig {
    public final boolean chatCoins;
    public final int killsToDefeat;
    public final int laterThrows;
    public final double wsChance;
    public final double enoughChance;
    /** A recorded camp's lines (null: the camp asks for its own). */
    public final List<CampMech.Request> recordedLines;

    public CampConfig(boolean chatCoins, int killsToDefeat, int laterThrows, double wsChance, double enoughChance, List<CampMech.Request> recordedLines) {
        this.chatCoins = chatCoins;
        this.killsToDefeat = killsToDefeat;
        this.laterThrows = laterThrows;
        this.wsChance = wsChance;
        this.enoughChance = enoughChance;
        this.recordedLines = recordedLines;
    }

    public CampConfig(boolean chatCoins, int killsToDefeat, int laterThrows, double wsChance, double enoughChance) {
        this(chatCoins, killsToDefeat, laterThrows, wsChance, enoughChance, null);
    }

    public CampConfig(boolean chatCoins, int killsToDefeat, int laterThrows, double wsChance) {
        this(chatCoins, killsToDefeat, laterThrows, wsChance, 0.07, null);
    }

    public CampConfig(boolean chatCoins, int killsToDefeat, int laterThrows) {
        this(chatCoins, killsToDefeat, laterThrows, WallPlan.WS_THROW_CHANCE, 0.07, null);
    }

    public CampConfig(boolean chatCoins, int killsToDefeat) {
        this(chatCoins, killsToDefeat, CampMech.LATER_THROWS, WallPlan.WS_THROW_CHANCE, 0.07, null);
    }

    public CampConfig(boolean chatCoins) {
        this(chatCoins, CampMech.KILLS_TO_DEFEAT, CampMech.LATER_THROWS, WallPlan.WS_THROW_CHANCE, 0.07, null);
    }

    public CampConfig() {
        this(true, CampMech.KILLS_TO_DEFEAT, CampMech.LATER_THROWS, WallPlan.WS_THROW_CHANCE, 0.07, null);
    }
}
