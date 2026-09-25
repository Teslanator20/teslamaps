package com.teslamaps.features.garden;

// Ported from SkyHanni's PestParticleWaypoint.kt, curve fit through utils/BezierFitter
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.PestData;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.BezierFitter;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PestGuess {

    private static final long TRAIL_MS = 5000;

    private static final BezierFitter fitter = new BezierFitter(3);
    private static long lastUse;
    private static Vec3 guess;
    private static boolean plotMiddle;
    private static Object lastLevel;
    private static Feature feature;

    private PestGuess() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("PestGuess").section("Garden").when(() -> cfg().pestGuess)
                .tick(PestGuess::tick)
                .world(PestGuess::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Pest Tracker Guess");
                    b.toggle("Pest Guess Waypoint", () -> c.pestGuess, v -> c.pestGuess = v)
                            .desc("Left click with a vacuum: the pest tracker's particle trail is fitted to a curve and its end shown as a waypoint with a beam. Gone on arrival or when no pests are left.")
                            .tags("pest", "vacuum", "tracker", "particle", "waypoint");
                    b.toggle("  Line To Crosshair", () -> c.pestGuessLine, v -> c.pestGuessLine = v);
                    b.toggle("  Mark Plot Middle Yellow", () -> c.pestGuessPlotMiddle, v -> c.pestGuessPlotMiddle = v)
                            .desc("A trail that only points at the middle of a plot is shown yellow");
                    b.toggle("  Hide Tracker Particles", () -> c.pestGuessHideParticles, v -> c.pestGuessHideParticles = v);
                    b.slider("  Show For (sec)", 5f, 60f, () -> (float) c.pestGuessSeconds,
                            v -> c.pestGuessSeconds = Math.round(v), 0);
                });
    }

    private static boolean on() {
        return feature != null && feature.active() && SkyblockUtils.isInGarden();
    }

    private static void reset() {
        lastUse = 0;
        guess = null;
        plotMiddle = false;
        fitter.reset();
    }

    /** From SwapSoundMixin, main thread. */
    public static void onLeftClick() {
        if (!on()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isShiftKeyDown()) return;
        if (!ItemUtil.skyblockId(mc.player.getMainHandItem()).contains("VACUUM")) return;
        reset();
        lastUse = System.currentTimeMillis();
    }

    private static boolean inWindow() {
        return System.currentTimeMillis() - lastUse <= TRAIL_MS;
    }

    private static boolean plain(ClientboundLevelParticlesPacket p) {
        return p.getXDist() == 0f && p.getYDist() == 0f && p.getZDist() == 0f;
    }

    /** From ClientPlayNetworkHandlerMixin, main thread. */
    public static void onParticle(ClientboundLevelParticlesPacket p) {
        if (lastUse == 0 || !inWindow() || !Minecraft.getInstance().isSameThread() || !on()) return;
        if (p.getParticle().getType() != ParticleTypes.ANGRY_VILLAGER || p.getCount() != 1 || p.getMaxSpeed() != 0f || !plain(p)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 at = new Vec3(p.getX(), p.getY(), p.getZ());
        if (fitter.isEmpty() && at.distanceTo(mc.player.position()) > 5) return;
        if (!fitter.tryAdd(at, 3.0)) return;
        Vec3 solved = fitter.solve();
        if (solved == null) return;
        guess = solved;
        plotMiddle = Math.ceil(solved.x) % 96 == 0 && Math.ceil(solved.z) % 96 == 0;
    }

    /** True hides the particle. Main thread. */
    public static boolean hideParticle(ClientboundLevelParticlesPacket p) {
        TeslaMapsConfig c = cfg();
        if (!c.pestGuess || !c.pestGuessHideParticles || !on()) return false;
        ParticleType<?> type = p.getParticle().getType();
        if (type == ParticleTypes.FIREWORK) return true;
        if (!inWindow()) return false;
        if (type == ParticleTypes.ENCHANT) return p.getCount() == 10 && p.getMaxSpeed() == -2f && plain(p);
        return type == ParticleTypes.ANGRY_VILLAGER && p.getCount() == 1 && p.getMaxSpeed() == 0f && plain(p);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
            return;
        }
        if (guess == null) return;
        long age = System.currentTimeMillis() - lastUse;
        if (age > cfg().pestGuessSeconds * 1000L || PestData.hasPestSection() && PestData.alive() == 0) {
            reset();
            return;
        }
        double dx = guess.x - mc.player.getX(), dz = guess.z - mc.player.getZ();
        if (age >= 1000 && dx * dx + dz * dz <= 64) reset();
    }

    private static void render(PoseStack pose, Vec3 cam) {
        Vec3 at = guess;
        if (at == null || !SkyblockUtils.isInGarden()) return;
        TeslaMapsConfig c = cfg();
        boolean middle = plotMiddle && c.pestGuessPlotMiddle;
        int color = middle ? 0xFFFFFF55 : 0xFFFF5555;
        BlockPos pos = BlockPos.containing(at);
        ESPRenderer.drawFilledBox(pose, new AABB(pos), (color & 0x00FFFFFF) | 0x66000000, cam, true);
        ESPRenderer.drawBeaconBeam(pose, pos, color, cam);
        Vec3 center = Vec3.atCenterOf(pos);
        WaypointSet.drawLabel(pose, cam, "§aPest Guess", center.add(0, 1.5, 0), 1.3f, true);
        if (middle) WaypointSet.drawLabel(pose, cam, "§e(plot middle)", center.add(0, 1.0, 0), 1.0f, true);
        if (c.pestGuessLine) ESPRenderer.drawTracerFromCamera(pose, center, color, cam, true);
    }
}
