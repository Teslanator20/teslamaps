package com.teslamaps.adm.misc.pf;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.api.DungeonData;
import com.teslamaps.api.HypixelApi;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

// Ported from ADM's pf/PlayerStats.kt: cata level, secrets and S+ times per player, through our HypixelApi, a day on disk
public final class PlayerStats {

    public sealed interface Lookup permits Loading, Failed, Ready {}
    public record Loading() implements Lookup {}
    public record Failed(long at) implements Lookup {}
    public record Ready(Stats stats) implements Lookup {}

    public static final class Stats {
        public double cataXp;
        public double cataLevel;
        public long secrets;
        /** "f"/"m" -> floor "0".."7" -> S+ ms. */
        public Map<String, Map<String, Double>> sPlus = new HashMap<>();
        public long fetchedAt;
    }

    private static final Loading LOADING = new Loading();
    private static final long RETRY_FAILED_MS = 60_000L;
    private static final long STATS_TTL_MS = 24 * 60 * 60 * 1000L;
    private static final int MAX_STORED = 2000;
    private static final Gson GSON = new Gson();

    private static final Map<String, Lookup> cache = new ConcurrentHashMap<>();
    private static final Set<String> refreshing = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean loaded = new AtomicBoolean(false);
    private static final Object SAVE_LOCK = new Object();

    private PlayerStats() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("teslamaps").resolve("adm").resolve("pfstats.json");
    }

    public static Lookup lookup(String name) {
        if (loaded.compareAndSet(false, true)) load();
        String key = name.toLowerCase(Locale.ROOT);
        Lookup entry = cache.get(key);
        long now = System.currentTimeMillis();
        if (entry == null || (entry instanceof Failed f && now - f.at() > RETRY_FAILED_MS)) {
            cache.put(key, LOADING);
            fetch(name, key);
            return LOADING;
        }
        if (entry instanceof Ready r && now - r.stats().fetchedAt > STATS_TTL_MS && refreshing.add(key)) fetch(name, key);
        return entry;
    }

    private static void fetch(String name, String key) {
        HypixelApi.nameToUuid(name)
                .thenCompose(uuid -> uuid == null ? CompletableFuture.completedFuture(null) : HypixelApi.getProfiles(uuid))
                .handle((resp, err) -> {
                    Stats fresh = null;
                    try {
                        if (err == null && resp != null && resp.has("profiles")) {
                            JsonObject member = selectedMember(resp.getAsJsonObject("profiles"));
                            if (member != null) fresh = toStats(member);
                        }
                    } catch (Throwable t) {
                        fresh = null;
                    }
                    boolean refreshFailed = fresh == null && cache.get(key) instanceof Ready;
                    if (fresh != null) {
                        cache.put(key, new Ready(fresh));
                        save();
                    } else if (!refreshFailed) {
                        cache.put(key, new Failed(System.currentTimeMillis()));
                    }
                    if (refreshFailed) {
                        CompletableFuture.delayedExecutor(RETRY_FAILED_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                                .execute(() -> refreshing.remove(key));
                    } else {
                        refreshing.remove(key);
                    }
                    return null;
                });
    }

    private static JsonObject selectedMember(JsonObject profiles) {
        JsonObject fallback = null;
        for (Map.Entry<String, JsonElement> e : profiles.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject p = e.getValue().getAsJsonObject();
            JsonObject data = p.has("data") && p.get("data").isJsonObject() ? p.getAsJsonObject("data") : null;
            if (data == null) continue;
            if (fallback == null) fallback = data;
            if (p.has("current") && p.get("current").getAsBoolean()) return data;
        }
        return fallback;
    }

    private static Stats toStats(JsonObject member) {
        DungeonData dd = new DungeonData(member);
        Stats s = new Stats();
        s.cataXp = dd.getCatacombsXp();
        s.cataLevel = dd.getCatacombsLevel() + dd.getCatacombsProgress();
        s.secrets = dd.getSecretsFound();
        s.sPlus.put("f", times(dd.getNormalFloors()));
        s.sPlus.put("m", times(dd.getMasterFloors()));
        s.fetchedAt = System.currentTimeMillis();
        return s;
    }

    private static Map<String, Double> times(Map<String, DungeonData.FloorData> floors) {
        Map<String, Double> out = new HashMap<>();
        for (Map.Entry<String, DungeonData.FloorData> e : floors.entrySet())
            if (e.getValue().getFastestSPlusTime() > 0) out.put(e.getKey(), (double) e.getValue().getFastestSPlusTime());
        return out;
    }

    public static String cataStr(Stats s) {
        return String.format(Locale.ROOT, "%.1f", s.cataLevel);
    }

    public static String secretsStr(long count) {
        if (count >= 100_000) return String.format(Locale.ROOT, "%.0fk", count / 1000.0);
        if (count >= 1_000) return String.format(Locale.ROOT, "%.1fk", count / 1000.0);
        return Long.toString(count);
    }

    /** S+ time as "m:ss", or "§7—". */
    public static String pbStr(Stats s, String mode, String floor) {
        if (floor == null) return "§7—";
        Map<String, Double> m = s.sPlus == null ? null : s.sPlus.get(mode);
        Double ms = m == null ? null : m.get(floor);
        if (ms == null || ms <= 0) return "§7—";
        long total = (long) (ms / 1000);
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }

    private static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Map<String, Stats> stored = GSON.fromJson(Files.readString(f), new TypeToken<Map<String, Stats>>() {}.getType());
            if (stored == null) return;
            stored.forEach((k, v) -> { if (v != null) cache.put(k, new Ready(v)); });
            TeslaMaps.LOGGER.info("[adm] player stats: {} players from disk", stored.size());
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("[adm] player stats load failed", t);
        }
    }

    private static void save() {
        synchronized (SAVE_LOCK) {
            try {
                List<Map.Entry<String, Stats>> ready = new ArrayList<>();
                for (Map.Entry<String, Lookup> e : cache.entrySet())
                    if (e.getValue() instanceof Ready r) ready.add(Map.entry(e.getKey(), r.stats()));
                ready.sort((a, b) -> Long.compare(b.getValue().fetchedAt, a.getValue().fetchedAt));
                Map<String, Stats> out = new LinkedHashMap<>();
                for (int i = 0; i < Math.min(MAX_STORED, ready.size()); i++) out.put(ready.get(i).getKey(), ready.get(i).getValue());
                Path f = file();
                Files.createDirectories(f.getParent());
                Path tmp = f.resolveSibling("pfstats.json.tmp");
                Files.writeString(tmp, GSON.toJson(out));
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable t) {
                TeslaMaps.LOGGER.warn("[adm] player stats save failed", t);
            }
        }
    }
}
