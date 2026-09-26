package com.teslamaps.features.kuudra;

// Ported from Odin's nether/KuudraInfo.kt (highlight + getCurrentHealthDisplay), percent mode from Odin Forge's KuudraDisplay.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EspStyle;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class KuudraBoss {

    private static final String[] MODES = {"Scaled", "Percent", "Absolute"};
    private static final String SAMPLE = "§a99.975M§7/§a240M§c❤";

    private static String hpText = null;
    private static Vec3 hpPos = null;
    private static HudEditElement hud;

    private KuudraBoss() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("kuuBossHp", "Kuudra HP").section("Kuudra")
                .enabled(() -> cfg().kuuBossHp && cfg().kuuBossHpHud)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE) + 6, () -> 14)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("KuudraBoss").section("Kuudra").when(() -> (cfg().kuuBossHighlight || cfg().kuuBossHp) && Kuudra.inKuudra())
                .tick(KuudraBoss::tick)
                .world(KuudraBoss::render)
                .hud("kuudraboss", KuudraBoss::renderHud)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Kuudra Boss");
                    b.toggle("Highlight Kuudra", () -> c.kuuBossHighlight, v -> c.kuuBossHighlight = v)
                            .desc("Box or glow on Kuudra's hitbox (the size 30 magma cube)");
                    b.espStyle("Kuudra", () -> c.kuuBossStyle);
                    b.toggle("Kuudra HP", () -> c.kuuBossHp, v -> c.kuuBossHp = v)
                            .desc("Kuudra's health, colored by how much is left");
                    b.dropdown("  HP Format", MODES, () -> c.kuuBossHpMode, v -> c.kuuBossHpMode = v)
                            .desc("Scaled = T5 shows 240M below 25k, Percent, or the raw 100k");
                    b.toggle("  HP in World", () -> c.kuuBossHpWorld, v -> c.kuuBossHpWorld = v);
                    b.slider("  World Text Size", 1f, 8f, () -> c.kuuBossHpSize, v -> c.kuuBossHpSize = v, 1);
                    b.toggle("  HP HUD", () -> c.kuuBossHpHud, v -> c.kuuBossHpHud = v)
                            .desc("Move it in /tmap gui");
                });
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        MagmaCube boss = Kuudra.boss();
        if (boss == null || boss.isRemoved()) {
            hpText = null;
            hpPos = null;
            return;
        }
        if (c.kuuBossHighlight) EspStyle.of(c.kuuBossStyle).tickEntity(boss);
        if (c.kuuBossHp) {
            hpText = format(boss.getHealth(), c.kuuBossHpMode);
            hpPos = boss.position().add(boss.getLookAngle().scale(13)).add(0, 10, 0);
        } else {
            hpText = null;
        }
    }

    static String format(float hp, String mode) {
        String color = hp > 99000 ? "§a" : hp > 75000 ? "§2" : hp > 50000 ? "§e" : hp > 25000 ? "§6" : hp > 10000 ? "§c" : "§4";
        float k = hp / 1000f;
        if ("Scaled".equals(mode) && hp <= 25000 && Kuudra.tier() == 5)
            return color + String.format(Locale.ROOT, "%.2fM", k * 9.6f) + "§7/§a240M§c❤";
        if ("Percent".equals(mode)) return color + String.format(Locale.ROOT, "%.2f", k) + "§a% §c❤";
        return color + String.format(Locale.ROOT, "%.1fK", k) + "§7/§a100K§c❤";
    }

    private static void render(PoseStack pose, Vec3 cam) {
        TeslaMapsConfig c = cfg();
        MagmaCube boss = Kuudra.boss();
        if (boss == null || boss.isRemoved()) return;
        if (c.kuuBossHighlight) EspStyle.of(c.kuuBossStyle).drawEntity(pose, boss, cam, 1f);
        String t = hpText;
        Vec3 p = hpPos;
        if (c.kuuBossHp && c.kuuBossHpWorld && t != null && p != null) ESPRenderer.drawText(pose, t, p, c.kuuBossHpSize, cam);
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        String t = hpText;
        if (t == null || hud == null || !cfg().kuuBossHpHud) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), t);
    }

    static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, mc.font.width(text) + 6, 14);
        g.text(mc.font, text, 3, 3, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
