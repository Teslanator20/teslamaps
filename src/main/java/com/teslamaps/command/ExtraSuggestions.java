package com.teslamaps.command;

// Ported from Skyblocker's JoinInstance/Viewstash/AuctionBazaar
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lists for /joininstance, /joindungeon, /joinkuudra, /viewstash, /ahs, /bz and /setmaxspeed. */
public final class ExtraSuggestions {

    private static final String[] NUMBERS = {"ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN"};

    public static final List<String> VIEWSTASH = List.of("material", "item");
    public static final List<String> MAX_SPEED = List.of("100", "150", "200", "250", "300", "350", "400", "450", "500");

    private static final Map<String, String> INSTANCES = instanceList();

    private ExtraSuggestions() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ExtraSuggestions").section("Chat").when(() -> cfg().commandSuggestionsExtra);
    }

    public static boolean enabled() {
        return HypixelSuggestions.enabled() && cfg().commandSuggestionsExtra;
    }

    private static Map<String, String> instanceList() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("CATACOMBS_ENTRANCE", "Catacombs");
        for (String n : NUMBERS) m.put("CATACOMBS_FLOOR_" + n, "Catacombs");
        for (String n : NUMBERS) m.put("MASTER_CATACOMBS_FLOOR_" + n, "Catacombs");
        for (String k : new String[]{"NORMAL", "HOT", "BURNING", "FIERY", "INFERNAL"}) m.put("KUUDRA_" + k, "Kuudra");
        return m;
    }

    /** type = null (all), "Catacombs" or "Kuudra". */
    public static List<String> instances(String type) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : INSTANCES.entrySet()) {
            if (type == null || type.equalsIgnoreCase(e.getValue())) out.add(e.getKey());
        }
        return out;
    }
}
