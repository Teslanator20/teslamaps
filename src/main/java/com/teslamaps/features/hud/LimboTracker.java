package com.teslamaps.features.hud;

// Ported from SkyHanni's features/misc/limbo/LimboTimeTracker.kt (no mod API here, so limbo comes from chat)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class LimboTracker {

    private static final List<String> SAMPLE = List.of("§eIn Limbo since §b12m 34s");
    private static final AABB BEDWARS_LIMBO = new AABB(-662, 43, -76, -619, 86, -27);

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, timer;
    private static boolean realLimbo, fakeLimbo;
    private static long joinedAt;
    private static boolean shownPb;
    private static Object lastLevel;

    private LimboTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("limboTracker", "Limbo Time")
                .enabled(() -> cfg().limboTracker)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("LimboTracker").when(() -> cfg().limboTracker)
                .tick(LimboTracker::tick)
                .serverChat(LimboTracker::onChat)
                .hud("limbotracker", LimboTracker::render)
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Limbo");
                    b.toggle("Limbo Time Tracker", () -> c.limboTracker, v -> c.limboTracker = v)
                            .desc("\"In Limbo since ...\" while in limbo, duration and personal best when you leave. Position in /tmap gui.");
                    b.info("  PB §e" + format(c.limboPersonalBest) + " §7· total §e" + format(c.limboTotalSeconds));
                });
    }

    private static boolean inLimbo() {
        return realLimbo || fakeLimbo;
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (t.equals("You were spawned in Limbo.") || t.equals("You are AFK. Move around to return from AFK.")) {
            boolean was = inLimbo();
            realLimbo = true;
            lastLevel = Minecraft.getInstance().level;
            update(was);
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            boolean was = inLimbo();
            realLimbo = false;
            fakeLimbo = false;
            if (was) leave();
        }
        if (++timer % 10 != 0) return;
        boolean was = inLimbo();
        fakeLimbo = SkyblockUtils.isOnHypixel() && ScoreboardUtils.getScoreboardTitle().contains("BED WARS")
                && BEDWARS_LIMBO.contains(mc.player.position());
        update(was);

        if (!inLimbo()) {
            lines = List.of();
            return;
        }
        long secs = (System.currentTimeMillis() - joinedAt) / 1000;
        long pb = cfg().limboPersonalBest;
        if (!shownPb && pb > 0 && secs > pb) {
            shownPb = true;
            msg("§d§lPERSONAL BEST§f! You've passed your old record of §e" + format(pb) + "§f!");
        }
        lines = List.of("§eIn Limbo since §b" + format(secs));
        width = TextHud.width(lines);
    }

    private static void update(boolean was) {
        boolean now = inLimbo();
        if (now && !was) {
            joinedAt = System.currentTimeMillis();
            shownPb = false;
        } else if (!now && was) {
            leave();
        }
    }

    private static void leave() {
        lines = List.of();
        if (joinedAt == 0) return;
        long secs = (System.currentTimeMillis() - joinedAt) / 1000;
        joinedAt = 0;
        TeslaMapsConfig c = cfg();
        c.limboTotalSeconds += secs;
        if (secs > c.limboPersonalBest) {
            long old = c.limboPersonalBest;
            c.limboPersonalBest = secs;
            msg("§fYou were in Limbo for §e" + format(secs) + "§f! §d§lPERSONAL BEST§f!");
            if (old > 0) msg("§fYour previous best was §e" + format(old) + "§f.");
        } else {
            msg("§fYou were in Limbo for §e" + format(secs) + "§f.");
        }
        TeslaMapsConfig.save();
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }

    private static String format(long s) {
        if (s >= 3600) return s / 3600 + "h " + (s / 60 % 60) + "m";
        if (s >= 60) return s / 60 + "m " + (s % 60) + "s";
        return s + "s";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty() || hud == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
