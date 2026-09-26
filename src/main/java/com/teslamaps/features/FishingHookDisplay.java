package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
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

    // "3.0" / "3.0s" while it counts down, "!!!" when the hook can be pulled.
    // The colour codes Hypixel puts in front of it are not stable, so match the plain text.
    private static final Pattern TIMER = Pattern.compile("\\d+(?:\\.\\d+)?s?");

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

        for (Entity entity : com.teslamaps.utils.EntityCache.armorStands()) {
            if (!(entity instanceof ArmorStand stand) || stand.getCustomName() == null) continue;
            String name = stand.getCustomName().getString();
            String plain = com.teslamaps.utils.StringUtil.stripTrim(name);
            boolean alert = plain.equals("!!!");
            if (!alert && !TIMER.matcher(plain).matches()) continue;

            // several bobbers can be in the water, take the stand closest to ours
            double distance = entity.distanceToSqr(bobber);
            if (distance > bestDistance) continue;
            bestDistance = distance;
            bestId = entity.getId();
            bestAlert = alert;
            best = alert ? c.hookAlertText.replace('&', '§')
                    : (name.indexOf('§') >= 0 ? name : "§e§l" + plain);
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

    private static net.minecraft.client.renderer.entity.state.EntityRenderState scaledState;

    /** EntityNametagMixin: remember the render state of our own countdown label. */
    public static void markState(Entity entity, net.minecraft.client.renderer.entity.state.EntityRenderState state) {
        if (entityId != -1 && entity.getId() == entityId && TeslaMapsConfig.get().hookWorldLabelScale > 1.01f) scaledState = state;
    }

    /** Scale for the nametag of this state, 1 = untouched. */
    public static float labelScale(net.minecraft.client.renderer.entity.state.EntityRenderState state) {
        if (state != scaledState || state == null) return 1f;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.hookDisplay ? c.hookWorldLabelScale : 1f;
    }

    private static void reset() {
        scaledState = null;
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
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;

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
