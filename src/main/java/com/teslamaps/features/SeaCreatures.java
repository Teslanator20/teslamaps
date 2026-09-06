package com.teslamaps.features;

import java.util.regex.Pattern;

// Every sea creature announces itself with its own sentence, so a catch is
// detected by the creature name instead of the full message. Player chat is
// skipped so "I caught a Loch Emperor!" in party chat doesn't count.
public final class SeaCreatures {

    private static final String[] NAMES = {
        "Squid", "Night Squid", "Sea Walker", "Sea Guardian", "Sea Witch", "Sea Archer",
        "Monster of the Deep", "Catfish", "Carrot King", "Sea Leech", "Guardian Defender",
        "Deep Sea Protector", "Water Hydra", "Sea Emperor", "Loch Emperor",
        "Nurse Shark", "Blue Shark", "Tiger Shark", "Great White Shark",
        "Frozen Steve", "Frosty", "Grinch", "Nutcracker", "Yeti", "Reindrake",
        "Nightmare", "Werewolf", "Phantom Fisher", "Grim Reaper", "Scarecrow",
        "Water Worm", "Poisoned Water Worm", "Abyssal Miner", "Zombie Miner",
        "Thunder", "Lord Jawbus", "Flyfish", "Blue Ringed Octopus", "Agarimoo",
        "Titanoboa", "Moldfin", "Fiery Scuttler", "Ragnarok", "Vanquisher", "Taurus",
        "Steaming-Hot Flounder", "Plhlegblast", "Bogged", "Wetwing", "Liltad",
        "Trash Gobbler", "Dumpster Diver", "Ent", "Haggard", "Banshee", "Brineling",
        "Sprawl", "Bayou Sludge", "Torrid", "Giant Isopod", "Alligator",
        "Atoll Croaker", "Silkbreeze", "Lotus Guardian", "Manta Ray", "Snapping Turtle",
        "Frog Man", "Frog Prince", "Tadgang", "Inkling", "Puddle Jumper", "Nessie",
        "Wiki Tiki", "Oasis Rabbit", "Oasis Sheep", "Drowned Captain", "Fried Chicken",
        "Rider of the Deep", "Jumpin' Jack", "Fireproof Witch", "Flaming Worm",
        "Pyroclastic Worm", "Lava Blaze", "Lava Flame", "Lava Leech", "Lava Pigman",
        "Magma Slug", "Magma Pillar", "Moogma", "Volcanic Snail", "Stridersurfer",
        "Fire Eel", "Mithril Grubber"
    };

    private static final Pattern MATCH = build();

    private SeaCreatures() {}

    private static Pattern build() {
        StringBuilder sb = new StringBuilder();
        for (String n : NAMES) {
            if (sb.length() > 0) sb.append('|');
            sb.append(Pattern.quote(n));
        }
        return Pattern.compile("\\b(?:" + sb + ")\\b");
    }

    public static boolean isCatch(String plain) {
        // the repo knows the exact sentence per creature, the name list below is the fallback
        if (com.teslamaps.utils.SeaCreatureList.nameFor(plain) != null) return true;
        if (plain.contains(">") || plain.contains(": ")) return false;
        return MATCH.matcher(plain).find();
    }
}
