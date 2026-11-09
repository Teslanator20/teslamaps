package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.registry.HudEditElement.HudCtx;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Livid invulnerability countdown (390 server ticks), ported from Odin's and NoammAddons' LividSolver. */
public final class LividTimer {

    private static final String START = "[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows.";
    private static final int TICKS = 390;
    private static final String SAMPLE = "§aLivid: 390t";

    private static final ServerTickTimer TIMER = new ServerTickTimer();
    private static List<DungeonTimers.Row> row;

    private LividTimer() {}

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    public static void register() {
        Feature.of("LividTimer").section("Timers").when(() -> cfg().lividTimerHud)
                .serverChat(LividTimer::onChat)
                .tick(LividTimer::tick)
                .hud("lividtimer", LividTimer::render);
        HudEditElement.of("lividTimer", "Livid Timer").ctx(HudCtx.DUNGEON).section("Timers")
                .enabled(() -> cfg().lividTimerHud)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, m, x, y, s) -> DungeonTimers.drawRows(g, m, x, y, s, List.of(new DungeonTimers.Row(SAMPLE, null))));
    }

    private static boolean floor5() {
        DungeonFloor f = DungeonManager.getCurrentFloor();
        return f == DungeonFloor.F5 || f == DungeonFloor.M5;
    }

    private static void onChat(String text) {
        if (!floor5() || text.indexOf("Livid") < 0) return;
        if (!START.equals(StringUtil.stripTrim(text))) return;
        TIMER.start(TICKS, LividTimer::onDone);
    }

    private static void onDone() {
        row = null;
        if (!cfg().lividTimerHud || !cfg().lividIceSprayAlert) return;
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.setTimes(0, 40, 5);
        mc.gui.hud.setTitle(Component.literal("§bIce Spray Livid!"));
        if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
    }

    private static void tick() {
        if (!DungeonManager.isInDungeon()) TIMER.stop();
        if (!TIMER.active()) { row = null; return; }
        int t = (int) TIMER.remaining();
        String color = t > 260 ? "§a" : t > 130 ? "§e" : "§c";
        row = List.of(new DungeonTimers.Row(color + "Livid: " + t + "t", null));
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<DungeonTimers.Row> r = row;
        if (r == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        TeslaMapsConfig c = cfg();
        DungeonTimers.drawRows(g, mc, c.lividTimerX, c.lividTimerY, c.lividTimerScale, r);
    }
}
