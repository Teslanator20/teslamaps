package com.teslamaps.features;

// Concept from Zen's features/general/EntityHighlight.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class EntityHighlight {

    private EntityHighlight() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("EntityHighlight").section("Render").when(() -> cfg().entityHighlight)
                .world(EntityHighlight::render)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Entity Highlight");
                    b.toggle("Entity Highlight", () -> c.entityHighlight, v -> c.entityHighlight = v)
                            .desc("Outlines the entity under your crosshair, coloured by type. The entity counterpart of the block overlay.");
                    b.slider("  Line Width", 1f, 8f, () -> c.entityHighlightWidth, v -> c.entityHighlightWidth = v, 1);
                    b.color("  Players", () -> c.entityHighlightPlayer, v -> c.entityHighlightPlayer = v);
                    b.color("  Mobs", () -> c.entityHighlightMob, v -> c.entityHighlightMob = v);
                    b.color("  Animals", () -> c.entityHighlightAnimal, v -> c.entityHighlightAnimal = v);
                    b.color("  Other", () -> c.entityHighlightOther, v -> c.entityHighlightOther = v);
                });
    }

    private static void render(PoseStack pose, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.crosshairPickEntity;
        if (e == null || e.isRemoved() || e.isInvisible() || e instanceof ArmorStand) return;
        if (e == mc.player && !mc.gameRenderer.getMainCamera().isDetached()) return;
        TeslaMapsConfig c = cfg();
        String hex = e instanceof Player ? c.entityHighlightPlayer
                : e instanceof Enemy ? c.entityHighlightMob
                : e instanceof Animal ? c.entityHighlightAnimal
                : c.entityHighlightOther;
        ESPRenderer.drawBoxOutline(pose, ESPRenderer.lerpBox(e).inflate(0.02), TeslaMapsConfig.parseColor(hex),
                c.entityHighlightWidth, cam, false);
    }
}
