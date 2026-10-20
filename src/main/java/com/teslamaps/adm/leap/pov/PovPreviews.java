package com.teslamaps.adm.leap.pov;

// Ported from ADM's pov/PovPreviews.kt
import com.teslamaps.adm.Adm;
import com.teslamaps.adm.leap.AdmLeapMenu;
import com.teslamaps.adm.leap.ShowIn;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Each teammate's own view inside their quarter of the ADM leap menu. CHEAT. */
public final class PovPreviews {

    private PovPreviews() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    /** Read by the leap menu: its boxes shrink and fade while the previews are up. */
    public static float overlayScale = 1f;
    public static float overlayAlpha = 1f;

    private static HudEditElement costHud;
    private static final String COST_SAMPLE = "§7POV §f1.8§7ms  §f4§7/4";

    public static int fade(int argb) {
        if (overlayAlpha >= 1f) return argb;
        int a = Math.max(0, Math.min(255, (int) ((argb >>> 24) * overlayAlpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static PovPose.Mode mode() {
        String s = cfg().admPovHeadSmoothing;
        if ("Raw".equals(s)) return PovPose.Mode.RAW;
        if ("Custom".equals(s)) return PovPose.Mode.CUSTOM;
        return PovPose.Mode.VANILLA;
    }

    public static void register() {
        costHud = HudEditElement.of("admPovCost", "POV Cost").ctx(HudEditElement.HudCtx.DUNGEON).section(Adm.SECTION)
                .enabled(() -> cfg().admPovPreviews && cfg().admPovShowCost)
                .size(() -> Minecraft.getInstance().font.width(COST_SAMPLE), () -> 9)
                .preview((g, mc, x, y, s) -> line(g, x, y, s, COST_SAMPLE));
        Feature.of("AdmPovPreviews").section(Adm.SECTION).legit().when(() -> cfg().admPovPreviews)
                .tickAlways(PovPreviews::tick)
                .serverChat(ShowIn::onChat)
                .hud("admpovcost", PovPreviews::renderCost)
                .config("ADM Leap & Recore", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("POV Previews");
                    b.toggle("POV Previews", () -> c.admPovPreviews, v -> { c.admPovPreviews = v; if (!v) onDisable(); }).cheat()
                            .desc("Renders each teammate's first-person view into their quarter of the leap menu (ADM Leap Menu).");
                    b.slider("Previews Per Frame", 1, 4, () -> (float) c.admPovPerFrame, v -> c.admPovPerFrame = Math.round(v), 0)
                            .desc("Feeds refreshed each frame; the rest keep their last image. Higher is smoother and costs more.");
                    b.slider("Resolution", 0.25f, 1f, () -> c.admPovResolution, v -> c.admPovResolution = Math.round(v * 20f) / 20f, 2)
                            .desc("Render scale of each feed, as a fraction of its quadrant. Lower is cheaper and softer.");
                    b.slider("Opacity", 0.1f, 1f, () -> c.admPovOpacity, v -> c.admPovOpacity = Math.round(v * 20f) / 20f, 2)
                            .desc("How opaque the previews are. Below 1 you can see your own game through them (your own view keeps rendering).");
                    b.slider("Leap Box Size", 0.3f, 1f, () -> c.admPovLeapBoxScale, v -> c.admPovLeapBoxScale = Math.round(v * 20f) / 20f, 2)
                            .desc("Shrinks the leap boxes toward the centre while the previews are showing.");
                    b.slider("Leap Box Opacity", 0.1f, 1f, () -> c.admPovLeapBoxOpacity, v -> c.admPovLeapBoxOpacity = Math.round(v * 20f) / 20f, 2)
                            .desc("Fades the background of the leap boxes (not the head or name) while the previews are showing.");
                    b.toggle("Skip Own View", () -> c.admPovSkipOwnView, v -> c.admPovSkipOwnView = v)
                            .desc("Skips rendering your own view while the previews cover the screen. Saves a full world render per frame.");
                    b.dropdown("Head Smoothing", new String[]{"Vanilla", "Raw", "Custom"}, () -> c.admPovHeadSmoothing, v -> c.admPovHeadSmoothing = v)
                            .desc("How a teammate's head movement is interpolated for their preview.");
                    b.slider("Smoothing Ticks", 0, 6, () -> (float) c.admPovSmoothingTicks, v -> c.admPovSmoothingTicks = Math.round(v), 0)
                            .desc("How far in the past Custom renders the pose, in ticks (50 ms each). 0 is the same as Raw.");
                    b.label("Keep HUDs On Top");
                    b.toggle("Keep All HUDs", () -> c.admPovKeepAll, v -> c.admPovKeepAll = v)
                            .desc("Redraws every TeslaMaps HUD that is on over the previews. Off: only the ones ticked below.");
                    b.toggle("Keep Tick Timers", () -> c.admPovKeepTickTimers, v -> c.admPovKeepTickTimers = v).desc("Redraws the boss tick timers over the previews.");
                    b.toggle("Keep Invincibility Timer", () -> c.admPovKeepInvincibility, v -> c.admPovKeepInvincibility = v).desc("Redraws the Invincibility Timer HUD over the previews.");
                    b.toggle("Keep Melody Display", () -> c.admPovKeepMelody, v -> c.admPovKeepMelody = v).desc("Redraws the Melody progress HUD over the previews.");
                    b.toggle("Keep Role HUD", () -> c.admPovKeepRoleHud, v -> c.admPovKeepRoleHud = v).desc("Redraws the role HUD over the previews.");
                    b.toggle("Keep Term Info", () -> c.admPovKeepTermInfo, v -> c.admPovKeepTermInfo = v).desc("Redraws the Term Info HUDs and Section Time over the previews.");
                    b.label("Show In");
                    b.toggle("Only In Blood Rush", () -> c.admPovOnlyBloodRush, v -> c.admPovOnlyBloodRush = v)
                            .desc("Shows the previews on blood rush. Adds to the other Only In places; none ticked is everywhere.");
                    b.toggle("Only In Boss", () -> c.admPovOnlyBoss, v -> c.admPovOnlyBoss = v)
                            .desc("Shows the previews in boss. Adds to the other Only In places; none ticked is everywhere.");
                    b.toggle("Only In Goldor", () -> c.admPovOnlyGoldor, v -> c.admPovOnlyGoldor = v)
                            .desc("Shows the previews in Goldor (F7/M7 phase 3). Adds to the other Only In places; none ticked is everywhere.");
                    b.toggle("Show Cost", () -> c.admPovShowCost, v -> c.admPovShowCost = v)
                            .desc("HUD line with the milliseconds the previews added to the last frame. Position in /tmap gui.");
                });
    }

    private static List<ShowIn.Place> places() {
        List<ShowIn.Place> out = new ArrayList<>();
        if (cfg().admPovOnlyBloodRush) out.add(ShowIn.Place.BLOOD_RUSH);
        if (cfg().admPovOnlyBoss) out.add(ShowIn.Place.BOSS);
        if (cfg().admPovOnlyGoldor) out.add(ShowIn.Place.GOLDOR);
        return out;
    }

    /** Something to draw into: on, not a cheat-blocked session, the ADM leap menu up in an allowed place. */
    public static boolean wants() {
        if (!cfg().admPovPreviews || !Adm.on() || PovCapture.disabledForSession || LegitMode.blocksCheats()) return false;
        if (!AdmLeapMenu.active(Minecraft.getInstance().screen)) return false;
        return ShowIn.allows(places());
    }

    /** Before the leap menu's boxes are drawn: the previews' blits and the overlay factors. */
    public static void onScreenExtract(GuiGraphicsExtractor g) {
        overlayScale = 1f;
        overlayAlpha = 1f;
        try {
            if (wants()) { overlayScale = cfg().admPovLeapBoxScale; overlayAlpha = cfg().admPovLeapBoxOpacity; }
            PovCapture.onScreenExtract(g);
        } catch (Throwable t) {
            com.teslamaps.TeslaMaps.LOGGER.error("[adm] pov gui failed", t);
        }
    }

    private static Object lastLevel;

    private static void tick() {
        Object level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            lastLevel = level;
            ShowIn.reset();
            PovPose.reset();
            PovCapture.onWorldChange();
        }
        PovPose.onClientTick();
        if (!wants()) PovCapture.releaseNow();
    }

    private static void onDisable() {
        overlayScale = 1f;
        overlayAlpha = 1f;
        PovPose.reset();
        PovCapture.requestFree();
    }

    /** Client tick while the feature is off: free the targets once. */
    public static void tickOff() {
        if (!cfg().admPovPreviews || !Adm.on()) PovCapture.releaseNow();
    }

    private static void renderCost(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = PovCapture.costLine();
        if (l == null || costHud == null) return;
        line(g, costHud.x(), costHud.y(), costHud.scale(), l);
    }

    private static void line(GuiGraphicsExtractor g, int x, int y, float s, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        g.text(Minecraft.getInstance().font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    /** Whether the HUD with registry id [id] is drawn again above the previews. */
    public static boolean keepsHud(String id) {
        TeslaMapsConfig c = cfg();
        if (c.admPovKeepAll) return true;
        String l = id.toLowerCase();
        if (c.admPovKeepTickTimers && (l.contains("bosstick") || l.contains("ticktimer"))) return true;
        if (c.admPovKeepInvincibility && (l.contains("invinc") || l.contains("mask"))) return true;
        if (c.admPovKeepMelody && l.contains("melody")) return true;
        if (c.admPovKeepRoleHud && l.contains("role")) return true;
        return c.admPovKeepTermInfo && (l.contains("terminfo") || l.contains("sectiontime"));
    }
}
