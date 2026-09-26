package com.teslamaps.features.rift;

// After Zen's CoherentRodOverlay.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class CoherentRodRadius {

    private static final double RADIUS = 8;
    private static boolean holding;

    private CoherentRodRadius() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CoherentRodRadius").section("Rift").when(() -> cfg().coherentRodRadius)
                .tick(CoherentRodRadius::tick)
                .world(CoherentRodRadius::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Nearly Coherent Rod Radius", () -> c.coherentRodRadius, v -> c.coherentRodRadius = v)
                            .desc("Holding the Nearly Coherent Rod: filled 8 block circle around you.");
                    b.color("  Color", () -> c.coherentRodColor, v -> c.coherentRodColor = v);
                });
    }

    private static void tick() {
        holding = "NEARLY_COHERENT_ROD".equals(ItemUtil.skyblockId(Minecraft.getInstance().player.getMainHandItem()));
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!holding) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 p = mc.player.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        ESPRenderer.drawDisc(pose, p.x, p.y + 0.02, p.z, RADIUS, TeslaMapsConfig.parseColor(cfg().coherentRodColor), cam);
    }
}
