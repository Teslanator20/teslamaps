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

    public static final String LINES_ID = "petOverlay";
    private static final String[] LINE_KEYS = {"name", "progress", "total", "rate", "eta", "item", "share", "last"};
    private static final String[] LINE_LABELS = {"Name + Level", "Level Progress", "Total Pet XP", "XP/h",
            "Until Next / Max Level", "Pet Item", "Exp Share Pets", "Last Pet"};

    static {
        com.teslamaps.utils.LineOrder.define(LINES_ID, LINE_KEYS, LINE_LABELS);
    }

    private static final ItemStack[] NO_SHARE = new ItemStack[0];
    private static ItemStack[] cachedShare = NO_SHARE;
    private static boolean[] cachedShareOff = new boolean[0];
    private static float shownProgress = -1;

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
        shareIcons();
    }

    private static void shareIcons() {
        if (!TeslaMapsConfig.get().petOverlayExpShare) {
            cachedShare = NO_SHARE;
            return;
        }
        PetStore.Pet[] slots = PetStore.expShareSlots();
        int active = com.teslamaps.api.ElectionApi.hasPerk("Sharing is Caring") ? 3 : 1;
        List<ItemStack> icons = new ArrayList<>(3);
        List<Boolean> off = new ArrayList<>(3);
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null || slots[i].type == null) continue;
            ItemStack s = TeslaMapsConfig.get().petOverlaySkinIcon && slots[i].skin != null ? PetIcons.forSkin(slots[i].skin) : ItemStack.EMPTY;
            if (s.isEmpty()) s = PetIcons.forType(slots[i].type);
            icons.add(s);
            off.add(i >= active);
        }
        cachedShare = icons.toArray(NO_SHARE);
        boolean[] o = new boolean[off.size()];
        for (int i = 0; i < o.length; i++) o[i] = off.get(i);
        cachedShareOff = o;
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
        float p = cachedProgress;
        if (c.petOverlayAnimate) {
            if (shownProgress < 0 || p < shownProgress - 0.5f) shownProgress = p;
            else shownProgress += (p - shownProgress) * Math.min(1f, delta.getRealtimeDeltaTicks() * 0.15f);
            p = shownProgress;
        }
        draw(ctx, mc, c.petOverlayX, c.petOverlayY, c.petOverlayScale, cachedLines, cachedIcon, p);
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
        com.teslamaps.utils.LineOrder.Lines l = com.teslamaps.utils.LineOrder.lines(LINES_ID);
        l.key("name").add("§7[Lvl " + PetTracker.level() + "] " + PetTracker.rarityColor()
                + PetTracker.displayName() + (PetTracker.hasSkin() ? " §d✦" : ""));

        if (PetTracker.totalXp() >= 0) {
            l.key("progress");
            if (PetTracker.maxed()) {
                double overflow = PetTracker.overflowXp();
                if (overflow > 0) l.add("§eMax-Level XP§7: §7+§b" + separators(overflow));
                else l.add("§eLevel Progress§7: §bMAX LEVEL");
            } else {
                double cur = PetTracker.currentLevelXp();
                double needed = PetTracker.neededLevelXp();
                double pct = needed > 0 ? Math.min(100, cur / needed * 100) : 0;
                l.add("§eLevel Progress§7: §b" + separators(cur) + "§9/§b" + shortFormat(needed)
                        + " §7- §e" + String.format(Locale.ROOT, "%.1f", pct) + "%");
            }
            l.key("total").add("§ePet XP§7: §b" + separators(PetTracker.totalXp()));

            double rate = PetTracker.xpPerHour();
            if (c.petOverlayXpRate) l.key("rate").add("§eXP/h§7: §b" + (rate > 0 ? shortFormat(rate) : "-"));
            if (c.petOverlayEta && !PetTracker.maxed()) {
                double toNext = PetTracker.neededLevelXp() - PetTracker.currentLevelXp();
                String internal = PetTracker.internalName();
                int max = PetConstants.maxLevel(internal);
                double toMax = PetConstants.levelToXp(internal, PetTracker.rarity(), max) - PetTracker.totalXp();
                l.key("eta").add("§eUntil L" + (PetTracker.level() + 1) + "§7: §b" + eta(toNext, rate));
                l.add("§eUntil L" + max + "§7: §b" + eta(toMax, rate));
            }
        }

        String held = PetTracker.heldItem();
        if (held != null && !held.isEmpty()) l.key("item").add("§ePet Item§7: §9" + held);
        if (c.petOverlayExpShare) {
            PetStore.Pet[] share = PetStore.expShareSlots();
            int active = com.teslamaps.api.ElectionApi.hasPerk("Sharing is Caring") ? 3 : 1;
            for (int i = 0; i < share.length; i++) {
                PetStore.Pet p = share[i];
                if (p == null || p.type == null) continue;
                l.key("share").add((i >= active ? "§8Share (off): §7" : "§eShare§7: ") + "[Lvl " + p.level + "] "
                        + rarityCode(p.rarity) + PetConstants.displayName(p.type));
            }
        }
        String last = PetTracker.lastPetLine();
        if (c.petOverlayLastPet && last != null) l.key("last").add("§8Last: " + last);
        List<String> out = l.build();
        if (com.teslamaps.features.TabWidgetHint.on() && PetTracker.widgetMissing()) out.add("§cPet tab widget missing §7(/tab)");
        return out.toArray(new String[0]);
    }

    private static String rarityCode(String rarity) {
        if (rarity == null) return "§7";
        return switch (rarity) {
            case "COMMON" -> "§f";
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            default -> "§7";
        };
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
        ItemStack[] share = hasIcon ? cachedShare : NO_SHARE;
        int h = Math.max(ls.length * 10, hasIcon ? iconBox + (share.length > 0 ? 10 : 0) : 0);

        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        TeslaGui.backdropRect(ctx, -4, -4, w + 8, h + 4);
        if (hasIcon) {
            int iy = Math.max(0, (h - iconBox) / 2);
            if (share.length > 0) iy = Math.max(0, (h - iconBox - 10) / 2);
            if (ring) {
                drawRing(ctx, 12, iy + 12, progress);
                ctx.item(icon, 4, iy + 4);
            } else {
                ctx.item(icon, 0, iy);
            }
            boolean[] off = cachedShareOff;
            for (int i = 0; i < share.length; i++) {
                int sx = i * 8, sy = iy + iconBox + 1;
                pose.pushMatrix();
                pose.translate(sx, sy);
                pose.scale(0.5f, 0.5f);
                ctx.item(share[i], 0, 0);
                pose.popMatrix();
                if (i < off.length && off[i]) ctx.fill(sx, sy, sx + 8, sy + 8, 0x99000000);
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
        return new int[]{width(mc, ls) + iconBox + 2 + 8, Math.max(ls.length * 10, iconBox + (cachedShare.length > 0 ? 10 : 0)) + 4};
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
