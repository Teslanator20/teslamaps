package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.M4Mobs;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public class M4Highlight {
    private static int damaged = 0; // ticks left of the thorn damage flash

    public static void onHurtAnimation(int entityId) {
        if (!M4Mobs.inM4Boss()) return;
        if (M4Mobs.thorn != null && M4Mobs.thorn.getId() == entityId) damaged = 10;
    }

    public static void tick() {
        if (damaged > 0) damaged--;
    }

    public static boolean shouldHideEntity(Entity e) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.m4Highlight || !c.m4HideNametags) return false;
        if (!M4Mobs.inM4Boss()) return false;
        return e instanceof ArmorStand && e.getName().getString().contains("❤");
    }

    public static int styleOf(String s) {
        return "Filled".equals(s) ? 0 : "Outline".equals(s) ? 1 : 2;
    }

    public static void drawStyled(PoseStack m, AABB box, int color, int style, Vec3 cam) {
        if (style == 0 || style == 2) ESPRenderer.drawFilledBox(m, box, (color & 0xFFFFFF) | 0x66000000, cam, true);
        if (style == 1 || style == 2) ESPRenderer.drawBoxOutline(m, box, color, 2.0f, cam, true);
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.m4Highlight || !M4Mobs.inM4Boss()) return;
        int style = styleOf(c.m4HighlightStyle);

        if (c.m4HlCow) draw(matrices, M4Mobs.cows, col(c.m4ColorCow), style, cameraPos);
        if (c.m4HlSheep) draw(matrices, M4Mobs.sheep, col(c.m4ColorSheep), style, cameraPos);
        if (c.m4HlChicken) draw(matrices, M4Mobs.chickens, col(c.m4ColorChicken), style, cameraPos);
        if (c.m4HlBat) draw(matrices, M4Mobs.bats, col(c.m4ColorBat), style, cameraPos);
        if (c.m4HlWolf) draw(matrices, M4Mobs.wolves, col(c.m4ColorWolf), style, cameraPos);
        if (c.m4HlRabbit) draw(matrices, M4Mobs.rabbits, col(c.m4ColorRabbit), style, cameraPos);

        if (c.m4HlThorn && M4Mobs.thorn != null) {
            int color = (c.m4ThornDmgFlash && damaged > 0) ? col(c.m4ColorThornDmg) : col(c.m4ColorThorn);
            drawStyled(matrices, M4Mobs.thorn.getBoundingBox(), color, style, cameraPos);
        }
        if (c.m4HlBear && M4Mobs.bear != null && !(c.m4BearOnlyMage && !"Mage".equals(PlayerTracker.getLocalClass()))) {
            drawStyled(matrices, M4Mobs.bear.getBoundingBox(), col(c.m4ColorBear), style, cameraPos);
        }
    }

    private static void draw(PoseStack m, Set<Entity> set, int color, int style, Vec3 cam) {
        for (Entity e : set) drawStyled(m, e.getBoundingBox(), color, style, cam);
    }

    private static int col(String hex) {
        return TeslaMapsConfig.parseColor(hex);
    }
}
