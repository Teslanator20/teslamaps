package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Power orbs, lanterns, the Black Hole and the Trophy Fishing Umberella all announce their
 * own remaining time in their nametag ("Umberella 298s"), so the timer is just read off it.
 */
public class Deployables {

    public enum Type { FLUX, LANTERN, UMBERELLA, BLACK_HOLE, FLARE }

    public enum Deployable {
        RADIANT("Radiant", "§aRadiant", 18, Type.FLUX, 1),
        MANA_FLUX("Mana Flux", "§9Mana Flux", 18, Type.FLUX, 2),
        OVERFLUX("Overflux", "§5Overflux", 18, Type.FLUX, 3),
        PLASMAFLUX("Plasmaflux", "§d§lPlasmaflux", 20, Type.FLUX, 4),
        DWARVEN_LANTERN("Dwarven Lantern", "§fDwarven Lantern", 30, Type.LANTERN, 1),
        MITHRIL_LANTERN("Mithril Lantern", "§aMithril Lantern", 30, Type.LANTERN, 2),
        TITANIUM_LANTERN("Titanium Lantern", "§9Titanium Lantern", 30, Type.LANTERN, 3),
        GLACITE_LANTERN("Glacite Lantern", "§5Glacite Lantern", 30, Type.LANTERN, 4),
        WILL_O_WISP("Will-o'-wisp", "§6§lWill-o'-wisp", 30, Type.LANTERN, 5),
        BLACK_HOLE("Black Hole", "§5Black Hole", 20, Type.BLACK_HOLE, 0),
        UMBERELLA("Umberella", "§9Umberella", 30, Type.UMBERELLA, 0),
        // flares carry no nametag, they are found by their head texture instead
        WARNING_FLARE("Warning Flare", "§aWarning Flare", 40, Type.FLARE, 1),
        ALERT_FLARE("Alert Flare", "§9Alert Flare", 40, Type.FLARE, 2),
        SOS_FLARE("SOS Flare", "§5SOS Flare", 40, Type.FLARE, 3);

        public final String matchName;
        public final String displayName;
        public final int range;
        public final Type type;
        public final int tier;

        Deployable(String name, String displayName, int range, Type type, int tier) {
            this.matchName = name;
            this.displayName = displayName;
            this.range = range;
            this.type = type;
            this.tier = tier;
        }
    }

    /** A deployable is only up while its own nametag says so, right now and in range. */
    private static final class Track {
        long end;
        long lastSeenAt;
        int lastSeconds = -1;
        long valueSince;
        boolean active;
    }

    // stands drop out of entitiesForRendering for a tick or two, that is not the timer ending
    private static final long SEEN_TIMEOUT_MS = 2000;
    // a live nametag counts down; one that stopped belongs to a leftover entity
    private static final long FROZEN_MS = 4000;

    /**
     * Any nametag of the form "<Name> 18s" or "<Name> 4m 58s" is a deployable timer, so the
     * tracks are keyed by the parsed name instead of the enum. That way flares, Willow and
     * whatever Hypixel adds next show up without needing an entry here first - the enum only
     * still supplies the colour, range, tier and type toggle for the ones we do know.
     */
    private static final Pattern ANY_TIMER = Pattern.compile(
            "^(?<name>.+?) (?:(?<min>\\d+)m )?(?<sec>\\d+)s$");
    private static final int DEFAULT_RANGE = 30;

    // flare head textures, taken from SkyHanni's repo constant "Skulls"
    private static final String FLARE_WARNING_URL = "22e2bf6c1ec330247927ba63479e5872ac66b06903c86c82b52dac9f1c971458";
    private static final String FLARE_ALERT_URL = "9d2bf9864720d87fd06b84efa80b795c48ed539b16523c3b1f1990b40c003f6b";
    private static final String FLARE_SOS_URL = "c0062cc98ebda72a6a4b89783adcef2815b483a01d73ea87b3df76072a89d13b";
    private static final long FLARE_MS = 180_000L;
    // decoding a head texture per stand per tick would be silly, so the verdict is kept per entity
    private static final Map<Integer, String> flareCache = new java.util.HashMap<>();

    private static final Map<String, Track> tracks = new java.util.LinkedHashMap<>();
    private static java.lang.ref.WeakReference<net.minecraft.world.level.Level> lastLevel =
            new java.lang.ref.WeakReference<>(null);

    public static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.deployableOverlay && !c.umbrellaWarning) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        // a server hop leaves the old stands behind, and their frozen nametags would read as timers
        if (mc.level != lastLevel.get()) {
            reset();
            lastLevel = new java.lang.ref.WeakReference<>(mc.level);
        }

        long now = System.currentTimeMillis();

        boolean flares = c.deployableOverlay && c.deployableFlare;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || stand.isRemoved()) continue;
            if (stand.getCustomName() == null) {
                if (flares) trackFlare(stand, mc, now);
                continue;
            }
            String name = StringUtil.strip(stand.getCustomName().getString()).trim();
            if (name.isEmpty()) continue;

            Matcher m = ANY_TIMER.matcher(name);
            if (!m.matches()) continue;
            String label = m.group("name").trim();
            if (label.isEmpty()) continue;

            Deployable known = byName(label);
            int range = known != null ? known.range : DEFAULT_RANGE;
            // out of range it does nothing for you, so it should not be on the HUD either
            if (entity.distanceToSqr(mc.player) > (double) range * range) continue;

            int seconds = Integer.parseInt(m.group("sec"));
            if (m.group("min") != null) seconds += Integer.parseInt(m.group("min")) * 60;

            Track t = tracks.get(label);
            if (t == null) {
                t = new Track();
                tracks.put(label, t);
                // /tmap debug prints the raw tag, so an unexpected format is easy to report
                com.teslamaps.TeslaMaps.debug("[Deployable] new timer tag: '{}' -> {}", name, label);
            }
            if (seconds != t.lastSeconds) {
                t.lastSeconds = seconds;
                t.valueSince = now;
            }
            if (now - t.valueSince <= FROZEN_MS) {
                t.lastSeenAt = now;
                t.end = now + seconds * 1000L;
                t.active = true;
            }
        }

        for (Track t : tracks.values()) {
            if (t.active && (now >= t.end || now - t.lastSeenAt > SEEN_TIMEOUT_MS)) t.active = false;
        }
    }

    /**
     * A flare has no nametag, so the type comes from its head texture and the time left from how
     * long the entity has been alive. Walking up to a flare that was already burning therefore
     * starts the count at the full three minutes.
     */
    private static void trackFlare(ArmorStand stand, Minecraft mc, long now) {
        String label = flareOf(stand);
        if (label == null) return;
        if (stand.distanceToSqr(mc.player) > 1600) return;

        long left = FLARE_MS - stand.tickCount * 50L;
        if (left <= 0) return;

        Track t = tracks.computeIfAbsent(label, k -> new Track());
        t.lastSeenAt = now;
        t.end = now + left;
        t.active = true;
    }

    private static String flareOf(ArmorStand stand) {
        String cached = flareCache.get(stand.getId());
        if (cached != null) return cached.isEmpty() ? null : cached;

        net.minecraft.world.item.ItemStack head =
                stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        // equipment arrives in its own packet, so an empty head on a fresh stand proves nothing
        if (head.isEmpty()) {
            if (stand.tickCount > 20) flareCache.put(stand.getId(), "");
            return null;
        }

        String url = SoulweaverHider.headTextureUrl(head);
        String label = "";
        if (FLARE_WARNING_URL.equals(url)) label = Deployable.WARNING_FLARE.matchName;
        else if (FLARE_ALERT_URL.equals(url)) label = Deployable.ALERT_FLARE.matchName;
        else if (FLARE_SOS_URL.equals(url)) label = Deployable.SOS_FLARE.matchName;

        if (flareCache.size() > 512) flareCache.clear();
        flareCache.put(stand.getId(), label);
        return label.isEmpty() ? null : label;
    }

    /** Lists every named stand nearby and whether its tag parses, so a changed format is visible. */
    public static void debugDump() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§b[TeslaMaps] §fNamed stands within 40 blocks:"));
        int found = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand)) continue;
            if (entity.distanceToSqr(mc.player) > 1600) continue;
            if (stand.getCustomName() == null) {
                net.minecraft.world.item.ItemStack head =
                        stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                String url = SoulweaverHider.headTextureUrl(head);
                if (url == null) continue;
                found++;
                String flare = flareOf(stand);
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        (flare != null ? "§a" : "§7") + " head " + url.substring(0, Math.min(16, url.length()))
                        + "… §8" + (int) Math.sqrt(entity.distanceToSqr(mc.player)) + "m alive="
                        + stand.tickCount / 20 + "s" + (flare != null ? " §a" + flare : "")));
                com.teslamaps.TeslaMaps.debug("[DeployableDump] head url={} flare={} tickCount={}",
                        url, flare, stand.tickCount);
                continue;
            }
            String name = StringUtil.strip(stand.getCustomName().getString()).trim();
            if (name.isEmpty()) continue;
            found++;
            boolean match = ANY_TIMER.matcher(name).matches();
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    (match ? "§a" : "§7") + " \"" + name + "\" §8"
                    + (int) Math.sqrt(entity.distanceToSqr(mc.player)) + "m"
                    + (match ? " §amatches" : "")));
            com.teslamaps.TeslaMaps.debug("[DeployableDump] \"{}\" raw=\"{}\" match={}",
                    name, stand.getCustomName().getString(), match);
        }
        if (found == 0) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7 none"));
        }
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§7 overlay=" + TeslaMapsConfig.get().deployableOverlay
                + " flux=" + TeslaMapsConfig.get().deployableFlux
                + " lantern=" + TeslaMapsConfig.get().deployableLantern
                + " umbrella=" + TeslaMapsConfig.get().deployableUmbrella
                + " blackhole=" + TeslaMapsConfig.get().deployableBlackHole
                + " flare=" + TeslaMapsConfig.get().deployableFlare
                + " others=" + TeslaMapsConfig.get().deployableOthers
                + " tracks=" + tracks.size()));
    }

    /** @return seconds left, or -1 when that deployable is not up. */
    public static long secondsLeft(String name) {
        Track t = tracks.get(name);
        if (t == null || !t.active) return -1;
        return Math.max(0, (t.end - System.currentTimeMillis() + 999) / 1000);
    }

    public static long secondsLeft(Deployable deployable) {
        return secondsLeft(deployable.matchName);
    }

    public static void reset() {
        tracks.clear();
        flareCache.clear();
    }

    public static void clear(Deployable deployable) {
        Track t = tracks.get(deployable.matchName);
        if (t != null) t.active = false;
    }

    private static Deployable byName(String name) {
        for (Deployable d : Deployable.values()) {
            if (d.matchName.equalsIgnoreCase(name)) return d;
        }
        return null;
    }

    private static boolean shows(TeslaMapsConfig c, Type type) {
        return switch (type) {
            case FLUX -> c.deployableFlux;
            case LANTERN -> c.deployableLantern;
            case UMBERELLA -> c.deployableUmbrella;
            case BLACK_HOLE -> c.deployableBlackHole;
            case FLARE -> c.deployableFlare;
        };
    }

    public static List<String> lines() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Track> e : tracks.entrySet()) {
            long left = secondsLeft(e.getKey());
            if (left < 0) continue;

            Deployable known = byName(e.getKey());
            if (known == null) {
                if (!c.deployableOthers) continue;
                out.add("§b" + e.getKey() + " §e" + format(left));
                continue;
            }
            if (!shows(c, known.type)) continue;
            // a Radiant next to an Overflux is just noise
            if (c.deployableHighestTierOnly && outranked(known)) continue;
            out.add(known.displayName + " §e" + format(left));
        }
        return out;
    }

    private static boolean outranked(Deployable deployable) {
        for (Deployable other : Deployable.values()) {
            if (other.type != deployable.type || other.tier <= deployable.tier) continue;
            if (secondsLeft(other) >= 0) return true;
        }
        return false;
    }

    private static String format(long seconds) {
        return seconds >= 60 ? String.format("%dm %02ds", seconds / 60, seconds % 60) : seconds + "s";
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.deployableOverlay) return;
        Minecraft mc = Minecraft.getInstance();
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;

        List<String> lines = lines();
        if (lines.isEmpty()) return;
        draw(context, mc, c.deployableX, c.deployableY, c.deployableScale, lines);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        List<String> lines = lines();
        draw(context, mc, x, y, scale, lines.isEmpty() ? List.of("§9Umberella §e4m 58s") : lines);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, List<String> lines) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int row = 0;
        for (String line : lines) {
            context.text(mc.font, line, 0, row * (mc.font.lineHeight + 1), 0xFFFFFFFF);
            row++;
        }
        pose.popMatrix();
    }

    public static int[] previewSize(Font font) {
        List<String> lines = lines();
        if (lines.isEmpty()) lines = List.of("§9Umberella §e4m 58s");
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        return new int[] { w, lines.size() * (font.lineHeight + 1) };
    }
}
