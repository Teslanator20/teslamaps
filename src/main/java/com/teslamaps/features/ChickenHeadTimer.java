package com.teslamaps.features;

// Ported from SkyHanni's features/itemabilities/ChickenHeadTimer.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.Locale;

public final class ChickenHeadTimer {

    private static final long COOLDOWN = 5000;
    private static final String EGG = "You laid an egg!";
    private static final String SAMPLE = "Chicken Head Timer: §b3.2s";

    private static Feature feature;
    private static HudEditElement hud;
    private static boolean wearing;
    private static long lastEgg;
    private static Object lastLevel;
    private static String line;
    private static int ticks;

    private ChickenHeadTimer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("chickenHead", "Chicken Head Timer").section("Timers")
                .enabled(() -> cfg().chickenHeadTimer)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        feature = Feature.of("ChickenHeadTimer").section("Timers").when(() -> cfg().chickenHeadTimer)
                .tick(ChickenHeadTimer::tick)
                .serverChat(ChickenHeadTimer::onChat)
                .hud("chickenhead", ChickenHeadTimer::render)
                .config("Timers", b -> {
                    b.label("Chicken Head");
                    b.toggle("Chicken Head Timer", () -> cfg().chickenHeadTimer, v -> cfg().chickenHeadTimer = v)
                            .desc("5s egg cooldown while a Chicken Head is on your head (/tmap gui)");
                    b.toggle("  Hide Egg Message", () -> cfg().chickenHeadHideChat, v -> cfg().chickenHeadHideChat = v)
                            .desc("Hides \"You laid an egg!\"");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            lastEgg = System.currentTimeMillis();
        }
        if (++ticks % 5 == 0) {
            wearing = SkyblockUtils.isInSkyblock()
                    && "CHICKEN_HEAD".equals(ItemUtil.skyblockId(mc.player.getItemBySlot(EquipmentSlot.HEAD)));
        }
        if (!wearing) { line = null; return; }
        long left = COOLDOWN - (System.currentTimeMillis() - lastEgg);
        line = left <= 0 ? "Chicken Head Timer: §aNow" : "Chicken Head Timer: §b" + String.format(Locale.ROOT, "%.1fs", left / 1000.0);
    }

    private static void onChat(String text) {
        if (wearing && EGG.equals(StringUtil.stripTrim(text))) lastEgg = System.currentTimeMillis();
    }

    /** ChatFilter: true = drop the line. */
    public static boolean hideLine(String stripped) {
        TeslaMapsConfig c = cfg();
        return c.chickenHeadHideChat && wearing && feature != null && feature.active() && EGG.equals(stripped.trim());
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
