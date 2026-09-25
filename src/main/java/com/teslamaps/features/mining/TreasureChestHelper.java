package com.teslamaps.features.mining;

// Ported from Skyblocker's CrystalsChestHighlighter (NEU / Skytils do the same)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Boxes the treasure chest you just uncovered in the Hollows and marks the lockpick spot. */
public final class TreasureChestHelper {

    private static final String SPAWN = "You uncovered a treasure chest!";
    private static final long PARTICLE_LIFE = 250;

    private static final Set<BlockPos> chests = new HashSet<>();
    private static final List<double[]> particles = new ArrayList<>();
    private static int waiting;
    private static int picks, needed;
    private static boolean on;
    private static Object lastLevel;

    private TreasureChestHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TreasureChestHelper").section("Mining").when(() -> cfg().treasureChestHelper)
                .tick(TreasureChestHelper::tick)
                .serverChat(TreasureChestHelper::onChat)
                .world(TreasureChestHelper::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Treasure Chest Helper", () -> c.treasureChestHelper, v -> c.treasureChestHelper = v)
                            .desc("Boxes the treasure chest you just uncovered in the Crystal Hollows; it goes away once opened.")
                            .tags("treasure", "chest", "lockpick", "crystal hollows", "gemstone");
                    b.toggle("  Lockpick Helper", () -> c.treasureChestLockpick, v -> c.treasureChestLockpick = v)
                            .desc("Marks where the lock particle is and counts the picks while you look at the chest");
                    b.color("  Chest Color", () -> c.colorTreasureChest, v -> c.colorTreasureChest = v);
                });
    }

    /** From BlockUpdateMixin, main thread. */
    public static boolean listening() {
        return on;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        on = SkyblockUtils.isInArea("Crystal Hollows");
        if (!on || chests.isEmpty()) return;
        chests.removeIf(p -> !mc.level.getBlockState(p).is(Blocks.CHEST));
    }

    private static void reset() {
        chests.clear();
        particles.clear();
        waiting = 0;
        picks = 0;
    }

    private static void onChat(String text) {
        if (on && StringUtil.stripTrim(text).equals(SPAWN)) waiting++;
    }

    public static void onBlockUpdate(BlockPos pos, BlockState newState) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.isSameThread() || !active()) return;
        if (waiting > 0 && newState.is(Blocks.CHEST)) {
            if (pos.distToCenterSqr(mc.player.position()) > 100) return;
            chests.add(pos.immutable());
            picks = 0;
            waiting--;
        } else if (newState.isAir() && chests.remove(pos)) {
            picks = 0;
        }
    }

    private static boolean active() {
        TeslaMapsConfig c = cfg();
        return on && c.treasureChestHelper && c.section("Mining");
    }

    public static void onParticle(ClientboundLevelParticlesPacket packet) {
        if (!Minecraft.getInstance().isSameThread() || chests.isEmpty() || !active() || !cfg().treasureChestLockpick) return;
        if (packet.getParticle().getType() != ParticleTypes.CRIT) return;
        particles.add(new double[]{packet.getX(), packet.getY(), packet.getZ(), System.currentTimeMillis()});
        if (particles.size() > 64) particles.remove(0);
    }

    public static void onSound(ClientboundSoundPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.player == null || chests.isEmpty() || !active()) return;
        SoundEvent sound = packet.getSound().value();
        if (sound.location().equals(SoundEvents.EXPERIENCE_ORB_PICKUP.location()) && packet.getPitch() == 1f) {
            Vec3 eye = mc.player.getEyePosition(0);
            Vec3 end = eye.add(mc.player.getViewVector(0).scale(mc.player.blockInteractionRange()));
            BlockHitResult hit = mc.player.level().isBlockInLine(new ClipBlockStateContext(eye, end, s -> s.is(Blocks.CHEST)));
            if (hit.getType() != HitResult.Type.MISS) {
                picks++;
                particles.clear();
            }
        } else if (sound.location().equals(SoundEvents.VILLAGER_NO.location())) {
            picks = 0;
            particles.clear();
        } else if (sound.location().equals(SoundEvents.CHEST_OPEN.location())) {
            needed = Math.min(picks, 5);
            picks = 0;
            particles.clear();
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!on || chests.isEmpty()) return;
        int color = TeslaMapsConfig.parseColor(cfg().colorTreasureChest) | 0xFF000000;
        for (BlockPos p : chests) {
            ESPRenderer.drawBoxOutline(pose, AABB.ofSize(Vec3.atCenterOf(p).subtract(0, 0.0625, 0), 0.885, 0.885, 0.885),
                    color, 3f, cam, false);
        }
        if (!cfg().treasureChestLockpick) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK || !chests.contains(bh.getBlockPos())) return;
        Vec3 c = Vec3.atCenterOf(bh.getBlockPos());
        long now = System.currentTimeMillis();
        double sx = 0, sy = 0, sz = 0;
        int n = 0;
        for (int i = 0; i < particles.size(); i++) {
            double[] pt = particles.get(i);
            if (now - pt[3] > PARTICLE_LIFE) continue;
            double dx = pt[0] - c.x, dy = pt[1] - c.y, dz = pt[2] - c.z;
            if (dx * dx + dy * dy + dz * dz > 0.64) continue;
            sx += pt[0]; sy += pt[1]; sz += pt[2];
            n++;
        }
        if (n > 0) {
            Vec3 spot = new Vec3(sx / n, sy / n, sz / n);
            ESPRenderer.drawFilledBox(pose, AABB.ofSize(spot, 0.1, 0.1, 0.1), color, cam, false);
        }
        if (needed > 0) {
            WaypointSet.drawLabel(pose, cam, "§e" + Math.min(picks, needed) + "/" + needed, c.add(0, 0.8, 0), 0.8f, false);
        }
    }
}
