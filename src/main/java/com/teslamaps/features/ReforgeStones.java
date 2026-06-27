/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import java.util.Map;

/**
 * Maps an item's reforge modifier (NBT "modifier") to the reforge-stone item id
 * used to look up its market price. Unknown reforges simply resolve to null, in
 * which case the breakdown shows no value (intrinsic reforges have no stone).
 */
public class ReforgeStones {

    private static final Map<String, String> STONE = Map.ofEntries(
        Map.entry("gilded", "MIDAS_JEWEL"),
        Map.entry("fabled", "DRAGON_CLAW"),
        Map.entry("renowned", "DRAGON_HORN"),
        Map.entry("spiked", "DRAGON_SCALE"),
        Map.entry("bountiful", "GOLDEN_BALL"),
        Map.entry("fruitful", "ONYX"),
        Map.entry("blessed", "BLESSED_FRUIT"),
        Map.entry("ancient", "PRECURSOR_GEAR"),
        Map.entry("necrotic", "NECROMANCER_BROOCH"),
        Map.entry("fortified", "DIAMONITE"),
        Map.entry("mithraic", "PURE_MITHRIL"),
        Map.entry("jaded", "JADERALD"),
        Map.entry("loving", "RED_SCARF"),
        Map.entry("reinforced", "RARE_DIAMOND"),
        Map.entry("submerged", "DEEP_SEA_ORB"),
        Map.entry("perfect", "DIAMOND_ATOM"),
        Map.entry("moil", "MOIL_LOG"),
        Map.entry("toil", "TOIL_LOG"),
        Map.entry("blooming", "FLOWERING_BOUQUET"),
        Map.entry("rooted", "BURROWING_SPORES"),
        Map.entry("stellar", "PETRIFIED_STARFALL"),
        Map.entry("aote_stone", "WARPED_STONE")
    );

    public static String stoneId(String modifier) {
        if (modifier == null) return null;
        return STONE.get(modifier.toLowerCase());
    }
}
