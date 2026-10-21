// Ported from ADM's p3sim/clear/bots/BotIdentity.kt
package com.teslamaps.adm.sim.clear.bots;

import java.util.UUID;

/** What a teammate bot looks like to a client: its name and profile id, and the text round the name over its head (team prefix and suffix). */
public class BotLook {
    public final String name;
    public final UUID profileId;
    public final String prefix;
    public final String suffix;
    public final String nameColour;

    public BotLook(String name, UUID profileId, String prefix, String suffix, String nameColour) {
        this.name = name;
        this.profileId = profileId;
        this.prefix = prefix;
        this.suffix = suffix;
        this.nameColour = nameColour;
    }

    public BotLook(String name, UUID profileId, String prefix, String suffix) { this(name, profileId, prefix, suffix, "§b"); }

    /** The whole text as it reads over the head. */
    public String tag() { return prefix + name + suffix; }
}
