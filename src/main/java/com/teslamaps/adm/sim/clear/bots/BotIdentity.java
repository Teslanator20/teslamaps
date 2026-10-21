// Ported from ADM's p3sim/clear/bots/BotIdentity.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.run.PlayerRecord;
import java.util.UUID;

/** Who a teammate bot is, as a player on Hypixel is: a name, a rank, a SkyBlock level, a class level, an optional glyph, a version 4 profile id. */
public class BotIdentity {
    public final String name;
    public final BotRank rank;
    public final int sbLevel;
    public final int classLevel;
    /** The glyph after the name with its colour codes, or null for none. */
    public final String glyph;
    public final UUID uuid;

    public BotIdentity(String name, BotRank rank, int sbLevel, int classLevel, String glyph, UUID uuid) {
        this.name = name;
        this.rank = rank;
        this.sbLevel = sbLevel;
        this.classLevel = classLevel;
        this.glyph = glyph;
        this.uuid = uuid;
    }

    /** The colour code of the level in square brackets. */
    public String levelColour() { return BotIdentities.levelColour(sbLevel); }

    /** What floats over the head. */
    public String nametag() { return look().tag(); }

    /** What a client is shown of the bot: the name, the profile id, and the text before and after the name. */
    public BotLook look() {
        return new BotLook(name, uuid, "§8[" + levelColour() + sbLevel + "§8] " + rank.nameColour, glyph != null ? " " + glyph : "", rank.nameColour);
    }

    /** Writes the facts the other modules read into the bot's player record. */
    public void applyTo(PlayerRecord rec) {
        rec.extras.put("sbLevel", sbLevel);
        rec.extras.put("classLevel", classLevel);
        rec.extras.put("rank", rank.name());
        rec.extras.put("rankBracket", rank.bracket);
        rec.extras.put("nameColour", rank.nameColour);
        rec.extras.put("glyph", glyph != null ? glyph : "");
    }
}
