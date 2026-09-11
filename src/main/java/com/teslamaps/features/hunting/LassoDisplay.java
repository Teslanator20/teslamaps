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

    private static final double RANGE = 12.0;

    private static String text = null;
    private static boolean reeling = false;
    private static int handTicks = 0;
    private static int standTicks = 0;

    public static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.lassoDisplay) {
            reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        if (!holdingLasso(mc.player.getMainHandItem())) {
            debugHand(mc);
            reset();
            return;
        }

        boolean reel = false;
        String bar = null;

        // Hypixel does not always leash the animal client side, so take any stand around the player
        AABB area = mc.player.getBoundingBox().inflate(RANGE);
        for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class, area)) {
            if (stand.getCustomName() == null) continue;
            String name = stand.getCustomName().getString();
            String plain = StringUtil.stripTrim(name);
            if (plain.equals("REEL")) {
                reel = true;
                break;
            }
            if (name.contains("§m") && bar == null) bar = name;
        }

        debugStands(mc, area);

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

    private static void debugHand(Minecraft mc) {
        if (!TeslaMapsConfig.get().debugMode) return;
        if (++handTicks < 40) return;
        handTicks = 0;
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) return;
        say(mc, "§7[Lasso] no lasso: id=" + ItemUtil.skyblockId(stack)
                + " name=" + StringUtil.stripTrim(stack.getHoverName().getString()));
    }

    private static void debugStands(Minecraft mc, AABB area) {
        if (!TeslaMapsConfig.get().debugMode) return;
        if (++standTicks < 20) return;
        standTicks = 0;
        int leashed = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Leashable l && l.getLeashHolder() == mc.player) leashed++;
        }
        StringBuilder sb = new StringBuilder("§7[Lasso] leashed=" + leashed + " stands:");
        for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class, area)) {
            if (stand.getCustomName() == null) continue;
            sb.append(" [").append(stand.getCustomName().getString().replace("§", "&")).append(']');
        }
        say(mc, sb.toString());
    }

    private static void say(Minecraft mc, String msg) {
        if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
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
