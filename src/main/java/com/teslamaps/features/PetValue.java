package com.teslamaps.features;

// Pet pricing after SkyHelper-Networth (Skyblocker's networth lib) and GanKura's PetBin
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.profileviewer.data.PetData;
import com.teslamaps.profileviewer.data.PetRepo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Lowest BIN of a pet by rarity and level (LVL_1 / LVL_100 / LVL_200), interpolated by xp. */
public final class PetValue {

    private static final String URL = "https://hysky.de/api/auctions/lowestbins";
    private static final long REFRESH_MS = 30 * 60_000L;
    private static final double CANDY_FACTOR = 0.65;

    private static final Map<String, Double> prices = new ConcurrentHashMap<>();
    private static volatile long fetched;
    private static volatile boolean loading;
    private static volatile int version;

    public record Pet(String type, String tier, double exp, int level, int candy, String heldItem, String skin) {}

    private PetValue() {}

    public static boolean enabled() { return TeslaMapsConfig.get().petValue; }

    public static int version() { return version; }

    public static void ensureFresh() {
        if (enabled()) load();
    }

    /** Lowest BIN of a level 1 pet, fetched even while Pet Value is off. */
    public static Double lvl1Price(String tier, String type) {
        load();
        return prices.get("LVL_1_" + tier + "_" + type);
    }

    private static void load() {
        if (loading) return;
        long now = System.currentTimeMillis();
        if (now - fetched < REFRESH_MS) return;
        loading = true;
        fetched = now;
        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest req = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(20)).GET().build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
                    for (var e : json.entrySet()) {
                        if (!e.getKey().startsWith("LVL_")) continue;
                        try { prices.put(e.getKey(), e.getValue().getAsDouble()); } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Pet price fetch failed: {}", e.toString());
            } finally {
                loading = false;
                version++;
            }
        });
    }

    /** Reads the item's petInfo json, null when it is not a pet. */
    public static Pet parse(String petInfo) {
        if (petInfo == null || petInfo.isBlank()) return null;
        try {
            JsonObject o = JsonParser.parseString(petInfo).getAsJsonObject();
            String type = o.has("type") ? o.get("type").getAsString() : null;
            String tier = o.has("tier") ? o.get("tier").getAsString().toUpperCase(Locale.ROOT) : null;
            if (type == null || tier == null) return null;
            double exp = o.has("exp") ? o.get("exp").getAsDouble() : 0;
            int candy = o.has("candyUsed") ? o.get("candyUsed").getAsInt() : 0;
            String held = o.has("heldItem") && !o.get("heldItem").isJsonNull() ? o.get("heldItem").getAsString() : null;
            String skin = o.has("skin") && !o.get("skin").isJsonNull() ? o.get("skin").getAsString() : null;
            int level = new PetData(type, tier, exp, false, held, skin, candy).getLevel();
            return new Pet(type, tier, exp, level, candy, held, skin);
        } catch (Exception e) {
            return null;
        }
    }

    /** The pet itself without held item or skin, null when no price is known. */
    public static Double basePrice(Pet pet) {
        ensureFresh();
        String key = "_" + pet.tier() + "_" + pet.type();
        Double lvl1 = prices.get("LVL_1" + key);
        Double lvl100 = prices.get("LVL_100" + key);
        Double lvl200 = prices.get("LVL_200" + key);

        Double price;
        if (pet.level() >= 100) {
            if (lvl200 != null && lvl100 != null) {
                double f = Math.min(1.0, (pet.level() - 100) / 100.0);
                price = lvl100 + (lvl200 - lvl100) * f;
            } else {
                price = pet.level() >= 200 && lvl200 != null ? lvl200 : lvl100 != null ? lvl100 : lvl1;
            }
        } else if (lvl1 != null && lvl100 != null) {
            double need = xpTo100(pet);
            double f = need > 0 ? Math.min(1.0, pet.exp() / need) : 0;
            price = lvl1 + (lvl100 - lvl1) * f;
        } else {
            price = lvl1 != null ? lvl1 : lvl100;
        }
        if (price == null) return null;
        if (pet.candy() > 0 && pet.level() < 100) price *= CANDY_FACTOR;
        return price;
    }

    private static double xpTo100(Pet pet) {
        if (!PetRepo.isLoaded()) return 0;
        double sum = 0;
        for (int l = 1; l < 100; l++) sum += PetRepo.costFor(pet.type(), pet.tier(), l);
        return sum;
    }

    /** Skin ids carry the PET_SKIN_ prefix on the market. */
    public static String skinId(String skin) {
        return skin.startsWith("PET_SKIN_") ? skin : "PET_SKIN_" + skin;
    }
}
