// Ported from SkyHanni's HotfApi.kt (LGPL-2.1), see NOTICE.md
package com.teslamaps.features.foraging;

import java.util.List;
import java.util.regex.Pattern;

/** The rotating Lottery and Beekeeper effects of the Heart of the Forest. */
public class HotfPerks {

    public record Perk(Pattern pattern, String display) {}

    // the stat icons are private-use glyphs of the Hypixel font, matched as a wildcard
    public static final List<Perk> LOTTERY = List.of(
            new Perk(Pattern.compile("Gain \\+10\\s*\\S?\\s*Sweep\\."), "§2+10 Sweep"),
            new Perk(Pattern.compile("Gain \\+50\\s*\\S?\\s*Mangrove Fortune\\."), "§6+50 Mangrove Fortune"),
            new Perk(Pattern.compile("Gain \\+50\\s*\\S?\\s*Fig Fortune\\."), "§6+50 Fig Fortune"),
            new Perk(Pattern.compile("Gain \\+50\\s*\\S?\\s*Helix Fortune\\."), "§6+50 Helix Fortune"));

    public static final List<Perk> BEEKEEPER = List.of(
            new Perk(Pattern.compile("Honeyhives refill 20% faster\\."), "§6Honeyhives §7refill §a20% §7faster"),
            new Perk(Pattern.compile("Trees lathered with Honeycomb attract\\s*\\S?\\s*Critters 20% faster\\."),
                    "§a20% §7faster §aCritters §7from §6Honeycomb"),
            new Perk(Pattern.compile("Gain 2x Honeycomb from Honeyhives and Trees lathered with Honeycomb\\."),
                    "§a2x §6Honeycomb"),
            new Perk(Pattern.compile("Trees lathered with Honeycomb have a 25% chance to attract a second\\s*\\S?\\s*Critter\\."),
                    "§a25% §7chance for a second §aCritter"));

    /** "New buff: <effect>" and the "■ <effect>" list under it. */
    public static final Pattern ROTATING_LINE = Pattern.compile(" ?(?:New buff: |■ )(?<perk>.*)");

    public static String matchLottery(String effect) {
        return match(LOTTERY, effect);
    }

    public static String matchBeekeeper(String effect) {
        return match(BEEKEEPER, effect);
    }

    private static String match(List<Perk> perks, String effect) {
        for (Perk p : perks) {
            if (p.pattern().matcher(effect).matches()) return p.display();
        }
        return null;
    }
}
