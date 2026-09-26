package com.teslamaps.features.rift;

// Ported from SkyHanni's LivingCaveDefenseBlocks.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EntityGlow;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class LivingCaveDefenseBlocks {

    private static final Set<String> MOBS = Set.of("Autonull", "Autocap", "Autochest", "Autopants", "Autoboots");

    private static final class Block {
        final Player entity;
        final double x, y, z;
        long until;
        boolean hidden;

        Block(Player entity, double x, double y, double z) {
            this.entity = entity; this.x = x; this.y = y; this.z = z;
        }

        double distSq(double ox, double oy, double oz) {
            double dx = x - ox, dy = y - oy, dz = z - oz;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static final List<Block> moving = new ArrayList<>();
    private static final List<Block> statics = new ArrayList<>();
    private static boolean inCave;
    private static int ticks;
    private static Feature feature;

    private LivingCaveDefenseBlocks() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("LivingCaveDefenseBlocks").section("Rift").legit().when(() -> cfg().defenseBlocksHighlight)
                .tick(LivingCaveDefenseBlocks::tick)
                .world(LivingCaveDefenseBlocks::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Defense Blocks", () -> c.defenseBlocksHighlight, v -> c.defenseBlocksHighlight = v)
                            .desc("Living Cave: marks the defense blocks of the Auto mobs with \"Break!\", a line to the mob they protect, "
                                    + "and colors that mob. Flying blocks get a line to your crosshair.")
                            .tags("defense", "block", "living", "cave", "rift", "autonull", "autocap").legit();
                    b.color("  Color", () -> c.defenseBlocksColor, v -> c.defenseBlocksColor = v);
                    b.toggle("  Hide Particles", () -> c.defenseBlocksHideParticles, v -> c.defenseBlocksHideParticles = v);
                });
    }

    private static boolean on() {
        return inCave && feature != null && feature.active();
    }

    public static boolean listening() {
        return inCave && cfg().defenseBlocksHighlight;
    }

    private static void tick() {
        if (++ticks % 20 == 0) {
            inCave = LivingMetalHelper.livingCave();
            if (!inCave) { moving.clear(); statics.clear(); }
        }
        statics.removeIf(b -> b.entity.isRemoved() || b.entity.isDeadOrDying());
        int rgb = TeslaMapsConfig.parseColor(cfg().defenseBlocksColor) & 0xFFFFFF;
        for (Block b : statics) EntityGlow.mark(b.entity, rgb);
    }

    private static boolean correctMob(Player p) {
        return MOBS.contains(p.getName().getString().trim());
    }

    /** Particle hide chain, main thread. */
    public static boolean onParticle(ClientboundLevelParticlesPacket p) {
        if (!on()) return false;
        long now = System.currentTimeMillis();
        moving.removeIf(m -> now > m.until + 2000);
        moving.removeIf(m -> { for (Block s : statics) if (s.distSq(m.x, m.y, m.z) < 2.25) return true; return false; });
        double x = p.getX() - 0.5, y = p.getY(), z = p.getZ() - 0.5;
        boolean hide = cfg().defenseBlocksHideParticles;
        for (Block s : statics) if (s.distSq(x, y, z) < 9) return hide;
        boolean nearMoving = false;
        for (Block m : moving) if (m.distSq(x, y, z) < 9) { nearMoving = true; break; }
        if (p.getParticle().getType() != ParticleTypes.ENCHANTED_HIT) return hide && nearMoving;

        Player entity = null;
        Block old = nearest(moving, x, y, z);
        if (old != null && old.distSq(x, y, z) < 0.25) {
            old.hidden = true;
            entity = old.entity;
        }
        if (entity == null) {
            Minecraft mc = Minecraft.getInstance();
            double cy = p.getY() - 1.5;
            double bestD = 4;
            for (Player pl : mc.level.players()) {
                if (pl == mc.player || !correctMob(pl) || pl.getHealth() >= pl.getMaxHealth()) continue;
                double d = pl.distanceToSqr(x, cy, z);
                if (d < bestD) { bestD = d; entity = pl; }
            }
        }
        if (entity == null) return hide && nearMoving;
        Block nb = new Block(entity, x, y, z);
        nb.until = now + 250;
        moving.add(nb);
        return hide;
    }

    private static Block nearest(List<Block> list, double x, double y, double z) {
        Block best = null;
        double bestD = 225;
        for (Block b : list) {
            double d = b.distSq(x, y, z);
            if (d < bestD) { bestD = d; best = b; }
        }
        return best;
    }

    /** From BlockUpdateMixin, main thread. */
    public static void onBlockUpdate(BlockPos pos, BlockState old, BlockState now) {
        if (!on()) return;
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (old.isAir() && (now.getBlock() instanceof StainedGlassBlock || now.is(Blocks.DIAMOND_BLOCK))) {
            Block m = nearest(moving, x, y, z);
            if (m != null) statics.add(new Block(m.entity, x, y, z));
        }
        if (now.isAir()) {
            Block s = nearest(statics, x, y, z);
            if (s != null && s.distSq(x, y, z) < 0.01) statics.remove(s);
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (moving.isEmpty() && statics.isEmpty()) return;
        int color = TeslaMapsConfig.parseColor(cfg().defenseBlocksColor);
        int fill = (color & 0xFFFFFF) | 0x60000000;
        long now = System.currentTimeMillis();
        for (Block b : moving) {
            if (b.hidden || b.until <= now) continue;
            ESPRenderer.drawFilledBox(pose, new AABB(b.x, b.y, b.z, b.x + 1, b.y + 1, b.z + 1), fill, cam, true);
            ESPRenderer.drawTracerFromCamera(pose, new Vec3(b.x + 0.5, b.y + 0.5, b.z + 0.5), color, cam);
        }
        if (statics.isEmpty()) return;
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Block b : statics) {
            Vec3 c = new Vec3(b.x + 0.5, b.y + 0.5, b.z + 0.5);
            ESPRenderer.drawFilledBox(pose, new AABB(b.x, b.y, b.z, b.x + 1, b.y + 1, b.z + 1), fill, cam, false);
            ESPRenderer.drawText(pose, "§bBreak!", new Vec3(c.x, b.y + 1.3, c.z), 1.5f, cam, true, false);
            ESPRenderer.drawLine(pose, b.entity.getPosition(pt).add(0, 0.5, 0), c, color, 3f, cam);
        }
    }
}
