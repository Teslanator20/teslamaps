package com.teslamaps.features.rift;

// After SkyHanni's RiftWiltedBerberisHelper.kt (particle mode) and Zen's BerberisHelper.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class WiltedBerberisHelper {

    private static final ConcurrentLinkedQueue<Vec3> pending = new ConcurrentLinkedQueue<>();
    private static final Map<BlockPos, Long> spots = new HashMap<>();
    private static volatile boolean active;
    private static List<AABB> boxes = List.of();
    private static int ticks;

    private WiltedBerberisHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("WiltedBerberisHelper").section("Rift").when(() -> cfg().riftBerberisHelper)
                .tick(WiltedBerberisHelper::tick)
                .world(WiltedBerberisHelper::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Wilted Berberis Helper", () -> c.riftBerberisHelper, v -> c.riftBerberisHelper = v)
                            .desc("Dreadfarm: boxes the dead bush the Wilted Berberis sparks sit on (within 20 blocks)")
                            .tags("rift", "berberis", "dreadfarm", "farming");
                    b.color("  Color", () -> c.riftBerberisColor, v -> c.riftBerberisColor = v);
                });
    }

    /** From ClientPlayNetworkHandlerMixin particle HEAD inject, any thread: only queues the position. */
    public static void onParticle(ClientboundLevelParticlesPacket p) {
        if (!active || !cfg().riftBerberisHelper || p.particle().getType() != ParticleTypes.FIREWORK) return;
        if (pending.size() < 256) pending.add(new Vec3(p.x(), p.y(), p.z()));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (ticks++ % 20 == 0) active = RiftSpot.inRift() && RiftSpot.sub().equals("Dreadfarm");
        if (!active) {
            pending.clear();
            spots.clear();
            boxes = List.of();
            return;
        }
        long now = System.currentTimeMillis();
        Vec3 v;
        Vec3 me = mc.player.position();
        while ((v = pending.poll()) != null) {
            if (v.distanceToSqr(me) > 20 * 20) continue;
            BlockPos base = BlockPos.containing(v);
            for (int dy = 0; dy <= 2; dy++) {
                BlockPos p = base.below(dy);
                if (mc.level.getBlockState(p).is(Blocks.DEAD_BUSH) && mc.level.getBlockState(p.below()).is(Blocks.FARMLAND)) {
                    spots.put(p, now);
                    break;
                }
            }
        }
        spots.entrySet().removeIf(e -> now - e.getValue() > 800 || !mc.level.getBlockState(e.getKey()).is(Blocks.DEAD_BUSH));
        if (spots.isEmpty()) {
            boxes = List.of();
            return;
        }
        List<AABB> out = new ArrayList<>(spots.size());
        for (BlockPos p : spots.keySet()) out.add(new AABB(p));
        boxes = out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<AABB> l = boxes;
        if (l.isEmpty()) return;
        int col = TeslaMapsConfig.parseColor(cfg().riftBerberisColor);
        for (int i = 0; i < l.size(); i++) ESPRenderer.drawFilledBox(pose, l.get(i), col, cam, false);
    }
}
