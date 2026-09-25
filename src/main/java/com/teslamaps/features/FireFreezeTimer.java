package com.teslamaps.features;

// Ported from Devonian's FireFreezeTimer.kt (110 ticks), NOW + anvil from Zen's FireFreezeTimer.kt
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
import net.minecraft.sounds.SoundEvents;

import java.util.List;

public final class FireFreezeTimer {

    private static final String TRIGGER = "[BOSS] The Professor: Oh? You found my Guardians' one weakness?";
    private static final int TICKS = 110;
    private static final String SAMPLE = "§bFire Freeze§f: §a5.50s";

    private static final ServerTickTimer TIMER = new ServerTickTimer();
    private static List<DungeonTimers.Row> row;
    private static long nowUntil;

    private FireFreezeTimer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("FireFreezeTimer").section("Timers").when(() -> cfg().fireFreezeTimer)
                .serverChat(FireFreezeTimer::onChat)
                .tick(FireFreezeTimer::tick)
                .hud("firefreeze", FireFreezeTimer::render);
        HudEditElement.of("fireFreeze", "Fire Freeze Timer").ctx(HudCtx.DUNGEON).section("Timers")
                .enabled(() -> cfg().fireFreezeTimer)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, m, x, y, s) -> DungeonTimers.drawRows(g, m, x, y, s, List.of(new DungeonTimers.Row(SAMPLE, null))));
    }

    private static boolean floor3() {
        DungeonFloor f = DungeonManager.getCurrentFloor();
        return f != null && f.getLevel() == 3;
    }

    private static void onChat(String text) {
        if (!floor3() || text.indexOf("Professor") < 0) return;
        if (!TRIGGER.equals(StringUtil.stripTrim(text))) return;
        nowUntil = 0;
        TIMER.start(TICKS, FireFreezeTimer::onDone);
    }

    private static void onDone() {
        nowUntil = System.currentTimeMillis() + 1500;
        Minecraft mc = Minecraft.getInstance();
        if (cfg().fireFreezeSound && mc.player != null) mc.player.playSound(SoundEvents.ANVIL_LAND, 1f, 1f);
    }

    private static void tick() {
        if (!DungeonManager.isInDungeon()) { TIMER.stop(); nowUntil = 0; }
        if (TIMER.active()) {
            row = List.of(new DungeonTimers.Row("§bFire Freeze§f: §a" + ServerTickTimer.seconds(TIMER.remaining()), null));
        } else if (System.currentTimeMillis() < nowUntil) {
            row = List.of(new DungeonTimers.Row("§bFire Freeze§f: §c§lNOW", null));
        } else {
            row = null;
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<DungeonTimers.Row> r = row;
        if (r == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        TeslaMapsConfig c = cfg();
        DungeonTimers.drawRows(g, mc, c.fireFreezeX, c.fireFreezeY, c.fireFreezeScale, r);
    }
}
