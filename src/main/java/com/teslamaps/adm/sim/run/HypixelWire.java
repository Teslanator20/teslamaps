// Ported from ADM's p3sim/run/HypixelWire.kt
package com.teslamaps.adm.sim.run;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class HypixelWire {
    private HypixelWire() {}

    public static final String LOCATION_KIND = "ads.engine.hypixel";

    public static final String PARTY_REQUEST_KIND = "ads.engine.partyreq";

    public static final String PARTY_KIND = "ads.engine.party";

    public static final String BRAND = "Hypixel BungeeCord (2026.10.9.1) <- Hygot 2026.10.15.1";

    public static final class Member {
        public final UUID uuid;
        public final boolean leader;

        public Member(UUID uuid, boolean leader) { this.uuid = uuid; this.leader = leader; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Member m)) return false;
            return leader == m.leader && Objects.equals(uuid, m.uuid);
        }

        @Override
        public int hashCode() { return Objects.hash(uuid, leader); }

        @Override
        public String toString() { return "Member(uuid=" + uuid + ", leader=" + leader + ")"; }
    }

    public static String serverName(KRandom random) {
        int digits = 1 + random.nextInt(3);
        int letters = 1 + random.nextInt(2);
        StringBuilder sb = new StringBuilder("mini");
        for (int i = 0; i < digits; i++) sb.append((char) ('0' + random.nextInt(10)));
        for (int i = 0; i < letters; i++) sb.append((char) ('A' + random.nextInt(26)));
        return sb.toString();
    }

    public static int partyDelayTicks(KRandom random) {
        int n = random.nextInt(56);
        int[][] table = {{0, 37}, {1, 10}, {2, 7}, {3, 2}};
        for (int[] dw : table) { if (n < dw[1]) return dw[0]; n -= dw[1]; }
        return 0;
    }

    public static List<Member> partyMembers(List<UUID> humans, List<UUID> bots) {
        LinkedHashSet<UUID> all = new LinkedHashSet<>(humans);
        all.addAll(bots);
        List<Member> out = new ArrayList<>();
        int i = 0;
        for (UUID id : all) { out.add(new Member(id, i == 0)); i++; }
        return out;
    }

    public static String encodeLocation(String serverName) {
        JsonObject o = new JsonObject();
        o.addProperty("server", serverName);
        return o.toString();
    }

    public static String decodeLocation(String text) {
        try {
            JsonElement e = JsonParser.parseString(text).getAsJsonObject().get("server");
            if (e == null || !e.isJsonPrimitive()) return null;
            String s = e.getAsString();
            return s == null || s.isBlank() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    public static String encodeParty(List<Member> members) {
        JsonArray arr = new JsonArray();
        for (Member m : members) {
            JsonObject o = new JsonObject();
            o.addProperty("id", m.uuid.toString());
            o.addProperty("leader", m.leader);
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("members", arr);
        return root.toString();
    }

    public static List<Member> decodeParty(String text) {
        try {
            List<Member> out = new ArrayList<>();
            for (JsonElement e : JsonParser.parseString(text).getAsJsonObject().getAsJsonArray("members")) {
                JsonObject o = e.getAsJsonObject();
                UUID id;
                try {
                    id = UUID.fromString(o.get("id").getAsString());
                } catch (Throwable t) {
                    id = null;
                }
                if (id == null) continue;
                JsonElement l = o.get("leader");
                out.add(new Member(id, l != null && l.isJsonPrimitive() ? l.getAsBoolean() : false));
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
