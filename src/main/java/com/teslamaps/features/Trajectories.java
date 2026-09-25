package com.teslamaps.features;

// Ported from shizo's module/impl/render/Trajectories + OdinClient's Trajectories.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public final class Trajectories {

    private static final int NONE = 0, BOW = 1, PEARL = 2;
    private static int held = NONE;
    private static boolean terminator;

    private Trajectories() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("Trajectories").section("Render").when(() -> cfg().trajectories)
                .tick(Trajectories::tick)
                .world(Trajectories::render)
                .config("Render", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Trajectories");
                    b.toggle("Trajectories", () -> c.trajectories, v -> c.trajectories = v)
                            .desc("Shows where your arrow or ender pearl will fly and land.");
                    b.toggle("  Bows", () -> c.trajBows, v -> c.trajBows = v);
                    b.toggle("  Terminator 3 Arrows", () -> c.trajTerminator, v -> c.trajTerminator = v);
                    b.toggle("  Ender Pearls", () -> c.trajPearls, v -> c.trajPearls = v);
                    b.toggle("  Show Line", () -> c.trajLines, v -> c.trajLines = v);
                    b.toggle("  Show Impact Box", () -> c.trajBoxes, v -> c.trajBoxes = v);
                    b.toggle("  Show Impact Plane", () -> c.trajPlane, v -> c.trajPlane = v);
                    b.toggle("  Mark Hit Entities", () -> c.trajEntities, v -> c.trajEntities = v);
                    b.toggle("  Through Walls", () -> c.trajThroughWalls, v -> c.trajThroughWalls = v);
                    b.slider("  Range (ticks)", 10f, 120f, () -> (float) c.trajRange, v -> c.trajRange = Math.round(v));
                    b.slider("  Line Width", 1f, 5f, () -> c.trajLineWidth, v -> c.trajLineWidth = v, 1);
                    b.color("  Color", () -> c.trajColor, v -> c.trajColor = v);
                });
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        ItemStack s = Minecraft.getInstance().player.getMainHandItem();
        held = NONE;
        terminator = false;
        if (s.isEmpty()) return;
        if (c.trajBows && s.getItem() instanceof BowItem) {
            held = BOW;
            terminator = c.trajTerminator && ItemUtil.skyblockId(s).equals("TERMINATOR");
        } else if (c.trajPearls && s.is(Items.ENDER_PEARL)) {
            String id = ItemUtil.skyblockId(s);
            if (!id.contains("SPIRIT_LEAP")) held = PEARL;
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (held == NONE) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        TeslaMapsConfig c = cfg();
        int color = TeslaMapsConfig.parseColor(c.trajColor);
        if ((color >>> 24) == 0) color |= 0xFF000000;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        if (held == PEARL) {
            simulate(pose, cam, p, pt, 0f, true, 1.5, c, color);
            return;
        }
        double power = 1.0;
        if (p.isUsingItem()) power = BowItem.getPowerForTime(p.getTicksUsingItem());
        if (power < 0.1) return;
        simulate(pose, cam, p, pt, 0f, false, power * 3.0, c, color);
        if (terminator) {
            simulate(pose, cam, p, pt, -5f, false, power * 3.0, c, color);
            simulate(pose, cam, p, pt, 5f, false, power * 3.0, c, color);
        }
    }

    private static final List<Vec3> PTS = new java.util.ArrayList<>();
    private static float[] widths = new float[0];

    private static void simulate(PoseStack pose, Vec3 cam, LocalPlayer p, float pt, float yawOffset, boolean pearl,
                                 double speed, TeslaMapsConfig c, int color) {
        PTS.clear();
        try {
            step(pose, cam, p, pt, yawOffset, pearl, speed, c, color);
        } finally {
            int n = PTS.size();
            if (c.trajLines && n >= 2) {
                if (widths.length < n) widths = new float[n * 2];
                java.util.Arrays.fill(widths, 0, n, c.trajLineWidth);
                ESPRenderer.drawPolyline(pose, PTS, widths, color, cam, c.trajThroughWalls);
            }
            PTS.clear();
        }
    }

    private static void step(PoseStack pose, Vec3 cam, LocalPlayer p, float pt, float yawOffset, boolean pearl,
                             double speed, TeslaMapsConfig c, int color) {
        Minecraft mc = Minecraft.getInstance();
        double yawRad = Math.toRadians(p.getYRot());
        double x = p.xo + (p.getX() - p.xo) * pt - Math.cos(yawRad) * 0.16;
        double y = p.yo + (p.getY() - p.yo) * pt + p.getEyeHeight() - 0.1;
        double z = p.zo + (p.getZ() - p.zo) * pt - Math.sin(yawRad) * 0.16;
        Vec3 look = Vec3.directionFromRotation(p.getXRot(), p.getYRot() + yawOffset);
        double vx = look.x * speed, vy = look.y * speed, vz = look.z * speed;
        boolean through = c.trajThroughWalls;
        float width = c.trajLineWidth;
        Vec3 pos = new Vec3(x, y, z);
        PTS.add(pos);
        for (int i = 0; i < c.trajRange; i++) {
            Vec3 next = new Vec3(pos.x + vx, pos.y + vy, pos.z + vz);
            BlockHitResult bh = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            Vec3 end = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation() : next;
            Entity hitEntity = null;
            if (!pearl && c.trajEntities) {
                double best = Double.MAX_VALUE;
                List<Entity> list = mc.level.getEntities(p, new AABB(pos, end).inflate(1.0),
                        e -> !(e instanceof Projectile) && !(e instanceof ArmorStand) && !e.isSpectator());
                for (Entity e : list) {
                    Optional<Vec3> h = e.getBoundingBox().inflate(e.getPickRadius()).clip(pos, end);
                    if (h.isEmpty()) continue;
                    double d = pos.distanceToSqr(h.get());
                    if (d < best) { best = d; hitEntity = e; end = h.get(); }
                }
            }
            PTS.add(end);
            if (hitEntity != null) {
                AABB box = hitEntity.getBoundingBox().move(
                        (hitEntity.xo - hitEntity.getX()) * (1 - pt), (hitEntity.yo - hitEntity.getY()) * (1 - pt),
                        (hitEntity.zo - hitEntity.getZ()) * (1 - pt));
                ESPRenderer.drawBoxOutline(pose, box, color, width, cam, through);
                ESPRenderer.drawFilledBox(pose, box, alpha(color, 0.3f), cam, through);
                return;
            }
            if (bh.getType() == HitResult.Type.BLOCK) {
                Vec3 h = bh.getLocation();
                if (c.trajBoxes) {
                    double s = 0.15;
                    AABB box = new AABB(h.x - s, h.y - s, h.z - s, h.x + s, h.y + s, h.z + s);
                    ESPRenderer.drawBoxOutline(pose, box, color, width, cam, through);
                    ESPRenderer.drawFilledBox(pose, box, alpha(color, 0.3f), cam, through);
                }
                if (c.trajPlane) {
                    double s = 0.3, t = 0.02;
                    Direction.Axis a = bh.getDirection().getAxis();
                    AABB plane = switch (a) {
                        case Y -> new AABB(h.x - s, h.y - t, h.z - s, h.x + s, h.y + t, h.z + s);
                        case Z -> new AABB(h.x - s, h.y - s, h.z - t, h.x + s, h.y + s, h.z + t);
                        default -> new AABB(h.x - t, h.y - s, h.z - s, h.x + t, h.y + s, h.z + s);
                    };
                    ESPRenderer.drawFilledBox(pose, plane, alpha(color, 0.5f), cam, through);
                }
                return;
            }
            pos = next;
            vx *= 0.99;
            vz *= 0.99;
            vy = vy * 0.99 - (pearl ? 0.03 : 0.05);
            if (pos.y < mc.level.getMinY() - 16) return;
        }
    }

    private static int alpha(int color, float f) {
        int a = Math.round(((color >>> 24) & 0xFF) * f);
        return (a << 24) | (color & 0xFFFFFF);
    }
}
