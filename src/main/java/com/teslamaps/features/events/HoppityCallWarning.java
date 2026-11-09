package com.teslamaps.features.events;

// Ported from SkyHanni's HoppityCallWarning.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;

public final class HoppityCallWarning {

    private static final long CALL_MS = 7_000;

    private static long warnUntil;
    private static int ticks;
    private static Object lastLevel;

    private HoppityCallWarning() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("HoppityCallWarning").section("Events")
                .when(() -> cfg().hoppityCallWarning)
                .tick(HoppityCallWarning::tick)
                .serverChat(HoppityCallWarning::onChat)
                .hud("hoppitycallflash", HoppityCallWarning::render)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Hoppity Call Warning", () -> c.hoppityCallWarning, v -> c.hoppityCallWarning = v)
                            .desc("When Hoppity calls you over the Abiphone the screen flashes and a sound plays until you pick up (7s max).")
                            .tags("hoppity", "abiphone", "call", "chocolate", "rabbit");
                    b.toggle("  Flash Screen", () -> c.hoppityCallFlash, v -> c.hoppityCallFlash = v);
                    b.color("  Flash Color", () -> c.hoppityCallFlashColor, v -> c.hoppityCallFlashColor = v);
                    b.toggle("  Sound", () -> c.hoppityCallSound, v -> c.hoppityCallSound = v);
                });
    }

    private static void onChat(String raw) {
        if (!cfg().hoppityCallWarning) return;
        String t = StringUtil.stripTrim(raw);
        if (t.startsWith("✆ Hoppity ✆")) {
            if (warnUntil == 0) warnUntil = System.currentTimeMillis() + CALL_MS;
        } else if (t.startsWith("[NPC] Hoppity: ✆")) {
            warnUntil = 0;
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            warnUntil = 0;
        }
        if (warnUntil == 0) return;
        if (System.currentTimeMillis() >= warnUntil) {
            warnUntil = 0;
            return;
        }
        if (cfg().hoppityCallSound && ++ticks % 2 == 0) LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        TeslaMapsConfig c = cfg();
        if (warnUntil == 0 || !c.hoppityCallWarning || !c.hoppityCallFlash) return;
        int alpha = (int) ((2 + Math.sin(System.currentTimeMillis() / 1000.0)) * 255 / 4);
        alpha = Math.max(0, Math.min(255, alpha));
        int rgb = TeslaMapsConfig.parseColor(c.hoppityCallFlashColor) & 0xFFFFFF;
        Minecraft mc = Minecraft.getInstance();
        g.fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), (alpha << 24) | rgb);
    }
}
