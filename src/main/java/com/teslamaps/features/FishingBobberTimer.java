package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

/** How long the bobber has been out, either since the cast or since it hit the liquid. */
public class FishingBobberTimer {

    public static final String SAMPLE_TEXT = "Bobber: 12.34s";

    private static FishingHook lastHook = null;
    private static long startedAt = 0;
    private static boolean caught = false;
    private static long pulls = 0;

    /** Counts up on every cast, so a catch can be tied to the pull it came from. */
    public static long pullId() {
        return pulls;
    }

    /** A catch does not remove the bobber on Hypixel, so the announcement stops the clock. */
    public static void onCatch() {
        if (startedAt == 0) return;
        caught = true;
        startedAt = 0;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            reset();
            return;
        }

        FishingHook hook = mc.player.fishing;
        if (hook == null || !holdingRod(mc)) {
            reset();
            return;
        }

        boolean liquidStart = TeslaMapsConfig.get().bobberTimerOnLiquid;
        if (hook != lastHook) {
            lastHook = hook;
            pulls++;
            caught = false;
            startedAt = liquidStart ? 0 : System.currentTimeMillis();
        }
        // the hook keeps bobbing after it landed, so the first touch is what starts the clock
        if (liquidStart && !caught && startedAt == 0 && (hook.isInWater() || hook.isInLava())) {
            startedAt = System.currentTimeMillis();
        }
    }

    private static void reset() {
        lastHook = null;
        startedAt = 0;
        caught = false;
    }

    private static boolean holdingRod(Minecraft mc) {
        ItemStack held = mc.player.getMainHandItem();
        return held.getItem() instanceof FishingRodItem;
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.bobberTimer || startedAt == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;
        if (!SkyblockUtils.isInSkyblock()) return;

        draw(context, mc, c.bobberTimerX, c.bobberTimerY, c.bobberTimerScale, text());
    }

    private static String text() {
        double seconds = (System.currentTimeMillis() - startedAt) / 1000.0;
        return "§aBobber: §f" + String.format("%.2fs", seconds);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        draw(context, mc, x, y, scale, startedAt == 0 ? "§a" + SAMPLE_TEXT : text());
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, String text) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        context.text(mc.font, text, 0, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }
}
