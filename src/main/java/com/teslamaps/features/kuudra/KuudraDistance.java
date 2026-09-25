package com.teslamaps.features.kuudra;

// IQ's PhaseFourConfig.kuudraDistanceDisplay
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.monster.MagmaCube;

import java.util.Locale;

public final class KuudraDistance {

    private static final String SAMPLE = "§7Kuudra: §a13.5m";
    private static String line;
    private static HudEditElement hud;

    private KuudraDistance() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("kuudraDistance", "Kuudra Distance").section("Kuudra")
                .enabled(() -> cfg().kuudraDistance)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> KuudraTickTimers.draw(g, mc, x, y, s, SAMPLE));
        Feature.of("KuudraDistance").section("Kuudra").when(() -> cfg().kuudraDistance)
                .tick(KuudraDistance::tick)
                .hud("kuudradistance", KuudraDistance::render)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Kuudra Distance Display", () -> c.kuudraDistance, v -> c.kuudraDistance = v)
                            .desc("Your distance to Kuudra in the fight phases (Bonemerang range is about 13.5 blocks). Move it in /tmap gui");
                });
    }

    private static void tick() {
        var p = Minecraft.getInstance().player;
        MagmaCube boss = Kuudra.boss();
        if (p == null || boss == null || !boss.isAlive() || !Kuudra.inKuudra() || !Kuudra.phase().combat()) {
            line = null;
            return;
        }
        double d = p.position().distanceTo(boss.position());
        line = "§7Kuudra: " + (d <= 13.5 ? "§a" : d <= 20 ? "§e" : "§c") + String.format(Locale.ROOT, "%.1fm", d);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = line;
        Minecraft mc = Minecraft.getInstance();
        if (l == null || hud == null || mc.options.hideGui) return;
        KuudraTickTimers.draw(g, mc, hud.x(), hud.y(), hud.scale(), l);
    }
}
