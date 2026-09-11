package com.teslamaps.features.hunting;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Hypixel floats the lasso progress bar and the "REEL" prompt over the leashed animal, where it is
 * easy to miss. Same text, on the HUD. Ported from SkyHanni's LassoDisplay.
 */
public class LassoDisplay {

    public static final String SAMPLE_TEXT = "§e§l          REEL          ";

    private static final String REEL = "§e§lREEL";

    private static String text = null;
    private static boolean reeling = false;

    public static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.lassoDisplay) {
            reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !holdingLasso(mc.player.getMainHandItem())) {
            reset();
            return;
        }

        boolean reel = false;
        String bar = null;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Leashable leashable)) continue;
            if (leashable.getLeashHolder() != mc.player) continue;

            AABB area = new AABB(entity.position().add(0, 2, 0), entity.position().add(0, 2, 0)).inflate(2.0);
            for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class, area)) {
                if (stand.getCustomName() == null) continue;
                String name = stand.getCustomName().getString();
                if (name.contains("§l§m")) bar = name;
                if (name.replace("§r", "").trim().equals(REEL)) {
                    reel = true;
                    break;
                }
            }
            if (reel) break;
        }

        if (reel && !reeling && c.lassoReelVolume > 0f) {
            com.teslamaps.utils.LoudSound.play(
                    com.teslamaps.utils.SoundOptions.resolve(c.lassoReelSound),
                    c.lassoReelVolume, c.lassoReelPitch);
        }
        reeling = reel;

        text = reel ? c.lassoReelText.replace('&', '§') : bar;
    }

    private static void reset() {
        text = null;
        reeling = false;
    }

    private static boolean holdingLasso(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (ItemUtil.skyblockId(stack).toUpperCase(Locale.ROOT).contains("LASSO")) return true;
        return StringUtil.stripTrim(stack.getHoverName().getString()).toLowerCase(Locale.ROOT).contains("lasso");
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.lassoDisplay || text == null) return;
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;

        draw(context, Minecraft.getInstance(), c.lassoDisplayX, c.lassoDisplayY, c.lassoDisplayScale, text);
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
