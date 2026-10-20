package com.teslamaps.adm.map.clear;

// Ported from ADM's clearbreakdown/ClearBreakdown.kt, RunGate.kt, BreakdownText.kt
// (local only: no ADR Online relay, no Odin map sync socket; lifetime secrets through our HypixelApi)
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.TeslaMaps;
import com.teslamaps.adm.Adm;
import com.teslamaps.adm.map.AdmMapScan;
import com.teslamaps.adm.map.AdmMapScan.Check;
import com.teslamaps.adm.map.AdmMapScan.RType;
import com.teslamaps.adm.map.AdmMapScan.Room;
import com.teslamaps.adm.map.clear.ClearCredit.Clear;
import com.teslamaps.adm.map.clear.ClearCredit.Mark;
import com.teslamaps.adm.map.clear.SecretCredit.PlayerSecrets;
import com.teslamaps.adm.map.clear.SecretCredit.RoomSecrets;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.player.Player;

public final class ClearBreakdown {
    private static final String PREFIX = "§8[§6ADM§8] ";
    private static final String MORT = "[NPC] Mort: Here, I found this map when I first entered the dungeon.";
    private static final long FIRST_POLL_MS = 15_000L, POLL_MS = 30_000L, LAST_ASK_MS = 800L;

    static ClearCredit clears = new ClearCredit();
    static SecretCredit secrets = new SecretCredit();
    static boolean active;
    private static final RunGate gate = new RunGate();
    static final Map<String, Character> roster = new LinkedHashMap<>();
    private static ConcurrentHashMap<String, Long> before = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, Long> latest = new ConcurrentHashMap<>();
    private static long nextPollMs;
    private static final Map<Room, Check> marks = new IdentityHashMap<>();
    private static final Map<Room, Integer> founds = new IdentityHashMap<>();
    private static Object lastLevel;

    private ClearBreakdown() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static boolean enabled() {
        return Adm.overrides(cfg().admClearBreakdown);
    }

    public static void register() {
        Feature.of("AdmClearBreakdown").section(Adm.SECTION).when(() -> cfg().admClearBreakdown)
                .tickAlways(ClearBreakdown::tick)
                .serverChat(ClearBreakdown::onChat)
                .config("ADM Map", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Clear Breakdown", () -> c.admClearBreakdown, v -> c.admClearBreakdown = v)
                            .desc("At the end of a finished run, everyone's rooms (cleared alone - cleared) and secrets; hover for the rooms in order and the secrets room by room. Replaces the Team Stats lines while on.");
                });
    }

    static String me() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getUser().getName();
    }

    static Player entityOf(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (mc.player != null && name.equals(me())) return mc.player;
        PlayerTracker.DungeonPlayer p = PlayerTracker.getPlayer(name).orElse(null);
        UUID uuid = p != null ? p.getUuid() : null;
        return uuid != null ? mc.level.getPlayerByUUID(uuid) : null;
    }

    private static void reset() {
        clears = new ClearCredit();
        secrets = new SecretCredit();
        active = false;
        gate.reset();
        roster.clear();
        before = new ConcurrentHashMap<>();
        latest = new ConcurrentHashMap<>();
        nextPollMs = 0L;
        marks.clear();
        founds.clear();
        Sightings.reset();
    }

    private static void onChat(String raw) {
        if (!DungeonManager.isInDungeon()) return;
        String text = StringUtil.strip(raw);
        if (text.equals(MORT)) start();
        else if (active && gate.line(text)) finish();
    }

    private static void start() {
        reset();
        active = true;
        for (PlayerTracker.DungeonPlayer p : PlayerTracker.getPlayers()) join(p);
    }

    private static char colorCode(String cls) {
        String k = cls == null ? "" : cls.toLowerCase();
        return switch (k) {
            case "archer" -> '6';
            case "berserk" -> '4';
            case "healer" -> 'd';
            case "mage" -> 'b';
            case "tank" -> '2';
            default -> 'f';
        };
    }

    private static void join(PlayerTracker.DungeonPlayer p) {
        String name = p.getName();
        if (roster.containsKey(name)) return;
        roster.put(name, colorCode(p.getDungeonClass()));
        ConcurrentHashMap<String, Long> into = before;
        pullSecrets(name).thenAccept(v -> { if (v >= 0) into.put(name, v); });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        if (!active || !DungeonManager.isInDungeon()) return;
        for (PlayerTracker.DungeonPlayer p : PlayerTracker.getPlayers()) join(p);
        roomUpdates();
        if (DungeonManager.isInBossArea()) {
            pollTotals();
            return;
        }
        Sightings.tick();
        Map<String, String> where = presence();
        clears.tick(where);
        secrets.tick(where);
    }

    /** Odin's CheckmarkUpdateEvent / SecretsUpdateEvent, read off the ADM map's rooms. */
    private static void roomUpdates() {
        for (Room room : AdmMapScan.rooms) {
            Check prev = marks.put(room, room.checkmark);
            if (prev != null && prev != room.checkmark) {
                Mark mark = switch (room.checkmark) {
                    case WHITE -> Mark.WHITE;
                    case GREEN -> Mark.GREEN;
                    case RED -> Mark.RED;
                    default -> Mark.OTHER;
                };
                if (room.type != RType.FAIRY && room.type != RType.ENTRANCE) clears.onCheckmark(room.key(), room.displayName(), mark);
                if (mark == Mark.GREEN && room.maxSecrets > 0) secrets.observe(room.key(), room.displayName(), room.maxSecrets);
            }
            int found = room.foundSecrets;
            Integer was = founds.put(room, found);
            if (found > 0 && (was == null || found > was)) secrets.observe(room.key(), room.displayName(), found);
        }
    }

    private static Map<String, String> presence() {
        Map<String, String> out = new HashMap<>();
        for (String name : roster.keySet()) {
            PlayerTracker.DungeonPlayer p = PlayerTracker.getPlayer(name).orElse(null);
            out.put(name, p == null || !p.isAlive() ? null : roomOf(name));
        }
        return out;
    }

    private static String roomOf(String name) {
        Player e = entityOf(name);
        if (e != null) {
            Room r = AdmMapScan.roomAt(e.getX(), e.getZ());
            return r != null ? r.key() : null;
        }
        int idx = 0;
        String self = me();
        for (PlayerTracker.DungeonPlayer p : PlayerTracker.getPlayers()) {
            if (p.getName().equals(self) || !p.isAlive()) continue;
            if (p.getName().equals(name)) break;
            idx++;
        }
        if (idx >= AdmMapScan.mapPlayers.size()) return null;
        int[] pos = AdmMapScan.mapPlayers.get(idx);
        double x = ((pos[0] + 128) / 2.0 - AdmMapScan.startX) * 32.0 / AdmMapScan.roomGap() - 200;
        double z = ((pos[1] + 128) / 2.0 - AdmMapScan.startY) * 32.0 / AdmMapScan.roomGap() - 200;
        Room r = AdmMapScan.roomAt(x, z);
        return r != null ? r.key() : null;
    }

    private static void pollTotals() {
        long now = System.currentTimeMillis();
        if (nextPollMs == 0L) nextPollMs = now + FIRST_POLL_MS;
        if (now < nextPollMs) return;
        nextPollMs = now + POLL_MS;
        ConcurrentHashMap<String, Long> into = latest;
        for (String name : roster.keySet()) pullSecrets(name).thenAccept(v -> { if (v >= 0) into.put(name, v); });
    }

    private static void finish() {
        active = false;
        Map<String, Character> names = new LinkedHashMap<>(roster);
        ConcurrentHashMap<String, Long> start = before, now = latest;
        ClearCredit runClears = clears;
        SecretCredit runSecrets = secrets;
        Runnable postNow = () -> {
            Map<String, Integer> totals = new LinkedHashMap<>();
            for (String n : names.keySet()) {
                Long after = now.get(n), was = start.get(n);
                totals.put(n, after != null && was != null ? (int) Math.max(0L, after - was) : null);
            }
            post(names, runClears.clears(), runSecrets.resolve(totals));
        };
        List<String> missing = new ArrayList<>();
        for (String n : names.keySet()) if (!now.containsKey(n) && start.containsKey(n)) missing.add(n);
        if (missing.isEmpty()) {
            postNow.run();
            return;
        }
        List<CompletableFuture<Void>> asks = new ArrayList<>();
        for (String n : missing) asks.add(pullSecrets(n).thenAccept(v -> { if (v >= 0) now.put(n, v); }));
        CompletableFuture.allOf(asks.toArray(new CompletableFuture[0]))
                .completeOnTimeout(null, LAST_ASK_MS, TimeUnit.MILLISECONDS)
                .whenComplete((v, e) -> Minecraft.getInstance().execute(() -> {
                    try {
                        postNow.run();
                    } catch (Exception ex) {
                        TeslaMaps.LOGGER.warn("[adm] clear breakdown post failed", ex);
                    }
                }));
    }

    private static void msg(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(c);
    }

    private static void post(Map<String, Character> names, Map<String, List<Clear>> rooms, Map<String, PlayerSecrets> found) {
        msg(Component.literal(PREFIX + "§6Clear Breakdown"));
        for (Map.Entry<String, Character> e : names.entrySet()) {
            String name = e.getKey();
            String[] p = line(name, e.getValue(), rooms.getOrDefault(name, List.of()), found.get(name));
            msg(Component.literal(PREFIX + p[0]).append(hover(p[1], p[2])).append(Component.literal(" ")).append(hover(p[3], p[4])));
        }
    }

    private static Component hover(String text, String hover) {
        return Component.literal(text).withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    /** BreakdownText.line: name, rooms, rooms hover, secrets, secrets hover. */
    private static String[] line(String name, char colour, List<Clear> clears, PlayerSecrets secrets) {
        int solo = 0;
        for (Clear c : clears) if (c.solo()) solo++;
        String rooms = "§f" + solo + "§7-§f" + clears.size();
        StringBuilder rh = new StringBuilder();
        if (clears.isEmpty()) rh.append("§7No rooms cleared");
        else for (int i = 0; i < clears.size(); i++) {
            if (i > 0) rh.append('\n');
            Clear c = clears.get(i);
            rh.append("§7").append(i + 1).append(". §f").append(c.room()).append(c.solo() ? " §a(solo)" : "");
        }
        Integer total = secrets != null ? secrets.total() : null;
        List<String> seen = new ArrayList<>();
        if (secrets != null) for (RoomSecrets r : secrets.rooms()) seen.add("§f" + (r.estimate() ? "~" : "") + r.count() + " §7" + r.room());
        String sh;
        if (total == null) {
            List<String> l = new ArrayList<>();
            l.add("§7Hypixel API didn't answer; seen in rooms:");
            l.addAll(seen);
            sh = String.join("\n", l);
        } else if (seen.isEmpty() && secrets.unknown() == 0) sh = "§7No secrets";
        else {
            List<String> l = new ArrayList<>(seen);
            if (secrets.unknown() > 0) l.add("§f" + secrets.unknown() + " §7in unknown rooms");
            sh = String.join("\n", l);
        }
        return new String[]{"§" + colour + name + "§7: ", rooms, rh.toString(), "§f" + (total != null ? total : "?") + " §7secrets", sh};
    }

    /** Lifetime secrets of a player (current profile), -1 when the API doesn't answer. */
    private static CompletableFuture<Long> pullSecrets(String name) {
        return HypixelApi.nameToUuid(name).thenCompose(uuid -> {
            if (uuid == null) return CompletableFuture.completedFuture((JsonObject) null);
            HypixelApi.invalidateProfiles(uuid);
            return HypixelApi.getProfiles(uuid);
        }).thenApply(ClearBreakdown::secretsOf).exceptionally(e -> -1L);
    }

    private static long secretsOf(JsonObject json) {
        if (json == null || !json.has("profiles") || !json.get("profiles").isJsonObject()) return -1;
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("profiles").entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject prof = e.getValue().getAsJsonObject();
            if (!prof.has("current") || !prof.get("current").getAsBoolean()) continue;
            try {
                return prof.getAsJsonObject("data").getAsJsonObject("dungeons").get("secrets").getAsLong();
            } catch (Exception ex) {
                return -1;
            }
        }
        return -1;
    }

    /** RunGate: the end line and the "☠ Defeated" line, in either order, once. */
    private static final class RunGate {
        private static final Pattern END = Pattern.compile("^\\s*(?:Master Mode )?(?:The )?Catacombs - (?:Entrance|Floor .{1,3})$");
        private static final Pattern DEFEATED = Pattern.compile("^\\s*☠ Defeated ");
        private boolean ended, won, fired;

        boolean line(String text) {
            if (fired) return false;
            if (END.matcher(text).find()) ended = true;
            else if (DEFEATED.matcher(text).find()) won = true;
            else return false;
            fired = ended && won;
            return fired;
        }

        void reset() { ended = false; won = false; fired = false; }
    }
}
