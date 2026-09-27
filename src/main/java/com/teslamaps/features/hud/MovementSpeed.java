package com.teslamaps.features.hud;

// Ported from SkyHanni's features/misc/MovementSpeedDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public final class MovementSpeed {

    private static final List<String> SAMPLE = List.of("§eMovement Speed: §f12.34");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, timer;

    private MovementSpeed() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("movementSpeed", "Movement Speed")
                .enabled(() -> cfg().movementSpeed)
                .size(() -> TextHud.width(SAMPLE), () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("MovementSpeed").when(() -> cfg().movementSpeed)
                .tick(MovementSpeed::tick)
                .hudHiddenInInventory("movementspeed", MovementSpeed::render)
                .config("HUD Widgets", b -> {
                    b.label("Movement Speed");
                    b.toggle("Movement Speed", () -> cfg().movementSpeed, v -> cfg().movementSpeed = v)
                            .desc("Your speed in blocks per second (not the Speed stat). Position in /tmap gui.");
                });
    }

    private static void tick() {
        if (++timer % 5 != 0) return;
        Player p = Minecraft.getInstance().player;
        double dx = p.getX() - p.xo, dy = p.getY() - p.yo, dz = p.getZ() - p.zo;
        double bps = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20;
        lines = List.of("§eMovement Speed: §f" + Math.round(bps * 100) / 100.0);
        width = TextHud.width(lines);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
