package com.teslamaps.features;

// Ported from SkyHanni's features/fishing/SeaCreatureLootshareSphere.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SeaCreatureList;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class LootshareSphere {

    private static final double RANGE = 30.0;
    private static final int LAT = 7, LON = 8, SEG = 32;
    private static final float[] WIDTHS = new float[SEG + 1];
    static { Arrays.fill(WIDTHS, 1.5f); }

    private record Sphere(Vec3 pos, int color, List<List<Vec3>> meridians) {}

    private static List<Sphere> spheres = List.of();
    private static final Map<String, String> NAME_BY_TAG = new HashMap<>();
    private static final IntOpenHashSet own = new IntOpenHashSet();
    private static final IntOpenHashSet seen = new IntOpenHashSet();
    private static String pendingName;
    private static long pendingAt;
    private static Object lastLevel;
    private static int timer;

    private LootshareSphere() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("LootshareSphere").section("Fishing").when(() -> cfg().lootshareSphere)
                .tick(LootshareSphere::tick)
                .world(LootshareSphere::render)
                .serverChat(LootshareSphere::onChat)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Lootshare");
                    b.toggle("Lootshare Range Sphere", () -> c.lootshareSphere, v -> c.lootshareSphere = v)
                            .desc("30-block wireframe sphere around rare sea creatures. Green = your own catch or you are in range, else white.")
                            .tags("lootshare", "sphere", "range", "sea creature", "fishing");
                    b.dropdown("  Creatures", new String[] { "Rare Flag", "Epic+", "Legendary+" },
                            () -> c.lootshareSphereRarity, v -> c.lootshareSphereRarity = v);
                });
    }

    private static void onChat(String text) {
        String name = SeaCreatureList.nameFor(StringUtil.stripTrim(text));
        if (name == null) return;
        pendingName = name;
        pendingAt = System.currentTimeMillis();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            own.clear();
            seen.clear();
            spheres = List.of();
        }
        if (timer++ % 4 != 0) return;
        if (!SeaCreatureList.ready() || !SkyblockUtils.inSkyblockCached()) {
            spheres = List.of();
            return;
        }
        boolean pending = pendingName != null && System.currentTimeMillis() - pendingAt < 3000;
        Vec3 me = mc.player.position();
        List<Sphere> next = new ArrayList<>();
        IntOpenHashSet nowSeen = new IntOpenHashSet();
        for (Entity tag : mc.level.getEntities((Entity) null, mc.player.getBoundingBox().inflate(64),
                x -> x instanceof ArmorStand && x.hasCustomName())) {
            Component cn = tag.getCustomName();
            String name = cn == null ? null : creature(cn.getString());
            if (name == null || !wanted(name)) continue;
            int id = tag.getId();
            nowSeen.add(id);
            if (!seen.contains(id) && pending && name.equals(pendingName) && tag.position().distanceTo(me) < 16) {
                own.add(id);
                pendingName = null;
            }
            LivingEntity mob = mobUnder(mc, tag);
            Vec3 pos = mob != null ? mob.position() : tag.position().subtract(0, 1.5, 0);
            int near = 0;
            for (Sphere s : next) if (s.pos.distanceTo(pos) < 10) near++;
            if (near >= 2) continue;
            boolean green = own.contains(id) || pos.distanceTo(me) < RANGE;
            next.add(new Sphere(pos, green ? 0xFF55FF55 : 0xFFFFFFFF, meridians(pos)));
        }
        seen.clear();
        seen.addAll(nowSeen);
        own.retainAll(nowSeen);
        spheres = next;
    }

    private static boolean wanted(String name) {
        SeaCreatureList.Info info = SeaCreatureList.info(name);
        if (info == null) return false;
        return switch (cfg().lootshareSphereRarity) {
            case "Epic+" -> rank(info.rarity()) >= 3;
            case "Legendary+" -> rank(info.rarity()) >= 4;
            default -> info.rare();
        };
    }

    private static int rank(String r) {
        return switch (r) {
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            default -> 0;
        };
    }

    // "[Lv15] Sea Walker 1,500/1,500❤"
    private static String creature(String raw) {
        String cached = NAME_BY_TAG.get(raw);
        if (cached != null) return cached.isEmpty() ? null : cached;
        if (NAME_BY_TAG.size() > 1024) NAME_BY_TAG.clear();
        String found = lookup(StringUtil.strip(raw).trim());
        NAME_BY_TAG.put(raw, found == null ? "" : found);
        return found;
    }

    private static String lookup(String tag) {
        if (!tag.endsWith("❤")) return null;
        int br = tag.indexOf("] ");
        if (br >= 0) tag = tag.substring(br + 2);
        int sp = tag.lastIndexOf(' ');
        if (sp <= 0) return null;
        String name = tag.substring(0, sp).trim();
        for (int i = 0; i < 3 && !name.isEmpty(); i++) {
            if (SeaCreatureList.info(name) != null) return name;
            int next = name.indexOf(' ');
            if (next < 0) break;
            name = name.substring(next + 1);
        }
        return null;
    }

    private static LivingEntity mobUnder(Minecraft mc, Entity tag) {
        AABB box = new AABB(tag.getX() - 1, tag.getY() - 4, tag.getZ() - 1, tag.getX() + 1, tag.getY() + 0.5, tag.getZ() + 1);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities(tag, box, x -> x instanceof LivingEntity && !(x instanceof ArmorStand))) {
            if (e == mc.player || e instanceof Player p && p.getUUID().version() == 4) continue;
            double dx = e.getX() - tag.getX(), dz = e.getZ() - tag.getZ();
            double d = dx * dx + dz * dz;
            if (d < bestD) { bestD = d; best = (LivingEntity) e; }
        }
        return best;
    }

    private static List<List<Vec3>> meridians(Vec3 c) {
        List<List<Vec3>> out = new ArrayList<>(LON);
        for (int j = 0; j < LON; j++) {
            double phi = Math.PI * j / LON;
            double cx = Math.cos(phi), cz = Math.sin(phi);
            List<Vec3> pts = new ArrayList<>(SEG + 1);
            for (int i = 0; i <= SEG; i++) {
                double t = Math.PI * 2 * i / SEG;
                double r = Math.cos(t) * RANGE;
                pts.add(new Vec3(c.x + cx * r, c.y + Math.sin(t) * RANGE, c.z + cz * r));
            }
            out.add(pts);
        }
        return out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<Sphere> list = spheres;
        for (Sphere s : list) {
            for (int i = 1; i <= LAT; i++) {
                double t = Math.PI * i / (LAT + 1) - Math.PI / 2;
                ESPRenderer.drawCircle(pose, s.pos.x, s.pos.y + Math.sin(t) * RANGE, s.pos.z, Math.cos(t) * RANGE,
                        s.color, 1.5f, cam);
            }
            for (List<Vec3> m : s.meridians) ESPRenderer.drawPolyline(pose, m, WIDTHS, s.color, cam, false);
        }
    }
}
