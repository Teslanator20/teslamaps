package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.registry.HudEditElement.HudCtx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** F7 tick timers as single movable HUDs (Odin style), fed by TimerTriggers. */
public final class BossTickHuds {

    private record Slot(String key, String prefix, String label, String sample, BooleanSupplier on) {}

    private static final Slot[] SLOTS = {
            new Slot("goldortick", "goldorTickHud", "Goldor Tick", "§7Tick§f: §a2.95s", () -> cfg().goldorTickTimer),
            new Slot("stormpad", "stormPadHud", "Storm Pad", "§bPad§f: §a0.75s", () -> cfg().stormPadTimer),
            new Slot("necrondrop", "necronDropHud", "Necron Drop", "§4Necron§f: §62.10s", () -> cfg().necronDropTimer),
            new Slot("lightning", "lightningHud", "Storm Lightning", "§bLightning§f: §a28.00s", () -> cfg().lightningTimer),
            new Slot("purplepad", "purplePadHud", "Purple Pad", "§dPurple§f: §d4.80s", () -> cfg().purplePadTimer),
            new Slot("bossstart", "bossStartHud", "Boss Start", "§aStart§f: §a8.35s", () -> cfg().bossStartTimers),
            new Slot("deathtick", "deathTickHud", "Death Tick", "§cDeath§f: §61.20s", () -> cfg().deathTickTimer),
    };

    private static final Map<String, List<DungeonTimers.Row>> ROWS = new HashMap<>();

    private BossTickHuds() {}

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    public static boolean owns(String key) {
        for (Slot s : SLOTS) if (s.key.equals(key)) return true;
        return false;
    }

    public static void set(String key, String text) {
        ROWS.put(key, List.of(new DungeonTimers.Row(text, null)));
    }

    public static void clear(String key) {
        ROWS.remove(key);
    }

    public static void register() {
        for (Slot s : SLOTS) {
            HudEditElement el = HudEditElement.of(s.prefix, s.label).ctx(HudCtx.DUNGEON).section("Timers")
                    .enabled(s.on)
                    .size(() -> Minecraft.getInstance().font.width(s.sample), () -> 9)
                    .scaleRange(0.5f, 5.0f)
                    .preview((g, m, x, y, sc) -> DungeonTimers.drawRows(g, m, x, y, sc,
                            List.of(new DungeonTimers.Row(s.sample, null))));
            Feature.of("BossTickHud." + s.key).section("Timers")
                    .hud("bosstick_" + s.key, (g, d) -> render(g, s.key, el));
        }
    }

    private static void render(GuiGraphicsExtractor g, String key, HudEditElement el) {
        List<DungeonTimers.Row> row = ROWS.get(key);
        if (row == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        DungeonTimers.drawRows(g, mc, el.x(), el.y(), el.scale(), row);
    }
}
