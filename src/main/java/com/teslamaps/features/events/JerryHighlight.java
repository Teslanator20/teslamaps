package com.teslamaps.features.events;

// Ported from SkyHanni's HighlightJerries.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.NametagMobs;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class JerryHighlight {

    private record Jerry(LivingEntity mob, int rgb) {}

    private static List<Jerry> jerries = List.of();
    private static int ticks;

    private JerryHighlight() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("JerryHighlight").section("Events").when(() -> cfg().highlightJerries)
                .tick(JerryHighlight::tick)
                .world(JerryHighlight::render)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Highlight Jerries", () -> c.highlightJerries, v -> c.highlightJerries = v)
                            .desc("Jerrys from the Jerrypocalypse perk glow in their color: Green, Blue, Purple or Golden.")
                            .tags("jerry", "jerrypocalypse", "golden", "mayor");
                    b.toggle("  Line To Jerries", () -> c.jerryLines, v -> c.jerryLines = v);
                });
    }

    private static int color(String name) {
        return switch (name) {
            case "Green Jerry" -> 0x55FF55;
            case "Blue Jerry" -> 0x5555FF;
            case "Purple Jerry" -> 0xAA00AA;
            case "Golden Jerry" -> 0xFFAA00;
            default -> -1;
        };
    }

    private static void tick() {
        if (++ticks % 10 == 0) scan();
        if (!cfg().highlightJerries) return;
        for (Jerry j : jerries) if (!j.mob.isRemoved()) EntityGlow.mark(j.mob, j.rgb);
    }

    private static void scan() {
        List<Entity> all = EntityCache.all();
        List<Jerry> out = null;
        for (Entity e : all) {
            if (!(e instanceof ArmorStand stand)) continue;
            String tag = NametagMobs.tagName(stand);
            if (tag == null || !tag.contains("Jerry")) continue;
            NametagMobs.Info info = NametagMobs.parse(tag, false);
            int rgb = color(info != null ? info.name() : tag);
            if (rgb < 0) continue;
            LivingEntity best = null;
            double bestD = 9;
            for (Entity m : all) {
                if (!(m instanceof LivingEntity le) || m instanceof ArmorStand || m instanceof Player || m.isRemoved()) continue;
                double dx = m.getX() - stand.getX(), dz = m.getZ() - stand.getZ(), dy = stand.getY() - m.getY();
                if (dy < -0.5 || dy > 4) continue;
                double d = dx * dx + dz * dz + dy * dy * 0.1;
                if (d < bestD) { bestD = d; best = le; }
            }
            if (best == null) continue;
            if (out == null) out = new ArrayList<>();
            out.add(new Jerry(best, rgb));
        }
        jerries = out == null ? List.of() : out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!cfg().jerryLines || jerries.isEmpty()) return;
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Jerry j : jerries) {
            if (j.mob.isRemoved()) continue;
            Vec3 p = j.mob.getPosition(pt).add(0, j.mob.getBbHeight() / 2, 0);
            ESPRenderer.drawTracerFromCamera(pose, p, 0xFF000000 | j.rgb, cam);
        }
    }
}
