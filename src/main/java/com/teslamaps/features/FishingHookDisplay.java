// Ported from SkyHanni's FishingHookDisplay.kt (LGPL-2.1), see NOTICE.md
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;

/**
 * Hypixel floats a countdown over the bobber ("3.0", "2.0", "!!!") that is tiny and easy to
 * miss. This puts the same text on the HUD, big.
 */
public class FishingHookDisplay {

    public static final String SAMPLE_TEXT = "§e§l3.0";

    // "§e§l3.0" while it counts down, "§c§l!!!" when the hook can be pulled
    private static final Pattern TIMER = Pattern.compile("§e§l(?<time>\\d+(?:\\.\\d+)?)|(?:§.)*(?<alert>!!!)");

    private static String text = null;
    private static int entityId = -1;
    private static boolean alerting = false;

    public static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.hookDisplay) {
            reset();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !holdingRod(mc)) {
            reset();
            return;
        }
        FishingHook bobber = mc.player.fishing;
        if (bobber == null) {
            reset();
            return;
        }

        String best = null;
        int bestId = -1;
        boolean bestAlert = false;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || stand.getCustomName() == null) continue;
            String name = stand.getCustomName().getString();
            Matcher m = TIMER.matcher(name);
            if (!m.matches()) continue;

            // several bobbers can be in the water, take the stand closest to ours
            double distance = entity.distanceToSqr(bobber);
            if (distance > bestDistance) continue;
            bestDistance = distance;
            bestId = entity.getId();
            bestAlert = m.group("alert") != null;
            best = bestAlert ? c.hookAlertText.replace('&', '§') : name;
        }

        // only on the switch to "!!!", the stand keeps that name for a while
        if (bestAlert && !alerting && c.hookAlertVolume > 0f) {
            com.teslamaps.utils.LoudSound.play(
                    com.teslamaps.utils.SoundOptions.resolve(c.hookAlertSound),
                    c.hookAlertVolume, c.hookAlertPitch);
        }
        alerting = bestAlert;

        text = best;
        entityId = bestId;
    }

    /** Called from EntityRenderDispatcherMixin to drop Hypixel's own tiny label. */
    public static boolean shouldHide(Entity entity) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.hookDisplay && c.hookHideArmorStand && text != null && entity.getId() == entityId;
    }

    private static void reset() {
        text = null;
        entityId = -1;
        alerting = false;
    }

    private static boolean holdingRod(Minecraft mc) {
        return mc.player.getMainHandItem().getItem() instanceof FishingRodItem;
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.hookDisplay || text == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

        draw(context, mc, c.hookDisplayX, c.hookDisplayY, c.hookDisplayScale, text);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        draw(context, mc, x, y, scale, text != null ? text : SAMPLE_TEXT);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, String line) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        context.text(mc.font, line, 0, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }
}
