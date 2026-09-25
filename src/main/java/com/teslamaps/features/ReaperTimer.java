package com.teslamaps.features;

// Ported from DulkirMod's ReaperDisplay.kt (sound + armor bar) and Zen's ReaperTimer.kt (sneak + HUD)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public final class ReaperTimer {

    private static final long ACTIVE = 6000, COOLDOWN = 25000;
    private static final String SAMPLE = "Reaper: §a4.2s";

    private static Feature feature;
    private static HudEditElement hud;
    private static boolean wearing, wasSneaking;
    private static long started;
    private static String line;
    private static int ticks;
    private static ItemStack chest, legs, boots;
    public static volatile boolean barActive;

    private ReaperTimer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("reaperTimer", "Reaper Armor Timer").section("Timers")
                .enabled(() -> cfg().reaperTimer)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        feature = Feature.of("ReaperTimer").section("Timers").when(() -> cfg().reaperTimer)
                .tick(ReaperTimer::tick)
                .hud("reapertimer", ReaperTimer::render)
                .config("Timers", b -> {
                    b.label("Reaper Armor");
                    b.toggle("Reaper Armor Timer", () -> cfg().reaperTimer, v -> cfg().reaperTimer = v)
                            .desc("6s ability, then cooldown, while wearing full Reaper armor (/tmap gui)").tags("reaper");
                    b.toggle("  Cooldown Bar On Armor", () -> cfg().reaperTimerBar, v -> cfg().reaperTimerBar = v)
                            .desc("25s draining durability bar on chestplate, leggings and boots");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (++ticks % 5 == 0) {
            chest = p.getItemBySlot(EquipmentSlot.CHEST);
            legs = p.getItemBySlot(EquipmentSlot.LEGS);
            boots = p.getItemBySlot(EquipmentSlot.FEET);
            wearing = SkyblockUtils.isInSkyblock()
                    && "REAPER_CHESTPLATE".equals(ItemUtil.skyblockId(chest))
                    && "REAPER_LEGGINGS".equals(ItemUtil.skyblockId(legs))
                    && "REAPER_BOOTS".equals(ItemUtil.skyblockId(boots));
        }
        boolean sneak = p.isShiftKeyDown();
        if (sneak && !wasSneaking && wearing) trigger();
        wasSneaking = sneak;

        long since = System.currentTimeMillis() - started;
        barActive = wearing && cfg().reaperTimerBar && since < COOLDOWN;
        if (!wearing || since >= COOLDOWN) { line = null; return; }
        line = since < ACTIVE
                ? "Reaper: §a" + String.format(Locale.ROOT, "%.1fs", (ACTIVE - since) / 1000.0)
                : "Reaper: §c" + String.format(Locale.ROOT, "%.1fs", (COOLDOWN - since) / 1000.0);
    }

    private static void trigger() {
        long now = System.currentTimeMillis();
        if (now - started < COOLDOWN) return;
        started = now;
    }

    // SoundEngine.play, main thread
    public static void onSound(SoundInstance s) {
        if (!wearing || feature == null || !feature.active()) return;
        try {
            if ("minecraft:entity.zombie_villager.cure".equals(s.getIdentifier().toString())) trigger();
        } catch (Throwable ignored) {}
    }

    private static boolean isWorn(ItemStack s) {
        return s == chest || s == legs || s == boots;
    }

    /** ItemStack bar mixins: null = leave vanilla. */
    public static boolean barFor(ItemStack s) {
        return barActive && s != null && isWorn(s) && cfg().reaperTimer && cfg().reaperTimerBar;
    }

    public static int barWidth() {
        long left = COOLDOWN - (System.currentTimeMillis() - started);
        return (int) Math.round(13.0 * Math.max(0, Math.min(COOLDOWN, left)) / COOLDOWN);
    }

    public static int barColor() {
        long since = System.currentTimeMillis() - started;
        return since < ACTIVE ? 0x55FF55 : 0xFF5555;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = line;
        if (l == null || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
