package com.teslamaps.features;

// Ported from SkyHanni's GlowingMushroomHighlighter.kt + Skyblocker's GlowingMushrooms.java (hold time, break removal)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EspStyle;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public final class GlowingMushrooms {

    private static final long HOLD_MS = 3000L;
    private static final int MAX_PENDING = 256;

    private static final ArrayDeque<BlockPos> pending = new ArrayDeque<>();
    private static final Map<BlockPos, Long> seen = new HashMap<>();
    private static volatile boolean active;
    private static Object lastLevel;

    private GlowingMushrooms() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("GlowingMushrooms").section("Garden").when(() -> cfg().glowingMushrooms)
                .tick(GlowingMushrooms::tick)
                .world(GlowingMushrooms::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Glowing Mushroom Highlight", () -> c.glowingMushrooms, v -> c.glowingMushrooms = v)
                            .desc("Marks the glowing mushrooms in the Glowing Mushroom Cave (The Farming Islands), found by their particles.")
                            .tags("mushroom", "glowing", "barn", "farming islands", "cave");
                    b.espStyle("Mushroom", () -> c.glowingMushroomStyle);
                });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
            if (active) seen.remove(pos);
            return InteractionResult.PASS;
        });
    }

    public static void onParticle(ClientboundLevelParticlesPacket packet) {
        if (!active || !cfg().glowingMushrooms || !Minecraft.getInstance().isSameThread()) return;
        if (packet.particle().getType() != ParticleTypes.ENTITY_EFFECT) return;
        double x = packet.x(), y = packet.y(), z = packet.z();
        if (!frac(x, 0.5) || !frac(y, 0.1) || !frac(z, 0.5)) return;
        if (pending.size() < MAX_PENDING) pending.add(BlockPos.containing(x, y, z));
    }

    private static boolean frac(double v, double want) {
        return Math.abs(Math.round(Math.abs(v % 1) * 10) / 10.0 - want) < 1e-6;
    }

    private static boolean mushroom(Minecraft mc, BlockPos pos) {
        Block b = mc.level.getBlockState(pos).getBlock();
        return b == Blocks.RED_MUSHROOM || b == Blocks.BROWN_MUSHROOM;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            pending.clear();
            seen.clear();
        }
        active = SkyblockUtils.isInArea("The Farming Islands");
        if (!active) {
            pending.clear();
            seen.clear();
            return;
        }
        long now = System.currentTimeMillis();
        BlockPos p;
        while ((p = pending.poll()) != null) {
            if (mushroom(mc, p)) seen.put(p, now);
        }
        seen.entrySet().removeIf(e -> e.getValue() + HOLD_MS <= now || !mushroom(mc, e.getKey()));
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (seen.isEmpty()) return;
        EspStyle style = EspStyle.of(cfg().glowingMushroomStyle);
        for (BlockPos p : seen.keySet()) {
            style.drawBox(pose, new AABB(p.getX() + 0.3, p.getY(), p.getZ() + 0.3, p.getX() + 0.7, p.getY() + 0.4, p.getZ() + 0.7), cam);
        }
    }
}
