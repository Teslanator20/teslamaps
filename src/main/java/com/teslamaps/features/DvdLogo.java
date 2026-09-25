package com.teslamaps.features;

// Concept from Odin (Forge)'s features/impl/render/DVD.kt
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.awt.Color;

public final class DvdLogo {

    private static final long CORNER_MS = 2000;

    private static float x = 20, y = 20, dx = 1, dy = 1;
    private static int color = 0xFFFF5555;
    private static long last, cornerAt;

    private DvdLogo() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DvdLogo").when(() -> cfg().dvdLogo)
                .hud("dvdlogo", DvdLogo::render)
                .config("Interface", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("DVD");
                    b.toggle("DVD Logo", () -> c.dvdLogo, v -> c.dvdLogo = v)
                            .desc("A text box bouncing around the screen like the DVD logo. Set the text with /tmap dvd <text>.");
                    b.slider("  Speed", 0.2f, 6f, () -> c.dvdSpeed, v -> c.dvdSpeed = v, 1);
                    b.slider("  Scale", 0.5f, 4f, () -> c.dvdScale, v -> c.dvdScale = v, 1);
                    b.toggle("  Corner Hit Alert", () -> c.dvdCornerAlert, v -> c.dvdCornerAlert = v)
                            .desc("Sound and a big CORNER! when it hits a corner exactly.");
                });
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("dvd")
                .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            cfg().dvdText = StringArgumentType.getString(ctx, "text").replace('&', '§');
                            TeslaMapsConfig.save();
                            ctx.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §7DVD text set"));
                            return 1;
                        }));
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        String text = c.dvdText == null || c.dvdText.isEmpty() ? "DVD" : c.dvdText;
        float s = c.dvdScale;
        int pad = 4;
        float w = (mc.font.width(text) + pad * 2) * s, h = (9 + pad * 2) * s;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();

        long now = System.currentTimeMillis();
        float step = last == 0 ? 0 : Math.min(100, now - last) / 16.6667f * c.dvdSpeed;
        last = now;
        x += dx * step;
        y += dy * step;
        boolean hitX = false, hitY = false;
        if (x <= 0) { x = 0; dx = 1; hitX = true; }
        else if (x + w >= sw) { x = Math.max(0, sw - w); dx = -1; hitX = true; }
        if (y <= 0) { y = 0; dy = 1; hitY = true; }
        else if (y + h >= sh) { y = Math.max(0, sh - h); dy = -1; hitY = true; }
        if (hitX || hitY) color = 0xFF000000 | Color.HSBtoRGB((float) Math.random(), 0.8f, 1f);
        if (hitX && hitY && c.dvdCornerAlert) {
            cornerAt = now;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1f, 1f));
        }

        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(s, s);
        HudStyle.panel(g, 0, 0, w / s, h / s);
        g.text(mc.font, text, pad, pad + 1, color, true);
        pose.popMatrix();

        if (cornerAt != 0 && now - cornerAt < CORNER_MS) {
            pose.pushMatrix();
            pose.translate(sw / 2f, sh / 3f);
            pose.scale(4, 4);
            g.centeredText(mc.font, "CORNER!", 0, 0, color);
            pose.popMatrix();
        }
    }
}
