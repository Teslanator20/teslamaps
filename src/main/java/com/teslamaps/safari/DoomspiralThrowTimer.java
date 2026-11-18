package com.teslamaps.safari;

// Ported from Sparkling Mutuals' DoomspiralThrowTimer.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/** Server-tick countdown from the summon line to the Doomspiral coming up, minus a lead: THROW NOW. */
public final class DoomspiralThrowTimer {

    private static final String STIRS = "Something stirs in the Haunted Biome...";
    private static final int LINGER = 40;

    private static final ServerTickTimer timer = new ServerTickTimer();
    private static boolean triggered;
    private static int throwAt;
    private static boolean wardenNear;
    private static Level level;
    private static HudEditElement hud;

    private DoomspiralThrowTimer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("safariDoomThrow", "Doomspiral Throw").ctx(HudEditElement.HudCtx.SAFARI).section("Critter Safari")
                .enabled(() -> cfg().safariDoomThrowTimer)
                .size(() -> TextHud.width(lines(40)), () -> TextHud.height(2))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines(40), TextHud.width(lines(40))));
        Feature.of("DoomspiralThrowTimer").section("Critter Safari").legit().when(() -> cfg().safariDoomThrowTimer)
                .tick(DoomspiralThrowTimer::tick)
                .hudHiddenInInventory("doomspiralthrow", DoomspiralThrowTimer::render)
                .serverChat(DoomspiralThrowTimer::onChat)
                .config("Critter Safari", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Doomspiral Throw Timer");
                    b.toggle("Doomspiral Throw Timer", () -> c.safariDoomThrowTimer, v -> c.safariDoomThrowTimer = v).cheat()
                            .desc("Counts server ticks from \"Something stirs in the Haunted Biome...\" until the Doomspiral surfaces and says THROW NOW a little early. Shows next to the Warden. Position in /tmap gui")
                            .tags("doomspiral", "throw", "capsule", "timer", "warden");
                    b.slider("  Emergence (ticks)", 1f, 600f, () -> (float) c.safariDoomThrowTicks,
                            v -> c.safariDoomThrowTicks = Math.round(v), 0);
                    b.slider("  Throw Lead (ticks)", 0f, 40f, () -> (float) c.safariDoomThrowLead,
                            v -> c.safariDoomThrowLead = Math.round(v), 0);
                });
    }

    private static void clear() {
        timer.stop();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            clear();
            triggered = false;
            level = mc.level;
        }
        if (!SafariData.inSafari()) {
            clear();
            return;
        }
        SafariData.Critter doom = SafariData.byName("Doomspiral");
        if (doom != null && SafariTracker.hasCaught(doom)) clear();
        wardenNear = timer.active() && mc.player != null && !mc.level.getEntitiesOfClass(Warden.class, mc.player.getBoundingBox().inflate(10.0),
                w -> !w.isRemoved() && w.isAlive() && mc.player.distanceToSqr(w) <= 100.0).isEmpty();
    }

    private static void onChat(String raw) {
        if (triggered || !SafariData.inSafari()) return;
        boolean hit = false;
        for (String line : StringUtil.strip(raw).split("\n")) {
            if (line.trim().equals(STIRS)) hit = true;
        }
        if (!hit) return;
        SafariData.Critter doom = SafariData.byName("Doomspiral");
        if (doom != null && SafariTracker.hasCaught(doom)) return;
        triggered = true;
        int emergence = Math.max(1, Math.min(600, cfg().safariDoomThrowTicks));
        int lead = Math.max(0, Math.min(40, cfg().safariDoomThrowLead));
        throwAt = Math.max(emergence - lead, 0);
        timer.start(throwAt + LINGER);
    }

    private static List<String> lines(long ticks) {
        String row;
        if (ticks == 0) {
            row = "§aTHROW NOW";
        } else {
            String col = ticks <= 10 ? "§c" : ticks <= 25 ? "§6" : "§f";
            row = col + ticks + " ticks (" + String.format(Locale.ROOT, "%.2f", ticks / 20.0) + "s)";
        }
        return List.of("§7Doomspiral throw", row);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || hud == null || !timer.active() || !wardenNear) return;
        long left = Math.max(timer.remaining() - LINGER, 0);
        List<String> ls = lines(left);
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), ls, TextHud.width(ls));
    }
}
