package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.LoudSound;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;

/**
 * Watches the block at (7, 77, 34) every 20 ticks. When it turns into a sea
 * lantern, flashes "STOP!" on screen and plays the configured alert sounds
 * (warden emerge / wither death), each toggleable in the Sounds settings.
 */
public class BearSpawnWarning {
    private static final BlockPos WATCH_POS = new BlockPos(7, 77, 34);
    private static final int SCAN_INTERVAL = 20;   // ticks between scans
    private static final int ALERT_DURATION = 60;  // ticks the "STOP!" stays on screen

    private static int tickCounter = 0;
    private static boolean wasSeaLantern = false;
    private static int alertTicks = 0;

    /** Called every client tick. */
    public static void tick() {
        if (alertTicks > 0) alertTicks--;

        if (!TeslaMapsConfig.get().bearSpawnWarning) {
            wasSeaLantern = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Only scan every SCAN_INTERVAL ticks
        if (++tickCounter < SCAN_INTERVAL) return;
        tickCounter = 0;

        boolean isSeaLantern = mc.level.getBlockState(WATCH_POS).is(Blocks.SEA_LANTERN);
        // Edge-triggered: only fire when it just became a sea lantern
        if (isSeaLantern && !wasSeaLantern) {
            trigger();
        }
        wasSeaLantern = isSeaLantern;
    }

    private static void trigger() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        alertTicks = ALERT_DURATION;
        float vol = config.bearSpawnVolume;
        if (config.bearSpawnWardenSound) LoudSound.play(SoundEvents.WARDEN_EMERGE, vol, 1.0f);
        if (config.bearSpawnWitherSound) LoudSound.play(SoundEvents.WITHER_DEATH, vol, 1.0f);
    }

    public static final String ALERT_TEXT = "STOP!";

    /** Registered as a HUD element - draws the big "STOP!" while an alert is active. */
    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        if (alertTicks <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        TeslaMapsConfig config = TeslaMapsConfig.get();
        drawAlert(context, mc, config.bearSpawnX, config.bearSpawnY, config.bearSpawnScale);
    }

    /** Shared draw used by both the live HUD and the HUD edit preview. */
    public static void drawAlert(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        context.text(mc.font, ALERT_TEXT, 0, 0, 0xFFFF0000);
        pose.popMatrix();
    }
}
