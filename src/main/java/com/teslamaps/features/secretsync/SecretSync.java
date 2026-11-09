package com.teslamaps.features.secretsync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.DungeonScore;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.HypixelModApi;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shares per-room secret counts and mimic/prince/bat kills with everyone in the same dungeon
 * instance, keyed by the Hypixel server id like Skyblocker's secret sync. Runs on
 * supabase realtime broadcast.
 */
public class SecretSync {
    private static final Pattern SERVER = Pattern.compile("\\b(m\\d+[A-Za-z]+|mini\\d+[A-Za-z]*|mega\\d+[A-Za-z]*)\\b");
    private static final long HEARTBEAT_MS = 25_000;

    private static final SocketClient realtime = new SocketClient("SecretSync");
    private static String joined;
    private static long lastHeartbeat;
    private static int ref;
    private static int tickCount;

    private static final Map<String, Integer> known = new HashMap<>();
    private static boolean mimicSeen, princeSeen, batSeen;
    private static boolean mimicRemote, princeRemote, batRemote;

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    public static void register() {
        Feature.of("SecretSync").section("Secrets & Waypoints")
                .when(() -> cfg().secretSync)
                .tick(SecretSync::tick)
                .config("Secrets & Waypoints", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Secret Sync");
                    b.toggle("Secret Sync", () -> c.secretSync, v -> c.secretSync = v)
                            .desc("Shares room secret counts and mimic/prince/bat kills with TeslaMaps users in the same run")
                            .tags("secret", "sync", "share", "party");
                });
    }

    private static void tick() {
        if (!DungeonManager.isInDungeon()) {
            if (joined != null || !known.isEmpty()) leave();
            return;
        }
        if (++tickCount % 10 != 0) return;
        String server = serverId();
        if (server == null) return;

        joinRealtime(server);
        publishChanges();
    }

    private static void leave() {
        if (joined != null) realtime.close();
        joined = null;
        known.clear();
        mimicSeen = princeSeen = batSeen = false;
        mimicRemote = princeRemote = batRemote = false;
    }

    /** Hypixel server name ("mini57N"), as Skyblocker keys its channels. */
    static String serverId() {
        String api = HypixelModApi.serverName();
        if (api != null && !api.isEmpty()) return api;
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            Matcher m = SERVER.matcher(StringUtil.strip(line));
            if (!m.find()) continue;
            String id = m.group(1);
            if (id.startsWith("m") && !id.startsWith("mini") && !id.startsWith("mega")) id = "mini" + id.substring(1);
            return id;
        }
        return null;
    }

    // ---- supabase realtime (phoenix protocol) ----

    private static void joinRealtime(String server) {
        if (!realtime.open()) {
            if (realtime.connecting()) return;
            joined = null;
            String url = cfg().capeServerUrl.trim().replaceFirst("^http", "ws")
                    + "/realtime/v1/websocket?apikey=" + cfg().capeServerKey.trim() + "&vsn=1.0.0";
            realtime.connect(url, Map.of(), SecretSync::onRealtime, () -> {});
            return;
        }
        if (!server.equals(joined)) {
            if (joined != null) realtime.send(frame(topic(joined), "phx_leave", new JsonObject()));
            JsonObject config = new JsonObject();
            JsonObject broadcast = new JsonObject();
            broadcast.addProperty("self", false);
            config.add("broadcast", broadcast);
            config.addProperty("private", false);
            JsonObject payload = new JsonObject();
            payload.add("config", config);
            realtime.send(frame(topic(server), "phx_join", payload));
            joined = server;
            lastHeartbeat = System.currentTimeMillis();
            TeslaMaps.debug("[SecretSync] joined {}", server);
        }
        if (System.currentTimeMillis() - lastHeartbeat > HEARTBEAT_MS) {
            lastHeartbeat = System.currentTimeMillis();
            realtime.send(frame("phoenix", "heartbeat", new JsonObject()));
        }
    }

    private static String topic(String server) {
        return "realtime:tmdungeon:" + server;
    }

    private static String frame(String topic, String event, JsonObject payload) {
        JsonObject o = new JsonObject();
        o.addProperty("topic", topic);
        o.addProperty("event", event);
        o.add("payload", payload);
        o.addProperty("ref", String.valueOf(++ref));
        return o.toString();
    }

    private static void publish(JsonObject msg) {
        if (joined == null || !realtime.open()) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "broadcast");
        payload.addProperty("event", "secret");
        payload.add("payload", msg);
        realtime.send(frame(topic(joined), "broadcast", payload));
        TeslaMaps.debug("[SecretSync] sent {}", msg);
    }

    private static void onRealtime(String text) {
        JsonObject o = JsonParser.parseString(text).getAsJsonObject();
        if (!"broadcast".equals(str(o, "event"))) return;
        JsonObject outer = o.getAsJsonObject("payload");
        if (outer == null || !outer.has("payload")) return;
        JsonObject msg = outer.getAsJsonObject("payload");
        Minecraft.getInstance().execute(() -> handle(msg, "tm"));
    }

    // ---- local changes ----

    private static void publishChanges() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String self = mc.player.getUUID().toString();
        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            String name = room.getName();
            if (name == null || "Unknown".equals(name) || room.getSecrets() <= 0) continue;
            int found = room.getFoundSecrets();
            if (found <= 0 || found <= known.getOrDefault(name, 0)) continue;
            known.put(name, found);
            JsonObject msg = base("room_secret_count", self);
            msg.addProperty("room", name);
            msg.addProperty("secretCount", found);
            publish(msg);
        }
        if (DungeonScore.isMimicKilled() && !mimicSeen) {
            mimicSeen = true;
            if (!mimicRemote) publish(base("mimic_killed", self));
        }
        if (DungeonScore.isPrinceKilled() && !princeSeen) {
            princeSeen = true;
            if (!princeRemote) publish(base("prince_killed", self));
        }
        if (DungeonScore.isBatKilled() && !batSeen) {
            batSeen = true;
            if (!batRemote) publish(base("bat_killed", self));
        }
    }

    private static JsonObject base(String type, String uuid) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        o.addProperty("uuid", uuid);
        return o;
    }

    // ---- incoming, same format from both sources ----

    static void handle(JsonObject msg, String source) {
        if (!DungeonManager.isInDungeon()) return;
        String type = str(msg, "type");
        if (type == null || !fromRunMember(str(msg, "uuid"))) return;
        TeslaMaps.debug("[SecretSync] {} <- {}", source, msg);
        switch (type) {
            case "room_secret_count" -> {
                DungeonRoom room = roomByName(str(msg, "room"));
                if (room == null || !msg.has("secretCount")) return;
                int count = msg.get("secretCount").getAsInt();
                if (count <= room.getFoundSecrets() || count > room.getSecrets()) return;
                room.setFoundSecrets(count);
                known.merge(room.getName(), count, Math::max);
            }
            case "mimic_killed" -> {
                if (DungeonScore.isMimicKilled()) return;
                mimicRemote = true;
                DungeonScore.onMimicKilled();
            }
            case "prince_killed" -> {
                if (DungeonScore.isPrinceKilled()) return;
                princeRemote = true;
                DungeonScore.setPrinceKilledFromSync();
            }
            case "bat_killed" -> {
                if (DungeonScore.isBatKilled()) return;
                batRemote = true;
                DungeonScore.setBatKilledFromSync();
            }
            default -> {
            }
        }
    }

    /** Only players on this server count; a dungeon instance holds just the run. */
    private static boolean fromRunMember(String uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (uuid == null || mc.player == null || mc.getConnection() == null) return false;
        try {
            UUID id = UUID.fromString(uuid);
            return !id.equals(mc.player.getUUID()) && mc.getConnection().getPlayerInfo(id) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** "Arrow Trap", "arrow-trap-1" and "arrow_trap" all match. */
    private static DungeonRoom roomByName(String name) {
        if (name == null) return null;
        String want = norm(name);
        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            if (room.getName() != null && norm(room.getName()).equals(want)) return room;
        }
        TeslaMaps.debug("[SecretSync] no room for '{}'", name);
        return null;
    }

    private static String norm(String name) {
        return name.toLowerCase().replaceFirst("-\\d+$", "").replaceAll("[^a-z0-9]", "");
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }
}
