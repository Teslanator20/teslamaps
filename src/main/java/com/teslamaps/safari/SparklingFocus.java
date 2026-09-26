package com.teslamaps.safari;

// Ported from FrankyAddons' SparklingAlert
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Locale;

public final class SparklingFocus {

    private static final double RADIUS_SQ = 64.0 * 64.0;
    private static final long PARTICLE_TTL = 30L;
    private static final int MAX_HITS = 256;
    private static final String LABEL = "Sparkling Alert";
    private static final int MAUVE = 0xFFCBA6F7;

    private record Hit(double x, double y, double z, long tick) {}

    private static final ArrayDeque<Hit> hits = new ArrayDeque<>();
    private static long ticks = 0;
    private static int scanTimer = 0;
    private static Entity focusEntity = null;
    private static Vec3 focusPos = null;
    private static boolean detected = false;

    private SparklingFocus() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean enabled() {
        TeslaMapsConfig c = cfg();
        return c.safariSparklingAlert && (c.safariSparklingHud || c.safariSparklingFocus);
    }

    public static void register() {
        Feature.of("SparklingFocus").section("Critter Safari").when(SparklingFocus::enabled)
                .tick(SparklingFocus::tick)
                .world(SparklingFocus::render)
                .hud("sparklingfocus", SparklingFocus::renderHud);
        HudEditElement.of("safariSparklingHud", "Sparkling Alert").ctx(HudEditElement.HudCtx.SAFARI).section("Critter Safari")
                .enabled(() -> cfg().safariSparklingAlert && cfg().safariSparklingHud)
                .size(() -> Minecraft.getInstance().font.width(LABEL), () -> 9)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s));
    }

    /** Queue only, evaluated in the tick. */
    public static void onParticlePacket(ClientboundLevelParticlesPacket packet) {
        if (!Minecraft.getInstance().isSameThread() || !enabled()) return;
        if (packet.getParticle().getType() != ParticleTypes.WAX_ON.getType()) return;
        hits.add(new Hit(packet.getX(), packet.getY(), packet.getZ(), ticks));
        if (hits.size() > MAX_HITS) hits.poll();
    }

    /** Focus mode is on screen: other Safari ESP stays hidden meanwhile. */
    public static boolean focusActive() {
        return detected && cfg().safariSparklingFocus && enabled() && cfg().section("Critter Safari");
    }

    private static void clear() {
        hits.clear();
        detected = false;
        focusEntity = null;
        focusPos = null;
    }

    private static void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        if (!SafariData.inSafari() && !cfg().safariSparklingOutside) {
            clear();
            return;
        }
        while (!hits.isEmpty() && ticks - hits.peek().tick() > PARTICLE_TTL) hits.poll();

        if (--scanTimer <= 0) {
            scanTimer = 10;
            focusEntity = scanNames(mc);
        } else if (focusEntity != null && focusEntity.isRemoved()) {
            focusEntity = null;
        }

        Hit closest = null;
        double best = Double.MAX_VALUE;
        for (Hit h : hits) {
            double d = mc.player.distanceToSqr(h.x(), h.y(), h.z());
            if (d <= RADIUS_SQ && d < best) {
                best = d;
                closest = h;
            }
        }
        detected = closest != null || focusEntity != null;
        focusPos = focusEntity == null && closest != null ? new Vec3(closest.x(), closest.y(), closest.z()) : null;
    }

    private static Entity scanNames(Minecraft mc) {
        for (Entity e : EntityCache.all()) {
            if (!e.hasCustomName() || mc.player.distanceToSqr(e) > RADIUS_SQ) continue;
            String n = StringUtil.strip(e.getCustomName().getString()).toLowerCase(Locale.ROOT);
            if (n.contains("sparkling")) return body(e);
        }
        return null;
    }

    // the label is an armor stand, the box goes on the mob under it
    private static Entity body(Entity label) {
        Entity best = label;
        double bestD = 9.0;
        for (Entity e : EntityCache.all()) {
            if (e == label || !mobLike(e)) continue;
            double d = e.position().distanceToSqr(label.position());
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private static boolean mobLike(Entity e) {
        EntityType<?> t = e.getType();
        return t != EntityType.ARMOR_STAND && t != EntityType.INTERACTION && t != EntityType.ITEM_DISPLAY
                && t != EntityType.BLOCK_DISPLAY && t != EntityType.TEXT_DISPLAY && t != EntityType.PLAYER
                && t != EntityType.ITEM;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!detected || !cfg().safariSparklingFocus) return;
        int color = TeslaMapsConfig.parseColor(cfg().colorSafariSparkling) | 0xFF000000;
        AABB box;
        if (focusEntity != null && !focusEntity.isRemoved()) box = ESPRenderer.lerpBox(focusEntity);
        else if (focusPos != null) box = AABB.ofSize(focusPos, 0.5, 0.5, 0.5);
        else return;
        ESPRenderer.drawFilledBox(pose, box, (color & 0x00FFFFFF) | 0x40000000, cam, true);
        ESPRenderer.drawBoxOutline(pose, box, color, 2.0f, cam, true);
        ESPRenderer.drawTracerFromCamera(pose, box.getCenter(), color, cam, true);
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        TeslaMapsConfig c = cfg();
        if (!detected || !c.safariSparklingHud || com.teslamaps.utils.HudVisibility.suppressed()) return;
        draw(g, Minecraft.getInstance(), c.safariSparklingHudX, c.safariSparklingHudY, c.safariSparklingHudScale);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.text(mc.font, LABEL, 0, 0, MAUVE, true);
        pose.popMatrix();
    }
}
