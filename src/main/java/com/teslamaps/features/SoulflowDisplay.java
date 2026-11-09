package com.teslamaps.features;

// Concept from Skytils' ItemFeatures.kt (SoulflowHud, lowSoulflowPing)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public final class SoulflowDisplay {

    private static final List<String> SAMPLE = List.of("§3100⸎ Soulflow");

    private static List<String> lines = List.of();
    private static int width;
    private static boolean warned;
    private static int ticks;
    private static HudEditElement hud;

    private SoulflowDisplay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean on() {
        TeslaMapsConfig c = cfg();
        return c.deployableOverlay && (c.soulflowDisplay || c.lowSoulflowWarning);
    }

    public static void register() {
        hud = HudEditElement.of("soulflowDisplay", "Soulflow Display").section("Skills")
                .enabled(() -> cfg().deployableOverlay && cfg().soulflowDisplay)
                .size(() -> TextHud.width(SAMPLE), () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        TextHud.width(lines.isEmpty() ? SAMPLE : lines)));
        Feature.of("SoulflowDisplay").section("Skills").when(SoulflowDisplay::on)
                .tick(SoulflowDisplay::tick)
                .hudHiddenInInventory("soulflowdisplay", SoulflowDisplay::render);
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        String value = SkyblockUtils.isInSkyblock() ? internalized(mc.player.getInventory()) : null;
        if (value == null) {
            lines = List.of();
            return;
        }
        lines = List.of("§3" + value);
        width = TextHud.width(lines);

        TeslaMapsConfig c = cfg();
        int amount = number(value);
        if (!c.lowSoulflowWarning || amount < 0) return;
        if (amount <= c.lowSoulflowThreshold) {
            if (!warned) {
                mc.gui.hud.setTimes(0, 20, 5);
                mc.gui.hud.setTitle(Component.literal("§cLow Soulflow"));
                warned = true;
            }
        } else {
            warned = false;
        }
    }

    private static String internalized(Inventory inv) {
        for (int i = 0, n = inv.getContainerSize(); i < n; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            String id = ItemUtil.skyblockId(s);
            if (!id.equals("SOULFLOW_PILE") && !id.equals("SOULFLOW_BATTERY") && !id.equals("SOULFLOW_SUPERCELL")) continue;
            ItemLore lore = ItemUtil.lore(s);
            if (lore == null) continue;
            for (Component line : lore.lines()) {
                String l = StringUtil.strip(line.getString()).trim();
                if (l.startsWith("Internalized: ")) return l.substring(14);
            }
        }
        return null;
    }

    private static int number(String s) {
        int n = 0;
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isDigit(ch)) {
                n = n * 10 + (ch - '0');
                any = true;
            } else if (ch != ',' && any) break;
        }
        return any ? n : -1;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || lines.isEmpty() || !cfg().soulflowDisplay) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
