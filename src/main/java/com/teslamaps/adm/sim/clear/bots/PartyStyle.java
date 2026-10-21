// Ported from ADM's p3sim/clear/bots/BotIdentity.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.run.PlayerRecord;

/** The tab, chat and nametag style of a party member, read from the player record. */
public final class PartyStyle {
    private PartyStyle() {}

    public static final String DEFAULT_COLOUR = "§b";
    public static final String DEFAULT_BRACKET = "§b[MVP§c+§b]";

    /** The colour code of the member's name. */
    public static String nameColour(PlayerRecord rec) {
        Object v = rec != null ? rec.extras.get("nameColour") : null;
        return v instanceof String s ? s : DEFAULT_COLOUR;
    }

    /** The rank in front of the name in chat. */
    public static String bracket(PlayerRecord rec) {
        Object v = rec != null ? rec.extras.get("rankBracket") : null;
        return v instanceof String s ? s : DEFAULT_BRACKET;
    }

    /** The glyph after the name with its codes, "" for none; null when the record has no identity. */
    public static String glyph(PlayerRecord rec) {
        Object v = rec != null ? rec.extras.get("glyph") : null;
        return v instanceof String s ? s : null;
    }
}
