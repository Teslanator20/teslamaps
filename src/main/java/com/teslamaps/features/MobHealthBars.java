package com.teslamaps.features;

// After Skyblocker's skyblock/HealthBars.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A colored bar under every mob nametag that carries "a/b❤" (or just "a❤", relative to the first value seen). */
public final class MobHealthBars {

    private static final Pattern RATIO = Pattern.compile("([\\d,.]+[kKmMbBtT]?)/([\\d,.]+[kKmMbBtT]?)❤");
    private static final Pattern ONLY = Pattern.compile("([\\d,.]+[kKmMbBtT]?)❤");

    private static final class Bar {
        String name;
        double start;
        float ratio = -1;
    }

    private record Shown(ArmorStand stand, float ratio) {}

    private static final Map<ArmorStand, Bar> bars = new IdentityHashMap<>();
    private static List<Shown> shown = List.of();
    private static int ticks;

    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();
    private static final float[] QUAD = new float[12];

    private MobHealthBars() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MobHealthBars").section("Combat").when(() -> cfg().mobHealthBars)
                .tick(MobHealthBars::tick)
                .world(MobHealthBars::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Mob Health Bars");
                    b.toggle("Mob Health Bars", () -> c.mobHealthBars, v -> c.mobHealthBars = v)
                            .desc("A bar under every mob nametag with a health value, colored full / half / empty")
                            .tags("health", "hp", "bar", "nametag");
                    b.toggle("  Hide At Full Health", () -> c.mobHealthBarsHideFull, v -> c.mobHealthBarsHideFull = v);
                    b.toggle("  Mobs Without Max HP", () -> c.mobHealthBarsHealthOnly, v -> c.mobHealthBarsHealthOnly = v)
                            .desc("Tags showing only \"a❤\": the first value seen counts as full");
                    b.slider("  Bar Size", 0.5f, 3f, () -> c.mobHealthBarsScale, v -> c.mobHealthBarsScale = v, 1);
                    b.color("  Full Color", () -> c.colorHealthBarFull, v -> c.colorHealthBarFull = v);
                    b.color("  Half Color", () -> c.colorHealthBarHalf, v -> c.colorHealthBarHalf = v);
                    b.color("  Empty Color", () -> c.colorHealthBarEmpty, v -> c.colorHealthBarEmpty = v);
                });
    }

    private static void tick() {
        if (ticks++ % 2 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInSkyblock()) {
            bars.clear();
            shown = List.of();
            return;
        }
        TeslaMapsConfig c = cfg();
        bars.keySet().removeIf(ArmorStand::isRemoved);
        List<Shown> out = new ArrayList<>();
        for (ArmorStand s : EntityCache.namedArmorStands()) {
            if (s.isRemoved() || !s.isInvisible() || !s.isCustomNameVisible() || s.getCustomName() == null) continue;
            if (s.distanceToSqr(mc.player) > 2304) continue;
            String name = s.getCustomName().getString();
            if (name.indexOf('❤') < 0) continue;
            Bar bar = bars.get(s);
            if (bar == null) {
                bar = new Bar();
                bars.put(s, bar);
            }
            if (!name.equals(bar.name)) {
                bar.name = name;
                bar.ratio = parse(StringUtil.strip(name), bar, c.mobHealthBarsHealthOnly);
            }
            if (bar.ratio < 0) continue;
            if (c.mobHealthBarsHideFull && bar.ratio >= 1f) continue;
            out.add(new Shown(s, bar.ratio));
        }
        shown = out;
    }

    private static float parse(String name, Bar bar, boolean healthOnly) {
        Matcher m = RATIO.matcher(name);
        if (m.find()) {
            double cur = number(m.group(1)), max = number(m.group(2));
            return max > 0 ? (float) Math.max(0, Math.min(1, cur / max)) : -1;
        }
        if (!healthOnly) return -1;
        m = ONLY.matcher(name);
        if (!m.find()) return -1;
        double cur = number(m.group(1));
        if (bar.start <= 0 || cur > bar.start) bar.start = cur;
        return bar.start > 0 ? (float) (cur / bar.start) : -1;
    }

    private static double number(String s) {
        s = s.replace(",", "");
        double mult = 1;
        char last = Character.toLowerCase(s.charAt(s.length() - 1));
        switch (last) {
            case 'k' -> mult = 1e3;
            case 'm' -> mult = 1e6;
            case 'b' -> mult = 1e9;
            case 't' -> mult = 1e12;
            default -> { }
        }
        if (mult > 1) s = s.substring(0, s.length() - 1);
        try {
            return Double.parseDouble(s) * mult;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<Shown> l = shown;
        if (l.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        Quaternionf rot = mc.gameRenderer.getMainCamera().rotation();
        rot.transform(RIGHT.set(1, 0, 0));
        rot.transform(UP.set(0, 1, 0));
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float w = c.mobHealthBarsScale, h = w * 0.1f;
        int full = TeslaMapsConfig.parseColor(c.colorHealthBarFull), half = TeslaMapsConfig.parseColor(c.colorHealthBarHalf),
                empty = TeslaMapsConfig.parseColor(c.colorHealthBarEmpty);
        for (int i = 0; i < l.size(); i++) {
            Shown s = l.get(i);
            if (s.stand.isRemoved()) continue;
            Vec3 p = s.stand.getEyePosition(pt).add(0, 0.25 - h, 0);
            Vec3 toCam = cam.subtract(p).normalize().scale(0.01);
            quad(p, -w / 2, w / 2, 0, h, 0, 0, 0);
            ESPRenderer.drawQuad(pose, QUAD, 0x90000000, cam);
            quad(p, -w / 2, -w / 2 + w * s.ratio, 0, h, toCam.x, toCam.y, toCam.z);
            ESPRenderer.drawQuad(pose, QUAD, 0xFF000000 | mix(s.ratio, empty, half, full), cam);
        }
    }

    private static void quad(Vec3 p, float x0, float x1, float y0, float y1, double ox, double oy, double oz) {
        corner(0, p, x0, y0, ox, oy, oz);
        corner(1, p, x1, y0, ox, oy, oz);
        corner(2, p, x1, y1, ox, oy, oz);
        corner(3, p, x0, y1, ox, oy, oz);
    }

    private static void corner(int i, Vec3 p, float x, float y, double ox, double oy, double oz) {
        QUAD[i * 3] = (float) (p.x + RIGHT.x * x + UP.x * y + ox);
        QUAD[i * 3 + 1] = (float) (p.y + RIGHT.y * x + UP.y * y + oy);
        QUAD[i * 3 + 2] = (float) (p.z + RIGHT.z * x + UP.z * y + oz);
    }

    private static int mix(float t, int empty, int half, int full) {
        if (t < 0.5f) return lerp(t * 2, empty, half);
        return lerp((t - 0.5f) * 2, half, full);
    }

    private static int lerp(float t, int a, int b) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
