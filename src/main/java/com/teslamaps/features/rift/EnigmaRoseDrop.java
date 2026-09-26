package com.teslamaps.features.rift;

// Ported from SkyHanni's EnigmaRoseFlowerpot.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class EnigmaRoseDrop {

    private static final AABB AREA = new AABB(25, 165, 90, 52, 185, 120);
    private static final BlockPos DROP = new BlockPos(40, 161, 116);
    private static final AABB DROP_BOX = new AABB(DROP);
    private static final Vec3 LABEL = new Vec3(40.5, 162.5, 116.5);

    private static boolean inArea;
    private static int ticks;

    private EnigmaRoseDrop() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("EnigmaRoseDrop").section("Rift").when(() -> cfg().enigmaRoseDrop)
                .tick(EnigmaRoseDrop::tick)
                .world(EnigmaRoseDrop::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Enigma Rose Flowerpot Drop", () -> c.enigmaRoseDrop, v -> c.enigmaRoseDrop = v)
                            .desc("In the Rose's End parkour: a white beacon marks the spot to drop down from to reach the flowerpot.");
                });
    }

    private static void tick() {
        if (++ticks % 2 != 0) return;
        inArea = CraftRoomHolograms.inRift() && AREA.contains(Minecraft.getInstance().player.position());
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!inArea) return;
        ESPRenderer.drawFilledBox(pose, DROP_BOX, 0x60FFFFFF, cam);
        ESPRenderer.drawBeaconBeam(pose, DROP, 0x80FFFFFF, cam);
        ESPRenderer.drawText(pose, "Drop", LABEL, 1.5f, cam);
    }
}
