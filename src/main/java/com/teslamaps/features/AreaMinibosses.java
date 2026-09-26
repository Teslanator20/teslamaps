package com.teslamaps.features;

// Ported from SkyHanni's features/combat/mobs/AreaMiniBossFeatures.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class AreaMinibosses {

    private record Type(String name, String area, int rgb, String chat, Vec3[] spawns) {}

    private static final Type[] TYPES = {
            new Type("Golden Ghoul", "Hub", 0xFFFF55, "§e", v(-99, 39, -86, -128, 42, -138)),
            new Type("Old Wolf", "Hub", 0xFFAA00, "§6", v(-248, 123, 54, -256, 105, 75, -268, 90, 97, -258, 94, 75, -225, 92, 127)),
            new Type("Soul of the Alpha", "The Park", 0xFFAA00, "§6", v(-381, 56, -94, -394, 63, -52, -386, 50, -2, -396, 58, 29)),
            new Type("Voidling Extremist", "The End", 0xFF55FF, "§d", v(-591, 10, -304, -544, 21, -301, -593, 26, -328, -565, 41, -307, -573, 51, -353)),
            new Type("Millennia-Aged Blaze", "Crimson Isle", 0xAA0000, "§4", v(-292, 97, -999, -232, 77, -951, -304, 73, -952, -281, 82, -1010, -342, 86, -1035)),
    };

    private static Vec3[] v(int... c) {
        Vec3[] out = new Vec3[c.length / 3];
        for (int i = 0; i < out.length; i++) out[i] = new Vec3(c[i * 3] + 0.5, c[i * 3 + 1] + 1.2, c[i * 3 + 2] + 0.5);
        return out;
    }

    private static final java.util.Map<LivingEntity, Type> bosses = new java.util.HashMap<>();
    private static final List<LivingEntity> seen = new ArrayList<>();
    private static Type type;
    private static long lastSpawn;
    private static long cooldown = 11_000;
    private static final List<Vec3> labelPos = new ArrayList<>();
    private static final List<String> labelText = new ArrayList<>();
    private static Object lastLevel;
    private static int ticks;

    private AreaMinibosses() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("AreaMinibosses").section("Combat").when(() -> cfg().areaMinibossHighlight || cfg().areaMinibossTimer)
                .tick(AreaMinibosses::tick)
                .world(AreaMinibosses::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Area Minibosses");
                    b.toggle("Area Miniboss Highlight", () -> c.areaMinibossHighlight, v -> c.areaMinibossHighlight = v)
                            .desc("Glow on Golden Ghoul, Old Wolf, Soul of the Alpha, Voidling Extremist and Millennia-Aged Blaze.")
                            .tags("miniboss", "golden ghoul", "old wolf", "alpha", "voidling", "blaze", "slayer");
                    b.toggle("  Respawn Timer", () -> c.areaMinibossTimer, v -> c.areaMinibossTimer = v)
                            .desc("Countdown at every known spawn point within 15 blocks, from the measured respawn time.");
                });
    }

    private static Type typeOf(String name) {
        for (Type t : TYPES) if (t.name.equals(name)) return t;
        return null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            type = null;
            bosses.clear();
            seen.clear();
        }
        TeslaMapsConfig c = cfg();
        if (++ticks % 5 == 0) scan(mc);
        if (c.areaMinibossHighlight) {
            for (var e : bosses.entrySet()) EntityGlow.mark(e.getKey(), e.getValue().rgb);
        }
        labelPos.clear();
        labelText.clear();
        if (!c.areaMinibossTimer || type == null || !SkyblockUtils.isInArea(type.area)) return;
        long left = cooldown - (System.currentTimeMillis() - lastSpawn);
        String text = left <= 0 ? "§c?" : type.chat + String.format(java.util.Locale.ROOT, "%.1fs", left / 1000.0);
        Vec3 p = mc.player.position();
        for (Vec3 s : type.spawns) {
            if (s.distanceToSqr(p) > 15 * 15) continue;
            labelPos.add(s);
            labelText.add(text);
        }
    }

    private static void scan(Minecraft mc) {
        bosses.keySet().removeIf(e -> !e.isAlive() || e.isRemoved());
        seen.removeIf(e -> !e.isAlive() || e.isRemoved());
        String area = SkyblockUtils.getArea();
        boolean any = false;
        for (Type t : TYPES) if (t.area.equalsIgnoreCase(area)) { any = true; break; }
        if (!any) return;
        for (ArmorStand s : EntityCache.namedArmorStands()) {
            if (s.distanceToSqr(mc.player) > 64 * 64) continue;
            NametagMobs.Info info = NametagMobs.parse(s, false);
            if (info == null) continue;
            Type t = typeOf(info.name());
            if (t == null) continue;
            LivingEntity mob = NametagMobs.mobForTag(s);
            if (mob == null || seen.contains(mob)) continue;
            seen.add(mob);
            bosses.put(mob, t);
            long now = System.currentTimeMillis();
            long diff = now - lastSpawn;
            if (diff >= 5_000 && diff <= 20_000) cooldown = diff;
            lastSpawn = now;
            type = t;
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        for (int i = 0; i < labelPos.size(); i++) {
            ESPRenderer.drawText(pose, labelText.get(i), labelPos.get(i), 1.2f, cam, true, false);
        }
    }
}
