package com.teslamaps.features;

// Concept from Oracle's render/TerritoryTargeting.java + TerritoryBoxRenderer.java: dwell lock + HUD card
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class LookTargeting {

    private static final int LOAD_TICKS = 7;
    private static final double PICK_ANGLE = 3.0;
    private static final double DESELECT_ANGLE = 15.0;
    private static final int RING_DOTS = 32;
    private static final int RING_RADIUS = 7;

    private static final Matrix4f MAT = new Matrix4f();
    private static final Vector4f VEC = new Vector4f();

    private static Entity targeted, locked;
    private static int progress, prevProgress;
    private static Component title;
    private static String distance = "";
    private static int cardWidth;
    private static float sx, sy;

    private LookTargeting() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("LookTargeting").section("Render").when(() -> cfg().lookTargeting)
                .tick(LookTargeting::tick)
                .hudHiddenInInventory("looktargeting", LookTargeting::render)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Look Targeting");
                    b.toggle("Look Targeting", () -> c.lookTargeting, v -> c.lookTargeting = v)
                            .desc("Look at a name tag for a moment: a ring fills, then an info card with name and distance pins next to it. Look away to release.");
                    b.slider("  Range", 8f, 96f, () -> c.lookTargetingRange, v -> c.lookTargetingRange = v, 0);
                    b.toggle("  Leader Line", () -> c.lookTargetingLine, v -> c.lookTargetingLine = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1f);
        double range = cfg().lookTargetingRange;

        Entity hit = null;
        double best = PICK_ANGLE;
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(range),
                x -> x instanceof ArmorStand && x.hasCustomName() && x.isCustomNameVisible())) {
            double a = angle(eye, look, anchor(e, 1f));
            if (a < best && eye.distanceToSqr(e.position()) <= range * range) { best = a; hit = e; }
        }

        if (locked != null) {
            if (locked.isRemoved() || hit != null && hit != locked) {
                reset();
                targeted = hit;
            } else if (hit == null && angle(eye, look, anchor(locked, 1f)) > DESELECT_ANGLE) {
                reset();
            }
        } else if (hit != null && hit == targeted) {
            prevProgress = progress++;
            if (progress >= LOAD_TICKS) {
                locked = hit;
                progress = LOAD_TICKS;
            }
        } else {
            targeted = hit;
            prevProgress = progress = 0;
        }

        if (locked != null) {
            title = locked.getCustomName();
            distance = "§7" + Math.round(eye.distanceTo(locked.position())) + "m away";
            Font f = mc.font;
            cardWidth = Math.max(title == null ? 0 : f.width(title), f.width(distance));
        }
    }

    private static void reset() {
        targeted = locked = null;
        progress = prevProgress = 0;
        title = null;
    }

    private static Vec3 anchor(Entity e, float pt) {
        return e.getPosition(pt).add(0, e.getBbHeight() + 0.25, 0);
    }

    private static double angle(Vec3 eye, Vec3 look, Vec3 p) {
        Vec3 to = p.subtract(eye);
        double len = to.length();
        if (len < 1e-4) return 180;
        double dot = Math.max(-1, Math.min(1, look.dot(to) / len));
        return Math.toDegrees(Math.acos(dot));
    }

    private static boolean project(Minecraft mc, Entity e, float pt, int w, int h) {
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 p = anchor(e, pt).subtract(cam.position());
        cam.getViewRotationProjectionMatrix(MAT);
        VEC.set((float) p.x, (float) p.y, (float) p.z, 1f);
        MAT.transform(VEC);
        if (VEC.w <= 0.01f) return false;
        float nx = VEC.x / VEC.w, ny = VEC.y / VEC.w;
        if (nx < -1.2f || nx > 1.2f || ny < -1.2f || ny > 1.2f) return false;
        sx = (nx + 1f) / 2f * w;
        sy = (1f - ny) / 2f * h;
        return true;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Entity e = locked != null ? locked : targeted;
        if (e == null || locked == null && progress == 0) return;
        float pt = d.getGameTimeDeltaPartialTick(true);
        if (!project(mc, e, pt, g.guiWidth(), g.guiHeight())) return;
        int cx = Math.round(sx), cy = Math.round(sy);

        float frac = locked != null ? 1f
                : Math.min(1f, Math.max(0f, (prevProgress + (progress - prevProgress) * pt) / LOAD_TICKS));
        int dots = Math.round(frac * RING_DOTS);
        int ring = locked != null ? 0xFFFFFFFF : 0xCCFFFFFF;
        for (int i = 0; i < dots; i++) {
            double a = i * Math.PI * 2 / RING_DOTS - Math.PI / 2;
            int x = cx + (int) Math.round(Math.cos(a) * RING_RADIUS);
            int y = cy + (int) Math.round(Math.sin(a) * RING_RADIUS);
            g.fill(x - 1, y - 1, x + 1, y + 1, ring);
        }
        if (locked == null || title == null) return;

        Font f = mc.font;
        int bx = cx + 28, by = cy - 34;
        int bw = cardWidth + 8, bh = f.lineHeight * 2 + 7;
        if (bx + bw > g.guiWidth() - 2) bx = cx - 28 - bw;
        if (by < 2) by = 2;
        if (cfg().lookTargetingLine) dashed(g, cx, cy, bx < cx ? bx + bw : bx, by + bh / 2);
        HudStyle.panel(g, bx, by, bw, bh);
        g.text(f, title, bx + 4, by + 4, 0xFFFFFFFF, true);
        g.text(f, distance, bx + 4, by + 4 + f.lineHeight + 1, 0xFFFFFFFF, true);
    }

    private static void dashed(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        double dx = x1 - x0, dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        for (double t = RING_RADIUS + 2; t < len; t += 5) {
            int x = x0 + (int) Math.round(dx * t / len), y = y0 + (int) Math.round(dy * t / len);
            g.fill(x, y, x + 2, y + 1, 0xCCFFFFFF);
        }
    }
}
