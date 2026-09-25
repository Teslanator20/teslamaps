package com.teslamaps.features.pets;

// Ported from Skysoft's ActivePetOverlay / PetDisplayTextFormatter (text layout);
// rate + ETA lines from NEU's PetInfoOverlay, xp ring after Skysoft's PetDisplayRenderer
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.profileviewer.data.PetIcons;
import com.teslamaps.registry.Feature;
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

    private static final int RING_POINTS = 40;
    private static final float[] RING_X = new float[RING_POINTS], RING_Y = new float[RING_POINTS];

    static {
        for (int i = 0; i < RING_POINTS; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / RING_POINTS;
            RING_X[i] = (float) Math.cos(a);
            RING_Y[i] = (float) Math.sin(a);
        }
    }

    private static String[] cachedLines = null;
    private static ItemStack cachedIcon = ItemStack.EMPTY;
    private static float cachedProgress = 0;
    private static int ticks;

    public static void register() {
        Feature.of("PetOverlay").section("Skills").when(() -> TeslaMapsConfig.get().petOverlay).tick(PetOverlay::tick);
    }

    static void tick() {
        if (++ticks < 10) return;
        ticks = 0;
        if (PetTracker.hasPet()) tickNow();
    }

    private static void tickNow() {
        cachedLines = lines();
        cachedIcon = icon();
        cachedProgress = progress();
    }

    private static final String[] MISSING = com.teslamaps.features.TabWidgetHint.lines("Pet");

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.petOverlay) return;
        if (!PetTracker.hasPet()) {
            if (com.teslamaps.features.TabWidgetHint.on() && PetTracker.widgetMissing()) {
                com.teslamaps.features.TabWidgetHint.draw(ctx, Minecraft.getInstance(), c.petOverlayX, c.petOverlayY, c.petOverlayScale, MISSING);
            }
            return;
        }
        if (!SkyblockUtils.isInSkyblock()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (cachedLines == null) tickNow();
        draw(ctx, mc, c.petOverlayX, c.petOverlayY, c.petOverlayScale, cachedLines, cachedIcon, cachedProgress);
    }

    public static void drawPreview(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        boolean live = PetTracker.hasPet() && cachedLines != null;
        ItemStack icon = live ? cachedIcon : PetIcons.forType("GOLDEN_DRAGON");
        draw(ctx, mc, x, y, scale, live ? cachedLines : PREVIEW_LINES, icon,
                live ? cachedProgress : 0.5f);
    }

    private static ItemStack icon() {
        String skin = PetTracker.skinId();
        if (TeslaMapsConfig.get().petOverlaySkinIcon && skin != null) {
            ItemStack s = PetIcons.forSkin(skin);
            if (!s.isEmpty()) return s;
        }
        return PetIcons.forType(PetTracker.internalName());
    }

    private static float progress() {
        if (PetTracker.maxed()) return 1;
        double needed = PetTracker.neededLevelXp();
        return needed > 0 ? (float) Math.min(1, PetTracker.currentLevelXp() / needed) : 0;
    }

    private static String[] lines() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        List<String> out = new ArrayList<>(8);
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

            double rate = PetTracker.xpPerHour();
            if (c.petOverlayXpRate) out.add("§eXP/h§7: §b" + (rate > 0 ? shortFormat(rate) : "-"));
            if (c.petOverlayEta && !PetTracker.maxed()) {
                double toNext = PetTracker.neededLevelXp() - PetTracker.currentLevelXp();
                String internal = PetTracker.internalName();
                int max = PetConstants.maxLevel(internal);
                double toMax = PetConstants.levelToXp(internal, PetTracker.rarity(), max) - PetTracker.totalXp();
                out.add("§eUntil L" + (PetTracker.level() + 1) + "§7: §b" + eta(toNext, rate));
                out.add("§eUntil L" + max + "§7: §b" + eta(toMax, rate));
            }
        }

        String held = PetTracker.heldItem();
        if (held != null && !held.isEmpty()) out.add("§ePet Item§7: §9" + held);
        String last = PetTracker.lastPetLine();
        if (c.petOverlayLastPet && last != null) out.add("§8Last: " + last);
        if (com.teslamaps.features.TabWidgetHint.on() && PetTracker.widgetMissing()) out.add("§cPet tab widget missing §7(/tab)");
        return out.toArray(new String[0]);
    }

    private static String eta(double xp, double rate) {
        if (xp <= 0) return "now";
        if (rate <= 0) return "-";
        long mins = (long) Math.ceil(xp / rate * 60);
        if (mins < 60) return mins + "m";
        long h = mins / 60;
        if (h < 48) return h + "h " + (mins % 60) + "m";
        return (h / 24) + "d " + (h % 24) + "h";
    }

    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale,
                             String[] ls, ItemStack icon, float progress) {
        boolean hasIcon = icon != null && !icon.isEmpty();
        boolean ring = hasIcon && TeslaMapsConfig.get().petOverlayRing;
        int iconBox = ring ? 24 : 18;
        int textOffset = hasIcon ? iconBox + 2 : 0;
        int w = width(mc, ls) + textOffset;
        int h = Math.max(ls.length * 10, hasIcon ? iconBox : 0);

        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        TeslaGui.backdropRect(ctx, -4, -4, w + 8, h + 4);
        if (hasIcon) {
            int iy = Math.max(0, (h - iconBox) / 2);
            if (ring) {
                drawRing(ctx, 12, iy + 12, progress);
                ctx.item(icon, 4, iy + 4);
            } else {
                ctx.item(icon, 0, iy);
            }
        }
        for (int i = 0; i < ls.length; i++) ctx.text(mc.font, ls[i], textOffset, i * 10, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    /** Rarity disc with an xp arc, clockwise from the top. */
    private static void drawRing(GuiGraphicsExtractor ctx, int cx, int cy, float progress) {
        int rarity = 0xFF000000 | rarityRgb();
        int disc = (rarity & 0x00FFFFFF) | 0x55000000;
        for (int dy = -10; dy <= 10; dy++) {
            int half = (int) Math.sqrt(100 - dy * dy);
            ctx.fill(cx - half, cy + dy, cx + half, cy + dy + 1, disc);
        }
        int lit = Math.round(progress * RING_POINTS);
        for (int i = 0; i < RING_POINTS; i++) {
            int px = cx + Math.round(RING_X[i] * 11), py = cy + Math.round(RING_Y[i] * 11);
            ctx.fill(px - 1, py - 1, px + 1, py + 1, i < lit ? 0xFF55FFFF : 0xFF333333);
        }
    }

    private static int rarityRgb() {
        return switch (PetTracker.rarityColor()) {
            case "§f" -> 0xFFFFFF;
            case "§a" -> 0x55FF55;
            case "§9" -> 0x5555FF;
            case "§5" -> 0xAA00AA;
            case "§6" -> 0xFFAA00;
            case "§d" -> 0xFF55FF;
            default -> 0xAAAAAA;
        };
    }

    private static int width(Minecraft mc, String[] ls) {
        int w = 0;
        for (String l : ls) w = Math.max(w, mc.font.width(l));
        return w;
    }

    public static int[] previewSize(Minecraft mc) {
        String[] ls = PetTracker.hasPet() && cachedLines != null ? cachedLines : PREVIEW_LINES;
        int iconBox = TeslaMapsConfig.get().petOverlayRing ? 24 : 18;
        return new int[]{width(mc, ls) + iconBox + 2 + 8, Math.max(ls.length * 10, iconBox) + 4};
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
