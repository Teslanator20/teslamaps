package com.teslamaps.safari;

// Ported from Sparkling Mutuals' ShywormProjection.kt + LitterbugProjection.kt
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Hidden Shyworms and Litterbugs: where they went under, when they move again, where they were last seen. */
public final class ShywormTracker {

    private static final String SHYWORM_SKIN = "b4287b8a0a642dac535a6ee29459efd17d5cee1eb359e436bc8e7abba3da14b7";
    private static final Set<String> REST_MESSAGES = Set.of(
            "The Shyworm hid back into the ground.",
            "The Shyworm saw a player and fled!",
            "The Shyworm fled because it was startled!");

    private record Mark(AABB box, int color, int textColor, String text) {}

    private static final Shyworms shyworms = new Shyworms();
    private static final Litterbugs litterbugs = new Litterbugs();
    private static final Map<UUID, double[]> lastPos = new HashMap<>();
    private static final Map<UUID, Long> movedAt = new HashMap<>();
    private static final Map<String, Boolean> skins = new HashMap<>();
    private static List<Mark> marks = List.of();
    private static Level level;
    private static int ticks;

    private ShywormTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ShywormTracker").section("Critter Safari").legit().when(() -> cfg().safariShywormTimer)
                .tick(ShywormTracker::tick)
                .world(ShywormTracker::render)
                .serverChat(ShywormTracker::onChat)
                .config("Critter Safari", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Shyworm / Litterbug");
                    b.toggle("Shyworm Tracker", () -> c.safariShywormTimer, v -> c.safariShywormTimer = v).cheat()
                            .desc("Box where a Shyworm went under with an 8s countdown until it moves again (\"moving soon...\"), and where one was last seen")
                            .tags("shyworm", "litterbug", "hidden", "timer");
                    b.toggle("  Last Seen Markers", () -> c.safariShywormMarkers, v -> c.safariShywormMarkers = v);
                    b.toggle("  Litterbug Too", () -> c.safariLitterbugTimer, v -> c.safariLitterbugTimer = v)
                            .desc("Box where a hidden Litterbug comes back up, with its countdown");
                    b.color("  Shyworm Color", () -> c.safariShywormColor, v -> c.safariShywormColor = v);
                    b.color("  Litterbug Color", () -> c.safariLitterbugColor, v -> c.safariLitterbugColor = v);
                });
    }

    private static void reset() {
        shyworms.reset();
        litterbugs.reset();
        lastPos.clear();
        movedAt.clear();
        marks = List.of();
        ticks = 0;
    }

    /** Called for every catch the run tracker sees. */
    public static void onCatch(String critter) {
        long now = System.currentTimeMillis();
        if (critter.equals("Shyworm")) shyworms.unassociatedCapture(now);
        else if (critter.equals("Litterbug")) litterbugs.unassociatedCapture(now);
    }

    private static void onChat(String raw) {
        String s = StringUtil.strip(raw).trim();
        if (!REST_MESSAGES.contains(s)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) shyworms.chat(System.currentTimeMillis(), mc.player.getX(), mc.player.getZ());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            reset();
            level = mc.level;
        }
        if (!SafariData.inSafari()) {
            if (!marks.isEmpty()) marks = List.of();
            return;
        }
        long now = System.currentTimeMillis();
        if (++ticks >= 5) {
            ticks = 0;
            scan(mc, now);
        }
        build(mc, now);
    }

    private static void scan(Minecraft mc, long now) {
        List<ArmorStand> worms = new ArrayList<>();
        for (ArmorStand as : EntityCache.armorStands()) {
            if (as.isRemoved() || !isShyworm(as)) continue;
            if (FloorDropPredictor.biomeAt(as.getX(), as.getZ()) != SafariData.Biome.CAVERN) continue;
            worms.add(as);
        }
        Set<UUID> ids = new HashSet<>();
        for (ArmorStand w : worms) ids.add(w.getUUID());
        shyworms.retain(ids);
        for (ArmorStand w : worms) {
            boolean moving = motion(w, now);
            boolean visible = w.getY() > 2.0 && (moving || labelNear(w, "Shyworm"));
            shyworms.observe(w.getUUID(), w.getX(), w.getY(), w.getZ(), visible, now);
        }
        shyworms.reconcile(now);
        shyworms.reconcileCapture(now, ids);

        Set<UUID> bugs = new HashSet<>();
        if (cfg().safariLitterbugTimer) {
            for (Endermite e : EntityCache.byType(Endermite.class)) {
                if (e.isRemoved()) continue;
                if (FloorDropPredictor.biomeAt(e.getX(), e.getZ()) != SafariData.Biome.HAUNTED) continue;
                bugs.add(e.getUUID());
                litterbugs.observe(e.getUUID(), e.getX(), e.getY(), e.getZ(), now);
            }
        }
        litterbugs.retain(bugs, now);
        lastPos.keySet().removeIf(id -> !ids.contains(id));
        movedAt.keySet().removeIf(id -> !ids.contains(id));
    }

    private static boolean motion(Entity e, long now) {
        double[] prev = lastPos.put(e.getUUID(), new double[]{e.getX(), e.getY(), e.getZ()});
        if (prev != null && Math.max(Math.abs(prev[0] - e.getX()), Math.abs(prev[2] - e.getZ())) > 0.02) movedAt.put(e.getUUID(), now);
        Long at = movedAt.get(e.getUUID());
        return at != null && now - at <= 1500;
    }

    private static boolean labelNear(Entity e, String name) {
        for (ArmorStand as : EntityCache.namedArmorStands()) {
            if (as == e || as.distanceToSqr(e) > 9.0) continue;
            if (as.getCustomName() != null && as.getCustomName().getString().contains(name)) return true;
        }
        return false;
    }

    private static boolean isShyworm(ArmorStand as) {
        ItemStack head = as.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.is(Items.PLAYER_HEAD)) return false;
        ResolvableProfile profile = head.get(DataComponents.PROFILE);
        if (profile == null) return false;
        for (Property p : profile.partialProfile().properties().get("textures")) {
            if (p == null) continue;
            String texture = p.value();
            Boolean known = skins.get(texture);
            if (known != null) return known;
            boolean hit;
            try {
                hit = new String(java.util.Base64.getDecoder().decode(texture), java.nio.charset.StandardCharsets.UTF_8).contains(SHYWORM_SKIN);
            } catch (IllegalArgumentException ex) {
                hit = false;
            }
            if (skins.size() > 256) skins.clear();
            skins.put(texture, hit);
            return hit;
        }
        return false;
    }

    private static void build(Minecraft mc, long now) {
        TeslaMapsConfig c = cfg();
        int wormColor = TeslaMapsConfig.parseColor(c.safariShywormColor) | 0xFF000000;
        int bugColor = TeslaMapsConfig.parseColor(c.safariLitterbugColor) | 0xFF000000;
        List<Mark> out = new ArrayList<>();
        for (Map.Entry<UUID, Shyworms.Worm> e : shyworms.worms.entrySet()) {
            Shyworms.Worm w = e.getValue();
            if (w.missing) continue;
            Double h = shyworms.hiddenHeight(e.getKey());
            Timer t = shyworms.timer(e.getKey(), now);
            if (h == null && t == null) continue;
            AABB box = marker(w.x, h != null ? h : w.y, w.z);
            out.add(t != null ? new Mark(box, wormColor, t.color(), t.text()) : new Mark(box, wormColor, 0, null));
        }
        if (c.safariShywormMarkers) {
            for (Shyworms.Worm w : shyworms.missingMarkers()) {
                out.add(new Mark(marker(w.x, w.height, w.z), wormColor, wormColor, "Shyworm (last seen)"));
            }
        }
        if (c.safariLitterbugTimer) {
            for (Endermite e : EntityCache.byType(Endermite.class)) {
                if (e.isRemoved()) continue;
                Litterbugs.Marker m = litterbugs.hiddenMarker(e.getUUID(), e.getX(), e.getY(), e.getZ(), now);
                if (m != null) out.add(new Mark(marker(e.getX(), m.height(), e.getZ()), bugColor, m.color(), m.text()));
            }
            for (Litterbugs.Location l : litterbugs.missingMarkers(now)) {
                out.add(new Mark(marker(l.x(), l.marker().height(), l.z()), bugColor, l.marker().color(), l.marker().text()));
            }
        }
        marks = out;
    }

    private static AABB marker(double x, double y, double z) {
        return new AABB(x - 0.4, y, z - 0.4, x + 0.4, y + 0.8, z + 0.4);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (marks.isEmpty() || SparklingFocus.focusActive()) return;
        boolean through = !cfg().safariLegitMode;
        for (Mark m : marks) ESPRenderer.drawBoxOutline(pose, m.box(), m.color(), 2.0f, cam, through);
        for (Mark m : marks) {
            if (m.text() == null) continue;
            Vec3 mid = m.box().getCenter();
            ESPRenderer.drawText(pose, m.text(), new Vec3(mid.x, m.box().maxY + 0.2, mid.z), 1.0f, cam, true, through, m.textColor());
        }
    }

    record Timer(String text, int color) {}

    static String countdown(long remaining) {
        long tenths = (remaining + 99) / 100;
        return tenths / 10 + "." + tenths % 10 + "s";
    }

    static int countdownColor(long remaining) {
        if (remaining == 0) return 0xFFFFFFFF;
        if (remaining > 5000) return 0xFF55FF55;
        if (remaining > 2000) return 0xFFFFFF55;
        return 0xFFFF5555;
    }

    static final class Shyworms {

        static final class Worm {
            double x, y, z;
            Double height;
            boolean visible;
            int dx, dz;
            double cornerX, cornerZ;
            long movedAt = Long.MIN_VALUE;
            Long restAt;
            Double groundHeight;
            final double startX, startZ;
            int firstDx, firstDz;
            boolean returnedToStart;
            boolean missing;

            Worm(double x, double y, double z) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.cornerX = x;
                this.cornerZ = z;
                this.startX = x;
                this.startZ = z;
            }
        }

        private record Rest(long at, double playerX, double playerZ) {}

        final Map<UUID, UUID> aliases = new LinkedHashMap<>();
        final Set<UUID> retired = new LinkedHashSet<>();
        final Map<UUID, Worm> worms = new LinkedHashMap<>();
        final List<Rest> rests = new ArrayList<>();
        long discardMissingUntil = Long.MIN_VALUE;

        UUID identity(UUID id) {
            UUID cur = id;
            for (int i = 0; i < 64; i++) {
                UUID next = aliases.get(cur);
                if (next == null) return cur;
                cur = next;
            }
            return cur;
        }

        void unassociatedCapture(long now) {
            discardMissingUntil = now + 1250;
            worms.entrySet().removeIf(e -> e.getValue().missing);
        }

        void reconcileCapture(long now, Set<UUID> active) {
            if (now <= discardMissingUntil) worms.entrySet().removeIf(e -> e.getValue().missing && !active.contains(e.getKey()));
        }

        void observe(UUID id, double x, double y, double z, boolean visible, long now) {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
            if (aliases.containsKey(id) || retired.contains(id) || retired.contains(identity(id))) return;
            if (!worms.containsKey(id) && !visible) return;
            if (!worms.containsKey(id) && y > 2.0) {
                UUID match = null;
                int matches = 0;
                for (Map.Entry<UUID, Worm> e : worms.entrySet()) {
                    Worm w = e.getValue();
                    if (!w.missing || w.height == null) continue;
                    if ((w.x - x) * (w.x - x) + (w.z - z) * (w.z - z) > 2.25) continue;
                    if (Math.abs(w.height - y) > 3.0) continue;
                    match = e.getKey();
                    matches++;
                }
                if (matches == 1) {
                    worms.remove(match);
                    aliases.put(match, id);
                }
            }

            Worm w = worms.computeIfAbsent(id, k -> new Worm(x, y, z));
            if (w.missing) {
                w.restAt = null;
                w.returnedToStart = false;
            }
            w.missing = false;
            double ddx = x - w.x;
            double ddz = z - w.z;
            double step = Math.max(Math.abs(ddx), Math.abs(ddz));
            boolean moved = step > 0.02;
            boolean atStart = Math.abs(x - w.startX) <= 0.15 && Math.abs(z - w.startZ) <= 0.15;
            boolean returning = moved && atStart && (w.firstDx != 0 || w.firstDz != 0);
            if (moved && !atStart) w.returnedToStart = false;

            if (w.restAt != null && y > 2.0 && (moved && !atStart || visible && Math.abs(w.y - 1.0) <= 0.01)) {
                w.restAt = null;
                w.dx = 0;
                w.dz = 0;
                w.cornerX = w.startX;
                w.cornerZ = w.startZ;
            }

            boolean surfaced = visible && y > 2.0;
            if (surfaced && w.restAt == null && !returning && !w.returnedToStart) {
                w.height = y;
                if (w.groundHeight == null) {
                    double best = 40.0;
                    for (double g : new double[]{40.0, 43.0, 60.0}) {
                        if (Math.abs(g - (y + 1.35)) < Math.abs(best - (y + 1.35))) best = g;
                    }
                    w.groundHeight = best;
                }
            }

            if (returning) {
                w.dx = 0;
                w.dz = 0;
                w.cornerX = w.startX;
                w.cornerZ = w.startZ;
                w.returnedToStart = true;
                w.x = x;
                w.y = y;
                w.z = z;
                w.visible = surfaced;
                return;
            }
            if (step > 8.0) {
                w.dx = 0;
                w.dz = 0;
            }
            if (step > 0.02 && step <= 8.0) {
                int ndx = 0, ndz = 0;
                boolean axis = true;
                if (Math.abs(ddx) > Math.abs(ddz) * 3) ndx = ddx > 0.0 ? 1 : -1;
                else if (Math.abs(ddz) > Math.abs(ddx) * 3) ndz = ddz > 0.0 ? 1 : -1;
                else axis = false;
                if (axis) {
                    if ((w.dx != 0 || w.dz != 0) && w.dx * ndz - w.dz * ndx != 1) {
                        if (ndx != w.dx || ndz != w.dz) {
                            w.dx = 0;
                            w.dz = 0;
                            w.x = x;
                            w.y = y;
                            w.z = z;
                            w.visible = surfaced;
                            return;
                        }
                    } else {
                        w.cornerX = w.x;
                        w.cornerZ = w.z;
                    }
                    w.dx = ndx;
                    w.dz = ndz;
                    w.movedAt = now;
                    if (w.firstDx == 0 && w.firstDz == 0) {
                        w.firstDx = ndx;
                        w.firstDz = ndz;
                    }
                }
            }
            w.x = x;
            w.y = y;
            w.z = z;
            w.visible = surfaced;
        }

        void chat(long now, double playerX, double playerZ) {
            if (Double.isFinite(playerX) && Double.isFinite(playerZ)) rests.add(new Rest(now, playerX, playerZ));
        }

        void reconcile(long now) {
            rests.removeIf(r -> now - r.at() < 0 || now - r.at() >= 2001);
            for (Rest rest : new ArrayList<>(rests)) {
                List<Worm> candidates = new ArrayList<>();
                for (Worm w : worms.values()) {
                    boolean down = Math.abs(w.y - 1.0) <= 0.01
                            || (!w.visible || w.returnedToStart)
                            && Math.abs(w.x - w.startX) <= 0.15 && Math.abs(w.z - w.startZ) <= 0.15;
                    if (down && w.restAt == null && w.height != null
                            && (w.movedAt == Long.MIN_VALUE || now - w.movedAt >= 250)) candidates.add(w);
                }
                candidates.sort((a, b) -> Double.compare(dist(rest, a), dist(rest, b)));
                if (candidates.isEmpty()) continue;
                Worm best = candidates.get(0);
                if (candidates.size() <= 1 || Math.abs(dist(rest, candidates.get(1)) - dist(rest, best)) >= 1.0E-4) {
                    best.restAt = rest.at();
                    rests.remove(rest);
                }
            }
        }

        private static double dist(Rest r, Worm w) {
            return (w.x - r.playerX()) * (w.x - r.playerX()) + (w.z - r.playerZ()) * (w.z - r.playerZ());
        }

        Double hiddenHeight(UUID id) {
            Worm w = worms.get(id);
            if (w == null) return null;
            return w.restAt != null || Math.abs(w.y - 1.0) <= 0.01 ? w.height : null;
        }

        Timer timer(UUID id, long now) {
            Worm w = worms.get(id);
            if (w == null || w.restAt == null) return null;
            long remaining = Math.max(8000 - Math.max(now - w.restAt, 0L), 0L);
            return new Timer(remaining == 0 ? "moving soon..." : countdown(remaining), countdownColor(remaining));
        }

        void retain(Set<UUID> ids) {
            for (Map.Entry<UUID, Worm> e : worms.entrySet()) if (!ids.contains(e.getKey())) e.getValue().missing = true;
        }

        List<Worm> missingMarkers() {
            List<Worm> out = new ArrayList<>();
            for (Worm w : worms.values()) if (w.height != null && w.missing) out.add(w);
            return out;
        }

        void reset() {
            worms.clear();
            rests.clear();
            aliases.clear();
            retired.clear();
            discardMissingUntil = Long.MIN_VALUE;
        }
    }

    static final class Litterbugs {

        record Marker(double height, String text, int color) {}
        record Location(UUID id, double x, double y, double z, Marker marker) {}

        private final Map<UUID, double[]> positions = new LinkedHashMap<>();
        private final Set<UUID> missing = new LinkedHashSet<>();
        private final Set<UUID> retired = new LinkedHashSet<>();
        private long discardMissingUntil = Long.MIN_VALUE;
        private final Map<UUID, Double> heights = new LinkedHashMap<>();
        private final Map<UUID, Long> hiddenSince = new LinkedHashMap<>();

        void observe(UUID id, double x, double y, double z, long now) {
            if (retired.contains(id)) return;
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
            positions.put(id, new double[]{x, y, z});
            missing.remove(id);
            if (y > 2.0) heights.put(id, y);
            if (hidden(y)) hiddenSince.putIfAbsent(id, now);
            else hiddenSince.remove(id);
        }

        void retain(Set<UUID> ids, long now) {
            List<UUID> gone = new ArrayList<>();
            for (UUID id : positions.keySet()) if (!ids.contains(id)) gone.add(id);
            missing.addAll(gone);
            if (now <= discardMissingUntil) for (UUID id : gone) forget(id);
        }

        List<Location> missingMarkers(long now) {
            List<Location> out = new ArrayList<>();
            for (UUID id : missing) {
                double[] p = positions.get(id);
                if (p == null) continue;
                Marker m = hiddenMarker(id, p[0], p[1], p[2], now);
                if (m != null) out.add(new Location(id, p[0], p[1], p[2], m));
            }
            return out;
        }

        private void forget(UUID id) {
            positions.remove(id);
            missing.remove(id);
            heights.remove(id);
            hiddenSince.remove(id);
        }

        void unassociatedCapture(long now) {
            discardMissingUntil = now + 1250;
            for (UUID id : new ArrayList<>(missing)) forget(id);
        }

        private static boolean hidden(double y) {
            return Double.isFinite(y) && Math.abs(y - 1) <= 0.01;
        }

        // without a seen surface height: the three rooms whose emerge height is known
        Double hiddenHeight(UUID id, double x, double y, double z) {
            if (!hidden(y)) return null;
            Double h = heights.get(id);
            if (h != null) return h;
            if (13.0 <= x && x <= 19.99 && -66.99 <= z && z <= -66.0) return 73.0;
            Double a = x >= 7.0 && x < 16.0 && z >= -81.0 && z < -66.0 ? 69.0 : null;
            Double b = x >= -11.0 && x < 9.0 && z >= -67.0 && z < -60.0 ? 85.0 : null;
            if (a != null && b == null) return a;
            if (b != null && a == null) return b;
            return null;
        }

        Marker hiddenMarker(UUID id, double x, double y, double z, long now) {
            Double height = hiddenHeight(id, x, y, z);
            if (height == null) return null;
            Long since = hiddenSince.get(id);
            if (since == null) return null;
            long remaining = Math.max(8000 - Math.max(now - since, 0L), 0L);
            return new Marker(height, remaining == 0 ? "Moving soon.." : countdown(remaining), countdownColor(remaining));
        }

        void reset() {
            heights.clear();
            hiddenSince.clear();
            positions.clear();
            missing.clear();
            retired.clear();
            discardMissingUntil = Long.MIN_VALUE;
        }
    }
}
