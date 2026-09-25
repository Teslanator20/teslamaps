package com.teslamaps.features;

// Ported (slim) from SkyHanni's leaderboarddisplays/EliteLeaderboards.kt, WeightDisplay.kt, CropDisplay.kt, PestDisplay.kt
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EliteLeaderboards {

    private static final String API = "https://api.eliteskyblock.com";
    private static final String SITE = "https://eliteskyblock.com/@";
    private static final long REFRESH_MS = 10 * 60_000L;
    private static final long RETRY_MS = 60_000L;
    public static final String[] GAMEMODES = {"All", "Ironman", "Stranded"};

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private record Board(int rank, double amount, double minAmount, String nextName, double nextAmount,
                         Integer nextRank, boolean disabled, long at) {}

    private static final Map<String, Board> boards = new ConcurrentHashMap<>();
    private static final Map<String, Long> requested = new ConcurrentHashMap<>();
    private static final Map<String, Double> factors = new ConcurrentHashMap<>();
    private static volatile String profileId;
    private static volatile boolean weightError;
    private static long weightRequested, factorsRequested;
    private static String profileFor;

    private static final List<String> SAMPLE = List.of("§6Farming Weight§7: §e104,481.49 §7[§b#5§7]",
            "§e170.21 §7(§b12h 32m§7) §7behind §bChissl");

    private static List<String> lines = List.of();
    private static List<String> links = List.of();
    private static int width, ticks;
    private static HudEditElement hud;

    private EliteLeaderboards() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("eliteLb", "Elite Leaderboards").section("Garden")
                .enabled(() -> cfg().eliteLeaderboards)
                .size(() -> CropStats.linesWidth(SAMPLE), () -> CropStats.linesHeight(SAMPLE.size()))
                .preview((g, mc, x, y, s) -> CropStats.drawLines(g, mc, x, y, s, SAMPLE, CropStats.linesWidth(SAMPLE)));
        Feature.of("EliteLeaderboards").section("Garden").when(() -> cfg().eliteLeaderboards)
                .tick(EliteLeaderboards::tick)
                .hud("eliteleaderboards", EliteLeaderboards::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Elite Farmers Leaderboards");
                    b.toggle("Elite Leaderboards", () -> c.eliteLeaderboards, v -> c.eliteLeaderboards = v)
                            .desc("Farming weight, crop collection and pest kill ranks from eliteskyblock.com, "
                                    + "with the gap to the next player. Open chat and click a line to open that Elite profile.");
                    b.toggle("  Farming Weight", () -> c.eliteLbWeight, v -> { c.eliteLbWeight = v; boards.clear(); });
                    b.toggle("  Crop Collection (last farmed crop)", () -> c.eliteLbCrop, v -> { c.eliteLbCrop = v; boards.clear(); });
                    b.toggle("  Pest Kills", () -> c.eliteLbPest, v -> { c.eliteLbPest = v; boards.clear(); });
                    b.toggle("  Monthly Instead Of All-Time", () -> c.eliteLbMonthly, v -> { c.eliteLbMonthly = v; boards.clear(); });
                    b.dropdown("  Gamemode", GAMEMODES, () -> c.eliteLbGamemode, v -> { c.eliteLbGamemode = v; boards.clear(); });
                    b.toggle("  Overtake ETA", () -> c.eliteLbOvertake, v -> c.eliteLbOvertake = v);
                    b.toggle("  Show Outside The Garden", () -> c.eliteLbEverywhere, v -> c.eliteLbEverywhere = v);
                    b.button("Reload Leaderboards", () -> { boards.clear(); requested.clear(); profileId = null; weightRequested = 0; });
                });
    }

    private static String uuid() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getUUID().toString().replace("-", "");
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        TeslaMapsConfig c = cfg();
        if (!SkyblockUtils.isInSkyblock() || (!c.eliteLbEverywhere && !SkyblockUtils.isInGarden())) {
            lines = List.of();
            return;
        }
        String uuid = uuid();
        if (uuid == null) return;
        long now = System.currentTimeMillis();
        String profile = SbProfile.name();
        if (profile != null && !profile.equals(profileFor)) {
            profileFor = profile;
            profileId = null;
            boards.clear();
            weightRequested = 0;
        }
        if (factors.isEmpty() && now - factorsRequested > REFRESH_MS) {
            factorsRequested = now;
            fetchFactors();
        }
        if (profileId == null) {
            if (now - weightRequested > RETRY_MS) {
                weightRequested = now;
                fetchProfile(uuid, profile);
            }
            lines = List.of(weightError ? "§cElite Leaderboards: cannot load profile" : "§7Loading Elite profile...");
            links = List.of("");
            width = CropStats.linesWidth(lines);
            return;
        }

        String suffix = c.eliteLbMonthly ? "-monthly" : "";
        List<String> out = new ArrayList<>();
        List<String> ls = new ArrayList<>();
        String self = Minecraft.getInstance().player.getGameProfile().name();
        CropStats.Crop last = CropStats.lastCrop();
        if (c.eliteLbWeight) board("farmingweight" + suffix, "Farming Weight", uuid, now, out, ls, self, last, true);
        if (c.eliteLbCrop) {
            if (last == null) {
                out.add("§7Crop Collection: §7farm a crop first");
                ls.add("");
            } else {
                board(last.eliteLb() + suffix, last.name() + " Collection", uuid, now, out, ls, self, last, false);
            }
        }
        if (c.eliteLbPest) board("pests" + suffix, "Pest Kills", uuid, now, out, ls, self, null, false);
        lines = out;
        links = ls;
        width = CropStats.linesWidth(out);
    }

    private static void board(String lb, String label, String uuid, long now, List<String> out, List<String> ls,
                              String self, CropStats.Crop crop, boolean weight) {
        Board b = boards.get(lb);
        Long req = requested.get(lb);
        if ((b == null || now - b.at() > REFRESH_MS) && (req == null || now - req > RETRY_MS)) {
            requested.put(lb, now);
            fetchBoard(lb, uuid);
        }
        String title = "§6" + label + (cfg().eliteLbMonthly ? " Monthly" : "")
                + ("All".equals(cfg().eliteLbGamemode) ? "" : " " + cfg().eliteLbGamemode) + "§7: ";
        if (b == null) {
            out.add(title + "§7Loading...");
            ls.add(self);
            return;
        }
        if (b.disabled()) {
            out.add(title + "§7Waiting for update...");
            ls.add(self);
            return;
        }
        if (b.rank() <= 0) {
            out.add(title + "§eNot ranked!");
            ls.add(self);
            double until = b.minAmount() - b.amount();
            if (until > 0) {
                out.add("§e" + CropStats.fmt(round(until)) + eta(until, crop, weight) + " §7until ranked!");
                ls.add(self);
            }
            return;
        }
        out.add(title + "§e" + CropStats.fmt(round(b.amount())) + " §7[§b#" + CropStats.fmt(b.rank()) + "§7]");
        ls.add(self);
        if (b.nextName() != null) {
            double until = b.nextAmount() - b.amount();
            String rank = b.nextRank() != null && b.nextRank() != b.rank() - 1 ? " §7[#" + CropStats.fmt(b.nextRank()) + "]" : "";
            out.add("§e" + CropStats.fmt(round(Math.max(0, until))) + eta(until, crop, weight) + " §7behind §b" + b.nextName() + rank);
            ls.add(b.nextName());
        }
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String eta(double until, CropStats.Crop crop, boolean weight) {
        if (!cfg().eliteLbOvertake || crop == null || until <= 0) return "";
        Double cps = CropStats.cropsPerSecond(crop);
        if (cps == null || cps <= 0) return "";
        double perSec = cps;
        if (weight) {
            Double f = factor(crop);
            if (f == null || f <= 0) return "";
            perSec = cps / f;
        }
        return " §7(§b" + CropStats.duration(until / perSec) + "§7)";
    }

    private static Double factor(CropStats.Crop crop) {
        Double f = factors.get(crop.key().toLowerCase());
        if (f == null) f = factors.get(crop.name().toLowerCase().replace(" ", ""));
        if (f == null) f = factors.get(crop.eliteLb());
        return f;
    }

    private static void get(String url, java.util.function.Consumer<JsonElement> ok, Runnable fail) {
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "TeslaMaps").GET().build();
        } catch (Exception e) {
            fail.run();
            return;
        }
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((resp, err) -> {
            try {
                if (err != null) throw err;
                if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
                ok.accept(JsonParser.parseString(resp.body()));
            } catch (Throwable e) {
                TeslaMaps.LOGGER.warn("[EliteLb] {} failed: {}", url, e.toString());
                fail.run();
            }
        });
    }

    private static void fetchProfile(String uuid, String localProfile) {
        get(API + "/weight/" + uuid, json -> {
            JsonObject o = json.getAsJsonObject();
            String selected = o.has("selectedProfileId") && !o.get("selectedProfileId").isJsonNull()
                    ? o.get("selectedProfileId").getAsString() : null;
            String found = null;
            for (JsonElement e : o.getAsJsonArray("profiles")) {
                JsonObject p = e.getAsJsonObject();
                String id = p.get("profileId").getAsString();
                String name = p.has("profileName") ? p.get("profileName").getAsString() : "";
                if (localProfile != null && name.equalsIgnoreCase(localProfile)) {
                    found = id;
                    break;
                }
                if (id.equals(selected) && found == null) found = id;
            }
            if (found == null) throw new IllegalStateException("no profile");
            profileId = found;
            weightError = false;
        }, () -> weightError = true);
    }

    private static void fetchFactors() {
        get(API + "/weights/all", json -> {
            JsonObject crops = json.getAsJsonObject().getAsJsonObject("crops");
            if (crops == null) return;
            for (Map.Entry<String, JsonElement> e : crops.entrySet()) {
                factors.put(e.getKey().toLowerCase().replace(" ", "").replace("_", ""), e.getValue().getAsDouble());
                factors.put(e.getKey().toLowerCase(), e.getValue().getAsDouble());
            }
        }, () -> {});
    }

    private static void fetchBoard(String lb, String uuid) {
        String pid = profileId;
        if (pid == null) return;
        String mode = switch (cfg().eliteLbGamemode) {
            case "Ironman" -> "&mode=ironman";
            case "Stranded" -> "&mode=island";
            default -> "";
        };
        get(API + "/leaderboard/" + lb + "/" + uuid + "/" + pid + "?upcoming=1&previous=1" + mode, json -> {
            JsonObject o = json.getAsJsonObject();
            String nextName = null;
            double nextAmount = 0;
            Integer nextRank = null;
            JsonArray up = o.has("upcomingPlayers") && o.get("upcomingPlayers").isJsonArray() ? o.getAsJsonArray("upcomingPlayers") : null;
            if (up != null && !up.isEmpty()) {
                JsonObject p = up.get(0).getAsJsonObject();
                nextName = p.has("ign") ? p.get("ign").getAsString() : "?";
                nextAmount = p.get("amount").getAsDouble();
                if (p.has("rank") && !p.get("rank").isJsonNull()) nextRank = p.get("rank").getAsInt();
            }
            boards.put(lb, new Board(
                    o.has("rank") ? o.get("rank").getAsInt() : -1,
                    o.has("amount") ? o.get("amount").getAsDouble() : 0,
                    o.has("minAmount") ? o.get("minAmount").getAsDouble() : 0,
                    nextName, nextAmount, nextRank,
                    o.has("disabled") && o.get("disabled").getAsBoolean(),
                    System.currentTimeMillis()));
        }, () -> {});
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> ls = lines;
        if (ls.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        CropStats.drawLines(g, mc, hud.x(), hud.y(), hud.scale(), ls, width);
    }

    /** Chat open: a click on a line opens that player's Elite profile. */
    public static boolean onChatClick(double mx, double my) {
        if (hud == null || lines.isEmpty() || !cfg().eliteLeaderboards || !cfg().section("Garden")) return false;
        float s = hud.scale();
        double lx = (mx - hud.x()) / s, ly = (my - hud.y()) / s;
        if (lx < 0 || lx > width || ly < 4) return false;
        int row = (int) ((ly - 4) / 10);
        List<String> ls = links;
        if (row < 0 || row >= ls.size() || ls.get(row).isEmpty()) return false;
        net.minecraft.util.Util.getPlatform().openUri(SITE + ls.get(row));
        return true;
    }
}
