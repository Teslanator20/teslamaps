package com.teslamaps.safari;

// Ported from SkyHanni's SafariNamesInCenter.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Biome labels at the four exits of the Safari hub while the player stands in no biome. */
public final class SafariCenterNames {

    private static final Vec3[] POS = {
            new Vec3(-27.1, 66.0, 22.8), new Vec3(-25.5, 66.0, -23.2),
            new Vec3(-73.3, 65.0, -23.4), new Vec3(-72.6, 65.5, 23.9)};
    private static final String[] NAMES = {"§2Forest Biome", "§5Haunted Biome", "§9Icy Biome", "§6Cavern Biome"};

    private static boolean show;

    private SafariCenterNames() {}

    public static void register() {
        Feature.of("SafariCenterNames").section("Critter Safari")
                .when(() -> TeslaMapsConfig.get().safariCenterNames)
                .tick(SafariCenterNames::tick)
                .world(SafariCenterNames::render)
                .config("Critter Safari", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Biome Names In Center", () -> c.safariCenterNames, v -> c.safariCenterNames = v)
                            .desc("Big Forest / Haunted / Icy / Cavern labels at the hub exits while you are in no biome")
                            .tags("biome", "label", "center", "hub");
                });
    }

    private static void tick() {
        show = SafariData.inSafari() && SafariData.currentBiome() == null;
    }

    private static void render(PoseStack matrices, Vec3 cam) {
        if (!show) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 p = mc.player.position();
        for (int i = 0; i < POS.length; i++) {
            if (POS[i].distanceToSqr(p) <= 50) continue;
            ESPRenderer.drawText(matrices, NAMES[i], POS[i], 2.5f, cam, true, true);
        }
    }
}
