// Ported from ADM's p3sim/clear/mobs/SignedSkins.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.util.Pair;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SignedSkins {
    private SignedSkins() {}

    private static volatile Map<String, Pair<String, String>> table;

    private static Map<String, Pair<String, String>> table() {
        Map<String, Pair<String, String>> t = table;
        if (t != null) return t;
        synchronized (SignedSkins.class) {
            if (table == null) table = load();
            return table;
        }
    }

    private static Map<String, Pair<String, String>> load() {
        try {
            String text;
            try (InputStream in = SignedSkins.class.getResourceAsStream("/assets/teslamaps/adm/ads/skins.json")) {
                if (in == null) return Collections.emptyMap();
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            Map<String, Pair<String, String>> out = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> en : JsonParser.parseString(text).getAsJsonObject().entrySet()) {
                JsonElement v = en.getValue();
                out.put(en.getKey(), Pair.of(v.getAsJsonArray().get(0).getAsString(), v.getAsJsonArray().get(1).getAsString()));
            }
            return out;
        } catch (Throwable t) {
            Ec.LOG.warn("[ads] signed skins: " + t.getMessage());
            return Collections.emptyMap();
        }
    }

    static String trimLeadingZeros(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == '0') i++;
        return s.substring(i);
    }

    public static Pair<String, String> of(String hash) {
        String k = trimLeadingZeros(hash);
        Map<String, Pair<String, String>> t = table();
        Pair<String, String> hit = t.get(k);
        if (hit != null) return hit;
        if (k.length() < 60) {
            for (Map.Entry<String, Pair<String, String>> en : t.entrySet()) if (en.getKey().startsWith(k)) return en.getValue();
            return null;
        }
        return null;
    }
}
