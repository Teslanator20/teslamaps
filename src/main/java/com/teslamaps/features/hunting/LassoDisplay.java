package com.teslamaps.features.hunting;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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

    private static final double PLAYER_RANGE = 12.0;
    private static final double ANIMAL_RANGE = 3.0;

    // the bar is a run of struck-through spaces, nothing else on Hypixel looks like that
    private static final int MIN_BAR_LENGTH = 8;

    private static String reelText = null;
    private static Component barText = null;
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

        // the labels sit over the animal, fall back to the player when the leash is not sent
        Entity animal = leashedAnimal(mc);
        AABB area = animal != null
                ? animal.getBoundingBox().inflate(ANIMAL_RANGE)
                : mc.player.getBoundingBox().inflate(PLAYER_RANGE);

        boolean reel = false;
        Component bar = null;
        for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class, area)) {
            Component name = stand.getCustomName();
            if (name == null) continue;
            if (StringUtil.stripTrim(name.getString()).equals("REEL")) {
                reel = true;
                break;
            }
            if (bar == null && isBar(name)) bar = name;
        }

        debugStands(mc, area);

        if (reel && !reeling && c.lassoReelVolume > 0f) {
            com.teslamaps.utils.LoudSound.play(
                    com.teslamaps.utils.SoundOptions.resolve(c.lassoReelSound),
                    c.lassoReelVolume, c.lassoReelPitch);
        }
        reeling = reel;

        reelText = reel ? c.lassoReelText.replace('&', '§') : null;
        barText = reel ? null : bar;
    }

    private static Entity leashedAnimal(Minecraft mc) {
        for (Entity e : com.teslamaps.utils.EntityCache.all()) {
            if (e instanceof Leashable l && l.getLeashHolder() == mc.player) return e;
        }
        return null;
    }

    /** Hypixel sends the bar as a styled component, not as § codes in the text. */
    private static boolean isBar(Component name) {
        String plain = name.getString();
        if (plain.length() < MIN_BAR_LENGTH || !plain.isBlank()) return false;
        return name.visit((style, part) -> style.isStrikethrough() ? Optional.of(true) : Optional.empty(),
                Style.EMPTY).isPresent();
    }

    private static void reset() {
        reelText = null;
        barText = null;
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
        StringBuilder sb = new StringBuilder("§7[Lasso] animal=" + (leashedAnimal(mc) != null) + " stands:");
        for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class, area)) {
            Component name = stand.getCustomName();
            if (name == null) continue;
            sb.append(" [").append(name.getString().replace("§", "&"))
                    .append(isBar(name) ? "|BAR]" : "]");
        }
        say(mc, sb.toString());
    }

    private static void say(Minecraft mc, String msg) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(msg));
    }

    private static boolean holdingLasso(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (ItemUtil.skyblockId(stack).toUpperCase(Locale.ROOT).contains("LASSO")) return true;
        return StringUtil.stripTrim(stack.getHoverName().getString()).toLowerCase(Locale.ROOT).contains("lasso");
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.lassoDisplay || (reelText == null && barText == null)) return;
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;

        draw(context, Minecraft.getInstance(), c.lassoDisplayX, c.lassoDisplayY, c.lassoDisplayScale);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        if (reelText != null) {
            context.text(mc.font, reelText, 0, 0, 0xFFFFFFFF);
        } else if (barText != null) {
            context.text(mc.font, barText, 0, 0, 0xFFFFFFFF);
        } else {
            context.text(mc.font, SAMPLE_TEXT, 0, 0, 0xFFFFFFFF);
        }
        pose.popMatrix();
    }
}
