package com.teslamaps.features;

// After SkyHanni's SprayDisplay / GardenPlotApi spray tracking
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.screen.AppleColors;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Spray type and expiry per plot, so every sprayed plot can be listed, not only the one you stand on. */
public final class PlotSprays {

    private static final Pattern SPRAYED = Pattern.compile(
            "SPRAYONATOR! You sprayed Plot - (?<plot>.+) with (?:\\d+ )?(?<spray>.+)!");
    private static final Pattern RAN_OUT = Pattern.compile(
            "SPRAYONATOR! The smell of .+ on Plot - (?<plot>.+) ran out!");
    private static final String WASHED = "SPLASH! Your Garden was cleared of all active Sprayonator effects!";

    private static final long SHOW_EXPIRED = 30 * 60_000L;
    private static final long KEEP = 24 * 3600_000L;

    public static class Spray {
        public String type;
        public long expiry;
        public boolean notified;
    }

    public record Row(String label, String value, int color) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Spray>>() {}.getType();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("teslamaps").resolve("plot_sprays.json");

    private static Map<String, Spray> sprays = new HashMap<>();
    private static boolean loaded = false;
    private static List<Row> rows = List.of();
    private static String heldSprayonator = null;
    private static long heldAt = 0L;
    private static boolean wasInGarden = false;
    private static int ticks = 0;

    private PlotSprays() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PlotSprays").section("Garden")
                .when(() -> cfg().pestOverlaySprayList || cfg().sprayExpiryChat)
                .tick(PlotSprays::tick)
                .serverChat(PlotSprays::onChat);
    }

    /** Rows for the pest overlay, built in tick. */
    /** True when standing on a plot the tab list reports as unsprayed. */
    public static boolean notSprayedHere() {
        return notSprayedHere && cfg().pestOverlaySprayList;
    }

    private static boolean notSprayedHere = false;

    public static List<Row> rows() {
        return cfg().pestOverlaySprayList && cfg().section("Garden") ? rows : List.of();
    }

    private static void onChat(String raw) {
        String text = StringUtil.strip(raw).trim();
        if (!text.startsWith("SPRAYONATOR!") && !text.startsWith("SPLASH!")) return;
        if (!loaded) load();
        long now = System.currentTimeMillis();
        Matcher m = SPRAYED.matcher(text);
        if (m.find()) {
            Spray s = new Spray();
            s.type = m.group("spray").trim();
            s.expiry = now + duration(now);
            sprays.put(m.group("plot").trim(), s);
            save();
            rebuild(now);
            return;
        }
        m = RAN_OUT.matcher(text);
        if (m.find()) {
            Spray s = sprays.get(m.group("plot").trim());
            if (s != null) {
                s.expiry = Math.min(s.expiry, now);
                s.notified = true;
                save();
                rebuild(now);
            }
            return;
        }
        if (text.equals(WASHED)) {
            sprays.clear();
            save();
            rebuild(now);
        }
    }

    // SkyHanni's SprayonatorType durations, from the sprayonator held last
    private static long duration(long now) {
        String id = now - heldAt < 10_000L ? heldSprayonator : null;
        if ("SALTY_SPRAYONATOR".equals(id)) return 60 * 60_000L;
        if ("JUICY_SPRAYONATOR".equals(id)) return 45 * 60_000L;
        return 30 * 60_000L;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean garden = SkyblockUtils.isInGarden();
        if (!garden) {
            wasInGarden = false;
            notSprayedHere = false;
            return;
        }
        if (mc.player != null) {
            String id = ItemUtil.skyblockId(mc.player.getMainHandItem());
            if (id != null && id.endsWith("SPRAYONATOR")) {
                heldSprayonator = id;
                heldAt = System.currentTimeMillis();
            }
        }
        if (++ticks % 20 != 0) return;
        if (!loaded) load();
        long now = System.currentTimeMillis();

        String plot = PestData.currentPlot();
        notSprayedHere = plot != null && PestData.hasPestSection() && PestData.spray() == null;
        if (plot != null && PestData.hasPestSection()) {
            Spray s = sprays.get(plot);
            String tabSpray = PestData.spray();
            if (tabSpray != null) {
                // the tab only reports whole minutes, so only a clear mismatch replaces the estimate
                long tabExpiry = now + PestData.spraySeconds() * 1000L;
                if (s == null || Math.abs(s.expiry - tabExpiry) > 90_000L || !tabSpray.equals(s.type)) {
                    if (s == null) s = new Spray();
                    s.type = tabSpray;
                    s.expiry = tabExpiry;
                    s.notified = false;
                    sprays.put(plot, s);
                    save();
                }
            } else if (s != null && s.expiry > now + 60_000L) {
                sprays.remove(plot);
                save();
            }
        }

        if (cfg().sprayExpiryChat) notifyExpired(mc, now, !wasInGarden);
        wasInGarden = true;
        rebuild(now);
    }

    private static void notifyExpired(Minecraft mc, long now, boolean wasAway) {
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Spray> e : sprays.entrySet()) {
            Spray s = e.getValue();
            if (s.notified || s.expiry > now) continue;
            s.notified = true;
            expired.add(e.getKey());
        }
        if (expired.isEmpty()) return;
        save();
        if (mc.player == null) return;
        expired.sort(PlotSprays::comparePlots);
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7" + (wasAway ? "While you were away, your" : "Your")
                + (expired.size() == 1 ? " spray" : " sprays") + " on §aPlot §7- §b"
                + String.join("§7, §b", expired) + " §7expired."));
    }

    private static void rebuild(long now) {
        sprays.values().removeIf(s -> now - s.expiry > KEEP);
        List<Map.Entry<String, Spray>> list = new ArrayList<>();
        for (Map.Entry<String, Spray> e : sprays.entrySet()) {
            if (now - e.getValue().expiry < SHOW_EXPIRED) list.add(e);
        }
        list.sort((a, b) -> Long.compare(a.getValue().expiry, b.getValue().expiry));
        List<Row> out = new ArrayList<>(list.size());
        for (Map.Entry<String, Spray> e : list) {
            if (out.size() >= 8) break;
            long left = (e.getValue().expiry - now) / 1000L;
            String value = left <= 0 ? "Expired" : left >= 60 ? (left / 60) + "m" : left + "s";
            int color = left <= 0 ? AppleColors.ACCENT_RED : left <= 300 ? 0xFFFFFF55 : AppleColors.TEXT_PRIMARY;
            out.add(new Row("Plot " + e.getKey(), value, color));
        }
        rows = out;
    }

    private static int comparePlots(String a, String b) {
        try {
            return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }

    private static void load() {
        loaded = true;
        try {
            if (Files.exists(PATH)) {
                Map<String, Spray> read = GSON.fromJson(Files.readString(PATH), TYPE);
                if (read != null) {
                    read.values().removeIf(s -> s == null);
                    sprays = read;
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load plot sprays", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(sprays));
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to save plot sprays", e);
        }
    }
}
