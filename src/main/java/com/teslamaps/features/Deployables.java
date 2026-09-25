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
        net.minecraft.world.item.ItemStack icon = net.minecraft.world.item.ItemStack.EMPTY;
        java.lang.ref.WeakReference<ArmorStand> iconEntity;
        long iconTry;
        double x, y, z;
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
    // the name must be plain words - digits or ':' mean it is something else entirely,
    // like the mayor cycle's "Opens in: 1d 6h 20m 27s"
    private static final Pattern ANY_TIMER = Pattern.compile(
            "^(?<name>[A-Za-z][A-Za-z'\\- ]*?) (?:(?<min>\\d+)m )?(?<sec>\\d+)s$");
    // no deployable runs anywhere near an hour
    private static final int MAX_SECONDS = 3600;
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
        if (!c.deployableOverlay && !c.umbrellaWarning && !c.deployableHideParticles) return;

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

        for (Entity entity : com.teslamaps.utils.EntityCache.armorStands()) {
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
            if (seconds > MAX_SECONDS) continue;

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
                t.x = stand.getX(); t.y = stand.getY(); t.z = stand.getZ();
                if (c.deployableIconMode && t.icon.isEmpty() && now - t.iconTry > 1000) {
                    t.iconTry = now;
                    ArmorStand head = headBelow(mc, stand);
                    if (head != null) {
                        t.icon = head.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).copy();
                        t.iconEntity = new java.lang.ref.WeakReference<>(head);
                    }
                }
            }
        }

        for (Track t : tracks.values()) {
            if (t.active && (now >= t.end || now - t.lastSeenAt > SEEN_TIMEOUT_MS)) t.active = false;
        }
        buildIcon(c);
    }

    // the timer tag floats above the stand that wears the orb/lantern head
    private static ArmorStand headBelow(Minecraft mc, ArmorStand tag) {
        var box = tag.getBoundingBox().expandTowards(0, -3, 0).inflate(0.6, 0, 0.6);
        for (ArmorStand s : mc.level.getEntitiesOfClass(ArmorStand.class, box)) {
            if (!s.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) return s;
        }
        return null;
    }

    private static String iconName = null;
    private static String iconTime = "";
    private static net.minecraft.world.item.ItemStack iconStack = net.minecraft.world.item.ItemStack.EMPTY;
    private static java.lang.ref.WeakReference<ArmorStand> iconEntity;

    // one deployable, the strongest in range, like Devonian/Odin
    private static void buildIcon(TeslaMapsConfig c) {
        iconName = null;
        if (!c.deployableIconMode || !c.deployableOverlay) return;
        String bestKey = null;
        int bestRank = Integer.MAX_VALUE;
        for (Map.Entry<String, Track> e : tracks.entrySet()) {
            if (!e.getValue().active) continue;
            Deployable known = byName(e.getKey());
            if (!visible(c, known)) continue;
            int r = rank(known);
            if (r < bestRank) { bestRank = r; bestKey = e.getKey(); }
        }
        if (bestKey == null) return;
        Deployable known = byName(bestKey);
        iconName = known != null ? known.displayName : "§b" + bestKey;
        iconTime = "§e" + format(Math.max(0, secondsLeft(bestKey)));
        iconStack = tracks.get(bestKey).icon;
        iconEntity = tracks.get(bestKey).iconEntity;
    }

    private static int rank(Deployable d) {
        if (d == null) return 100;
        return switch (d) {
            case PLASMAFLUX -> 0;
            case SOS_FLARE -> 1;
            case OVERFLUX -> 2;
            case ALERT_FLARE -> 3;
            case MANA_FLUX -> 4;
            case WARNING_FLARE -> 5;
            case RADIANT -> 6;
            case UMBERELLA -> 7;
            case BLACK_HOLE -> 8;
            default -> 20 - d.tier;
        };
    }

    private static final java.util.Set<Integer> ORB_DUST = java.util.Set.of(0x6CC7EB, 0xBC2525, 0x3B0E37, 0x6A3294, 0x0D0606);

    /** Main thread. The orbs' own HAPPY_VILLAGER / DUST / FLAME effects, matched like Devonian does. */
    public static boolean hideParticle(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket p) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.deployableHideParticles || !cfg.section("Skills")) return false;
        if (!p.alwaysShow() || !p.isOverrideLimiter()) return false;
        var type = p.getParticle().getType();
        if (type == net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER) {
            if (p.getMaxSpeed() != 0f) return false;
            if (p.getCount() == 4) return p.getXDist() == 0.3f && p.getYDist() == 0.2f && p.getZDist() == 0.3f;
            if (p.getCount() != 1 || p.getXDist() != 0f || p.getYDist() != 0f || p.getZDist() != 0f) return false;
            for (Track t : tracks.values()) {
                if (t.active && Math.abs(t.x - p.getX()) < 2 && Math.abs(t.y - 1 - p.getY()) < 3 && Math.abs(t.z - p.getZ()) < 2) return true;
            }
            return false;
        }
        if (type == net.minecraft.core.particles.ParticleTypes.DUST) {
            if (p.getCount() != 0 || p.getMaxSpeed() != 1f) return false;
            if (!(p.getParticle() instanceof net.minecraft.core.particles.DustParticleOptions o) || o.getScale() != 1f) return false;
            var col = o.getColor();
            int rgb = (Math.round(col.x() * 255f) << 16) | (Math.round(col.y() * 255f) << 8) | Math.round(col.z() * 255f);
            return ORB_DUST.contains(rgb);
        }
        if (type == net.minecraft.core.particles.ParticleTypes.FLAME) {
            return p.getCount() == 11 && p.getMaxSpeed() == 0.35f
                    && p.getXDist() == 1f && p.getYDist() == 1f && p.getZDist() == 1f;
        }
        return false;
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
        t.x = stand.getX(); t.y = stand.getY() + 1; t.z = stand.getZ();
        if (t.icon.isEmpty()) {
            t.icon = stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).copy();
            t.iconEntity = new java.lang.ref.WeakReference<>(stand);
        }
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
        iconName = null;
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
            if (!visible(c, known)) continue;
            out.add((known == null ? "§b" + e.getKey() : known.displayName) + " §e" + format(left));
        }
        return out;
    }

    private static boolean visible(TeslaMapsConfig c, Deployable known) {
        if (known == null) return c.deployableOthers;
        if (!shows(c, known.type)) return false;
        // a Radiant next to an Overflux is just noise
        return !(c.deployableHighestTierOnly && outranked(known));
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

        if (c.deployableIconMode) {
            if (iconName != null) drawIcon(context, mc, c.deployableX, c.deployableY, c.deployableScale, iconStack, iconName, iconTime);
            return;
        }
        List<String> lines = lines();
        if (lines.isEmpty()) return;
        draw(context, mc, c.deployableX, c.deployableY, c.deployableScale, lines);
    }

    private static final String ICON_SAMPLE_NAME = "§5Overflux";
    private static final String ICON_SAMPLE_TIME = "§e42s";

    private static void drawIcon(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale,
                                 net.minecraft.world.item.ItemStack stack, String name, String time) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int w = 36 + Math.max(mc.font.width(name), mc.font.width(time));
        com.teslamaps.render.gui.HudStyle.panel(g, -2, -2, w + 4, 36);
        ArmorStand orb = TeslaMapsConfig.get().deployableOrb3d && iconEntity != null && name == iconName ? iconEntity.get() : null;
        if (orb != null && !orb.isRemoved()) {
            pose.popMatrix();
            drawOrb(g, orb, x, y, scale);
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(scale, scale);
        } else if (!stack.isEmpty()) {
            pose.pushMatrix();
            pose.scale(2f, 2f);
            g.item(stack, 0, 0);
            pose.popMatrix();
        }
        g.text(mc.font, name, 36, 6, 0xFFFFFFFF, true);
        g.text(mc.font, time, 36, 18, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    // spinning head render, the way Devonian draws it
    private static void drawOrb(GuiGraphicsExtractor g, ArmorStand ent, int x, int y, float scale) {
        float yaw = (float) ((System.currentTimeMillis() % 1750L) / 1750.0 * 2.0 * Math.PI);
        org.joml.Quaternionf camRot = new org.joml.Quaternionf().rotateX(-0.38397244f).rotateY(yaw);
        org.joml.Quaternionf entRot = new org.joml.Quaternionf().rotateZ((float) Math.PI).mul(camRot);
        float bodyRot = ent.yBodyRot, yRot = ent.getYRot(), xRot = ent.getXRot(), headO = ent.yHeadRotO, head = ent.yHeadRot;
        ent.yBodyRot = 0f;
        ent.setYRot(0f);
        ent.setXRot(0f);
        ent.yHeadRotO = 0f;
        ent.yHeadRot = 0f;
        try {
            float size = 32f * scale;
            float entScale = ent.getScale();
            var state = net.minecraft.client.gui.screens.inventory.InventoryScreen.extractRenderState(ent);
            g.entity(state, 0.8f * size / entScale, new org.joml.Vector3f(0f, 1.6f * entScale, 0f), entRot, camRot,
                    x, y, (int) Math.ceil(x + size), (int) Math.ceil(y + size));
        } finally {
            ent.yBodyRot = bodyRot;
            ent.setYRot(yRot);
            ent.setXRot(xRot);
            ent.yHeadRotO = headO;
            ent.yHeadRot = head;
        }
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        if (TeslaMapsConfig.get().deployableIconMode) {
            if (iconName != null) drawIcon(context, mc, x, y, scale, iconStack, iconName, iconTime);
            else drawIcon(context, mc, x, y, scale,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD), ICON_SAMPLE_NAME, ICON_SAMPLE_TIME);
            return;
        }
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
        if (TeslaMapsConfig.get().deployableIconMode) {
            String n = iconName != null ? iconName : ICON_SAMPLE_NAME;
            String t = iconName != null ? iconTime : ICON_SAMPLE_TIME;
            return new int[] { 36 + Math.max(font.width(n), font.width(t)), 32 };
        }
        List<String> lines = lines();
        if (lines.isEmpty()) lines = List.of("§9Umberella §e4m 58s");
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        return new int[] { w, lines.size() * (font.lineHeight + 1) };
    }
}
