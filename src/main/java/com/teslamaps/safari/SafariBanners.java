package com.teslamaps.safari;

// Ported from critterMod's EncounterAlerts + FullScreenAlert
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.utils.LoudSound;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Stage banners for Gemzie / Wumpa / Doomspiral and the full-screen sparkling call. */
public final class SafariBanners {

    public enum Stage { READY, STARTED, DONE }

    private static final long BANNER_MS = 8000;
    private static final long STAGE_COOLDOWN_MS = 20_000;
    private static final float BANNER_SCALE = 2.0f;
    private static final int READY = 0xFFFFAA00, STARTED = 0xFFFF5555, DONE = 0xFF55FF55;
    private static final int GEMZIE_PER_CHAMBER = 3;

    private static final long FULL_MS = 7000;
    private static final long FULL_FADE_MS = 1200;
    private static final float TITLE_SCALE = 3.0f, SUB_SCALE = 1.6f;

    private static final Map<String, Long> lastFired = new HashMap<>();
    private static int gemzieRemaining = 0;

    private static String banner;
    private static int bannerColor;
    private static long bannerAt;

    private static String headline, subject, where;
    private static int tint;
    private static long fullAt;

    private SafariBanners() {}

    public static void register() {
        com.teslamaps.registry.Feature.of("SafariBanners").section("Critter Safari")
                .when(() -> TeslaMapsConfig.get().safariTracker
                        && (TeslaMapsConfig.get().safariEncounterBanner || TeslaMapsConfig.get().safariSparklingScreen))
                .hud("safaribanners", SafariBanners::render);
    }

    /** Banners replace the old chat alerts for that encounter. */
    public static boolean handles(String boss) {
        return TeslaMapsConfig.get().safariEncounterBanner && on(boss);
    }

    public static void onChat(String line) {
        if (!TeslaMapsConfig.get().safariEncounterBanner) return;
        if (line.startsWith("A rumbling sound can be heard")) {
            gemzieRemaining = GEMZIE_PER_CHAMBER;
            fire("Gemzie", Stage.READY, "chamber open, 3 to catch", 1.4f);
        } else if (line.startsWith("You hear the sound of massive footsteps")) {
            fire("Wumpa", Stage.READY, "it wakes in ~30s", 1.4f);
        } else if (line.startsWith("The Wumpa has awoken")) {
            fire("Wumpa", Stage.STARTED, "fight is live", 1.8f);
        } else if (line.startsWith("The cave opens up again")) {
            fire("Wumpa", Stage.DONE, "the cave has reopened", 1.2f);
        } else if (line.startsWith("You used the Soothing Incense to light the candle")) {
            fire("Doomspiral", Stage.READY, "ritual underway", 1.4f);
        } else if (line.startsWith("Your ritual summoned a Doomspiral")) {
            fire("Doomspiral", Stage.STARTED, "fight is live", 1.8f);
        } else if (line.startsWith("The Doomspiral retreats back underground")) {
            fire("Doomspiral", Stage.DONE, "it retreated", 1.2f);
        }
    }

    /** Every catch this run, by anyone. */
    public static void onCatch(String critter) {
        if (!TeslaMapsConfig.get().safariEncounterBanner) return;
        switch (critter) {
            case "Wumpa", "Doomspiral" -> fire(critter, Stage.DONE, "caught", 1.2f);
            case "Gemzie" -> {
                if (gemzieRemaining <= 0) return;
                if (--gemzieRemaining == 0) fire("Gemzie", Stage.DONE, "all 3 caught", 1.2f);
            }
            default -> { }
        }
    }

    public static void reset() {
        lastFired.clear();
        gemzieRemaining = 0;
        banner = null;
        headline = null;
    }

    public static void fullScreen(String call, String subject, String detail, int rgb) {
        headline = call;
        SafariBanners.subject = subject;
        where = detail;
        tint = rgb & 0xFFFFFF;
        fullAt = System.currentTimeMillis();
    }

    private static boolean on(String boss) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return switch (boss) {
            case "Gemzie" -> c.safariBannerGemzie;
            case "Wumpa" -> c.safariBannerWumpa;
            case "Doomspiral" -> c.safariBannerDoomspiral;
            default -> false;
        };
    }

    // fails open when the biome is unknown, silence would look broken
    private static boolean inItsBiome(String boss) {
        if (!TeslaMapsConfig.get().safariBannerBiomeOnly) return true;
        SafariData.Critter critter = SafariData.byName(boss);
        SafariData.Biome here = SafariData.currentBiome();
        return critter == null || here == null || critter.biome() == here;
    }

    private static void fire(String boss, Stage stage, String detail, float pitch) {
        if (!on(boss) || !inItsBiome(boss)) return;
        // gemzie chambers repeat and ready/done can be seconds apart
        if (!boss.equals("Gemzie")) {
            long now = System.currentTimeMillis();
            Long prev = lastFired.get(boss + ":" + stage);
            if (prev != null && now - prev < STAGE_COOLDOWN_MS) return;
            lastFired.put(boss + ":" + stage, now);
        }

        String text = boss.toUpperCase() + " " + stage.name();
        banner = text;
        bannerColor = switch (stage) {
            case READY -> READY;
            case STARTED -> STARTED;
            case DONE -> DONE;
        };
        bannerAt = System.currentTimeMillis();

        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.safariAlertVolume > 0) LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), c.safariAlertVolume, pitch);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §e" + text + " §7— " + detail));
        Announce.send(c.safariBannerChannel, boss + " " + stage.name().toLowerCase());
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        long now = System.currentTimeMillis();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Font font = mc.font;
        int width = g.guiWidth(), height = g.guiHeight();

        if (headline != null && c.safariSparklingScreen) {
            long age = now - fullAt;
            if (age > FULL_MS) {
                headline = null;
            } else {
                int alpha = age > FULL_MS - FULL_FADE_MS ? (int) (0xFF * (FULL_MS - age) / (double) FULL_FADE_MS) : 0xFF;
                int ta = Math.max(alpha, 8) << 24;
                g.fill(0, 0, width, height, (alpha / 10) << 24 | tint);
                int band = Math.max(2, height / 90);
                g.fill(0, 0, width, band, (alpha << 24) | tint);
                g.fill(0, height - band, width, height, (alpha << 24) | tint);

                g.pose().pushMatrix();
                g.pose().scale(TITLE_SCALE, TITLE_SCALE);
                g.centeredText(font, headline, (int) (width / (2 * TITLE_SCALE)), (int) (height * 0.36 / TITLE_SCALE),
                        ta | tint);
                g.pose().popMatrix();

                g.pose().pushMatrix();
                g.pose().scale(SUB_SCALE, SUB_SCALE);
                int sy = (int) (height * 0.47 / SUB_SCALE);
                g.centeredText(font, subject, (int) (width / (2 * SUB_SCALE)), sy, ta | 0xFFFFFF);
                if (where != null) {
                    g.centeredText(font, where, (int) (width / (2 * SUB_SCALE)), sy + 12, ta | 0xFFFFFF);
                }
                g.pose().popMatrix();
            }
        }

        if (banner != null && c.safariEncounterBanner) {
            long age = now - bannerAt;
            if (age > BANNER_MS) {
                banner = null;
                return;
            }
            int alpha = age > BANNER_MS - 1000 ? (int) (0xFF * (BANNER_MS - age) / 1000.0) : 0xFF;
            g.pose().pushMatrix();
            g.pose().scale(BANNER_SCALE, BANNER_SCALE);
            g.centeredText(font, banner, (int) (width / (2 * BANNER_SCALE)), (int) (height * 0.22 / BANNER_SCALE),
                    (Math.max(alpha, 8) << 24) | (bannerColor & 0xFFFFFF));
            g.pose().popMatrix();
        }
    }
}
