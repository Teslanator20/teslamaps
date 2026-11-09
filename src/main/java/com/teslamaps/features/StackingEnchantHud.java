package com.teslamaps.features;

// Ported from Devonian's StackingEnchantProgressHud.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.NumberUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

public final class StackingEnchantHud {

    private static final String SAMPLE = "§bExpertise §eIX §f(§c14.9k §f/ §a15k§f)";
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private static HudEditElement el;
    private static String line;
    private static int lineWidth;
    private static CustomData lastData;
    private static boolean lastCompact;

    private StackingEnchantHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        el = HudEditElement.of("stackingEnchantHud", "Stacking Enchant Progress")
                .enabled(() -> cfg().stackingEnchantHud)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, mc.font.width(SAMPLE)));
        Feature.of("StackingEnchantHud").when(() -> cfg().stackingEnchantHud)
                .tick(StackingEnchantHud::tick)
                .hud("stackingenchanthud", StackingEnchantHud::render);
    }

    private static void tick() {
        ItemStack held = Minecraft.getInstance().player.getMainHandItem();
        CustomData data = held.isEmpty() ? null : held.get(DataComponents.CUSTOM_DATA);
        boolean compact = cfg().stackingEnchantHudCompact;
        if (data == lastData && compact == lastCompact) return;
        lastData = data;
        lastCompact = compact;
        EnchantColors.StackProgress p = data == null ? null : EnchantColors.stackingProgress(held);
        if (p == null) {
            line = null;
            return;
        }
        String tier = p.tier() < ROMAN.length ? ROMAN[p.tier()] : String.valueOf(p.tier());
        String cur = compact ? NumberUtil.formatSuffixed(p.progress()) : String.format(Locale.US, "%,d", p.progress());
        String progress = p.next() < 0 ? "§e" + cur
                : "§c" + cur + " §f/ §a" + NumberUtil.formatSuffixed(p.next());
        line = "§b" + p.name() + " §e" + tier + " §f(" + progress + "§f)";
        lineWidth = Minecraft.getInstance().font.width(line);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = line;
        if (l == null || el == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, el.x(), el.y(), el.scale(), l, lineWidth);
    }

    public static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, 12);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
