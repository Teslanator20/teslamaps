package com.teslamaps.features.pets;

// Ported from Skysoft's ActivePetOverlay / PetDisplayTextFormatter (text layout)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.profileviewer.data.PetIcons;
import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PetOverlay {

    private static final String[] PREVIEW_LINES = {
            "§7[Lvl 100] §6Golden Dragon §d✦",
            "§eLevel Progress§7: §b3,000§9/§b6.0k §7- §e50.0%",
            "§ePet XP§7: §b25,353,230",
            "§ePet Item§7: §9Minos Relic",
    };

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.petOverlay || !PetTracker.hasPet()) return;
        if (!SkyblockUtils.isInSkyblock()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        draw(ctx, mc, c.petOverlayX, c.petOverlayY, c.petOverlayScale, lines(),
                PetIcons.forType(PetTracker.internalName()));
    }

    public static void drawPreview(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        ItemStack icon = PetTracker.hasPet() ? PetIcons.forType(PetTracker.internalName())
                : PetIcons.forType("GOLDEN_DRAGON");
        draw(ctx, mc, x, y, scale, PetTracker.hasPet() ? lines() : PREVIEW_LINES, icon);
    }

    private static String[] lines() {
        List<String> out = new ArrayList<>(4);
        out.add("§7[Lvl " + PetTracker.level() + "] " + PetTracker.rarityColor()
                + PetTracker.displayName() + (PetTracker.hasSkin() ? " §d✦" : ""));

        if (PetTracker.totalXp() >= 0) {
            if (PetTracker.maxed()) {
                double overflow = PetTracker.overflowXp();
                if (overflow > 0) out.add("§eMax-Level XP§7: §7+§b" + separators(overflow));
                else out.add("§eLevel Progress§7: §bMAX LEVEL");
            } else {
                double cur = PetTracker.currentLevelXp();
                double needed = PetTracker.neededLevelXp();
                double pct = needed > 0 ? Math.min(100, cur / needed * 100) : 0;
                out.add("§eLevel Progress§7: §b" + separators(cur) + "§9/§b" + shortFormat(needed)
                        + " §7- §e" + String.format(Locale.ROOT, "%.1f", pct) + "%");
            }
            out.add("§ePet XP§7: §b" + separators(PetTracker.totalXp()));
        }

        String held = PetTracker.heldItem();
        if (held != null && !held.isEmpty()) out.add("§ePet Item§7: §9" + held);
        return out.toArray(new String[0]);
    }

    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale,
                             String[] ls, ItemStack icon) {
        boolean hasIcon = icon != null && !icon.isEmpty();
        int textOffset = hasIcon ? 20 : 0;
        int w = width(mc, ls) + textOffset;
        int h = Math.max(ls.length * 10, hasIcon ? 18 : 0);

        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        TeslaGui.backdropRect(ctx, -4, -4, w + 8, h + 4);
        if (hasIcon) ctx.item(icon, 0, Math.max(0, (h - 18) / 2));
        for (int i = 0; i < ls.length; i++) ctx.text(mc.font, ls[i], textOffset, i * 10, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    private static int width(Minecraft mc, String[] ls) {
        int w = 0;
        for (String l : ls) w = Math.max(w, mc.font.width(l));
        return w;
    }

    public static int[] previewSize(Minecraft mc) {
        String[] ls = PetTracker.hasPet() ? lines() : PREVIEW_LINES;
        return new int[]{width(mc, ls) + 20 + 8, Math.max(ls.length * 10, 18) + 4};
    }

    private static String separators(double v) {
        return String.format(Locale.US, "%,d", (long) v);
    }

    private static String shortFormat(double v) {
        if (v >= 1_000_000_000) return trim(v / 1_000_000_000) + "b";
        if (v >= 1_000_000) return trim(v / 1_000_000) + "m";
        if (v >= 1_000) return trim(v / 1_000) + "k";
        return separators(v);
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
