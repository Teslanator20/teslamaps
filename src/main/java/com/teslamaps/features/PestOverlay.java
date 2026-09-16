package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.screen.AppleColors;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.SoundOptions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Pest cooldown, live pest count and infested plots, in one panel. */
public class PestOverlay {

    private static final int PANEL_WIDTH = 126;
    private static final int PADDING = 10;
    private static final int LINE = 11;
    private static int rows() {
        int rows = 3;   // Spawn, Blocks/s, Philip
        if (PestData.spray() != null) rows++;
        if (PestData.bonusPestChance() > 0 && PestRoutine.pestChanceGearOn()) rows++;
        return rows;
    }

    private static boolean philipWasActive = false;
    private static boolean philipWarned = false;

    private static boolean wasReady = false;
    private static int lastCountdown = -1;

    /**
     * Ticks the last seconds down on screen instead of announcing them once.
     * <p>
     * A single title at five seconds is gone before it means anything mid-row —
     * seeing the number fall tells you whether to start walking back.
     */
    private static void countdown(TeslaMapsConfig config, boolean ready, int seconds) {
        if (ready || seconds < 0 || seconds > config.pestCooldownWarnAt) {
            lastCountdown = -1;
            return;
        }
        if (seconds == lastCountdown) return;
        lastCountdown = seconds;

        if (config.pestCooldownTitle) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null) {
                // Stay just past a second so each number replaces the one before
                // it without the title blinking out in between.
                mc.gui.setTimes(0, 25, 5);
                mc.gui.setTitle(Component.literal("§ePests spawn soon"));
                mc.gui.setSubtitle(Component.literal("§7" + seconds + "s"));
            }
        }
        if (config.pestCooldownSound) {
            var sound = SoundOptions.resolve(config.pestCooldownSoundName);
            if (sound == null) return;
            // Rises as it counts down, so the last tick is unmistakable.
            int span = Math.max(config.pestCooldownWarnAt, 1);
            float pitch = 1.0f + (span - seconds) / (float) span * 0.6f;
            LoudSound.play(sound, config.pestCooldownVolume, pitch);
        }
    }

    private static long lastNag = 0L;
    private static final long NAG_INTERVAL = 5_000L;

    /**
     * Both reminders only make sense while the player is actually farming. A
     * broken crop is the farming xp signal we have; standing in the garden doing
     * anything else was getting nagged for no reason.
     */
    private static final long FARMING_WINDOW = 20_000L;

    private static boolean farming() {
        return System.currentTimeMillis() - FarmSpeed.lastBreakMillis() < FARMING_WINDOW;
    }

    /**
     * The spawn line in chat arrives a good second before the tab list catches
     * up, so the teleport reminder is fired straight off it instead of waiting
     * for the pest count to appear.
     */
    public static void onPestSpawn(String plot) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.pestTpReminder || !SkyblockUtils.isInGarden() || !farming()) return;

        String here = PestData.currentPlot();
        if (plot != null && here != null && here.equalsIgnoreCase(plot)) return;

        warn(config, "§cPests in Plot - " + (plot == null ? "?" : plot), "§7not on their plot");
    }

    /**
     * Keeps warning while something is being left on the table, rather than
     * saying it once and letting the moment pass.
     * <p>
     * Two cases: the spawn cooldown is up but the pest chance gear is still off,
     * and pests are alive somewhere else while the player keeps farming.
     */
    private static void nag(TeslaMapsConfig config, boolean ready) {
        String title = null;
        String subtitle = null;

        if (!farming()) {
            lastNag = 0L;
            return;
        }

        int alive = PestData.alive();
        if (config.pestGearReminder && ready && alive == 0 && !PestRoutine.pestChanceGearOn()) {
            title = "§cPest Chance Loadout";
            subtitle = "§7spawn cooldown is up";
        } else if (config.pestTpReminder && alive >= 2 && PestData.pestsOnCurrentPlot() == 0) {
            title = "§c" + alive + " Pests waiting";
            subtitle = "§7not on their plot";
        }

        if (title == null) {
            lastNag = 0L;
            return;
        }

        if (System.currentTimeMillis() - lastNag < NAG_INTERVAL) return;
        warn(config, title, subtitle);
    }

    private static void warn(TeslaMapsConfig config, String title, String subtitle) {
        lastNag = System.currentTimeMillis();

        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.setTimes(0, 45, 5);
            mc.gui.setTitle(Component.literal(title));
            mc.gui.setSubtitle(Component.literal(subtitle));
        }
        var sound = SoundOptions.resolve(config.pestCooldownSoundName);
        if (sound != null) LoudSound.play(sound, config.pestCooldownVolume, 0.8f);
    }

    public static void tick() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!SkyblockUtils.isInGarden()) {
            wasReady = false;
            lastCountdown = -1;
            lastNag = 0L;
            // The buff is only visible in the garden tab list. Keeping the last
            // known state across a trip elsewhere made the buff look like it had
            // run out the moment the list came back empty.
            philipWasActive = false;
            philipWarned = false;
            return;
        }

        boolean ready = PestData.effectiveReady();
        int seconds = PestData.effectiveCooldownSeconds();

        countdown(config, ready, seconds);
        nag(config, ready);

        philipTick(config);

        // Arm the routine ourselves the moment the game says the cooldown is up,
        // so it no longer depends on another mod announcing it in chat.
        if (ready && !wasReady) {
            lastCountdown = -1;
            if (config.pestCooldownArmsRoutine) PestRoutine.onCooldownReady();
        }
        if (!ready) wasReady = false;
        else wasReady = true;
    }

    /**
     * Watches the Philip buff in the tab list, so the warning and the routine
     * interrupt no longer wait for another mod to announce it in chat.
     */
    private static void philipTick(TeslaMapsConfig config) {
        // The tab list takes a moment to fill after a warp. Judging the buff off a
        // list that has not arrived yet reads every return to the garden as the
        // buff having expired.
        if (!PestData.hasPestSection()) return;

        boolean active = PestData.hasPhilipBonus();
        int left = PestData.philipSeconds();

        if (active && !philipWarned && left <= config.philipWarnSeconds) {
            philipWarned = true;
            PestRoutine.warnPhilip("§cPest Fortune runs out", "§e" + left + "s left");
        }

        if (philipWasActive && !active) {
            // Ran out: warn and queue the refill for the next key press.
            philipWarned = false;
            PestRoutine.warnPhilip("§cPest Fortune expired", "§e/call philip");
            if (config.philipArmsRoutine) PestRoutine.onPhilipExpired();
        }

        if (active && left > config.philipWarnSeconds) philipWarned = false;
        philipWasActive = active;
    }

    public static boolean shouldRender() {
        return TeslaMapsConfig.get().pestOverlay && SkyblockUtils.isInGarden();
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        if (!shouldRender()) return;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        draw(context, Minecraft.getInstance(), config.pestOverlayX, config.pestOverlayY,
                config.pestOverlayScale);
    }

    public static int width(float scale) {
        return (int) (PANEL_WIDTH * scale);
    }

    public static int height(float scale) {
        return (int) ((PADDING * 2 + LINE * rows()) * scale);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        boolean ready = PestData.effectiveReady();
        boolean max = PestData.maxPests();
        int seconds = PestData.effectiveCooldownSeconds();
        int alive = PestData.alive();

        int panelHeight = PADDING * 2 + LINE * rows();

        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);

        com.teslamaps.render.gui.HudStyle.panel(context, 0, 0, PANEL_WIDTH, panelHeight, 6);

        int row = PADDING;
        String cooldownText;
        int cooldownColor;
        if (max) {
            cooldownText = "MAX PESTS";
            cooldownColor = AppleColors.ACCENT_RED;
        } else if (ready) {
            cooldownText = "READY";
            cooldownColor = AppleColors.ACCENT_GREEN;
        } else if (seconds >= 0) {
            cooldownText = seconds >= 60
                    ? String.format("%dm %02ds", seconds / 60, seconds % 60)
                    : seconds + "s";
            cooldownColor = seconds <= TeslaMapsConfig.get().pestCooldownWarnAt
                    ? AppleColors.ACCENT_RED : AppleColors.TEXT_PRIMARY;
        } else {
            cooldownText = "-";
            cooldownColor = AppleColors.TEXT_TERTIARY;
        }
        drawRow(context, mc, "Spawn", cooldownText, cooldownColor, row);

        row += LINE;
        double bps = FarmSpeed.blocksPerSecond();
        drawRow(context, mc, "Blocks/s", String.format("%.1f", bps),
                bps > 0 ? AppleColors.TEXT_PRIMARY : AppleColors.TEXT_TERTIARY, row);

        row += LINE;
        int philipLeft = PestData.philipSeconds();
        String philipText;
        int philipColor;
        if (philipLeft < 0) {
            philipText = "none";
            philipColor = AppleColors.ACCENT_RED;
        } else {
            // The bonus is always the same +200, so only the time carries news.
            philipText = philipLeft >= 60
                    ? String.format("%dm", philipLeft / 60) : philipLeft + "s";
            philipColor = philipLeft <= TeslaMapsConfig.get().philipWarnSeconds
                    ? AppleColors.ACCENT_RED : AppleColors.TEXT_PRIMARY;
        }
        drawRow(context, mc, "Philip", philipText, philipColor, row);

        String sprayName = PestData.spray();
        if (sprayName != null) {
            row += LINE;
            // The tab list only ever reports whole minutes, so the seconds were
            // always a rounded zero pretending to be precision.
            int sprayLeft = PestData.spraySeconds();
            String sprayText = sprayLeft >= 60 ? (sprayLeft / 60) + "m" : sprayLeft + "s";
            drawRow(context, mc, "Spray", sprayText,
                    sprayLeft <= 60 ? AppleColors.ACCENT_RED : AppleColors.TEXT_PRIMARY, row);
        }

        int bpc = PestData.bonusPestChance();
        if (bpc > 0 && PestRoutine.pestChanceGearOn()) {
            row += LINE;
            drawRow(context, mc, "Pest Chance", bpc + "%", AppleColors.ACCENT_GREEN, row);
        }

        context.pose().popMatrix();
    }

    private static void drawRow(GuiGraphicsExtractor context, Minecraft mc,
                                 String label, String value, int valueColor, int y) {
        context.text(mc.font, Component.literal(label), PADDING, y,
                AppleColors.TEXT_SECONDARY, false);
        int valueX = PANEL_WIDTH - PADDING - mc.font.width(value);
        context.text(mc.font, Component.literal(value), valueX, y, valueColor, false);
    }
}
