package com.teslamaps.features.fishing;

// Ported from SkyHanni's features/fishing/ThunderSparksHighlight.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SoulweaverHider;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ThunderSparks {

    private static final String TEXTURE = "7b3328d3e9d710420322555b17239307f12270adf81bf63afc50faa04b5c06e1";

    private static List<ArmorStand> sparks = List.of();
    private static int ticks;

    private ThunderSparks() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ThunderSparks").section("Fishing").when(() -> cfg().thunderSparkHighlight)
                .tick(ThunderSparks::tick)
                .world(ThunderSparks::render)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Thunder Sparks");
                    b.toggle("Thunder Spark Highlight", () -> c.thunderSparkHighlight, v -> c.thunderSparkHighlight = v)
                            .desc("Crimson Isle: box and label on the Thunder Sparks a killed Thunder drops. Up close also through lava")
                            .tags("thunder", "spark", "bottle", "crimson", "lava");
                    b.color("  Color", () -> c.colorThunderSpark, v -> c.colorThunderSpark = v);
                });
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        if (!SkyblockUtils.isInArea("Crimson Isle")) {
            sparks = List.of();
            return;
        }
        List<ArmorStand> out = new ArrayList<>(2);
        for (ArmorStand as : EntityCache.armorStands()) {
            if (as.isRemoved()) continue;
            if (TEXTURE.equals(SoulweaverHider.headTextureUrl(as.getItemBySlot(EquipmentSlot.HEAD)))) out.add(as);
        }
        sparks = out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (sparks.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        int color = TeslaMapsConfig.parseColor(cfg().colorThunderSpark);
        for (ArmorStand s : sparks) {
            if (s.isRemoved()) continue;
            double x = s.getX(), y = s.getY(), z = s.getZ();
            double d = mc.player.distanceToSqr(x, y, z);
            boolean lava = d < 36 && mc.level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.LAVA);
            ESPRenderer.drawFilledBox(pose, new AABB(x - 0.25, y, z - 0.25, x + 0.25, y + 0.5, z + 0.25), color, cam, lava);
            if (d < 100) ESPRenderer.drawText(pose, "§eThunder Spark", new Vec3(x, y + 1.5, z), 1f, cam, true, lava);
        }
    }
}
