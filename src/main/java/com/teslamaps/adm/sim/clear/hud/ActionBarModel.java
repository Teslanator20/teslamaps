// Ported from ADM's p3sim/clear/hud/ActionBarModel.kt
package com.teslamaps.adm.sim.clear.hud;

import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.RunState;
import java.util.ArrayList;
import java.util.List;

/**
 * The action bar as Hypixel lays it out: health, defense, mana (with overflow), vitality, then `N/M Secrets` in a room with secrets.
 * A segment of priority REPLACES_DEFENSE or more takes the defense slot. Pure.
 */
public final class ActionBarModel {
    private ActionBarModel() {}

    /** Priority from which a segment replaces the defense segment. */
    public static final int REPLACES_DEFENSE = 100;

    /** The overflow mana every recorded dungeon bar carries. */
    public static final int OVERFLOW_MANA_DEFAULT = 600;

    private static final String GAP = "     ";

    public static String compose(HudStats stats, PlayerRecord me, RunState state, List<BarSegment> extras, int manaNow, int overflowMana,
                                 Integer vitalityNow, boolean showSecrets) {
        // A ghost's SkyBlock health stays full.
        int hp = (me == null || me.ghost) ? stats.maxHp : (int) Math.round(stats.maxHp * Math.max(0.0, me.hpPercent) / 100.0);
        List<String> parts = new ArrayList<>();
        parts.add("§c" + HudFormat.commas(hp) + "/" + HudFormat.commas(stats.maxHp) + HudGlyphs.HEALTH);
        BarSegment replacement = null;
        for (BarSegment e : extras) if (e.priority >= REPLACES_DEFENSE) { replacement = e; break; }
        parts.add(replacement != null ? replacement.text : "§a" + HudFormat.commas(stats.defense) + HudGlyphs.DEFENSE);
        String mana = "§b" + HudFormat.commas(manaNow) + "/" + HudFormat.commas(stats.maxMana) + HudGlyphs.MANA;
        parts.add(overflowMana > 0 ? mana + " §3" + HudFormat.commas(overflowMana) + HudGlyphs.OVERFLOW_MANA : mana);
        if (stats.vitality != null) {
            int v = stats.vitality;
            parts.add("§4" + Math.clamp(vitalityNow != null ? vitalityNow : v, 0, v) + "/" + v + HudGlyphs.VITALITY);
        }
        for (BarSegment e : extras) if (e != replacement) parts.add(e.text);
        String base = String.join(GAP, parts) + GAP;
        // The secrets segment follows the baseline's own trailing gap with a gap of its own.
        String secrets = (showSecrets && me != null) ? secretsSegment(state, me) : null;
        return secrets != null ? base + GAP + secrets : base;
    }

    public static String compose(HudStats stats, PlayerRecord me, RunState state, List<BarSegment> extras, int manaNow, int overflowMana, Integer vitalityNow) {
        return compose(stats, me, state, extras, manaNow, overflowMana, vitalityNow, true);
    }

    public static String compose(HudStats stats, PlayerRecord me, RunState state, List<BarSegment> extras, int manaNow, int overflowMana) {
        return compose(stats, me, state, extras, manaNow, overflowMana, null, true);
    }

    public static String compose(HudStats stats, PlayerRecord me, RunState state, List<BarSegment> extras, int manaNow) {
        return compose(stats, me, state, extras, manaNow, OVERFLOW_MANA_DEFAULT, null, true);
    }

    public static String compose(HudStats stats, PlayerRecord me, RunState state, List<BarSegment> extras) {
        return compose(stats, me, state, extras, stats.maxMana, OVERFLOW_MANA_DEFAULT, null, true);
    }

    /** `0/5 Secrets` for the room the player stands in, or null. */
    public static String secretsSegment(RunState state, PlayerRecord me) {
        RoomRecord room = state.roomAt(me.x, me.z);
        if (room == null) return null;
        if (room.secretsMax() <= 0) return null;
        return "§7" + room.secretsFound + "/" + room.secretsMax() + " Secrets";
    }
}
