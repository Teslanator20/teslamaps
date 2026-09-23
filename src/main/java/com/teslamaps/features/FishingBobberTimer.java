package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

/** How long the bobber has been out, either since the cast or since it hit the liquid. */
public class FishingBobberTimer {

    public static final String SAMPLE_TEXT = "Bobber: 12.34s";

    private static FishingHook lastHook = null;
    private static long startedAt = 0;
    private static boolean caught = false;
    private static long pulls = 0;
    private static boolean pinged = false;
    private static String timeText = "";
    private static int timeColor = 0xFFFFFFFF;

    public static void register() {
        com.teslamaps.registry.Feature.of("FishingBobberTimer.world").section("Fishing")
                .when(() -> TeslaMapsConfig.get().bobberTimer && TeslaMapsConfig.get().bobberTimerWorld)
                .world(FishingBobberTimer::renderWorld);
    }

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

        FishingHook hook = bobber(mc);
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
        updateText();
    }

    private static FishingHook bobber(Minecraft mc) {
        FishingHook hook = mc.player.fishing;
        if (hook != null && !hook.isRemoved()) return hook;
        if (mc.level == null) return null;
        // player.fishing can be set late
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof FishingHook h && !h.isRemoved() && h.getPlayerOwner() == mc.player) return h;
        }
        return null;
    }

    private static void updateText() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (startedAt == 0) {
            pinged = false;
            return;
        }
        double seconds = (System.currentTimeMillis() - startedAt) / 1000.0;
        timeText = String.format("%.2fs", seconds);
        boolean late = c.bobberTimerThreshold && seconds >= threshold(c);
        timeColor = late ? TeslaMapsConfig.parseColor(c.colorBobberTimerLate) : TeslaMapsConfig.parseColor(c.colorBobberTimer);
        if (late && !pinged) {
            pinged = true;
            if (c.bobberTimerPing && c.bobberTimer) LoudSound.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 2f, 2f);
        } else if (!late) {
            pinged = false;
        }
    }

    private static double threshold(TeslaMapsConfig c) {
        double max = 20;
        if (c.bobberTimerSlug && "SLUG".equals(com.teslamaps.features.pets.PetTracker.internalName())) {
            max *= 1 - 0.005 * com.teslamaps.features.pets.PetTracker.level();
        }
        return max;
    }

    private static void reset() {
        lastHook = null;
        startedAt = 0;
        caught = false;
        pinged = false;
    }

    private static void renderWorld(PoseStack matrices, Vec3 cam) {
        if (startedAt == 0 || lastHook == null || !SkyblockUtils.isInSkyblock()) return;
        Vec3 pos = lastHook.position().add(0, 1.2, 0);
        com.teslamaps.render.ESPRenderer.drawText(matrices, timeText, pos, 1f, cam, true, true, timeColor);
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

        draw(context, mc, c.bobberTimerX, c.bobberTimerY, c.bobberTimerScale, timeText, c.bobberTimerThreshold ? timeColor : 0xFFFFFFFF);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        if (startedAt == 0) draw(context, mc, x, y, scale, "12.34s", 0xFFFFFFFF);
        else draw(context, mc, x, y, scale, timeText, TeslaMapsConfig.get().bobberTimerThreshold ? timeColor : 0xFFFFFFFF);
    }

    private static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, String time, int color) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        context.text(mc.font, "§aBobber: ", 0, 0, 0xFFFFFFFF);
        context.text(mc.font, time, mc.font.width("Bobber: "), 0, color);
        pose.popMatrix();
    }
}
