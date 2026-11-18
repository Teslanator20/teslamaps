package com.teslamaps.safari.capsule;

// Ported from Sparkling Mutuals' safari/shots/preview (CapsulePreview, CapsulePreviewRules/State,
// CapsulePreviewFrameBuilder, CapsulePreviewRenderer)
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.TeslaRenderLayers;
import com.teslamaps.safari.SafariData;
import com.teslamaps.safari.capsule.CapsuleContactSolver.Kind;
import com.teslamaps.safari.capsule.CapsuleContactSolver.Target;
import com.teslamaps.safari.capsule.CapsuleContactSolver.Trace;
import com.teslamaps.safari.capsule.CapsulePathModel.Curve;
import com.teslamaps.utils.ItemUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import com.teslamaps.render.MultiBufferSource;
import com.teslamaps.render.WorldBuffers;
import net.minecraft.world.phys.Vec3;

public final class CapsulePreview {

    private static final String ORDINARY = "CRITTER_CAPSULE";
    private static final String MASTERFUL = "MASTERFUL_CRITTER_CAPSULE";

    private static ClientLevel world;
    private static CapsuleContactScene scene;
    private static List<Target> targets = List.of();
    private static boolean available;
    private static boolean estimate;
    private static long tickedAt = Long.MIN_VALUE;
    private static long revision;

    private static long traceRevision = -1;
    private static boolean traceEstimate;
    private static ShotVector traceEye, traceAim;
    private static Trace trace;
    private static List<ContactSurfaceBox> patch;
    private static boolean dashed;
    private static ShotVector meshCamera;
    private static List<CapsuleGuide.Line> lines = List.of();

    private CapsulePreview() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CapsulePathPreview").section("Critter Safari").legit()
                .when(() -> cfg().safariCapsulePath)
                .tick(CapsulePreview::tick)
                .world(CapsulePreview::render)
                .config("Critter Safari", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Capsule Path Preview", () -> c.safariCapsulePath, v -> c.safariCapsulePath = v).cheat()
                            .desc("Draws the Critter Capsule's throw arc while you hold one, up to 64 blocks. A cyan cross marks a critter it would hit first, an amber ring the block or Rockmite mound. Dashed where the arc is a guess (moving, masterful, water, outside pitch -10..1).")
                            .tags("capsule", "throw", "trajectory", "arc", "critter", "path");
                    b.toggle("Capsule Path Through Terrain", () -> c.safariCapsulePathThroughTerrain, v -> c.safariCapsulePathThroughTerrain = v)
                            .desc("The arc stays visible behind blocks.");
                    b.slider("Capsule Path Width", 1f, 5f, () -> c.safariCapsulePathWidth, v -> c.safariCapsulePathWidth = Math.round(v * 2f) / 2f, 1);
                    b.color("Capsule Path Color", () -> c.colorSafariCapsulePath, v -> c.colorSafariCapsulePath = v);
                    b.color("Capsule Critter Hit Color", () -> c.colorSafariCapsuleHit, v -> c.colorSafariCapsuleHit = v);
                    b.color("Capsule Path Outline Color", () -> c.colorSafariCapsuleOutline, v -> c.colorSafariCapsuleOutline = v);
                });
    }

    private static void reset() {
        world = null;
        scene = null;
        targets = List.of();
        available = false;
        trace = null;
        patch = null;
        lines = List.of();
        traceRevision = -1;
        revision++;
        CapsuleTargets.clearMounds();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || LegitMode.blocksCheats() || !SafariData.inSafari()) {
            reset();
            return;
        }
        if (mc.level != world) {
            reset();
            world = mc.level;
            scene = new CapsuleContactScene(mc.level, p);
        }
        String id = ItemUtil.skyblockId(p.getMainHandItem());
        boolean masterful = MASTERFUL.equals(id);
        Vec3 vel = p.getDeltaMovement();
        boolean flying = p.getAbilities().flying;
        available = (ORDINARY.equals(id) || masterful)
                && p.position().isFinite() && vel.isFinite()
                && !p.isInLava() && !flying && !p.isPassenger();
        if (!available) {
            targets = List.of();
            return;
        }
        boolean stationary = p.onGround() && !p.isShiftKeyDown() && !p.isSprinting() && !p.isSwimming()
                && !p.isInWater() && !p.isInLava() && !flying && !p.isPassenger()
                && Math.hypot(vel.x, vel.z) <= 0.001;
        estimate = p.isInWater() || p.isSwimming() || masterful || !stationary;

        scene.clear();
        List<Target> found = CapsuleTargets.collect(mc);
        if (found.size() > 64) {
            Curve curve = CapsulePathModel.sample(ShotVector.of(p.getEyePosition()), ShotVector.of(p.getLookAngle()), p.getXRot());
            List<ShotVector> pts = new ArrayList<>();
            for (int i = 0; i < curve.nodes().size(); i++) {
                if (i % 8 == 0 || i == curve.nodes().size() - 1) pts.add(curve.nodes().get(i).position());
            }
            found.sort(Comparator.comparingDouble(t -> CapsuleTargets.priority(pts, t)));
            found = found.subList(0, 64);
        }
        targets = found;
        tickedAt = mc.level.getGameTime();
        revision++;
    }

    private static void render(PoseStack matrices, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!available || scene == null || p == null || mc.level != world) return;
        if (mc.gui.screen() != null || mc.gui.hud.isHidden() || mc.level.getGameTime() - tickedAt > 1) return;

        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        ShotVector eye = ShotVector.of(p.getEyePosition(partial));
        ShotVector aim = ShotVector.of(p.getLookAngle());
        if (trace == null || traceRevision != revision || traceEstimate != estimate
                || !eye.equals(traceEye) || !aim.equals(traceAim)) {
            solve(eye, aim, p.getXRot());
            meshCamera = null;
        }
        if (trace == null) return;
        ShotVector camera = ShotVector.of(cam);
        if (!camera.equals(meshCamera)) {
            lines = CapsuleGuide.build(trace, camera, patch, dashed);
            meshCamera = camera;
        }
        if (lines.isEmpty()) return;
        draw(matrices, cam);
    }

    private static void solve(ShotVector eye, ShotVector aim, double pitch) {
        traceEye = eye;
        traceAim = aim;
        traceRevision = revision;
        traceEstimate = estimate;
        scene.firstWater = Double.POSITIVE_INFINITY;
        Curve curve = CapsulePathModel.sample(eye, aim, pitch);
        trace = CapsuleContactSolver.trace(curve, targets, scene::segment);
        patch = null;
        if (trace.primary() != null && trace.primary().kind() == Kind.TERRAIN) {
            patch = scene.surfacePatch(trace.primary().position(), targets);
            if (patch == null) patch = List.of();
        }
        double last = trace.nodes().isEmpty() ? Double.NEGATIVE_INFINITY : trace.nodes().get(trace.nodes().size() - 1).parameter();
        boolean water = Double.isFinite(scene.firstWater) && scene.firstWater <= last + 1.0E-7;
        dashed = estimate || water;
    }

    private static void draw(PoseStack matrices, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        MultiBufferSource.BufferSource src = WorldBuffers.INSTANCE;
        boolean through = c.safariCapsulePathThroughTerrain && !c.safariLegitMode;
        var layer = through ? TeslaRenderLayers.LINES_ESP : TeslaRenderLayers.LINES_DEPTH;
        float width = Float.isFinite(c.safariCapsulePathWidth) ? Math.max(1f, Math.min(5f, c.safariCapsulePathWidth)) : 3f;
        int path = TeslaMapsConfig.parseColor(c.colorSafariCapsulePath);
        int hit = TeslaMapsConfig.parseColor(c.colorSafariCapsuleHit);
        int outline = TeslaMapsConfig.parseColor(c.colorSafariCapsuleOutline);

        matrices.pushPose();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose pose = matrices.last();
        VertexConsumer buf = src.getBuffer(layer);
        for (CapsuleGuide.Line l : lines) line(buf, pose, l, outline, width + 2f);
        src.endBatch(layer);
        buf = src.getBuffer(layer);
        for (CapsuleGuide.Line l : lines) line(buf, pose, l, l.role() == CapsuleGuide.HIT ? hit : path, width);
        src.endBatch(layer);
        matrices.popPose();
    }

    private static void line(VertexConsumer buf, PoseStack.Pose pose, CapsuleGuide.Line l, int color, float width) {
        float dx = l.x1() - l.x0(), dy = l.y1() - l.y0(), dz = l.z1() - l.z0();
        buf.addVertex(pose, l.x0(), l.y0(), l.z0()).setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(width);
        buf.addVertex(pose, l.x1(), l.y1(), l.z1()).setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(width);
    }
}
